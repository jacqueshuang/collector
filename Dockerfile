FROM ghcr.io/astral-sh/uv:python3.11-bookworm

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates \
    && curl -fsSL https://deb.nodesource.com/setup_20.x | bash - \
    && apt-get install -y --no-install-recommends nodejs \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY python_collector/ /app/python_collector/
COPY node_helper/ /app/node_helper/

RUN cd /app/node_helper && npm ci

WORKDIR /app/python_collector
RUN uv sync --frozen --no-dev

EXPOSE 8787
CMD ["uv", "run", "server.py"]
