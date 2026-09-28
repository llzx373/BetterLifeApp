#!/usr/bin/env python3
"""把 entries.json 打成内容发布包:manifest.json + entries.json.gz + patch.json

用法:
  python tools/build_content_pack.py [--entries app/src/main/assets/entries.json]
      [--out-dir build/content] [--prev-manifest <path或URL>]
      [--base-url <release资产URL前缀>]

manifest.json 字段:
  contentVersion  内容版本(上游 commit short hash)
  entryCount      条目总数
  entries         [{key, hash}] 全量清单,App 端据此校验与 diff
  removed         相对上一版消失的 key(上一版有、本版没有)
  fullUrl/patchUrl/patchBase  下载地址与增量基线;patchBase=null 表示无增量包

patch.json(仅当有上一版时生成):
  {base, version, updated:[entry...], removedKeys:[...]}
  base 是上一版 contentVersion,App 端版本等于 base 才能用增量包,否则走全量。

检测「同节内条目消失+新增」会打警告:可能是上游改了条目标题(违反只加字约定),
需要人工确认并在 app/src/main/assets/key_aliases.json 里登记别名(App 同步时按它改写用户表)。
"""
import argparse
import gzip
import json
import os
import sys
import urllib.request

DEFAULT_BASE_URL = ("https://github.com/llzx373/BetterLifeApp/"
                    "releases/download/content-latest")


def load_json(src: str):
    if src.startswith("http://") or src.startswith("https://"):
        with urllib.request.urlopen(src, timeout=30) as r:
            return json.loads(r.read().decode("utf-8"))
    with open(src, encoding="utf-8") as f:
        return json.load(f)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--entries", default=r"app/src/main/assets/entries.json")
    ap.add_argument("--out-dir", default=r"build/content")
    ap.add_argument("--prev-manifest", default=None,
                    help="上一版 manifest.json 的路径或 URL;缺省则只产全量包")
    ap.add_argument("--base-url", default=DEFAULT_BASE_URL)
    args = ap.parse_args()

    data = load_json(args.entries)
    version = data.get("contentVersion") or "local"
    entries = data["entries"]

    prev = None
    if args.prev_manifest:
        try:
            prev = load_json(args.prev_manifest)
        except Exception as ex:
            print(f"警告: 上一版 manifest 读取失败({ex}),只产全量包")

    cur_by_key = {e["key"]: e for e in entries}
    prev_by_key = {e["key"]: e for e in prev["entries"]} if prev else {}

    new_keys = [k for k in cur_by_key if k not in prev_by_key]
    gone_keys = [k for k in prev_by_key if k not in cur_by_key]
    changed_keys = [k for k in cur_by_key
                    if k in prev_by_key and prev_by_key[k]["hash"] != cur_by_key[k]["hash"]]

    # 上游改标题事故探测:同节内同时有消失和新增
    gone_secs = {prev_by_key[k].get("secKey") for k in gone_keys}
    suspicious = [k for k in new_keys if cur_by_key[k].get("secKey") in gone_secs]
    if suspicious:
        print("警告: 以下新条目与消失条目同节,可能是上游改了标题"
              "(需登记 app/src/main/assets/key_aliases.json):")
        for k in suspicious:
            old = [pk for pk in gone_keys
                   if prev_by_key[pk].get("secKey") == cur_by_key[k].get("secKey")]
            print(f" - 新增 {k}《{cur_by_key[k]['title']}》;同节消失: "
                  + ", ".join(f"{ok}《{prev_by_key[ok]['title']}》" for ok in old))

    manifest = {
        "contentVersion": version,
        "upstreamCommit": version,
        "entryCount": len(entries),
        "entries": [{"key": e["key"], "hash": e["hash"]} for e in entries],
        "removed": gone_keys,
        "fullUrl": f"{args.base_url}/entries.json.gz",
        "patchUrl": None,
        "patchBase": None,
    }

    os.makedirs(args.out_dir, exist_ok=True)

    with open(os.path.join(args.out_dir, "entries.json.gz"), "wb") as f:
        f.write(gzip.compress(json.dumps(data, ensure_ascii=False).encode("utf-8"),
                              compresslevel=9))

    if prev:
        updated = [cur_by_key[k] for k in new_keys + changed_keys]
        patch = {
            "base": prev["contentVersion"],
            "version": version,
            # sections 全量带在 patch 里(33 节约 30 KB):新条目可能属于新章节,
            # App 端直接整表 upsert,不用判断章节是否变化
            "sections": data["sections"],
            "updated": updated,
            "removedKeys": gone_keys,
        }
        with open(os.path.join(args.out_dir, "patch.json"), "w", encoding="utf-8") as f:
            json.dump(patch, f, ensure_ascii=False, indent=1)
        manifest["patchUrl"] = f"{args.base_url}/patch.json"
        manifest["patchBase"] = prev["contentVersion"]

    with open(os.path.join(args.out_dir, "manifest.json"), "w", encoding="utf-8") as f:
        json.dump(manifest, f, ensure_ascii=False, indent=1)

    print(f"版本: {version},条目: {len(entries)}")
    if prev:
        print(f"相对 {prev['contentVersion']}: 新增 {len(new_keys)},"
              f"变更 {len(changed_keys)},消失 {len(gone_keys)}")
    else:
        print("无上一版,只产全量包")
    for name in ("manifest.json", "entries.json.gz", "patch.json"):
        p = os.path.join(args.out_dir, name)
        if os.path.exists(p):
            print(f"  {name}: {os.path.getsize(p)//1024} KB")


if __name__ == "__main__":
    main()
