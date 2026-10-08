#!/usr/bin/env python3
"""配图相关性校验:条目详情页回退到章节图时,哪些条目与图片主题不沾边。

判断规则与 App 端(AssetImage.kt rememberEntryBanner)保持一致,改任一边要同步另一边:
  - 条目标题命中所属章节图的任一关键词 → 相关
  - 否则正文(hay + human)命中至少 manifest.minBodyHits 个不同关键词 → 相关
  - 都不满足 → 不相关,App 端不显示章节回退图
关键词描述的是图片内容,维护在 tools/fetch_section_images.py 的 SELECTIONS 里,
由该脚本生成 assets/images/manifest.json。条目专属图(images/entries/{key}.webp)不受门控。

用法:
  python tools/check_image_relevance.py              # 每章匹配率 + 不相关条目清单
  python tools/check_image_relevance.py --verbose    # 连相关条目的命中关键词也列出来
  python tools/check_image_relevance.py --json       # 机器可读全量结果(供补图管线用)
"""
import argparse
import io
import json
import sys

ENTRIES = r"app/src/main/assets/entries.json"
MANIFEST = r"app/src/main/assets/images/manifest.json"


def load():
    with open(ENTRIES, encoding="utf-8") as f:
        entries_file = json.load(f)
    with open(MANIFEST, encoding="utf-8") as f:
        manifest = json.load(f)
    return entries_file, manifest


def judge(entry, keywords, min_body_hits):
    """返回 (是否相关, 标题命中, 正文命中)"""
    title = entry["title"].lower()
    body = (entry.get("hay", "") + "\n" + entry.get("human", "")).lower()
    title_hits = [k for k in keywords if k in title]
    body_hits = [k for k in keywords if k in body]
    relevant = bool(title_hits) or len(set(body_hits)) >= min_body_hits
    return relevant, title_hits, body_hits


def main():
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
    ap = argparse.ArgumentParser()
    ap.add_argument("--verbose", action="store_true")
    ap.add_argument("--json", action="store_true", help="输出全量判定结果 JSON 到 stdout")
    args = ap.parse_args()

    entries_file, manifest = load()
    min_body_hits = manifest.get("minBodyHits", 2)
    sections = manifest.get("sections", {})
    sec_titles = {s["key"]: s["title"] for s in entries_file["sections"]}

    results = []  # (entry, relevant, title_hits, body_hits)
    for e in entries_file["entries"]:
        kw = sections.get(e["secKey"], {}).get("keywords", [])
        if not kw:
            continue  # 章节没配关键词 = 不做门控,与 App 行为一致
        results.append((e, *judge(e, kw, min_body_hits)))

    if args.json:
        json.dump(
            [
                {
                    "key": e["key"], "secKey": e["secKey"], "title": e["title"],
                    "relevant": rel, "titleHits": th, "bodyHits": bh,
                }
                for e, rel, th, bh in results
            ],
            sys.stdout, ensure_ascii=False, indent=1,
        )
        print()
        return

    total = len(results)
    shown = sum(1 for _, rel, _, _ in results if rel)
    print(f"共 {total} 条参与门控,章节图会显示 {shown} 条 ({shown * 100 // total}%),"
          f"隐藏 {total - shown} 条 (minBodyHits={min_body_hits})\n")

    for sec_key, sec_title in sec_titles.items():
        rows = [(e, rel, th, bh) for e, rel, th, bh in results if e["secKey"] == sec_key]
        if not rows:
            continue
        ok = sum(1 for _, rel, _, _ in rows if rel)
        print(f"== {sec_title} ({sec_key}) 显示 {ok}/{len(rows)}")
        for e, rel, th, bh in rows:
            if rel:
                if args.verbose:
                    hits = th if th else bh
                    print(f"   [显示] {e['title']}  <- {', '.join(hits)}")
            else:
                print(f"   [隐藏] {e['title']}")
        print()


if __name__ == "__main__":
    main()
