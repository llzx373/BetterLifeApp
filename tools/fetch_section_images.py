#!/usr/bin/env python3
"""拉取章节配图:Unsplash 免费图 → app/src/main/assets/images/sections/{secKey}.webp

图片来源全部是 Unsplash 免费图(非 Unsplash+),Unsplash License 允许商用与再分发,
不强制署名;署名信息仍写入 assets/images/credits.json,在「关于」页可展示。

工作原理:unsplash.com/s/photos 搜索页与 napi 接口都需要登录/鉴权,但
/photos/<短ID>/download 端点是公开的,302 到 images.unsplash.com 的 CDN URL;
把 CDN URL 的参数换成 w=1600&q=72&fm=webp 即得压缩后的 WebP。
(download 端点同时会向作者记一次下载量,符合 Unsplash 的使用约定。)

映射表 SELECTIONS 是人工挑选的结果:列 = 章节 key、Unsplash 短 ID、主题备注、图片主题关键词。
换图 = 改短 ID 重跑;加条目级配图 = 另建 images/entries/{entryKey}.webp 同理。

关键词描述的是「图片内容」而不是章节内容,全部小写。条目详情页回退到章节图时,
App 会按 assets/images/manifest.json 里的关键词做相关性门控:条目标题命中任一关键词,
或正文(hay+human)命中至少 MIN_BODY_HITS 个不同关键词才显示,否则宁可不配图。
判断某条目是否该显示章节图、调关键词后的回归检查,用 tools/check_image_relevance.py。

用法: python tools/fetch_section_images.py [--force]   # 默认跳过已存在的文件
      manifest.json 每次运行都会重写,不触发下载(--only 只下载单章,manifest 仍是全量)
"""
import argparse
import json
import os
import re
import subprocess
import sys
import time
import urllib.parse

UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36"
OUT_DIR = r"app/src/main/assets/images/sections"
CREDITS = r"app/src/main/assets/images/credits.json"
MANIFEST = r"app/src/main/assets/images/manifest.json"

# 正文命中多少个不同关键词才算相关(标题命中 1 个即算)
MIN_BODY_HITS = 2

