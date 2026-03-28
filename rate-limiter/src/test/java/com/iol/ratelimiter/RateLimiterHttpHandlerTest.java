package com.iol.ratelimiter;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class RateLimiterHttpHandlerTest {

    @Mock
    private RateLimiter mockRateLimiter;
    @Mock
    private HttpExchange mockExchange;
    
    private RateLimiterHttpServer.LatencyTracker latencyTracker;
    private ConcurrentHashMap<String, AtomicLong> rejectedPerUser;
    private RateLimiterHttpServer.AllowEndpointHandler handler;
    
    private Headers headers;
    private OutputStream responseBody;

    @BeforeEach
    void setUp() {
        latencyTracker = new RateLimiterHttpServer.LatencyTracker();
        rejectedPerUser = new ConcurrentHashMap<>();
        handler = new RateLimiterHttpServer.AllowEndpointHandler(mockRateLimiter, latencyTracker, rejectedPerUser);
        
        headers = new Headers();
        responseBody = new ByteArrayOutputStream();
    }

    @Test
    void testAllowRequest_Success() throws IOException {
        // Prepare mock
        when(mockExchange.getRequestMethod()).thenReturn("GET");
        when(mockExchange.getRequestURI()).thenReturn(URI.create("/allow?userId=tincho"));
        when(mockExchange.getResponseHeaders()).thenReturn(headers);
        when(mockExchange.getResponseBody()).thenReturn(responseBody);
        
        when(mockRateLimiter.allowRequest("tincho")).thenReturn(true);
        when(mockRateLimiter.getRemainingTokens("tincho")).thenReturn(5L);
        when(mockRateLimiter.getCapacity()).thenReturn(10);

        // Execute
        handler.handle(mockExchange);

        // Verify status code 200
        verify(mockExchange).sendResponseHeaders(eq(200), anyLong());
        
        // Verify headers
        assertTrue(headers.containsKey("X-RateLimit-Limit"));
        assertTrue(headers.containsKey("X-RateLimit-Remaining"));
    }

    @Test
    void testAllowRequest_Rejected() throws IOException {
        // Prepare mock
        when(mockExchange.getRequestMethod()).thenReturn("GET");
        when(mockExchange.getRequestURI()).thenReturn(URI.create("/allow?userId=tincho"));
        when(mockExchange.getResponseHeaders()).thenReturn(headers);
        when(mockExchange.getResponseBody()).thenReturn(responseBody);
        
        when(mockRateLimiter.allowRequest("tincho")).thenReturn(false);
        when(mockRateLimiter.getWaitTimeMillis("tincho")).thenReturn(1000L);

        // Execute
        handler.handle(mockExchange);

        // Verify status code 429
        verify(mockExchange).sendResponseHeaders(eq(429), anyLong());
        
        // Verify Retry-After header
        assertTrue(headers.containsKey("Retry-After"));
    }
}
