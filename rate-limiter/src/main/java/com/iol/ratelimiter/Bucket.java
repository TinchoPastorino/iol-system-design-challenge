package com.iol.ratelimiter;

public class Bucket {
    private final int capacity;
    private final double refillRate; // tokens per second
    private long lastRefillTimestamp;
    private long tokens;

    public Bucket(int capacity, double refillRate) {
        this.capacity = capacity;
        this.refillRate = refillRate;
        this.tokens = capacity;
        this.lastRefillTimestamp = System.nanoTime();
    }

    /**
     * Synchronized method that calculates elapsed time, refills tokens, and consumes one if available.
     */
    public synchronized boolean consume() {
        refill();
        if (this.tokens >= 1) {
            this.tokens -= 1;
            return true;
        }
        return false;
    }

    private void refill() {
        long now = System.nanoTime();
        long elapsedNanos = now - this.lastRefillTimestamp;
        
        if (elapsedNanos > 0) {
            // Calculate exact tokens to add based on nanoseconds elapsed
            long tokensToAdd = (long) (elapsedNanos * this.refillRate / 1_000_000_000.0);
            
            if (tokensToAdd > 0) {
                this.tokens += tokensToAdd;
                
                if (this.tokens >= this.capacity) {
                    this.tokens = this.capacity;
                    this.lastRefillTimestamp = now;
                } else {
                    // Prevent token drift by advancing the timestamp only by the nanoseconds consumed by the added tokens
                    long consumedNanos = (long) (tokensToAdd * 1_000_000_000.0 / this.refillRate);
                    this.lastRefillTimestamp += consumedNanos;
                }
            }
        }
    }
    
    // For testing visibility
    public synchronized long getMillisUntilNextToken() {
        if (this.tokens >= 1) return 0;
        long nanosForOneToken = (long) (1_000_000_000.0 / this.refillRate);
        long now = System.nanoTime();
        long waitNanos = lastRefillTimestamp + nanosForOneToken - now;
        return (waitNanos > 0) ? (long) Math.ceil(waitNanos / 1_000_000.0) : 0;
    }

    public synchronized long getTokens() {
        refill();
        return this.tokens;
    }

    public synchronized long getLastRefillTimestamp() {
        return this.lastRefillTimestamp;
    }
}
