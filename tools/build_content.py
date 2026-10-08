#!/usr/bin/env python3
"""解析《高性价比人生指南》book/*.md → app/src/main/assets/entries.json

解析规则复刻自 HowToLiveBetter/index.html(parseReadme + COST_W + ratio 档位算法)。
用法: python tools/build_content.py [--book-dir upstream/HowToLiveBetter/book] [--version <commit>]
       [--prev-entries <上一版 entries.json>]

上游仓库以 submodule 形式挂在 upstream/HowToLiveBetter。
条目主键是稳定 key(标题派生),不是位置序号 SS-NN —— 上游插入条目会顺延条号,
但标题按上游约定只加字不换词,所以 key 稳定;SS-NN 仅保留在 id 字段供迁移映射。

--prev-entries(key 继承):标题「增减几个字」是自然 key 的盲区 —— 改一个字 key 就断。
给了上一版 entries.json 就执行模糊继承(阈值 0.8,候选必须唯一):节先按标题相似度
继承旧节 key,条目再在该节映射到的上一版节内先精确、后模糊继承旧条目 key。
没给(或读取失败,打警告)就完全按自然 key 生成,行为与旧版一致。
"""
import argparse
import difflib
import glob
import hashlib
import json
import os
import re
import sys
from datetime import datetime, timezone

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


def _normalize(title: str) -> str:
    """key 归一化:trim + 折叠连续空白。标题其余部分原样参与哈希。"""
    return re.sub(r"\s+", " ", title.strip())


# key 继承的相似度阈值:节与条目共用 0.8,且候选必须唯一(并列就不继承,宁缺毋滥)
INHERIT_THRESHOLD = 0.8


def _fuzzy_pick(title: str, candidates, threshold: float = INHERIT_THRESHOLD):
    """在 [(key, 标题)] 里找与 title 相似的候选;唯一过阈值才返回 key,否则 None。

    相似度用 difflib.SequenceMatcher 对归一化标题算 ratio;
    并列或多个候选过阈值都返回 None —— 猜错比重来代价大,留给人登记别名。
    """
    norm = _normalize(title)
    hits = [
        key for key, cand_title in candidates
        if difflib.SequenceMatcher(None, norm, _normalize(cand_title)).ratio() >= threshold
    ]
    return hits[0] if len(hits) == 1 else None


def _key(*parts: str) -> str:
    """稳定 key:sha1(归一化标题)前 12 位 hex。"""
    raw = "\n".join(_normalize(p) for p in parts)
    return hashlib.sha1(raw.encode("utf-8")).hexdigest()[:12]


# 参与增量 diff 的内容字段:key/sec/n/hash 等元数据不算
_HASH_FIELDS = ("title", "cost", "human", "gain", "grade", "src", "note",
                "money", "time", "will", "level", "lens")


def _entry_hash(e: dict) -> str:
    raw = "\n".join(e.get(f, "") for f in _HASH_FIELDS)
    return hashlib.sha1(raw.encode("utf-8")).hexdigest()[:12]


