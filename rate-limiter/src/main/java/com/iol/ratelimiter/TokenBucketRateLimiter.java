package com.iol.ratelimiter;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class TokenBucketRateLimiter implements RateLimiter {
    private static final Logger logger = Logger.getLogger(TokenBucketRateLimiter.class.getName());
    
    private static final long STALE_BUCKET_THRESHOLD_NS = TimeUnit.HOURS.toNanos(1);
    
    private final int capacity;
    private final double refillRate;
    private final ConcurrentHashMap<String, Bucket> buckets;
    private final ScheduledExecutorService cleanupExecutor;
    
    private final AtomicInteger allowedRequests = new AtomicInteger(0);
    private final AtomicInteger rejectedRequests = new AtomicInteger(0);

    public TokenBucketRateLimiter(int capacity, double refillRate) {
        this.capacity = capacity;
        this.refillRate = refillRate;
        this.buckets = new ConcurrentHashMap<>();
        
        // Daemon thread for periodic cleanup (eviction)
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "RateLimiter-Cleanup");
            t.setDaemon(true);
            return t;
        });
        
        // Runs memory cleanup every hour
        this.cleanupExecutor.scheduleAtFixedRate(this::cleanupStaleBuckets, 1, 1, TimeUnit.HOURS);
    }
    
    private void cleanupStaleBuckets() {
        long now = System.nanoTime();
        // Use lastAccessTimestamp to ensure we don't remove buckets of active users 
        // who just haven't needed to refill/consume recently.
        int initialSize = buckets.size();
        buckets.entrySet().removeIf(entry -> (now - entry.getValue().getLastAccessTimestamp()) > STALE_BUCKET_THRESHOLD_NS);
        int removed = initialSize - buckets.size();
        if (removed > 0) {
            logger.log(Level.INFO, "Memory Cleanup: removed {0} inactive buckets", removed);
        }
    }

    @Override
    public boolean allowRequest(String userId) {
        // computeIfAbsent is thread-safe and executes atomically
        Bucket bucket = buckets.computeIfAbsent(userId, k -> new Bucket(capacity, refillRate));
        
        if (bucket.consume()) {
            allowedRequests.incrementAndGet();
            // Use FINE level for allowed requests to avoid log flooding in high traffic
            logger.log(Level.FINE, "200 OK - allowed ({0})", userId);
            return true;
        } else {
            rejectedRequests.incrementAndGet();
            logger.log(Level.WARNING, "429 REJECTED - rate limit exceeded ({0})", userId);
            return false;
        }
    }

    public int getAllowedRequests() {
        return allowedRequests.get();
    }

    public int getRejectedRequests() {
        return rejectedRequests.get();
    }

    @Override
    public long getWaitTimeMillis(String userId) {
        Bucket bucket = buckets.get(userId);
        return (bucket != null) ? bucket.getMillisUntilNextToken() : 0;
    }

    @Override
    public long getRemainingTokens(String userId) {
        Bucket bucket = buckets.get(userId);
        return (bucket != null) ? bucket.getTokens() : capacity;
    }

    @Override
    public int getCapacity() {
        return capacity;
    }

    @Override
    public int getActiveUsers() {
        return buckets.size();
    }
}
