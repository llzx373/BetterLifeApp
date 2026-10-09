# 方案 B（百分比 %）× 双色拼接底：分割线沿 % 斜杠方向（左上行 = 心/健康域，
# 右下行 = 硬币/财富域），斜杠恰好骑在分界线上，成为「两个世界的梁」。
# 渲染 6 组左右配色候选，附 84px 小尺寸可读性测试。
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from PIL import Image, ImageDraw, ImageFont
from gen_icon_directions import (SIZE, W, WHITE, R, P, rcap, ring,
                                 heart_shape, yen, make_background)

# % 斜杠延长到图标边缘：过 (68,38)-(40,70)，x(y) = 68 - 0.875*(y-38)
# 上边缘 x≈101，下边缘 x≈6.75 → 左上域多边形
UL_POLY = [P(0, 0), P(101.25, 0), P(6.75, 108), P(0, 108)]

GOLD = ((0xC7, 0x7C, 0x02), (0x7A, 0x3E, 0x00))    # 琥珀金（财富侧常客）
COMBOS = [
    ("V1 teal|gold", ((0x00, 0x6A, 0x63), (0x00, 0x37, 0x33)), GOLD),
    ("V2 green|gold", ((0x43, 0xA0, 0x47), (0x1B, 0x5E, 0x20)), GOLD),
    ("V3 blue|gold", ((0x4F, 0x4D, 0xCB), (0x1C, 0x0D, 0x9D)), GOLD),
    ("V4 wine|gold", ((0xC2, 0x18, 0x5B), (0x6D, 0x0F, 0x36)), GOLD),
    ("V5 slate|gold", ((0x45, 0x5A, 0x64), (0x1B, 0x26, 0x2C)), GOLD),
    ("V6 rose|teal", ((0xE7, 0x54, 0x80), (0x9C, 0x1C, 0x46)),
     ((0x00, 0x6A, 0x63), (0x00, 0x37, 0x33))),
]


def split_bg(ul, lr):
    img_ul = make_background(*ul)
    img_lr = make_background(*lr)
    mask = Image.new("L", (W, W), 0)
    ImageDraw.Draw(mask).polygon(UL_POLY, fill=255)
    return Image.composite(img_ul, img_lr, mask)


def percent_glyph(d):
    rcap(d, 40, 70, 68, 38, 3.5)          # 斜杠（骑分界线的梁）
    heart_shape(d, 40.5, 42.5, 13)        # 心点（健康域）
    ring(d, 67.5, 65.5, 8.5, 2.8)         # 硬币点（财富域）
    yen(d, 67.5, 65.5, 8.5)


def main():
    icons = []
    for name, ul, lr in COMBOS:
        img = split_bg(ul, lr)
        percent_glyph(ImageDraw.Draw(img))
        icons.append((name, img.resize((SIZE, SIZE), Image.LANCZOS)))
        icons[-1][1].save(f"icon_preview/icon_split_{name[1]}.png")

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
        canvas.paste(icon.resize((small, small), Image.LANCZOS),
                     (x + (cell - small) // 2, pad + cell + label_h + pad))
    canvas.save("icon_preview/icon_split_compare.png")
    print("done -> icon_preview/icon_split_compare.png")


if __name__ == "__main__":
    main()
