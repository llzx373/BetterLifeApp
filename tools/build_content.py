#!/usr/bin/env python3
"""解析《高性价比人生指南》book/*.md → app/src/main/assets/entries.json

解析规则复刻自 HowToLiveBetter/index.html(parseReadme + COST_W + ratio 档位算法)。
用法: python tools/build_content.py [--book-dir F:/srcs/HowToLiveBetter/book]
"""
import argparse
import glob
import json
import os
import re
import sys

COST_W = {
    "money": {"0": 0, "少": 1, "多": 2},
    "time": {"少": 0, "中": 1, "多": 2},
    "will": {"否": 0, "些": 1, "是": 2},
}

RE_SECTION = re.compile(r"^#{1,2} (\d+)\. (.+)$")
RE_ANY_SECTION = re.compile(r"^#{1,2} ")
RE_ENTRY = re.compile(r"^### (\d+)\. (.+)$")
RE_TAGS = re.compile(r"^<!--\s*成本标签:\s*(.*?)\s*-->")
RE_COST = re.compile(r"^- 成本：(.*)$")
RE_HUMAN = re.compile(r"^- 说人话：(.*)$")
RE_GAIN = re.compile(r"^- 收益：(.*)$")
RE_GRADE = re.compile(r"^- 证据等级：\s*([ABC])")
RE_SRC = re.compile(r"^- 来源：(.*)$")
RE_NOTE = re.compile(r"^- 备注：(.*)$")


def parse_file(path: str):
    """解析单个章节文件,返回 (section_dict, entry_list)。"""
    with open(path, encoding="utf-8") as f:
        lines = f.read().splitlines()

    sec = None
    entry = None
    entries = []
    intro = []

    def flush():
        nonlocal entry
        if entry is not None:
            entries.append(entry)
            entry = None

    for raw in lines:
        line = raw.rstrip()
        m = RE_SECTION.match(line)
        if m and sec is None:
            sec = {"n": int(m.group(1)), "title": m.group(2).strip()}
            continue
        if RE_ANY_SECTION.match(line):
            # 文件内更高的标题(如总目录链接不命中);新章节头在这里不出现
            continue
        if sec is None:
            continue
        m = RE_ENTRY.match(line)
        if m:
            flush()
            entry = {
                "sec": sec["n"],
                "n": int(m.group(1)),
                "title": m.group(2).strip(),
                "cost": "", "human": "", "gain": "",
                "grade": "", "src": "", "note": "",
                "money": "", "time": "", "will": "",
                "level": "", "lens": "",
            }
            continue
        m = RE_TAGS.match(line)
        if m and entry is not None:
            for kv in m.group(1).split():
                k, _, v = kv.partition("=")
                if k == "钱":
                    entry["money"] = v
                elif k == "时间":
                    entry["time"] = v
                elif k == "毅力":
                    entry["will"] = v
                elif k == "收益":
                    entry["level"] = v
                elif k == "口径":
                    entry["lens"] = v
            continue
        if entry is not None:
            for regex, field in (
                (RE_COST, "cost"), (RE_HUMAN, "human"), (RE_GAIN, "gain"),
                (RE_SRC, "src"), (RE_NOTE, "note"),
            ):
                m = regex.match(line)
                if m:
                    entry[field] = m.group(1)
                    break
            else:
                m = RE_GRADE.match(line)
                if m:
                    entry["grade"] = m.group(1)
            continue
        if line:
            intro.append(line)
    flush()
    if sec is not None:
        sec["intro"] = "\n".join(intro)
    return sec, entries


def enrich(e: dict):
    """复刻 index.html:dispute/todo/cs/ratio/hay。"""
    e["dispute"] = e["note"].startswith("争议")
    e["todo"] = bool(re.search(r"待核实|TODO", e["src"] + e["gain"] + e["note"] + e["cost"]))
    e["cs"] = (COST_W["money"].get(e["money"], 0)
               + COST_W["time"].get(e["time"], 0)
               + COST_W["will"].get(e["will"], 0))
    if e["level"] == "大":
        e["ratio"] = "极高" if e["cs"] == 0 else ("高" if e["cs"] <= 2 else "一般")
    elif e["level"] == "中":
        e["ratio"] = "高" if e["cs"] == 0 else "一般"
    else:
        e["ratio"] = "一般"
    e["id"] = f"{e['sec']:02d}-{e['n']:02d}"
    e["hay"] = "\n".join([e["title"], e["human"], e["cost"], e["gain"],
                          e["note"], e["src"], e["grade"]]).replace("**", "").lower()
    return e


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--book-dir", default=r"F:/srcs/HowToLiveBetter/book")
    ap.add_argument("--out", default=r"app/src/main/assets/entries.json")
    args = ap.parse_args()

    files = sorted(glob.glob(os.path.join(args.book_dir, "*.md")))
    if not files:
        sys.exit(f"在 {args.book_dir} 没找到章节文件")

    sections, all_entries = [], []
    for path in files:
        sec, entries = parse_file(path)
        if sec is None:
            print(f"警告: {os.path.basename(path)} 没有解析出章节头,跳过")
            continue
        sec["entries"] = len(entries)
        sections.append(sec)
        all_entries.extend(enrich(e) for e in entries)

    # 自检
    problems = []
    for e in all_entries:
        for field in ("money", "time", "will", "level", "lens"):
            if not e[field]:
                problems.append(f"{e['id']} 缺成本标签 {field}")
        if not e["title"]:
            problems.append(f"{e['sec']} 节有空标题条目")
        if not e["grade"]:
            problems.append(f"{e['id']} 缺证据等级")

    out = {
        "source": "HowToLiveBetter (github.com/eternity4719/HowToLiveBetter)",
        "sections": sections,
        "entries": all_entries,
    }
    os.makedirs(os.path.dirname(args.out), exist_ok=True)
    with open(args.out, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, indent=1)

    lens_count = {}
    ratio_count = {}
    for e in all_entries:
        lens_count[e["lens"]] = lens_count.get(e["lens"], 0) + 1
        ratio_count[e["ratio"]] = ratio_count.get(e["ratio"], 0) + 1
    print(f"章节: {len(sections)},条目: {len(all_entries)}")
    print(f"口径: {lens_count}")
    print(f"档位: {ratio_count}")
    print(f"争议: {sum(e['dispute'] for e in all_entries)},"
          f"待核实: {sum(e['todo'] for e in all_entries)}")
    if problems:
        print(f"\n{len(problems)} 个问题:")
        for p in problems[:30]:
            print(" -", p)
    print(f"\n已写出 {args.out} ({os.path.getsize(args.out)//1024} KB)")


if __name__ == "__main__":
    main()
