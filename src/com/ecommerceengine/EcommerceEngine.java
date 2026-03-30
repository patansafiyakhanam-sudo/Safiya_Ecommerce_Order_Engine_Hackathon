package com.ecommerceengine;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;
import java.time.*;

/* ================= MODELS ================= */

class Product {
    String id, name;
    double price;
    int stock, reserved;

    Product(String id, String name, double price, int stock) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.stock = stock;
        this.reserved = 0;
    }
}

class CartItem {
    Product product;
    int qty;

    CartItem(Product p, int q) {
        product = p;
        qty = q;
    }
}

class Cart {
    String userId;
    Map<String, CartItem> items = new HashMap<>();

    Cart(String userId) {
        this.userId = userId;
    }
}

enum OrderStatus {
    CREATED, PAID, FAILED, CANCELLED
}

class Order {
    String id, userId;
    Map<String, CartItem> items;
    double total;
    OrderStatus status;

    Order(String id, String userId, Map<String, CartItem> items, double total) {
        this.id = id;
        this.userId = userId;
        this.items = items;
        this.total = total;
        this.status = OrderStatus.CREATED;
    }
}

/* ================= DATABASE ================= */

class DB {
    static Map<String, Product> products = new ConcurrentHashMap<>();
    static Map<String, Cart> carts = new ConcurrentHashMap<>();
    static Map<String, Order> orders = new ConcurrentHashMap<>();
    static Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();
}

/* ================= LOGGER ================= */

class Logger {
    static void log(String msg) {
        System.out.println(LocalDateTime.now() + " : " + msg);
    }
}

/* ================= PRODUCT SERVICE ================= */

class ProductService {

    void addProduct(String id, String name, double price, int stock) {
        if (DB.products.containsKey(id)) {
            System.out.println("Duplicate Product ID!");
            return;
        }
        DB.products.put(id, new Product(id, name, price, stock));
        System.out.println("Product Added!");
    }

    void viewProducts() {
        for (Product p : DB.products.values()) {
            System.out.println(p.id + " | " + p.name + " | Price:" + p.price +
                    " | Stock:" + p.stock + " | Reserved:" + p.reserved);
        }
    }

    void lowStockAlert() {
        for (Product p : DB.products.values()) {
            if (p.stock < 5) {
                System.out.println("Low stock: " + p.name);
            }
        }
    }
}

/* ================= CART SERVICE ================= */

class CartService {

    Cart getCart(String userId) {
        DB.carts.putIfAbsent(userId, new Cart(userId));
        return DB.carts.get(userId);
    }

    void addToCart(String userId, String pid, int qty) {
        Product p = DB.products.get(pid);
        if (p == null) {
            System.out.println("Product not found");
            return;
        }

        DB.locks.putIfAbsent(pid, new ReentrantLock());
        ReentrantLock lock = DB.locks.get(pid);

        lock.lock();
        try {
            if ((p.stock - p.reserved) < qty) {
                System.out.println("Not enough stock!");
                return;
            }

            Cart cart = getCart(userId);

            if (cart.items.containsKey(pid)) {
                cart.items.get(pid).qty += qty;
            } else {
                cart.items.put(pid, new CartItem(p, qty));
            }

            p.reserved += qty;
            System.out.println("Added to cart!");

        } finally {
            lock.unlock();
        }
    }

    void removeFromCart(String userId, String pid) {
        Cart cart = getCart(userId);
        CartItem item = cart.items.get(pid);
        if (item != null) {
            item.product.reserved -= item.qty;
            cart.items.remove(pid);
            System.out.println("Removed from cart");
        }
    }

    void viewCart(String userId) {
        Cart cart = getCart(userId);

        if (cart.items.isEmpty()) {
            System.out.println("Cart is empty");
            return;
        }

        System.out.println("Cart for user: " + userId);
        double total = 0;

        for (CartItem i : cart.items.values()) {
            double subtotal = i.product.price * i.qty;
            total += subtotal;

            System.out.println(i.product.name +
                    " | Qty: " + i.qty +
                    " | Price: " + i.product.price +
                    " | Subtotal: " + subtotal);
        }

        System.out.println("Total Cart Value: " + total);
    }
}

/* ================= PAYMENT SERVICE ================= */

class PaymentService {
    boolean pay() {
        return new Random().nextBoolean();
    }
}

/* ================= ORDER SERVICE ================= */

class OrderService {

    PaymentService payment = new PaymentService();

