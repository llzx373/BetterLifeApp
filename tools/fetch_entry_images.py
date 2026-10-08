#!/usr/bin/env python3
"""拉取条目级配图:tools/entry_image_selections.json → assets/images/entries/{entryKey}.webp

条目专属图在 App 端无条件显示(不受章节图关键词门控),所以选图必须确实贴合条目主题。
选图清单 entry_image_selections.json 是人工/辅助挑选的结果,格式:
  [{"key": "<条目 key>", "photoId": "<Unsplash 短 ID>", "note": "<条目标题 · 图内容>"}]
哪些条目缺图:python tools/check_image_relevance.py --json 里 relevant=false 的条目。

下载机制与 tools/fetch_section_images.py 相同(公开 download 端点 302 到 CDN),
署名并入 assets/images/credits.json。

用法: python tools/fetch_entry_images.py [--force]   # 默认跳过已存在的文件
"""
import argparse
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from fetch_section_images import CREDITS, resolve_cdn_url, _open  # noqa: E402

SELECTIONS = r"tools/entry_image_selections.json"
OUT_DIR = r"app/src/main/assets/images/entries"


def main():
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    ap = argparse.ArgumentParser()
    ap.add_argument("--force", action="store_true", help="已存在也重新下载")
    ap.add_argument("--only", default=None, help="只处理这个条目 key")
    args = ap.parse_args()

    with open(SELECTIONS, encoding="utf-8") as f:
        selections = json.load(f)

    os.makedirs(OUT_DIR, exist_ok=True)
    credits = {}
    if os.path.exists(CREDITS):
        with open(CREDITS, encoding="utf-8") as f:
            credits = json.load(f)

    ok, skip, fail = 0, 0, 0
    for row in selections:
        key, pid, note = row["key"], row["photoId"], row["note"]
        if args.only and key != args.only:
            continue
        out = os.path.join(OUT_DIR, f"{key}.webp")
        if os.path.exists(out) and not args.force:
            skip += 1
            continue
        try:
            base, author = resolve_cdn_url(pid)
            img = _open(f"{base}?q=72&fm=webp&w=1600&fit=max")
            if not img.startswith(b"RIFF"):
                raise RuntimeError(f"返回的不是 WebP({len(img)} 字节,头 {img[:12]!r})")
            with open(out, "wb") as f:
                f.write(img)
            credits[key] = {
                "file": f"images/entries/{key}.webp",
                "title": note,
                "author": author,
                "source": f"https://unsplash.com/photos/{pid}",
                "license": "Unsplash License (https://unsplash.com/license)",
            }
            print(f"OK  {key} {note} {len(img)//1024}KB <- {pid} ({author})")
            ok += 1
        except Exception as ex:
            print(f"FAIL {key} {note} {pid}: {ex}")
            fail += 1

    with open(CREDITS, "w", encoding="utf-8") as f:
        json.dump(credits, f, ensure_ascii=False, indent=1)
    print(f"\n完成: {ok} 下载, {skip} 跳过, {fail} 失败;entries 目录共 {len(selections)} 条选图")


if __name__ == "__main__":
    main()
