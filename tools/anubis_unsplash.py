#!/usr/bin/env python3
"""Anubis PoW 求解 + Unsplash 搜索/下载解析(默认直连,需要代理时设 UNSPLASH_PROXY 环境变量)。

用法:
  python tools/anubis_unsplash.py solve                 # 只过一次质询,预热 cookie
  python tools/anubis_unsplash.py search "ecg monitor"  # 列出候选 [{photoId, alt, author}]
  python tools/anubis_unsplash.py resolve <photoId>     # 解析 download → {base, author}
"""
import hashlib
import html as htmlmod
import json
import os
import re
import subprocess
import sys
import time
import urllib.parse

UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/126.0 Safari/537.36")
PROXY = os.environ.get("UNSPLASH_PROXY", "")  # 需要代理时 export UNSPLASH_PROXY=http://127.0.0.1:7890
JAR = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                   "..", "tmp_work", "anubis_cookies.txt")
JAR = os.path.normpath(JAR)


def curl(url, timeout=60):
    os.makedirs(os.path.dirname(JAR), exist_ok=True)
    if not os.path.exists(JAR):
        open(JAR, "w").close()
    hfile = JAR + ".hdr"
    bfile = JAR + ".body"
    head, body = "", b""
    args = ["curl", "-s", "-A", UA, "--max-time", str(timeout),
            "-b", JAR, "-c", JAR, "-D", hfile, "-o", bfile]
    if PROXY:
        args[1:1] = ["-x", PROXY]
    for attempt in range(4):
        open(hfile, "w").close()
        open(bfile, "wb").close()
        p = subprocess.run([*args, url], capture_output=True)
        head = open(hfile, encoding="utf-8", errors="replace").read()
        body = open(bfile, "rb").read()
        if p.returncode == 0 and head:
            break
        time.sleep(2)
    # 代理 CONNECT 的头块在最前,取最后一个 HTTP 头块
    blocks = re.split(r"\r?\n\r?\n", head)
    head = next((b for b in reversed(blocks) if b.startswith("HTTP/")), "")
    status = 0
    m = re.search(r"HTTP/[\d.]+ (\d+)", head)
    if m:
        status = int(m.group(1))
    return status, head, body


def solve_pow(random_data, difficulty):
    c = difficulty // 2
    odd = difficulty % 2
    t = 0
    rd = random_data.encode()
    while True:
        h = hashlib.sha256(rd + str(t).encode()).digest()
        if not any(h[:c]) and (not odd or h[c] >> 4 == 0):
            return h.hex(), t
        t += 1


def ensure_pass(url):
    """请求 url;若被 Anubis 拦,解 PoW 并重试。返回 (status, headers, body)。"""
    status, head, body = curl(url)
    for _ in range(3):
        if b"anubis_challenge" in body:
            pass  # 下方解析质询
        else:
            lm = re.search(r"(?im)^location:\s*(\S+)", head)
            if not (status in (301, 302, 303, 307, 308) and lm
                    and ".within.website" in lm.group(1)):
                return status, head, body
            # 307 到质询页:取质询页拿 challenge JSON
            loc = urllib.parse.urljoin(url, lm.group(1).strip())
            status, head, body = curl(loc)
            if b"anubis_challenge" not in body:
                return status, head, body
        m = re.search(
            rb'<script id="anubis_challenge" type="application/json">(.*?)</script>',
            body, re.S)
        if not m:
            return status, head, body
        ch = json.loads(m.group(1))
        diff = ch["rules"]["difficulty"]
        t0 = time.time()
        hhex, nonce = solve_pow(ch["challenge"]["randomData"], diff)
        elapsed = int((time.time() - t0) * 1000)
        qs = urllib.parse.urlencode({
            "id": ch["challenge"]["id"], "response": hhex, "nonce": nonce,
            "redir": "/", "elapsedTime": elapsed})
        curl("https://unsplash.com/.within.website/x/cmd/anubis/api/"
             f"pass-challenge?{qs}")
        time.sleep(0.5)
        status, head, body = curl(url)
    return status, head, body


def search(query):
    url = "https://unsplash.com/s/photos/" + urllib.parse.quote(query)
    status, head, body = ensure_pass(url)
    text = body.decode("utf-8", "replace")
    out, seen = [], set()
    # 搜索结果链接形如 /photos/{描述slug}-{11位id}
    for m in re.finditer(
            r'href="/photos/([a-z0-9-]+)-([A-Za-z0-9_-]{11})"', text):
        slug, pid = m.group(1), m.group(2)
        if pid in seen:
            continue
        seen.add(pid)
        out.append({"photoId": pid, "desc": slug.replace("-", " ")})
    return out


def resolve(pid):
    """经照片页解析:og:image → CDN base,og:title → 作者。plus 图标记出来。"""
    url = f"https://unsplash.com/photos/{pid}"
    status, head, body = ensure_pass(url)
    # 跟随 slug 308 跳转
    for _ in range(3):
        lm = re.search(r"(?im)^location:\s*(\S+)", head)
        if not (status in (301, 302, 303, 307, 308) and lm):
            break
        loc = urllib.parse.urljoin(url, lm.group(1).strip())
        status, head, body = ensure_pass(loc)
    text = body.decode("utf-8", "replace")
    im = re.search(r'<meta property="og:image" content="([^"]+)"', text)
    am = re.search(r'<meta property="og:title" content="Photo by (.+?) on Unsplash"',
                   text)
    tm = re.search(r"<title>([^<]+)</title>", text)
    base = im.group(1).split("?")[0] if im else ""
    plus = ("plus.unsplash.com" in base
            or (tm and "Unsplash+" in tm.group(1)))
    return {"photoId": pid,
            "base": "" if plus else base,
            "author": htmlmod.unescape(am.group(1)) if am else "",
            "plus": bool(plus), "status": status}


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    cmd = sys.argv[1]
    if cmd == "solve":
        s, h, b = ensure_pass("https://unsplash.com/")
        print("status", s, "anubis" if b"anubis_challenge" in b else "passed")
    elif cmd == "search":
        print(json.dumps(search(sys.argv[2]), ensure_ascii=False, indent=1))
    elif cmd == "resolve":
        print(json.dumps(resolve(sys.argv[2]), ensure_ascii=False, indent=1))