    void placeOrder(String userId, String coupon) {
        Cart cart = DB.carts.get(userId);

        if (cart == null || cart.items.isEmpty()) {
            System.out.println("Cart empty!");
            return;
        }

        double total = 0;
        int qty = 0;

        for (CartItem i : cart.items.values()) {
            total += i.product.price * i.qty;
            qty += i.qty;
        }

        total = applyDiscount(total, qty, coupon);

        Order o = new Order("ORD" + System.currentTimeMillis(),
                userId, new HashMap<>(cart.items), total);

        DB.orders.put(o.id, o);

        boolean success = payment.pay();

        if (success) {
            for (CartItem i : cart.items.values()) {
                i.product.stock -= i.qty;
                i.product.reserved -= i.qty;
            }

            o.status = OrderStatus.PAID;
            cart.items.clear();
            System.out.println("Order placed successfully! Order ID: " + o.id);
        } else {
            o.status = OrderStatus.FAILED;
            System.out.println("Payment failed! Order cancelled.");
        }
    }

    double applyDiscount(double total, int qty, String coupon) {
        if (total > 1000) total *= 0.9;
        if (qty > 3) total *= 0.95;

        if ("SAVE10".equalsIgnoreCase(coupon)) total *= 0.9;
        if ("FLAT200".equalsIgnoreCase(coupon)) total -= 200;

        return total;
    }

    void cancelOrder(String id) {
        Order o = DB.orders.get(id);
        if (o == null) return;

        for (CartItem i : o.items.values()) {
            i.product.stock += i.qty;
        }

        o.status = OrderStatus.CANCELLED;
        System.out.println("Order Cancelled");
    }

    void viewOrders() {
        for (Order o : DB.orders.values()) {
            System.out.println(o.id + " | " + o.userId + " | " + o.total + " | " + o.status);
        }
    }

    void searchOrder(String id) {
        Order o = DB.orders.get(id);
        if (o != null)
            System.out.println(o.id + " | " + o.status);
        else
            System.out.println("Order not found");
    }
}

/* ================= CONCURRENT TEST ================= */

class ConcurrentTest {
    static void runTest() {
        CartService cs = new CartService();

        Runnable user1 = () -> cs.addToCart("U1", "P1", 3);
        Runnable user2 = () -> cs.addToCart("U2", "P1", 3);

        Thread t1 = new Thread(user1);
        Thread t2 = new Thread(user2);

        t1.start();
        t2.start();
    }
}

/* ================= MAIN ================= */

public class EcommerceEngine {

    static ProductService ps = new ProductService();
    static CartService cs = new CartService();
    static OrderService os = new OrderService();

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);

        while (true) {
            System.out.println("\n===== MENU =====");
            System.out.println("1.Add Product");
            System.out.println("2.View Products");
            System.out.println("3.Add to Cart");
            System.out.println("4.Remove from Cart");
            System.out.println("5.View Cart");
            System.out.println("6.Place Order");
            System.out.println("7.Cancel Order");
            System.out.println("8.View Orders");
            System.out.println("9.Search Order");
            System.out.println("10.Low Stock Alert");
            System.out.println("11.Concurrent Test");
            System.out.println("0.Exit");

            System.out.print("Enter choice: ");
            int ch = Integer.parseInt(sc.nextLine());

            switch (ch) {
                case 1:
                    System.out.print("Product ID: ");
                    String id = sc.nextLine();
                    System.out.print("Name: ");
                    String name = sc.nextLine();
                    System.out.print("Price: ");
                    double price = Double.parseDouble(sc.nextLine());
                    System.out.print("Stock: ");
                    int stock = Integer.parseInt(sc.nextLine());
                    ps.addProduct(id, name, price, stock);
                    break;

                case 2:
                    ps.viewProducts();
                    break;

                case 3:
                    System.out.print("User ID: ");
                    String user = sc.nextLine();
                    System.out.print("Product ID: ");
                    String pid = sc.nextLine();
                    System.out.print("Qty: ");
                    int qty = Integer.parseInt(sc.nextLine());
                    cs.addToCart(user, pid, qty);
                    break;

                case 4:
                    System.out.print("User ID: ");
                    user = sc.nextLine();
                    System.out.print("Product ID: ");
                    pid = sc.nextLine();
                    cs.removeFromCart(user, pid);
                    break;

                case 5:
                    System.out.print("User ID: ");
                    user = sc.nextLine();
                    cs.viewCart(user);
                    break;

                case 6:
                    System.out.print("User ID: ");
                    user = sc.nextLine();
                    System.out.print("Coupon Code (or press Enter): ");
                    String coupon = sc.nextLine();
                    os.placeOrder(user, coupon);
                    break;

                case 7:
                    System.out.print("Order ID: ");
                    String oid = sc.nextLine();
                    os.cancelOrder(oid);
                    break;

                case 8:
                    os.viewOrders();
                    break;

                case 9:
                    System.out.print("Order ID: ");
                    oid = sc.nextLine();
                    os.searchOrder(oid);
                    break;

                case 10:
                    ps.lowStockAlert();
                    break;

                case 11:
                    ConcurrentTest.runTest();
                    break;

                case 0:
                    System.exit(0);
            }
        }
    }
}