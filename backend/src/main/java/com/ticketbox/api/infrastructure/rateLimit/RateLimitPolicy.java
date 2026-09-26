package com.ticketbox.api.infrastructure.rateLimit;

import java.util.regex.Pattern;

public record RateLimitPolicy(
        String method,
        Pattern path,
        String resource,
        int limit,
        long windowMs,
        boolean failClosed
) {
    public static RateLimitPolicy of(String method, String pathRegex, String resource,
                                     int limit, long windowMs, boolean failClosed) {
        return new RateLimitPolicy(method, Pattern.compile(pathRegex), resource, limit, windowMs, failClosed);
    }

    public boolean matches(String requestMethod, String requestPath) {
        return method.equals(requestMethod) && path.matcher(requestPath).matches();
    }
}