# (章节 key, Unsplash 短 ID, 主题备注, 图片主题关键词)
SELECTIONS = [
    ("7ee547847f27", "FLdFbeVh4n8", "不要早死 · 安全带卡扣",
     ["安全带", "头盔", "驾驶", "超速", "酒驾", "安全座椅", "摩托", "刹车", "汽车"]),
    ("b2b37b726cf0", "IGfIGP5ONV0", "不要慢慢死 · 健康餐",
     ["蔬菜", "水果", "饮食", "坚果", "谷物", "吃饭", "食物", "红肉", "鱼", "蛋", "奶", "酸奶",
      "饮料", "咖啡", "盐", "辣椒", "加工", "食用油", "外卖", "茶", "吃饭", "做饭", "食堂"]),
    ("a63d7bcc9c8c", "VieM9BdZKFo", "不要浪费精力 · 极简桌面",
     ["桌面", "书桌", "办公", "工作", "电脑", "手机", "通知", "消息", "邮件", "屏幕", "整理",
      "收纳", "极简", "开会", "会议"]),
    ("8332acbd5795", "LU8QqlsR2cc", "不要浪费时间 · 沙漏",
     ["时间", "拖延", "截止", "工期", "耗时", "日程", "计划", "期限", "日期", "通勤", "会议",
      "议程", "习惯", "刷屏", "短视频"]),
    ("36b6fccd6b24", "h0avRw9-bdI", "不要浪费钱 · 存钱罐",
     ["存钱", "储蓄", "存款", "理财", "基金", "股票", "投资", "应急金", "养老金", "房贷",
      "保险", "套餐", "续费", "分期", "信用卡", "彩票", "打赏", "充值", "冷静期", "囤货"]),
    ("194f0d603393", "TB7aNN4blTQ", "反面清单 · 手写清单",
     ["清单", "不要", "别", "避免", "戒"]),
    ("4fd6dcecc140", "LLp_CJpxSBc", "没钱的时候怎么活 · 空钱包",
     ["失业", "救助", "低保", "补贴", "医保", "欠薪", "生活费", "钱", "钱包", "自炊",
      "求职", "社保", "押金", "借钱", "高利贷", "房租"]),
    ("4e084e65b0b9", "zbQ5UaREHx4", "法律与财产安全 · 正义天平",
     ["法律", "起诉", "判决", "赔偿", "律师", "法院", "报警", "仲裁", "维权", "担保",
      "借条", "合同", "诈骗", "时效", "彩礼", "征信", "立案", "受案", "证据", "录音",
      "保护令", "国家赔偿", "自首", "追诉"]),
    ("9feb2e476e1b", "nSpj-Z12lX0", "法律红线 · 法槌",
     ["法律", "法院", "判决", "判刑", "拘留", "犯罪", "诈骗", "违法", "定罪", "起刑",
      "罪", "检察", "公安", "警察", "处罚", "没收"]),
    ("5749bfc6ae5c", "eCHGFsxZ__4", "恋爱和结婚 · 湖边牵手",
     ["恋爱", "结婚", "婚姻", "伴侣", "对象", "情侣", "牵手", "配偶", "离婚", "领证",
      "婚检", "登记", "彩礼"]),
    ("48adadb01fe4", "8qEB0fTe9Vw", "程序员红线 · 代码屏幕",
     ["代码", "编程", "程序", "开发", "爬虫", "脚本", "外挂", "开源", "源码", "app",
      "网站", "系统", "服务器", "漏洞", "算法", "vpn", "备案"]),
    ("f11e683449cf", "2FPjlAyMQTA", "创业 · 团队讨论",
     ["创业", "公司", "合伙", "团队", "开店", "生意", "执照", "员工", "合同", "加盟",
      "注册", "经营", "申报", "发票", "预售", "量产", "进货", "注销", "破产", "股东"]),
    ("7f6b4027636e", "t-wsT0Wdeyw", "紧急情况 · 急救包",
     ["急救", "120", "急诊", "止血", "抢救", "按压", "心肺复苏", "aed", "就医", "送医",
      "咬伤", "烫伤", "中毒", "中暑", "骨折", "溺水", "触电", "失温", "蛇", "地震",
      "休克", "过敏", "出血", "伤口", "噎", "高原", "蜱虫", "雷雨"]),
    ("3d94d9ae25c0", "FnA5pAzqhMM", "账号与信息安全 · 键盘挂锁",
     ["密码", "账号", "验证", "挂失", "冻结", "盗刷", "隐私", "个人信息", "刷脸",
      "sim", "登录", "授权", "锁屏"]),
    ("9431002c953a", "bqUZEAeWuok", "租房与买房 · 房门钥匙",
     ["租房", "租金", "押金", "房东", "钥匙", "买房", "租约", "中介", "腾房", "隔断",
      "产权", "抵押", "长租公寓"]),
    ("63f040aac92b", "hIgeoQjS_iE", "慢性病 · 听诊器",
     ["慢性病", "高血压", "糖尿病", "复查", "医生", "医院", "医保", "痛风", "尿酸",
      "结石", "门诊", "治疗", "指标", "吃药", "眼底"]),
    ("8f0f6b62b453", "CeZypKDceQc", "家里有老人 · 老人的手",
     ["老人", "养老", "遗嘱", "监护", "卧床", "护理", "失能", "压疮", "长辈"]),
    ("b57c6761b87b", "kvv5bm1dgc8", "养孩子 · 父亲抱孩子",
     ["孩子", "育儿", "产假", "生育", "怀孕", "哺乳", "婴儿", "带娃", "津贴"]),
    ("23d48f9bdab7", "oqnVnI5ixHg", "在职离职 · 下班",
     ["加班", "离职", "裁员", "辞退", "工资", "工伤", "工作", "单位", "年休假", "试用期",
      "竞业", "打卡", "职业病", "伤残", "工亡", "辞职", "被裁", "赔偿"]),
    ("8cd9e4b5bb72", "m9AZPo1l3fo", "新生儿 · 熟睡",
     ["婴儿", "新生儿", "宝宝", "母乳", "奶粉", "睡", "疫苗", "尿布", "辅食", "湿疹",
      "摇晃", "蜂蜜", "出生"]),
    ("0a4a96f07043", "tkduNVkHGig", "出国旅行 · 飞机舷窗",
     ["旅行", "出国", "境外", "护照", "签证", "航班", "飞机", "出境", "入境", "使领馆",
      "领事", "自驾", "行程", "取现"]),
    ("df2c1f69f988", "ya631mqQ7Ng", "娱乐减压 · 演唱会",
     ["娱乐", "ktv", "酒吧", "演出", "减压", "放松", "密室", "剧本杀", "网吧", "焦虑",
      "心情", "散步", "正念", "抑郁", "绿地", "遛弯", "叹息"]),
    ("7c0a05691dd5", "xiGrYtlCAyo", "学技能 · 写笔记",
     ["学习", "考试", "技能", "培训", "证书", "职称", "教育", "读书", "学历", "笔记",
      "助学", "中职", "普高", "统考", "背", "练", "考"]),
    ("fa4f8f311adb", "vsSu0oGtLoI", "看病 · 医院病床",
     ["看病", "医院", "医保", "转诊", "急诊", "病历", "挂号", "报销", "医生", "住院",
      "治疗", "统筹", "鉴定", "残联", "分诊"]),
    ("f62db691057d", "fvl4b1gjpbk", "人走了以后 · 烛光",
     ["死亡", "去世", "走了", "殡葬", "遗体", "火化", "尸检", "注销", "户口", "殡仪",
      "安葬", "死者", "太平间"]),
    ("780e331ac1b4", "-7oi_5uJPC4", "做网站 · 屏幕设计",
     ["网站", "平台", "服务器", "备案", "app", "直播", "许可证", "用户", "审核", "算法",
      "运维", "支付", "电信"]),
    ("d8756e7dbc3c", "ux53SGpRAHU", "怀孕生产 · 孕妇",
     ["怀孕", "孕", "产检", "分娩", "生产", "孕妇", "叶酸", "剖宫产", "产后", "破水",
      "出生", "哺乳", "胎动"]),
    ("22c48761e9f5", "VJ2s0c20qCo", "外形 · 哑铃架",
     ["减肥", "体重", "健身", "增肌", "肌肉", "节食", "运动", "医美", "医疗美容", "整形",
      "手术", "注射", "填充", "瘦", "类固醇", "体像", "哑铃"]),
    ("7444623b4d59", "Tt1Z-GV8zFY", "重大打击 · 风暴彩虹",
     ["打击", "变故", "亲人", "丧偶", "哀伤", "离婚", "失业", "重病", "抑郁", "自杀",
      "分居", "告别", "心理", "去世"]),
    ("62157389302c", "CYlPykF-qAM", "上学的孩子 · 书包",
     ["孩子", "学校", "学生", "上学", "近视", "欺凌", "体检", "作业", "休学", "考试",
      "书包", "户外", "屏幕", "视力", "散瞳", "窝沟"]),
    ("fdfc5a8bccf7", "TTYFzgLidGM", "十八岁之后 · 毕业",
     ["毕业", "学历", "高考", "大学", "当兵", "兵役", "退役", "考公", "就业", "十八",
      "编制", "教师", "消防", "自考", "志愿", "外卖", "网约车", "入伍"]),
    ("d8127ff6238a", "MUiv880yORo", "出国留学 · 校园",
     ["留学", "学校", "签证", "学费", "大学", "认证", "打工", "校园", "在读", "文凭",
      "oshc", "预警", "院校"]),
    ("a52f250bb5b9", "h-1ODGL1DXs", "残疾之后 · 轮椅篮球",
     ["残疾", "轮椅", "瘫痪", "康复", "无障碍", "导盲", "助听器", "脊髓", "失明", "耳聋",
      "盲", "精神障碍", "照护", "孤独症", "失能", "坐垫"]),
]


