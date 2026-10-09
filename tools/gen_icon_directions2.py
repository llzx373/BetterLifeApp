# 设计方向探索第二批（不修改任何 res 资源）：
#   G 字母 B 花押 —— BetterLife 的 B：竖干 + 上碗心形、下碗硬币
#   H 居中仪表盘 —— 半圆刻度 + 垂直居中指针，两端刻着心与硬币（平衡=不偏不倚）
#   I 双色拼接底 —— 左青右金斜切底色，极简脸（心左眼/硬币右眼/微笑）跨色而卧
# 复用 gen_icon_directions 的画笔与背景；同样带 84px 小尺寸可读性测试。
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from PIL import Image, ImageDraw, ImageFont
from gen_icon_directions import (SIZE, SS, W, S, WHITE, R, P, rcap, ring,
                                 heart_shape, yen, make_background)


def dir_g(img, d, bg):
    # 字母 B 花押
    rcap(d, 40, 34, 40, 76, 3.5)          # 竖干
    heart_shape(d, 50, 44, 16)            # 上碗 = 心
    ring(d, 52, 64, 10, 3.0)              # 下碗 = 硬币
    yen(d, 52, 64, 10, lw=2.0)


def dir_h(img, d, bg):
    # 居中仪表盘：半圆刻度 + 居中指针 + 端点心/硬币
    cx, cy, r = 54, 58, 22
    d.arc([R(cx - r), R(cy - r), R(cx + r), R(cy + r)], 180, 360,
          fill=WHITE, width=int(R(3.5)))
    for ang in (150, 120, 60, 30):        # 刻度（90° 留给指针）
        rad = math.radians(ang)
        d.line([P(cx + 19.5 * math.cos(rad), cy - 19.5 * math.sin(rad)),
                P(cx + 16 * math.cos(rad), cy - 16 * math.sin(rad))],
               fill=WHITE, width=int(R(2)))
    rcap(d, cx, cy, cx, cy - 18, 3.0)     # 居中指针
    d.ellipse([R(cx - 3), R(cy - 3), R(cx + 3), R(cy + 3)], fill=WHITE)
    heart_shape(d, 30.5, 62, 8)           # 左端点 = 心
    ring(d, 77.5, 62, 4.5, 2.0)           # 右端点 = 硬币


def dir_i(img, d, bg):
    pass  # I 的底色与图形都在 main 里特殊处理


def make_split_bg():
    """左青（health）右金（wealth）斜切底，各自保留 135° 渐变与高光"""
    teal = make_background((0x00, 0x6A, 0x63), (0x00, 0x37, 0x33))
    gold = make_background((0xC7, 0x7C, 0x02), (0x7A, 0x3E, 0x00))
    mask = Image.new("L", (W, W), 0)
    ImageDraw.Draw(mask).polygon(
        [P(0, 0), P(68, 0), P(40, 108), P(0, 108)], fill=255)
    return Image.composite(teal, gold, mask)


def face_min(d):
    heart_shape(d, 39, 43, 13)
    ring(d, 69, 43, 7.5, 3.0)
    d.arc([R(54 - 11), R(63 - 4.5), R(54 + 11), R(63 + 4.5)], 15, 165,
          fill=WHITE, width=int(R(3.5)))


def main():
    bg = make_background()
    icons = []
    for name, fn in [("G letter-B", dir_g), ("H gauge", dir_h)]:
        img = bg.copy()
        fn(img, ImageDraw.Draw(img), bg)
        icons.append((name, img.resize((SIZE, SIZE), Image.LANCZOS)))
    img = make_split_bg()
    face_min(ImageDraw.Draw(img))
    icons.append(("I split-bg", img.resize((SIZE, SIZE), Image.LANCZOS)))

    for name, icon in icons:
        icon.save(f"icon_preview/icon_dir_{name[0].lower()}.png")

    cell, pad, label_h, small = 300, 16, 36, 84
    n = len(icons)
    canvas = Image.new("RGB", (cell * n + pad * (n + 1),
                               pad + cell + label_h + pad + small + pad),
                       (238, 238, 238))
    font = ImageFont.load_default(size=24)
    cd = ImageDraw.Draw(canvas)
    for i, (name, icon) in enumerate(icons):
        x = pad + i * (cell + pad)
        canvas.paste(icon.resize((cell, cell), Image.LANCZOS), (x, pad))
        tb = cd.textbbox((0, 0), name, font=font)
        cd.text((x + cell / 2 - (tb[2] - tb[0]) / 2, pad + cell + 6), name,
                fill=(60, 60, 60), font=font)
        xs = x + (cell - small) // 2
        canvas.paste(icon.resize((small, small), Image.LANCZOS),
                     (xs, pad + cell + label_h + pad))
    canvas.save("icon_preview/icon_directions2.png")
    print("done -> icon_preview/icon_directions2.png")


if __name__ == "__main__":
    main()
