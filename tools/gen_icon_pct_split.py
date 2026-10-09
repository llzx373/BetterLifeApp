# 定稿 % 图标配色预览 v2：左上心域整片白底 + 红心，右下钱域着色 + 金色钱币。
# 斜杠骑分界线：白域半边用灰色、着色域半边用白色，两侧都保持对比。不修改任何 res 资源。
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from PIL import Image, ImageDraw, ImageFont
from gen_icon_directions import (SIZE, W, WHITE, R, rcap, ring,
                                 heart_shape, yen, make_background)
from gen_icon_split_percent import UL_POLY

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "icon_preview")

RED = (0xE2, 0x2B, 0x33)        # 红心
GOLD = (0xF2, 0xBE, 0x45)       # 金币
SLASH_GRAY = (0x8E, 0x8E, 0x99) # 斜杠在白域的半边

BGS = [
    ("green", (0x21, 0xA0, 0x5A), (0x0A, 0x44, 0x26)),   # 绿：翠绿 -> 墨绿
    ("black", (0x3C, 0x3C, 0x44), (0x08, 0x08, 0x0C)),   # 黑：炭灰 -> 近黑
    ("navy",  (0x35, 0x54, 0xA8), (0x0D, 0x18, 0x4A)),   # 深蓝（延续现有蓝紫调）
    ("wine",  (0xA6, 0x2D, 0x3C), (0x49, 0x0D, 0x17)),   # 酒红
]


def draw_icon(lr_light, lr_deep):
    img_ul = make_background((0xFF, 0xFF, 0xFF), (0xE6, 0xE6, 0xEC))  # 心域：白 -> 浅灰
    img_lr = make_background(lr_light, lr_deep)                        # 钱域：着色
    ul_mask = Image.new("L", (W, W), 0)
    ImageDraw.Draw(ul_mask).polygon(UL_POLY, fill=255)
    img = Image.composite(img_ul, img_lr, ul_mask)

    # 斜杠双色：白域内灰、着色域内白
    slash = Image.new("L", (W, W), 0)
    rcap(ImageDraw.Draw(slash), 40, 70, 68, 38, 3.5, color=255)
    black = Image.new("L", (W, W), 0)
    img.paste(Image.new("RGB", (W, W), SLASH_GRAY), (0, 0),
              Image.composite(slash, black, ul_mask))
    img.paste(Image.new("RGB", (W, W), WHITE), (0, 0),
              Image.composite(slash, black, ul_mask.point(lambda v: 255 - v)))

    d = ImageDraw.Draw(img)
    heart_shape(d, 40.5, 42.5, 13, RED)           # 红心（原始尺寸，不再需要白盘）
    ring(d, 67.5, 65.5, 8.5, 2.8, GOLD)           # 金环
    yen(d, 67.5, 65.5, 8.5, color=GOLD)           # 金 ¥
    return img


def main():
    icons = []
    for name, light, deep in BGS:
        icon = draw_icon(light, deep).resize((SIZE, SIZE), Image.LANCZOS)
        icon.save(os.path.join(OUT, f"icon_pctsplit_{name}.png"))
        icons.append((name, icon))

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
    canvas.save(os.path.join(OUT, "icon_pctsplit_compare.png"))
    print("done -> icon_preview/icon_pctsplit_compare.png")


if __name__ == "__main__":
    main()
