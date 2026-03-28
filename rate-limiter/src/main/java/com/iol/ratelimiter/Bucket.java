package com.iol.ratelimiter;

public class Bucket {
    private final int capacity;
    private final long nanosPerToken;
    private long lastRefillTimestamp;
    private long lastAccessTimestamp;
    private long tokens;

    public Bucket(int capacity, double refillRate) {
        this.capacity = capacity;
        // Convert tokens per second to nanoseconds per token (integer arithmetic)
        this.nanosPerToken = (long) (1_000_000_000.0 / refillRate);
        this.tokens = capacity;
        long now = System.nanoTime();
        this.lastRefillTimestamp = now;
        this.lastAccessTimestamp = now;
    }

    /**
     * Synchronized method that calculates elapsed time, refills tokens, and consumes one if available.
     */
    public synchronized boolean consume() {
        this.lastAccessTimestamp = System.nanoTime();
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
        
        if (elapsedNanos >= this.nanosPerToken) {
            // Calculate exact tokens to add based on rounded down nanosecond intervals
            long tokensToAdd = elapsedNanos / this.nanosPerToken;
            
            if (tokensToAdd > 0) {
                // Protect against overflow and cap at capacity
                this.tokens = Math.min(this.capacity, this.tokens + tokensToAdd);
                
                // Advance lastRefillTimestamp by exactly the amount used by added tokens
                // This preserves the "remainder" nanoseconds for the next refill cycle (anti-drift)
                this.lastRefillTimestamp += tokensToAdd * this.nanosPerToken;

                if (this.tokens >= this.capacity) {
                    // If we hit capacity, we can align the timestamp to now to simplify
                    this.lastRefillTimestamp = now;
                }
            }
        }
    }
    
    public synchronized long getMillisUntilNextToken() {
        if (this.tokens >= 1) return 0;
        long now = System.nanoTime();
        long waitNanos = lastRefillTimestamp + nanosPerToken - now;
        return (waitNanos > 0) ? (long) Math.ceil(waitNanos / 1_000_000.0) : 0;
    }

    public synchronized long getTokens() {
        this.lastAccessTimestamp = System.nanoTime();
        refill();
        return this.tokens;
    }

    public synchronized long getLastAccessTimestamp() {
        return this.lastAccessTimestamp;
    }

    public synchronized long getLastRefillTimestamp() {
        return this.lastRefillTimestamp;
    }
}
