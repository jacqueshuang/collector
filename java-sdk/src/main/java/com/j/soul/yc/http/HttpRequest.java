package com.j.soul.yc.http;

import java.util.Map;
import java.util.Objects;

public record HttpRequest(
        String method,
        String url,
        Map<String, String> headers,
        byte[] body,
        String contentType
) {
    public HttpRequest {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(url, "url");
        headers = headers == null ? Map.of() : Map.copyOf(headers);
    }
}
