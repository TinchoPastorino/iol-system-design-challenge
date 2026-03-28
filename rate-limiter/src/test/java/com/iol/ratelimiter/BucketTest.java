package com.iol.ratelimiter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BucketTest {

    @Test
    void testInitialTokensEqualCapacity() {
        Bucket bucket = new Bucket(5, 1.0);
        assertEquals(5, bucket.getTokens());
    }

    @Test
    void testConsumeDecrementsTokens() {
        Bucket bucket = new Bucket(3, 1.0);
        assertTrue(bucket.consume());
        assertEquals(2, bucket.getTokens());
        assertTrue(bucket.consume());
        assertEquals(1, bucket.getTokens());
        assertTrue(bucket.consume());
        assertEquals(0, bucket.getTokens());
    }

    @Test
    void testConsumeReturnsFalseWhenEmpty() {
        Bucket bucket = new Bucket(1, 0.0); // no refill
        assertTrue(bucket.consume());       // uses the single token
        assertFalse(bucket.consume());      // empty, should reject
        assertFalse(bucket.consume());      // still empty
    }

    @Test
    void testRefillAddsTokensOverTime() throws InterruptedException {
        // 2 capacity, refill 5 tokens/sec → 1 token every 200ms
        Bucket bucket = new Bucket(2, 5.0);
        assertTrue(bucket.consume());
        assertTrue(bucket.consume());
        assertFalse(bucket.consume()); // empty

        Thread.sleep(250); // wait for ~1 token to refill

        assertTrue(bucket.consume());  // refilled token consumed
        assertFalse(bucket.consume()); // no more tokens yet
    }

    @Test
    void testRefillNeverExceedsCapacity() throws InterruptedException {
        Bucket bucket = new Bucket(3, 100.0); // very fast refill
        assertTrue(bucket.consume());

        Thread.sleep(200); // plenty of time to overshoot

        // Even with aggressive refill, tokens should cap at capacity
        assertTrue(bucket.getTokens() <= 3, "Tokens should never exceed capacity");
    }

    @Test
    void testZeroCapacityAlwaysRejects() {
        Bucket bucket = new Bucket(0, 1.0);
        assertFalse(bucket.consume());
        assertFalse(bucket.consume());
    }

    @Test
    void testZeroRefillRateNoRecovery() {
        Bucket bucket = new Bucket(2, 0.0);
        assertTrue(bucket.consume());
        assertTrue(bucket.consume());
        assertFalse(bucket.consume()); // no refill ever
    }

    @Test
    void testLastRefillTimestampUpdates() throws InterruptedException {
        Bucket bucket = new Bucket(5, 1.0);
        long initialTimestamp = bucket.getLastRefillTimestamp();

        bucket.consume();
        Thread.sleep(50);
        bucket.getTokens(); // triggers refill internally

        // Timestamp should have advanced (or stayed same if no tokens added)
        assertTrue(bucket.getLastRefillTimestamp() >= initialTimestamp);
    }
}