def _curl(args: list) -> str:
    return subprocess.run(
        ["curl", "-s", "-A", UA, "--max-time", "30", *args],
        capture_output=True, check=True,
    ).stdout


def _open(url: str) -> bytes:
    return _curl([url])


def resolve_cdn_url(photo_id: str):
    """/photos/<id>/download 302 → CDN URL;返回 (cdn_base, 作者 slug)。

    用 curl 而不是 urllib:unsplash.com 的反爬(Anubis)按 TLS 指纹拦截,
    Python 的 TLS 栈会被重定向到质询页,curl 能拿到真实 CDN 地址。
    """
    url = f"https://unsplash.com/photos/{photo_id}/download?force=true&w=1600"
    out = _curl(["-o", os.devnull, "-w", "%{redirect_url}", url]).decode()
    if not out.startswith("https://images.unsplash.com/"):
        raise RuntimeError(f"download 端点没有给出 CDN 地址: {out[:80]!r}")
    base = out.split("?")[0]
    m = re.search(r"[?&]dl=([^&]+)", out)
    author = ""
    if m:
        # jorge-alberto-vega-barrera-<id>-unsplash.jpg → 作者名
        slug = urllib.parse.unquote(m.group(1))
        slug = re.sub(r"-unsplash\.\w+$", "", slug)
        slug = re.sub(r"-[A-Za-z0-9_-]{11}$", "", slug)
        author = slug.replace("-", " ").strip()
    return base, author


