# 方案 D「双色前景」效果预览（不修改任何 res 资源）。
# 几何严格复刻 app/src/main/res/drawable/ic_launcher_foreground.xml（含 0.82 总缩放
# 与心形 0.9 局部缩放），背景复刻 ic_launcher_background*.xml（135° 深→浅渐变 +
# 左上角 #12FFFFFF 高光）。
# 变体：D0 全白基准 / D1 心形浅金 / D2 心形珊瑚粉 / D3 心形+¥ 浅金 / D4 心形+硬币 浅金
# 第二行：D1 在四个品牌底色上的效果 + 圆形蒙版效果。
from PIL import Image, ImageDraw, ImageFont

SIZE = 768
SS = 3
W = SIZE * SS
S = W / 108.0

WHITE = (255, 255, 255)
GOLD = (0xFF, 0xD5, 0x4F)   # 浅金 amber300
CORAL = (0xFF, 0x8A, 0x80)  # 珊瑚粉 red A100

BACKGROUNDS = {
    "blue_purple": ((0x4F, 0x4D, 0xCB), (0x1C, 0x0D, 0x9D)),
    "green": ((0x43, 0xA0, 0x47), (0x1B, 0x5E, 0x20)),
    "orange": ((0xAC, 0x34, 0x00), (0x5D, 0x18, 0x00)),
    "teal": ((0x00, 0x6A, 0x63), (0x00, 0x37, 0x33)),
}


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def make_background(light, deep):
    small = 256
    img = Image.new("RGB", (small, small))
    px = img.load()
    for y in range(small):
        for x in range(small):
            t = (x + y) / (2.0 * (small - 1))
            px[x, y] = lerp(light, deep, t)
    img = img.resize((W, W), Image.BICUBIC)
    hl = Image.new("L", (W, W), 0)
    d = ImageDraw.Draw(hl)
    d.ellipse([-48 * S, -48 * S, 72 * S, 72 * S], fill=0x12)
    white = Image.new("RGB", (W, W), (255, 255, 255))
    return Image.composite(white, img, hl)


# ---- 坐标变换：先心形局部 0.9（pivot 30,48），再整体 0.82（pivot 54,54） ----

def tx(x, y):
    return (54 + (x - 54) * 0.82, 54 + (y - 54) * 0.82)


def hx(x, y):
    return tx(30 + (x - 30) * 0.9, 48 + (y - 48) * 0.9)


G = 0.82        # 整体缩放
HG = 0.9 * G    # 心形内元素的净缩放


def R(v):
    return v * S


def P(pt):
    return (R(pt[0]), R(pt[1]))


