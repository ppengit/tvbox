#!/usr/bin/env python3
"""把家宽公网 IP 同步到 Cloudflare 的 tvbox.pfac.fun A 记录。

只依赖标准库，容器里不需要安装 requests。Token 从环境变量或只读 secret 文件读取，
任何日志都不会输出 Token。
"""

from __future__ import annotations

import ipaddress
import json
import os
import time
import urllib.error
import urllib.parse
import urllib.request

API_BASE = "https://api.cloudflare.com/client/v4"
IP_SOURCES = (
    "http://ip.3322.net",
    "http://members.3322.org/dyndns/getip",
    "https://myip.ipip.net",
)
SECRET_FILE = "/run/secrets/cf-pfac-fun.env"


def log(message: str) -> None:
    print(time.strftime("%Y-%m-%d %H:%M:%S"), message, flush=True)


def read_token() -> str:
    token = os.environ.get("CF_API_TOKEN", "").strip()
    if token:
        return token
    try:
        with open(SECRET_FILE, "r", encoding="utf-8") as handle:
            for raw in handle:
                line = raw.strip()
                if line.startswith("CF_API_TOKEN="):
                    return line.split("=", 1)[1].strip().strip('"').strip("'")
    except OSError:
        pass
    return ""


def request_json(url: str, token: str, method: str = "GET", payload: dict | None = None) -> dict:
    body = None
    headers = {
        "Authorization": f"Bearer {token}",
        "User-Agent": "XiaoJi-TVBox-DDNS/1.0",
        "Accept": "application/json",
    }
    if payload is not None:
        body = json.dumps(payload).encode("utf-8")
        headers["Content-Type"] = "application/json"
    request = urllib.request.Request(url, data=body, headers=headers, method=method)
    with urllib.request.urlopen(request, timeout=25) as response:
        return json.loads(response.read().decode("utf-8"))


def detect_public_ip() -> str | None:
    for source in IP_SOURCES:
        try:
            with urllib.request.urlopen(source, timeout=10) as response:
                text = response.read().decode("utf-8", errors="ignore")
            # myip.ipip.net 返回“当前 IP：1.2.3.4  来自于：...”，其余源直接返回 IP。
            candidate = text.replace("\ufeff", "").strip().split()[0]
            if candidate.count(".") == 3:
                ipaddress.IPv4Address(candidate)
                return candidate
        except (OSError, ValueError, urllib.error.URLError):
            continue
    return None


def record_payload(ip: str, proxied: bool) -> dict:
    return {
        "type": "A",
        "name": os.environ.get("CF_RECORD_NAME", "tvbox.pfac.fun"),
        "content": ip,
        "ttl": 1,  # Cloudflare 自动 TTL；橙云代理时必须为 1
        "proxied": proxied,
    }


def sync_once(token: str, zone_id: str, record_name: str, proxied: bool) -> bool:
    ip = detect_public_ip()
    if not ip:
        log("公网 IP 探测失败，本轮跳过")
        return False

    query = urllib.parse.urlencode({"type": "A", "name": record_name})
    result = request_json(
        f"{API_BASE}/zones/{zone_id}/dns_records?{query}",
        token,
    )
    if not result.get("success"):
        log(f"查询 DNS 记录失败：{result.get('errors')}")
        return False
    records = result.get("result") or []
    payload = record_payload(ip, proxied)
    if records:
        record = records[0]
        if record.get("content") == ip and bool(record.get("proxied")) == proxied:
            log(f"{record_name} 已是最新：{ip}（proxied={proxied}）")
            return True
        updated = request_json(
            f"{API_BASE}/zones/{zone_id}/dns_records/{record['id']}",
            token,
            method="PATCH",
            payload=payload,
        )
    else:
        updated = request_json(
            f"{API_BASE}/zones/{zone_id}/dns_records",
            token,
            method="POST",
            payload=payload,
        )
    if not updated.get("success"):
        log(f"写入 DNS 记录失败：{updated.get('errors')}")
        return False
    log(f"{record_name} -> {ip}（proxied={proxied}）已同步")
    return True


def main() -> None:
    token = read_token()
    if not token:
        log("缺少 CF_API_TOKEN，DDNS 空转；请检查 secret 文件")
        while True:
            time.sleep(3600)
    zone_id = os.environ.get("CF_ZONE_ID", "").strip()
    record_name = os.environ.get("CF_RECORD_NAME", "tvbox.pfac.fun").strip()
    proxied = os.environ.get("CF_PROXIED", "true").lower() in ("1", "true", "yes")
    interval = max(60, int(os.environ.get("DDNS_INTERVAL_SECONDS", "300")))
    if not zone_id:
        log("缺少 CF_ZONE_ID，DDNS 空转")
        while True:
            time.sleep(3600)

    while True:
        try:
            sync_once(token, zone_id, record_name, proxied)
        except Exception as error:  # noqa: BLE001 - 单轮失败不能终止容器
            log(f"DDNS 同步异常：{type(error).__name__}: {error}")
        time.sleep(interval)


if __name__ == "__main__":
    main()
