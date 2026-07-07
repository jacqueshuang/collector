"""perfect99 HTTP 服务。

POST /api/send_sms        {"mobile": "13800138000"} → 自动完成滑块验证并发送短信
POST /api/replace_mobile  {"card_no": "...", "mobile": "...", "cert_no": "...", "sms_code": "..."}
GET  /api/health          健康检查
"""
from __future__ import annotations

import asyncio
import json
import logging
import time
from concurrent.futures import ThreadPoolExecutor

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, constr

from collector import Perfect99Client

logger = logging.getLogger("perfect99_service")
logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")

app = FastAPI(title="perfect99 SMS Service", version="1.0.0")

# Node helper 是阻塞调用（~15s），用线程池隔离避免阻塞事件循环
_executor = ThreadPoolExecutor(max_workers=3, thread_name_prefix="sms-worker")


class SmsRequest(BaseModel):
    mobile: constr(pattern=r"^1[3-9]\d{9}$", strict=True)


class ReplaceMobileRequest(BaseModel):
    card_no: constr(min_length=1, strict=True)
    mobile: constr(pattern=r"^1[3-9]\d{9}$", strict=True)
    cert_no: constr(min_length=1, strict=True)
    sms_code: constr(pattern=r"^\d{4,8}$", strict=True)
    access_token: str | None = None


def _send_sms_sync(mobile: str) -> dict:
    """同步执行完整短信发送流程。"""
    client = Perfect99Client()
    t0 = time.time()

    logger.info("[%s] 1/3 检查手机号", mobile)
    check = client.check_mobile_available(mobile)
    logger.info("[%s] check → %s", mobile, check)
    if check.get("code") != 200:
        return {"success": False, "step": "check_mobile", "error": check, "elapsed": round(time.time() - t0, 2)}

    logger.info("[%s] 2/3 生成滑块验证", mobile)
    captcha = client.get_captcha_verify_param(use_pure_python=True)
    logger.info("[%s] captcha → len=%d", mobile, len(captcha.get("captchaVerifyParam", "")))

    logger.info("[%s] 3/3 发送短信", mobile)
    result = client.get_sms_code(mobile, captcha)
    elapsed = round(time.time() - t0, 2)
    logger.info("[%s] sms → %s (%.1fs)", mobile, result, elapsed)

    return {"success": result.get("code") == 200, "data": result, "elapsed": elapsed}


def _replace_mobile_sync(req: ReplaceMobileRequest) -> dict:
    """同步执行重置手机号请求。"""
    client = Perfect99Client()
    t0 = time.time()

    logger.info("[%s] replace_mobile request", req.mobile)
    result = client.replace_mobile(
        req.card_no,
        req.mobile,
        req.cert_no,
        req.sms_code,
        req.access_token,
    )
    elapsed = round(time.time() - t0, 2)
    logger.info("[%s] replace_mobile → %s (%.1fs)", req.mobile, result, elapsed)

    return {"success": result.get("code") == 200, "data": result, "elapsed": elapsed}


@app.post("/api/send_sms")
async def send_sms(req: SmsRequest):
    """发送短信验证码。自动完成滑块验证，约 15-20 秒。"""
    loop = asyncio.get_running_loop()
    try:
        result = await loop.run_in_executor(_executor, _send_sms_sync, req.mobile)
        return result
    except Exception as e:
        logger.exception("[%s] error", req.mobile)
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/api/replace_mobile")
async def replace_mobile(req: ReplaceMobileRequest):
    """重置手机号（/mobile/openApi/replaceMobile）。"""
    loop = asyncio.get_running_loop()
    try:
        result = await loop.run_in_executor(_executor, _replace_mobile_sync, req)
        return result
    except Exception as e:
        logger.exception("[%s] replace_mobile error", req.mobile)
        raise HTTPException(status_code=500, detail=str(e))


@app.get("/api/health")
async def health():
    return {"status": "ok", "workers": _executor._max_workers}


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=8787, log_level="info")