def draw_icon(bg, heart_col, yen_col, ring_col):
    img = bg.copy()
    d = ImageDraw.Draw(img)

    # 横梁（眉）：[17,30.5]-[91,34] r=1.7
    d.rounded_rectangle([P(tx(17, 30.5)), P(tx(91, 34))], radius=R(1.7 * G), fill=WHITE)
    # 立柱（鼻）
    d.polygon([P(tx(54, 33.5)), P(tx(51, 57)), P(tx(57, 57))], fill=WHITE)
    # 嘴：椭圆 13x3 的 15°~165° 段（中心 (54,66.5)），stroke 3.2 圆头
    d.arc([R(54 - 13 * G), R(66.5 - 3 * G), R(54 + 13 * G), R(66.5 + 3 * G)],
          15, 165, fill=WHITE, width=int(R(3.2 * G)))
    # 左吊绳（端点 20.55/39.45 已是心形 0.9 缩放内收后的坐标）
    for q in ((20.55, 43.05), (39.45, 43.05)):
        d.line([P(tx(30, 34)), P(tx(*q))], fill=WHITE, width=int(R(1.4 * G)))
    # 右吊绳
    for q in ((68.8, 43.8), (87.2, 43.8)):
        d.line([P(tx(78, 34)), P(tx(*q))], fill=WHITE, width=int(R(1.4 * G)))
    # 硬币圆环：中心 (78,53) r=13，stroke 3
    cx, cy = tx(78, 53)
    r = 13 * G
    d.ellipse([R(cx - r), R(cy - r), R(cx + r), R(cy + r)], outline=ring_col, width=int(R(3 * G)))
    # ¥ 符号，stroke 2.8
    yw = int(R(2.8 * G))
    for a, b in [((74.1, 47.15), (78, 52.35)), ((78, 52.35), (81.9, 47.15)),
                 ((78, 52.35), (78, 60.15)), ((74.49, 54.95), (81.51, 54.95)),
                 ((74.49, 58.2), (81.51, 58.2))]:
        d.line([P(tx(*a)), P(tx(*b))], fill=yen_col, width=yw)
    # 心形：双瓣圆 (24,48)/(36,48) r=8.49 + 菱形 + 相切圆头尖 r=2.2
    for ccx in (24, 36):
        c = hx(ccx, 48)
        rr = 8.49 * HG
        d.ellipse([R(c[0] - rr), R(c[1] - rr), R(c[0] + rr), R(c[1] + rr)], fill=heart_col)
    d.polygon([P(hx(30, 42)), P(hx(42, 54)), P(hx(31.56, 64.44)),
               P(hx(28.44, 64.44)), P(hx(18, 54))], fill=heart_col)
    c = hx(30, 62.89)
    rr = 2.2 * HG
    d.ellipse([R(c[0] - rr), R(c[1] - rr), R(c[0] + rr), R(c[1] + rr)], fill=heart_col)
    return img.resize((SIZE, SIZE), Image.LANCZOS)


def main():
    bgs = {k: make_background(*v) for k, v in BACKGROUNDS.items()}

    row1 = [
        ("D0 all white", draw_icon(bgs["blue_purple"], WHITE, WHITE, WHITE)),
        ("D1 gold heart", draw_icon(bgs["blue_purple"], GOLD, WHITE, WHITE)),
        ("D2 coral heart", draw_icon(bgs["blue_purple"], CORAL, WHITE, WHITE)),
        ("D3 heart+yen", draw_icon(bgs["blue_purple"], GOLD, GOLD, WHITE)),
        ("D4 heart+coin", draw_icon(bgs["blue_purple"], GOLD, GOLD, GOLD)),
    ]
    row2 = [("on " + k, draw_icon(bgs[k], GOLD, WHITE, WHITE)) for k in
            ("blue_purple", "green", "orange", "teal")]
    # 第 5 格：D1 圆形蒙版（模拟圆形 launcher）
    gold = row1[1][1]
    m = Image.new("L", (SIZE, SIZE), 0)
    ImageDraw.Draw(m).ellipse([0, 0, SIZE, SIZE], fill=255)
    rd = Image.new("RGBA", (SIZE, SIZE), (238, 238, 238, 255))
    rd.paste(gold, (0, 0), m)
    row2.append(("D1 round", rd.convert("RGB")))

    cell, pad, label_h = 300, 16, 36
    n = len(row1)
    canvas = Image.new("RGB", (cell * n + pad * (n + 1),
                               (cell + label_h + pad) * 2 + pad), (238, 238, 238))
    font = ImageFont.load_default(size=24)
    cd = ImageDraw.Draw(canvas)
    for row_i, row in enumerate((row1, row2)):
        for i, (name, icon) in enumerate(row):
            x = pad + i * (cell + pad)
            y = pad + row_i * (cell + label_h + pad)
            canvas.paste(icon.resize((cell, cell), Image.LANCZOS), (x, y))
            tb = cd.textbbox((0, 0), name, font=font)
            cd.text((x + cell / 2 - (tb[2] - tb[0]) / 2, y + cell + 6),
                    name, fill=(60, 60, 60), font=font)
    canvas.save("icon_preview/icon_duo_compare.png")

    gold.save("icon_preview/icon_duo_d1_gold_heart.png")
    row1[3][1].save("icon_preview/icon_duo_d3_heart_yen.png")
    print("done -> icon_preview/icon_duo_compare.png")


if __name__ == "__main__":
    main()
