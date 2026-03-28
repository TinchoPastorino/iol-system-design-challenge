package com.iol.ratelimiter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class TokenBucketRateLimiterTest {
    private TokenBucketRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        // Capacity limit of 3, Refill rate 1 token per second
        rateLimiter = new TokenBucketRateLimiter(3, 1.0);
    }

    @Test
    void testBasicLimit() {
        String userId = "user1";
        assertTrue(rateLimiter.allowRequest(userId)); // token 1
        assertTrue(rateLimiter.allowRequest(userId)); // token 2
        assertTrue(rateLimiter.allowRequest(userId)); // token 3
        
        // Next request should be rejected
        assertFalse(rateLimiter.allowRequest(userId)); // no tokens left
        
        assertEquals(3, rateLimiter.getAllowedRequests());
        assertEquals(1, rateLimiter.getRejectedRequests());
    }

    @Test
    void testRefillBehavior() throws InterruptedException {
        // 3 tokens, 5 per second = 1 token every 200ms
        TokenBucketRateLimiter fastLimiter = new TokenBucketRateLimiter(2, 5.0);
        String userId = "userRefill";
        
        assertTrue(fastLimiter.allowRequest(userId));
        assertTrue(fastLimiter.allowRequest(userId));
        assertFalse(fastLimiter.allowRequest(userId)); // Bucket is empty
        
        // Wait for 1 token to be refilled (>200ms)
        Thread.sleep(250);
        
        // Should be allowed now
        assertTrue(fastLimiter.allowRequest(userId));
        // Next one should fail if we didn't wait long enough for 2
        assertFalse(fastLimiter.allowRequest(userId));
    }

    @Test
    void testIndependentUsers() {
        String userA = "A";
        String userB = "B";

        assertTrue(rateLimiter.allowRequest(userA));
        assertTrue(rateLimiter.allowRequest(userA));
        assertTrue(rateLimiter.allowRequest(userA));
        assertFalse(rateLimiter.allowRequest(userA)); // User A blocked

        // User B should still have full capacity
        assertTrue(rateLimiter.allowRequest(userB));
        assertTrue(rateLimiter.allowRequest(userB));
        assertTrue(rateLimiter.allowRequest(userB));
        assertFalse(rateLimiter.allowRequest(userB)); // User B blocked
    }

    @Test
    void testConcurrentAccess() throws InterruptedException {
        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        
        // 5 capacity
        TokenBucketRateLimiter concurrentLimiter = new TokenBucketRateLimiter(5, 0.0);
        AtomicInteger successCounter = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                if (concurrentLimiter.allowRequest("concurrentUser")) {
                    successCounter.incrementAndGet();
                }
                latch.countDown();
            });
        }
        
        latch.await();
        executor.shutdown();

        // Exactly 5 requests should pass concurrently without any race condition issues
        assertEquals(5, successCounter.get());
    }
}
