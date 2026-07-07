#!/usr/bin/env python3
"""perfect99 短信发送助手 — 通过 DrissionPage 控制真实 Chrome。

用 CDP Input.dispatchMouseEvent 发送真实鼠标事件完成滑块验证。
"""
import json
import time
import sys
import subprocess

from DrissionPage import Chromium, ChromiumOptions


TARGET_URL = "https://uc.perfect99.com/loginAndRegistration?isShow=resetPhone&channel=memberCardLogin"


def install_xhr_hook(tab):
    """拦截 getSmsCodeNewV2 请求。"""
    tab.run_js("""
        window.__smsResponses = [];
        window.__crytoInputs = [];
        const origOpen = XMLHttpRequest.prototype.open;
        const origSend = XMLHttpRequest.prototype.send;
        XMLHttpRequest.prototype.open = function(m, u) {
            this.__url = u;
            return origOpen.apply(this, arguments);
        };
        XMLHttpRequest.prototype.send = function(body) {
            if (this.__url && this.__url.indexOf('getSmsCode') >= 0) {
                const self = this;
                this.addEventListener('load', function() {
                    window.__smsResponses.push({
                        status: self.status,
                        response: self.responseText,
                        body: String(body)
                    });
                });
            }
            return origSend.apply(this, arguments);
        };
    """)


def get_slider_rect(tab):
    """获取滑块和轨道的位置。"""
    result = tab.run_js("""
        const slider = document.querySelector('#aliyunCaptcha-sliding-slider');
        const body = document.querySelector('#aliyunCaptcha-sliding-body');
        if (!slider || !body) return null;
        const sr = slider.getBoundingClientRect();
        const br = body.getBoundingClientRect();
        return {
            startX: sr.x + sr.width / 2,
            startY: sr.y + sr.height / 2,
            endX: br.x + br.width - sr.width / 2,
            endY: br.y + br.height / 2,
            width: br.width
        };
    """)
    return result


def dispatch_mouse(tab, event_type, x, y, button="left", click_count=1):
    """通过 CDP 发送鼠标事件。"""
    tab.run_cdp("Input.dispatchMouseEvent", type=event_type, x=x, y=y,
                button=button, clickCount=click_count)


def drag_slider(tab, rect):
    """用 CDP 鼠标事件模拟真实滑动。"""
    import random

    sx, sy = rect["startX"], rect["startY"]
    ex, ey = rect["endX"], rect["endY"]

    # mousemove 到滑块中心
    dispatch_mouse(tab, "mouseMoved", sx, sy)
    time.sleep(0.1)

    # mousedown
    dispatch_mouse(tab, "mousePressed", sx, sy)
    time.sleep(0.1)

    # 逐步 mousemove（30步，带随机抖动）
    steps = 30
    for i in range(1, steps + 1):
        progress = i / steps
        # 先快后慢的缓动函数
        eased = 1 - (1 - progress) ** 3
        x = sx + (ex - sx) * eased
        y = sy + random.uniform(-2, 2)
        dispatch_mouse(tab, "mouseMoved", x, y)
        time.sleep(random.uniform(0.015, 0.035))

    # 确保到达终点
    dispatch_mouse(tab, "mouseMoved", ex, ey)
    time.sleep(0.05)

    # mouseup
    dispatch_mouse(tab, "mouseReleased", ex, ey)


def send_sms(mobile="13800138000", card="100000000000", port=9222):
    co = ChromiumOptions().set_local_port(port)
    browser = Chromium(co)
    tab = browser.latest_tab

    # 打开页面
    tab.get(TARGET_URL)
    time.sleep(3)

    # 安装 XHR 拦截
    install_xhr_hook(tab)

    # 填写表单
    tab.ele("xpath://input[@placeholder='请输入新的手机号']").input(mobile, clear=True)
    tab.ele("xpath://input[@placeholder='请输入会员卡号']").input(card, clear=True)

    # 点击"获取验证码"
    tab.ele("text:获取验证码").click()
    print("[1] 点击获取验证码", file=sys.stderr)

    # 等待 awsc 弹窗和滑块
    for _ in range(20):
        time.sleep(1)
        rect = get_slider_rect(tab)
        if rect:
            break
    else:
        return {"error": "滑块未出现"}

    print(f"[2] 滑块位置: {rect}", file=sys.stderr)

    # 拖动滑块
    drag_slider(tab, rect)
    print("[3] 滑块拖动完成", file=sys.stderr)

    # 等待 SMS 请求
    for _ in range(30):
        time.sleep(1)
        responses = tab.run_js("return window.__smsResponses || [];")
        if responses:
            print(f"[4] 收到 {len(responses)} 个响应", file=sys.stderr)
            return {
                "smsResponse": responses[0],
                "mobile": mobile,
            }

    # 检查滑块状态
    state = tab.run_js("""
        const slider = document.querySelector('#aliyunCaptcha-sliding-slider');
        return {
            sliderDisplay: slider ? getComputedStyle(slider).display : null,
            sliderLeft: slider ? slider.style.left : null,
            smsCount: window.__smsResponses ? window.__smsResponses.length : 0
        };
    """)
    print(f"[!] 超时，滑块状态: {state}", file=sys.stderr)
    return {"error": "超时未收到 SMS 响应", "state": state}


if __name__ == "__main__":
    mobile = sys.argv[1] if len(sys.argv) > 1 else "13800138000"
    result = send_sms(mobile=mobile)
    print(json.dumps(result, ensure_ascii=False))
