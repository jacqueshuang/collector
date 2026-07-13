# python_collector / YC Java SDK

## HTTP transports (`java-sdk`)

- `OkHttpTransport` — default (`TransportType.OKHTTP`); uses OkHttp with connect/read timeouts and optional proxy from `YcClientConfig`.
- `Curl4jTransport` — phase-1 backend uses the system `curl` CLI via `ProcessBuilder` (not a Maven libcurl binding). Requires `curl` on `PATH`. Same constructor API: `Curl4jTransport(YcClientConfig)`.
