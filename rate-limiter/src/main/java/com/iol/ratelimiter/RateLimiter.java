package com.iol.ratelimiter;

public interface RateLimiter {
    /**
     * Determines whether the given user is allowed to make a request.
     *
     * @param userId The unique identifier of the user.
     * @return true if the request is allowed, false if the rate limit has been exceeded.
     */
    boolean allowRequest(String userId);

    /**
     * @return the total number of allowed requests processed by this rate limiter.
     */
    int getAllowedRequests();

    /**
     * @return the total number of rejected requests (Too Many Requests).
     */
    int getRejectedRequests();

    /**
     * @return the remaining tokens for a given user, or the full capacity if unknown.
     */
    long getRemainingTokens(String userId);
    
    /**
     * @return the number of milliseconds to wait until the next token is refilled.
     */
    long getWaitTimeMillis(String userId);

    /**
     * @return the configured maximum capacity per user.
     */
    int getCapacity();

    /**
     * @return the number of users currently tracked (active buckets in memory).
     */
    int getActiveUsers();
}
