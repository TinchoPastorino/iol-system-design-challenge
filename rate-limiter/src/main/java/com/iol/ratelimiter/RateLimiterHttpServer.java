package com.iol.ratelimiter;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RateLimiterHttpServer {
    private static final Logger logger = Logger.getLogger(RateLimiterHttpServer.class.getName());
    
    // Configuration fields
    private static final Properties props = new Properties();
    private static int capacity = 5;
    private static double refillRate = 1.0;
    private static int port = 8080;

    private final RateLimiter rateLimiter;
    private final ConcurrentHashMap<String, AtomicLong> rejectedPerUser = new ConcurrentHashMap<>();

    public RateLimiterHttpServer(RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    public void start(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        LatencyTracker latencyTracker = new LatencyTracker();
        server.createContext("/allow", new AllowEndpointHandler(rateLimiter, latencyTracker, rejectedPerUser));
        server.createContext("/metrics", new MetricsEndpointHandler(rateLimiter, latencyTracker));
        server.createContext("/metrics/prometheus", new PrometheusEndpointHandler(rateLimiter, latencyTracker, rejectedPerUser));
        
        // Optimización Senio: Pool dinámico basado en núcleos
        int nThreads = Math.max(4, Runtime.getRuntime().availableProcessors() * 2);
        server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(nThreads));
        
        server.start();
        logger.info("Rate Limiter HTTP server started on port " + port + " with " + nThreads + " threads.");
    }

    /**
     * Thread-safe latency tracker using atomic operations.
     * Tracks total processing time, request count, and max latency.
     */
    static class LatencyTracker {
        private final AtomicLong totalNanos = new AtomicLong(0);
        private final AtomicLong requestCount = new AtomicLong(0);
        private final AtomicLong maxNanos = new AtomicLong(0);

        void record(long elapsedNanos) {
            totalNanos.addAndGet(elapsedNanos);
            requestCount.incrementAndGet();
            // Update max using compare-and-swap loop (lock-free)
            long currentMax;
            do {
                currentMax = maxNanos.get();
            } while (elapsedNanos > currentMax && !maxNanos.compareAndSet(currentMax, elapsedNanos));
        }

        double getAvgLatencyMs() {
            long count = requestCount.get();
            return (count == 0) ? 0.0 : (totalNanos.get() / (double) count) / 1_000_000.0;
        }

        double getMaxLatencyMs() {
            return maxNanos.get() / 1_000_000.0;
        }

        long getRequestCount() {
            return requestCount.get();
        }
    }

    static class MetricsEndpointHandler implements HttpHandler {
        private final RateLimiter rateLimiter;
        private final LatencyTracker latencyTracker;

        public MetricsEndpointHandler(RateLimiter rateLimiter, LatencyTracker latencyTracker) {
            this.rateLimiter = rateLimiter;
            this.latencyTracker = latencyTracker;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);// Method Not Allowed
                return;
            }

            int allowed = rateLimiter.getAllowedRequests();
            int rejected = rateLimiter.getRejectedRequests();

            String response = String.format(
                "{\"allowedRequests\": %d, \"rejectedRequests\": %d, "
                + "\"avgLatencyMs\": %.3f, \"maxLatencyMs\": %.3f, "
                + "\"totalProcessed\": %d}",
                allowed, rejected,
                latencyTracker.getAvgLatencyMs(),
                latencyTracker.getMaxLatencyMs(),
                latencyTracker.getRequestCount()
            );
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.getBytes().length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        }
    }

    /**
     * Exposes metrics in Prometheus text exposition format for scraping.
     */
    static class PrometheusEndpointHandler implements HttpHandler {
        private final RateLimiter rateLimiter;
        private final LatencyTracker latencyTracker;
        private final ConcurrentHashMap<String, AtomicLong> rejectedPerUser;

        public PrometheusEndpointHandler(RateLimiter rateLimiter, LatencyTracker latencyTracker,
                ConcurrentHashMap<String, AtomicLong> rejectedPerUser) {
            this.rateLimiter = rateLimiter;
            this.latencyTracker = latencyTracker;
            this.rejectedPerUser = rejectedPerUser;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("# HELP rate_limiter_allowed_total Total number of allowed requests\n");
            sb.append("# TYPE rate_limiter_allowed_total counter\n");
            sb.append("rate_limiter_allowed_total ").append(rateLimiter.getAllowedRequests()).append("\n");
            sb.append("# HELP rate_limiter_rejected_total Total number of rejected requests\n");
            sb.append("# TYPE rate_limiter_rejected_total counter\n");
            sb.append("rate_limiter_rejected_total ").append(rateLimiter.getRejectedRequests()).append("\n");
            sb.append("# HELP rate_limiter_processed_total Total number of processed requests\n");
            sb.append("# TYPE rate_limiter_processed_total counter\n");
            sb.append("rate_limiter_processed_total ").append(latencyTracker.getRequestCount()).append("\n");
            sb.append("# HELP rate_limiter_latency_avg_ms Average processing latency in milliseconds\n");
            sb.append("# TYPE rate_limiter_latency_avg_ms gauge\n");
            sb.append("rate_limiter_latency_avg_ms ").append(String.format("%.3f", latencyTracker.getAvgLatencyMs())).append("\n");
            sb.append("# HELP rate_limiter_latency_max_ms Maximum processing latency in milliseconds\n");
            sb.append("# TYPE rate_limiter_latency_max_ms gauge\n");
            sb.append("rate_limiter_latency_max_ms ").append(String.format("%.3f", latencyTracker.getMaxLatencyMs())).append("\n");
            sb.append("rate_limiter_active_users ").append(rateLimiter.getActiveUsers()).append("\n");
            
            // Per-user rejection metrics
            sb.append("# HELP rate_limiter_rejected_by_user_total Total number of rejected requests by userId\n");
            sb.append("# TYPE rate_limiter_rejected_by_user_total counter\n");
            rejectedPerUser.forEach((userId, count) -> {
                sb.append("rate_limiter_rejected_by_user_total{userId=\"").append(userId).append("\"} ")
                  .append(count.get()).append("\n");
            });

            // Basic JVM metrics
            Runtime runtime = Runtime.getRuntime();
            sb.append("# HELP jvm_memory_used_bytes JVM heap memory currently used\n");
            sb.append("# TYPE jvm_memory_used_bytes gauge\n");
            sb.append("jvm_memory_used_bytes ").append(runtime.totalMemory() - runtime.freeMemory()).append("\n");
            sb.append("# HELP jvm_memory_max_bytes JVM maximum heap memory\n");
            sb.append("# TYPE jvm_memory_max_bytes gauge\n");
            sb.append("jvm_memory_max_bytes ").append(runtime.maxMemory()).append("\n");
            sb.append("# HELP jvm_threads_current Current number of live threads\n");
            sb.append("# TYPE jvm_threads_current gauge\n");
            sb.append("jvm_threads_current ").append(Thread.activeCount()).append("\n");

            String response = sb.toString();
            exchange.getResponseHeaders().set("Content-Type", "text/plain; version=0.0.4; charset=utf-8");
            exchange.sendResponseHeaders(200, response.getBytes().length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        }
    }
    static class AllowEndpointHandler implements HttpHandler {
        private final RateLimiter rateLimiter;
        private final LatencyTracker latencyTracker;
        private final ConcurrentHashMap<String, AtomicLong> rejectedPerUser;

        public AllowEndpointHandler(RateLimiter rateLimiter, LatencyTracker latencyTracker,
                ConcurrentHashMap<String, AtomicLong> rejectedPerUser) {
            this.rateLimiter = rateLimiter;
            this.latencyTracker = latencyTracker;
            this.rejectedPerUser = rejectedPerUser;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            long startNanos = System.nanoTime();

            try {
                if (!"GET".equals(exchange.getRequestMethod())) {
                    exchange.sendResponseHeaders(405, -1);// Method Not Allowed
                    return;
                }

            String query = exchange.getRequestURI().getQuery();
            String userId = parseUserId(query);

            if (userId == null || userId.isEmpty()) {
                sendResponse(exchange, 400, "Missing or empty userId parameter");
                return;
            }

            boolean allowed = rateLimiter.allowRequest(userId);

            // Standard rate-limit headers (RFC 6585 / industry convention)
            long remaining = rateLimiter.getRemainingTokens(userId);
            exchange.getResponseHeaders().set("X-RateLimit-Limit", String.valueOf(rateLimiter.getCapacity()));
            exchange.getResponseHeaders().set("X-RateLimit-Remaining", String.valueOf(remaining));

                if (allowed) {
                    sendResponse(exchange, 200, "OK: Request Allowed");
                } else {
                    // Record per-user rejection
                    rejectedPerUser.computeIfAbsent(userId, k -> new AtomicLong(0)).incrementAndGet();
                    
                    long waitMillis = rateLimiter.getWaitTimeMillis(userId);
                    // Standard Retry-After header expects seconds (integer)
                    long retryAfterSeconds = (long) Math.ceil(waitMillis / 1000.0);
                    exchange.getResponseHeaders().set("Retry-After", String.valueOf(retryAfterSeconds));
                    sendResponse(exchange, 429, "Too Many Requests");
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Internal server error processing request", e);
                sendResponse(exchange, 500, "Internal Server Error");
            } finally {
                latencyTracker.record(System.nanoTime() - startNanos);
            }
        }

        private String parseUserId(String query) {
            if (query == null || query.isEmpty())
                return null;
            try {
                // Robust parsing using URI decoding
                String[] pairs = query.split("&");
                for (String pair : pairs) {
                    int idx = pair.indexOf("=");
                    String key = idx > 0 ? URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8) : pair;
                    if ("userId".equals(key) && idx > 0 && pair.length() > idx + 1) {
                        return URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
                    }
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error parsing userId from query: " + query, e);
            }
            return null;
        }

        private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
            exchange.sendResponseHeaders(statusCode, response.getBytes().length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        }
    }

    private static void loadConfiguration() {
        // 1. Intentar cargar desde el directorio de trabajo (External Override - Docker/Prod)
        File externalConfig = new File("config.properties");
        if (externalConfig.exists()) {
            try (InputStream input = new FileInputStream(externalConfig)) {
                props.load(input);
                logger.info("Loaded configuration from external file: " + externalConfig.getAbsolutePath());
                parseProperties();
                return;
            } catch (IOException e) {
                logger.log(Level.WARNING, "Error loading external config.properties", e);
            }
        }

        // 2. Intentar cargar desde los recursos (Standard Maven - Defaults)
        try (InputStream input = RateLimiterHttpServer.class.getClassLoader()
                .getResourceAsStream("config.properties")) {
            if (input != null) {
                props.load(input);
                logger.info("Loaded configuration from internal resources.");
            } else {
                logger.warning("No config.properties found, using hardcoded defaults.");
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "Error loading internal config.properties", e);
        }
        
        parseProperties();
    }

    private static void parseProperties() {
        try {
            capacity = Integer.parseInt(props.getProperty("rate.limit.capacity", "5"));
            refillRate = Double.parseDouble(props.getProperty("rate.limit.refillRate", "1.0"));
            port = Integer.parseInt(props.getProperty("server.port", "8080"));
        } catch (NumberFormatException e) {
            logger.log(Level.WARNING, "Error parsing configuration numbers, using defaults", e);
        }
    }

    public static void main(String[] args) {
        loadConfiguration();
        
        RateLimiter rateLimiter = new TokenBucketRateLimiter(capacity, refillRate);
        RateLimiterHttpServer server = new RateLimiterHttpServer(rateLimiter);
        try {
            server.start(port);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to start server", e);
        }
    }
}
