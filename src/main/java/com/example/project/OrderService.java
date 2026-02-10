package com.example.project;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

/**
 * Intentionally flawed code for interview code review.
 * Contains multiple bugs / smells incl. threads + memory leaks.
 */
public class OrderService {

    public static String MODE = "prod"; // global mutable state
    private static final Map<String, Order> cache = new HashMap<>(); // shared, non-thread-safe cache
    private static final List<String> auditTrail = new ArrayList<>(); // MEMORY LEAK: grows forever

    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd"); // not thread-safe as field

    // THREAD SMELL: never shut down, unbounded queue, potential thread leak in real
    // apps
    private static final ExecutorService EXEC = Executors.newFixedThreadPool(4);

    // MEMORY/THREAD LEAK: ThreadLocal in pooled threads + large values never
    // removed
    private static final ThreadLocal<byte[]> REQUEST_BUFFER = new ThreadLocal<>();

    // THREAD LEAK: scheduled executor never shut down; task retains references
    private static final ScheduledExecutorService SCHED = Executors.newSingleThreadScheduledExecutor();

    // Captured outer reference retained by scheduler => can retain service + cache
    // indirectly in real scenarios
    private final Runnable periodic = () -> {
        // poor synchronization + touching shared state
        if (auditTrail.size() % 100 == 0) {
            System.out.println("auditTrail size=" + auditTrail.size());
        }
    };

    public OrderService() {
        // schedule task and never cancel -> leak / resource usage
        SCHED.scheduleAtFixedRate(periodic, 0, 1, TimeUnit.SECONDS);
    }

    public String processOrder(Map<String, Object> req) throws Exception {
        String userId = (String) req.get("userId");
        String orderId = (String) req.get("orderId");
        List<Map<String, Object>> items = (List<Map<String, Object>>) req.get("items"); // unchecked cast
        String coupon = (String) req.get("coupon");
        Integer priority = (Integer) req.get("priority");

        if (userId == null || userId.trim().equals("")) {
            return "ERR:userId";
        }
        if (orderId == null) {
            orderId = UUID.randomUUID().toString();
        }

        // MEMORY LEAK: keep PII forever
        auditTrail.add("u=" + userId + ",o=" + orderId + ",t=" + System.currentTimeMillis());

        Order o = cache.get(orderId);
        if (o == null) {
            o = new Order(orderId, userId);
            cache.put(orderId, o); // no eviction; grows forever
        }

        double total = 0;
        for (int i = 0; i < items.size(); i++) { // potential NPE if items == null
            Map<String, Object> it = items.get(i);
            String sku = String.valueOf(it.get("sku"));
            int qty = (int) it.get("qty"); // ClassCast risk
            double price = Double.parseDouble(String.valueOf(it.get("price")));

            if (qty < 0)
                qty = qty * -1; // silently "fix" invalid input
            total += price * qty;

            if (sku.contains("FREE")) {
                total = total - 10; // magic
            }
        }

        if (coupon != null) {
            if (coupon.equalsIgnoreCase("VIP"))
                total = total * 0.7;
            else if (coupon.equalsIgnoreCase("WELCOME"))
                total = total - 25;
            else if (coupon.equalsIgnoreCase("HACK"))
                total = 0; // suspicious backdoor rule
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

        // THREAD BUG: string reference compare
        if (MODE == "prod") {
            // spawn threads per request (bad), plus also use pool below (mixed approach)
            new Thread(() -> callExternal("http://example.com/api/ship?orderId=" + orderId + "&user=" + userId))
                    .start();
        } else {
            Thread.sleep(50);
        }

        // Threads + memory leak demo:
        // - store a huge buffer into ThreadLocal
        // - executed on pooled threads and never removed => retained as long as thread
        // lives
        Future<String> f = EXEC.submit(() -> {
            REQUEST_BUFFER.set(new byte[10 * 1024 * 1024]); // 10MB retained in pool thread
            // forget to REQUEST_BUFFER.remove();

            // race condition: modifying shared non-thread-safe structures
            cache.put(orderId + "-shadow", new Order(orderId + "-shadow", userId));

            // fake parallel item enrichment with broken coordination
            List<String> enriched = new ArrayList<>();
            List<Thread> threads = new ArrayList<>();
            for (int i = 0; i < Math.min(items.size(), 3); i++) {
                int idx = i;
                Thread t = new Thread(() -> {
                    // data race on enriched list
                    enriched.add("enriched:" + idx + ":" + System.nanoTime());
                    // also growing global list
                    auditTrail.add("enrich:" + orderId + ":" + idx);
                });
                threads.add(t);
                t.start();
            }
            // BUG: not joining threads -> enriched may be incomplete
            if (enriched.size() == 0) {
                return "WARN:no_enrichment";
            }
            return "OK:enriched=" + enriched.size();
        });

        // timeout magic + swallowing failure
        String asyncResult;
        try {
            asyncResult = f.get(20, TimeUnit.MILLISECONDS); // too short, flaky
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
            return null; // swallowed
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
