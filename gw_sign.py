"""GW 网关签名复现：MD5(path&method&nonce&timestamp&key)。

对应 app.js function ce(e)：
  sign_str = "path={p}&method={m}&nonce={n}&timestamp={ts}&key={k}"
  GW-Signature = MD5(sign_str)
  GW-Client = "mall_pc"
无 Authorization 时不含 accessToken 字段。
"""
from __future__ import annotations

import hashlib
import time
import uuid

GW_KEY = "8294e640299aae744184b3a529cd1e2f"
GW_CLIENT = "mall_pc"


def _uuid() -> str:
    """对应 app.js function se()：标准 UUID v4。"""
    return str(uuid.uuid4())


def gw_sign(path: str, method: str, access_token: str | None = None) -> dict:
    """生成 GW 网关签名 headers。

    返回 {"GW-Timestamp", "GW-Nonce", "GW-Signature", "GW-Client"}。
    """
    if not path.startswith("/"):
        path = "/" + path
    # 去掉 query string
    if "?" in path:
        path = path.split("?", 1)[0]

    nonce = _uuid()
    ts = int(time.time() * 1000)

    if access_token and len(access_token) > 7:
        sign_str = (
            f"path={path}&method={method.upper()}&nonce={nonce}"
            f"&timestamp={ts}&accessToken={access_token}&key={GW_KEY}"
        )
    else:
        sign_str = (
            f"path={path}&method={method.upper()}&nonce={nonce}"
            f"&timestamp={ts}&key={GW_KEY}"
        )

    signature = hashlib.md5(sign_str.encode()).hexdigest()

    return {
        "GW-Timestamp": str(ts),
        "GW-Nonce": nonce,
        "GW-Signature": signature,
        "GW-Client": GW_CLIENT,
    }
