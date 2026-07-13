package com.j.soul.yc.http;

import java.util.Map;

public record HttpResponse(int status, Map<String, String> headers, byte[] body) {
    public HttpResponse {
        headers = headers == null ? Map.of() : Map.copyOf(headers);
        body = body == null ? new byte[0] : body;
    }
}
