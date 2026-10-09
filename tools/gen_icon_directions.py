# 全新图标设计方向探索（不修改任何 res 资源）。
# 跳出「天平=脸」隐喻，从应用内核（健康×金钱×平衡）出发的 6 个方向：
#   A 铜钱·外圆内心 —— 中式铜钱的心形方孔，外圆内心
#   B 百分比 % —— 斜杠是天平梁，两点是心与硬币（配比/平衡）
#   C 无限环 ∞ —— 左环心、右环硬币，持续经营的生活
#   D 日出 —— 半日（负形心）跃出地平线，新的一天
#   E 极简脸 —— 只留双眼（心+硬币）与微笑，再无其他
#   F 萌芽 —— 硬币为壤，茎上两片心形叶，财富滋养健康
# 全部白稿、蓝紫底，第一行 300px 预览，第二行 84px 小尺寸可读性测试。
import math
from PIL import Image, ImageDraw, ImageFont

SIZE = 768
SS = 3
W = SIZE * SS
S = W / 108.0
WHITE = (255, 255, 255)


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def make_background(light=(0x4F, 0x4D, 0xCB), deep=(0x1C, 0x0D, 0x9D)):
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
    return Image.composite(Image.new("RGB", (W, W), WHITE), img, hl)


def R(v):
    return v * S


def P(x, y):
    return (R(x), R(y))


def rcap(d, x0, y0, x1, y1, w, color=WHITE):
    d.line([P(x0, y0), P(x1, y1)], fill=color, width=int(R(w)))
    r = R(w / 2)
    for q in ((x0, y0), (x1, y1)):
        d.ellipse([R(q[0]) - r, R(q[1]) - r, R(q[0]) + r, R(q[1]) + r], fill=color)


def ring(d, cx, cy, r, w, color=WHITE):
    d.ellipse([R(cx - r), R(cy - r), R(cx + r), R(cy + r)], outline=color, width=int(R(w)))


def heart_shape(d, cx, cy, w, color=WHITE):
    """实心圆头心，w 为最宽处，整体纵向中心在 cy"""
    x0, y0 = cx - w / 2, cy - 0.4482 * w
    r = 0.3536 * w
    d.polygon([P(cx, y0), P(x0 + w, y0 + 0.5 * w), P(cx, y0 + w), P(x0, y0 + 0.5 * w)],
              fill=color)
    for sx in (-0.25, 0.25):
        ccx, ccy = cx + sx * w, y0 + 0.25 * w
        d.ellipse([R(ccx - r), R(ccy - r), R(ccx + r), R(ccy + r)], fill=color)


def yen(d, cx, cy, r, lw=None, color=WHITE):
    """复刻矢量稿 ¥ 的相对坐标（圆环 r=13 时 stroke 2.8）"""
    f = r / 13.0
    lw = lw or 2.8 * f
    for a, b in [((-3.9, -5.85), (0, -0.65)), ((3.9, -5.85), (0, -0.65)),
                 ((0, -0.65), (0, 7.15)), ((-3.51, 1.95), (3.51, 1.95)),
                 ((-3.51, 5.2), (3.51, 5.2))]:
        d.line([P(cx + a[0] * f, cy + a[1] * f), P(cx + b[0] * f, cy + b[1] * f)],
               fill=color, width=int(R(lw)))


def cut(img, bg, draw_fn):
    """以负形方式在 img 上挖出 draw_fn 画出的形状（露出背景）"""
    mask = Image.new("L", (W, W), 0)
    draw_fn(ImageDraw.Draw(mask))
    img.paste(bg, (0, 0), mask)


# ---------------- 六个方向 ----------------

def dir_a(img, d, bg):
    # 铜钱·外圆内心：实心圆钱 + 心形方孔（负形），仿方孔铜钱的「外圆内心」
    d.ellipse([R(54 - 23), R(54 - 23), R(54 + 23), R(54 + 23)], fill=WHITE)
    cut(img, bg, lambda m: heart_shape(m, 54, 55, 17, 255))


def dir_b(img, d, bg):
    # 百分比：斜杠（天平梁的化身）+ 心点 + 硬币点
    rcap(d, 40, 70, 68, 38, 3.5)
    heart_shape(d, 40.5, 42.5, 13)
    ring(d, 67.5, 65.5, 8.5, 2.8)
    yen(d, 67.5, 65.5, 8.5)


def dir_c(img, d, bg):
    # 无限环：lemniscate + 左环心、右环硬币
    a, ys = 23.0, 1.55
    pts = []
    for i in range(241):
        t = 2 * math.pi * i / 240
        s2 = math.sin(t) ** 2
        x = a * math.cos(t) / (1 + s2)
        y = ys * a * math.sin(t) * math.cos(t) / (1 + s2)
        pts.append(P(54 + x, 54 + y))
    d.line(pts, fill=WHITE, width=int(R(4.0)), joint="curve")
    heart_shape(d, 42.5, 54, 7.5)
    ring(d, 65.5, 54, 4.2, 2.0)


def dir_d(img, d, bg):
    # 日出：半日（负形心）+ 地平线 + 光芒
    sun = (54, 60)
    for ang in (50, 90, 130):
        rad = math.radians(ang)
        rcap(d, sun[0] + 19.5 * math.cos(rad), sun[1] - 19.5 * math.sin(rad),
             sun[0] + 23.5 * math.cos(rad), sun[1] - 23.5 * math.sin(rad), 2.5)
    d.ellipse([R(sun[0] - 15), R(sun[1] - 15), R(sun[0] + 15), R(sun[1] + 15)], fill=WHITE)
    cut(img, bg, lambda m: heart_shape(m, sun[0], sun[1] - 5.5, 10, 255))
    # 地平线以下裁掉（太阳正在升起）
    cut(img, bg, lambda m: m.rectangle([0, R(60), W, W], fill=255))
    rcap(d, 34, 60, 74, 60, 3.0)


def dir_e(img, d, bg):
    # 极简脸：心左眼 + 硬币右眼 + 微笑
    heart_shape(d, 39, 43, 13)
    ring(d, 69, 43, 7.5, 3.0)
    d.arc([R(54 - 11), R(63 - 4.5), R(54 + 11), R(63 + 4.5)], 15, 165,
          fill=WHITE, width=int(R(3.5)))


def dir_f(img, d, bg):
    # 萌芽：硬币为壤，茎 + 两片心形叶
    ring(d, 54, 63, 10, 3.0)
    yen(d, 54, 63, 10, lw=2.2)
    rcap(d, 54, 53, 54, 39, 2.5)
    for cx, ang in ((45.5, 32), (62.5, -32)):
        layer = Image.new("RGBA", (W, W), (0, 0, 0, 0))
        heart_shape(ImageDraw.Draw(layer), cx, 37, 11)
        rot = layer.rotate(ang, center=P(cx, 37), resample=Image.BICUBIC)
        img.paste(rot, (0, 0), rot)


VARIANTS = [("A coin-heart", dir_a), ("B percent", dir_b), ("C infinity", dir_c),
            ("D sunrise", dir_d), ("E face-min", dir_e), ("F sprout", dir_f)]


def main():
    bg = make_background()
    icons = []
    for name, fn in VARIANTS:
        img = bg.copy()
        fn(img, ImageDraw.Draw(img), bg)
        icons.append((name, img.resize((SIZE, SIZE), Image.LANCZOS)))
        icons[-1][1].save(f"icon_preview/icon_dir_{name[0].lower()}.png")

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
    canvas.save("icon_preview/icon_directions.png")
    print("done -> icon_preview/icon_directions.png")


if __name__ == "__main__":
    main()
