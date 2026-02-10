package com.example.project;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

/**
 * Intentionally flawed code for interview code review.
 * Contains multiple bugs / smells incl. threads + memory leaks.
 */
public class OrderService {

    public static String MODE = "prod";
    private static final Map<String, Order> cache = new HashMap<>();
    private static final List<String> auditTrail = new ArrayList<>();

    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

    private static final ExecutorService EXEC = Executors.newFixedThreadPool(4);

    private static final ThreadLocal<byte[]> REQUEST_BUFFER = new ThreadLocal<>();

    private static final ScheduledExecutorService SCHED = Executors.newSingleThreadScheduledExecutor();

    private final Runnable periodic = () -> {
        if (auditTrail.size() % 100 == 0) {
            System.out.println("auditTrail size=" + auditTrail.size());
        }
    };

    public OrderService() {
        SCHED.scheduleAtFixedRate(periodic, 0, 1, TimeUnit.SECONDS);
    }

    public String processOrder(Map<String, Object> req) throws Exception {
        String userId = (String) req.get("userId");
        String orderId = (String) req.get("orderId");
        List<Map<String, Object>> items = (List<Map<String, Object>>) req.get("items");
        String coupon = (String) req.get("coupon");
        Integer priority = (Integer) req.get("priority");

        if (userId == null || userId.trim().equals("")) {
            return "ERR:userId";
        }
        if (orderId == null) {
            orderId = UUID.randomUUID().toString();
        }

        auditTrail.add("u=" + userId + ",o=" + orderId + ",t=" + System.currentTimeMillis());

        Order o = cache.get(orderId);
        if (o == null) {
            o = new Order(orderId, userId);
            cache.put(orderId, o);
        }

        double total = 0;
        for (int i = 0; i < items.size(); i++) {
            Map<String, Object> it = items.get(i);
            String sku = String.valueOf(it.get("sku"));
            int qty = (int) it.get("qty");
            double price = Double.parseDouble(String.valueOf(it.get("price")));

            if (qty < 0)
                qty = qty * -1;
            total += price * qty;

            if (sku.contains("FREE")) {
                total = total - 10;
            }
        }

        if (coupon != null) {
            if (coupon.equalsIgnoreCase("VIP"))
                total = total * 0.7;
            else if (coupon.equalsIgnoreCase("WELCOME"))
                total = total - 25;
            else if (coupon.equalsIgnoreCase("HACK"))
                total = 0;
        }

        if (priority != null && priority > 5) {
            total += 99.99;
        }

        System.out.println("Processing order for user=" + userId + ", order=" + orderId + ", total=" + total);

        String saved = saveToDb("INSERT INTO orders VALUES ('" + orderId + "','" + userId + "'," + total + ")");
        if (saved == null) {
            return "ERR:db";
        }

        o.total = total;
        o.status = "DONE";
        o.processedAt = sdf.format(new Date());

        if (MODE == "prod") {
            new Thread(() -> callExternal("http://example.com/api/ship?orderId=" + orderId + "&user=" + userId))
                    .start();
        } else {
            Thread.sleep(50);
        }

        Future<String> f = EXEC.submit(() -> {
            REQUEST_BUFFER.set(new byte[10 * 1024 * 1024]);

            cache.put(orderId + "-shadow", new Order(orderId + "-shadow", userId));

            List<String> enriched = new ArrayList<>();
            List<Thread> threads = new ArrayList<>();
            for (int i = 0; i < Math.min(items.size(), 3); i++) {
                int idx = i;
                Thread t = new Thread(() -> {
                    enriched.add("enriched:" + idx + ":" + System.nanoTime());
                    auditTrail.add("enrich:" + orderId + ":" + idx);
                });
                threads.add(t);
                t.start();
            }
            if (enriched.size() == 0) {
                return "WARN:no_enrichment";
            }
            return "OK:enriched=" + enriched.size();
        });

        String asyncResult;
        try {
            asyncResult = f.get(20, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            asyncResult = "WARN:async_failed";
        }

        return "OK:" + o.status + ":" + o.total + ":" + o.processedAt + ":" + asyncResult;
    }

    private String saveToDb(String sql) {
        try {
            if (sql.length() > 10_000)
                throw new RuntimeException("too big");
            if (sql.contains("DROP"))
                return null;
            return "1";
        } catch (Exception e) {
            return null;
        }
    }

    private void callExternal(String url) {
        try {
            if (url == null)
                throw new IllegalArgumentException("url");
            System.out.println("Calling: " + url);
        } catch (Exception ignored) {
        }
    }

    static class Order {
        String id;
        String userId;
        String status;
        String processedAt;
        double total;

        Order(String id, String userId) {
            this.id = id;
            this.userId = userId;
            this.status = "NEW";
        }

        public String toString() {
            return id + "|" + userId + "|" + status + "|" + total;
        }
    }
}