def main():
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    ap = argparse.ArgumentParser()
    ap.add_argument("--force", action="store_true", help="已存在也重新下载")
    ap.add_argument("--only", default=None, help="只处理这个章节 key")
    args = ap.parse_args()

    os.makedirs(OUT_DIR, exist_ok=True)
    os.makedirs(os.path.dirname(CREDITS), exist_ok=True)
    credits = {}
    if os.path.exists(CREDITS):
        with open(CREDITS, encoding="utf-8") as f:
            credits = json.load(f)

    ok, skip, fail = 0, 0, 0
    for key, pid, note, _keywords in SELECTIONS:
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
                "file": f"images/sections/{key}.webp",
                "title": note,
                "author": author,
                "source": f"https://unsplash.com/photos/{pid}",
                "license": "Unsplash License (https://unsplash.com/license)",
            }
            print(f"OK  {key} {note} {len(img)//1024}KB <- {pid} ({author})")
            ok += 1
            time.sleep(0.4)  # 礼貌限速
        except Exception as ex:
            print(f"FAIL {key} {note} {pid}: {ex}")
            fail += 1

    with open(CREDITS, "w", encoding="utf-8") as f:
        json.dump(credits, f, ensure_ascii=False, indent=1)

    # 相关性门控清单每次都重写:不触发下载,--only 时也是全量
    manifest = {
        "version": 1,
        "minBodyHits": MIN_BODY_HITS,
        "sections": {
            key: {"keywords": [k.lower() for k in keywords]}
            for key, _pid, _note, keywords in SELECTIONS
        },
    }
    with open(MANIFEST, "w", encoding="utf-8") as f:
        json.dump(manifest, f, ensure_ascii=False, indent=1)
    print(f"\n完成: {ok} 下载, {skip} 跳过, {fail} 失败;credits {len(credits)} 条,manifest {len(manifest['sections'])} 章")


if __name__ == "__main__":
    main()