def inherit_keys(sections: list, all_entries: list, prev: dict):
    """用上一版 entries.json 做 key 模糊继承,就地改写 sections/all_entries 的 key/secKey。

    两层映射:
    1. 节:自然 key 在上一版节 key 集合里 → 保留;否则对全部上一版节标题模糊匹配,
       唯一过阈值 → 继承旧节 key(同时记下映射到的旧节,供条目候选池用)。
    2. 条目(逐节):候选池 = 该节映射到的上一版节的条目。先在池内找归一化标题
       精确相同的 → 继承其 key(这同时解决节改名导致整节条目自然 key 全变的
       连锁断裂);否则模糊继承;都失败保留自然 key(真新增)。

    继承只动 key/secKey;hash、id(SS-NN) 等其余字段照旧,冲突自检在 main 里随后做。
    """
    prev_secs = prev.get("sections") or []
    prev_entries = prev.get("entries") or []
    if not prev_secs or not prev_entries:
        print("警告: 上一版 entries.json 缺 sections/entries,跳过 key 继承")
        return

    prev_sec_keys = {s["key"] for s in prev_secs}
    prev_entries_by_sec = {}
    for pe in prev_entries:
        prev_entries_by_sec.setdefault(pe.get("secKey", ""), []).append(pe)

    # 1) 节映射:新节 n → (最终 key, 映射到的上一版节 key 或 None)
    final_sec_key, mapped_prev_sec = {}, {}
    renamed_secs = 0
    for s in sections:
        if s["key"] in prev_sec_keys:
            final_sec_key[s["n"]] = s["key"]
            mapped_prev_sec[s["n"]] = s["key"]
            continue
        match = _fuzzy_pick(s["title"], [(ps["key"], ps["title"]) for ps in prev_secs])
        if match:
            print(f"节 key 继承: 第{s['n']}节「{s['title']}」继承旧节 key {match}")
            renamed_secs += 1
            final_sec_key[s["n"]] = match
            mapped_prev_sec[s["n"]] = match
        else:
            mapped_prev_sec[s["n"]] = None  # 全新节(或并列不敢猜),用自然 key
            final_sec_key[s["n"]] = s["key"]
    for s in sections:
        s["key"] = final_sec_key[s["n"]]

    # 2) 条目 key 分配(逐节)
    inherited = 0
    inherited_prev_keys = set()  # 已被继承的旧条目 key,用于「消失 vs 改名」警告
    unmatched_new = {}           # 节 n → 未继承的新条目
    for s in sections:
        pool = prev_entries_by_sec.get(mapped_prev_sec[s["n"]] or "", [])
        pool_by_norm = {}
        for pe in pool:
            pool_by_norm.setdefault(_normalize(pe["title"]), pe)
        for e in (e for e in all_entries if e["sec"] == s["n"]):
            hit = pool_by_norm.get(_normalize(e["title"]))
            if hit is None:
                fuzzy = _fuzzy_pick(e["title"], [(pe["key"], pe["title"]) for pe in pool])
                hit = next((pe for pe in pool if pe["key"] == fuzzy), None)
                if hit is not None:
                    print(f"条目 key 继承: {e['id']}「{e['title']}」"
                          f"模糊继承「{hit['title']}」的 key {hit['key']}")
            if hit is not None:
                e["key"] = hit["key"]
                inherited += 1
                inherited_prev_keys.add(hit["key"])
            else:
                unmatched_new.setdefault(s["n"], []).append(e)
            e["secKey"] = s["key"]

    # 3) 「消失 + 未匹配新增」同节出现 → 可能是改名低于阈值,提醒人工登记别名;
    #    单纯消失(无后继新条目)是正常删除,不警告
    for s in sections:
        pool = prev_entries_by_sec.get(mapped_prev_sec[s["n"]] or "", [])
        gone = [pe for pe in pool if pe["key"] not in inherited_prev_keys]
        new_unmatched = unmatched_new.get(s["n"], [])
        if gone and new_unmatched:
            print(f"警告: 第{s['n']}节「{s['title']}」有 {len(gone)} 条消失、"
                  f"{len(new_unmatched)} 条新条目未匹配,可能是改名低于阈值"
                  f"(确认后请在 app/src/main/assets/key_aliases.json 登记别名):")
            for pe in gone:
                print(f" - 消失 {pe['key']}《{pe['title']}》")
            for e in new_unmatched:
                print(f" - 新增 {e['key']}《{e['title']}》")
    print(f"key 继承: {renamed_secs} 个节改名继承,{inherited}/{len(all_entries)} 条继承旧条目 key")


def enrich(e: dict, sec_title: str):
    """复刻 index.html:dispute/todo/cs/ratio/hay;另生成稳定 key 与内容 hash。"""
    e["secKey"] = _key(sec_title)
    e["key"] = _key(sec_title, e["title"])
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
    e["hash"] = _entry_hash(e)
    return e


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--book-dir", default=r"upstream/HowToLiveBetter/book")
    ap.add_argument("--out", default=r"app/src/main/assets/entries.json")
    ap.add_argument("--version", default="local",
                    help="内容版本号,CI 传上游 submodule 的 commit short hash")
    ap.add_argument("--prev-entries", default=None,
                    help="上一版 entries.json;给了就按标题相似度继承旧 key(阈值 0.8、候选唯一)")
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
        sec["key"] = _key(sec["title"])
        sec["entries"] = len(entries)
        sections.append(sec)
        all_entries.extend(enrich(e, sec["title"]) for e in entries)

    # key 模糊继承:解析全部完成后再做(要看到全量新旧节),继承完再走自检与写出
    if args.prev_entries:
        try:
            with open(args.prev_entries, encoding="utf-8") as f:
                inherit_keys(sections, all_entries, json.load(f))
        except (OSError, json.JSONDecodeError) as ex:
            print(f"警告: 上一版 entries.json 读取失败({ex}),完全按自然 key 生成")

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
    seen = {}
    for e in all_entries:
        if e["key"] in seen:
            problems.append(f"key 冲突: {e['id']} 与 {seen[e['key']]} 同节同标题")
        seen[e["key"]] = e["id"]

    if any(p.startswith("key 冲突") for p in problems):
        print("key 冲突,构建中止:")
        for p in problems:
            if p.startswith("key 冲突"):
                print(" -", p)
        sys.exit(1)

    out = {
        "source": "HowToLiveBetter (github.com/eternity4719/HowToLiveBetter)",
        "contentVersion": args.version,
        "generatedAt": datetime.now(timezone.utc).isoformat(timespec="seconds"),
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
