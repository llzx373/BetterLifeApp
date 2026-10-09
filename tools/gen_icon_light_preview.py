# 光线/立体感预览：不修改任何 res 资源，只生成对比图。
# 基准 = 当前图标（对角渐变 + 左上角 0x12 高光）；
# L1 = 背景加强：顶部径向辉光 + 边缘暗角；
# L2 = L1 + 前景受光：白色图形自上而下渐变（顶部亮、底部偏蓝灰）+ 柔和投影；
# L3 = L2 + 玻璃感：一道斜向高光扫带。
import math
from PIL import Image, ImageDraw, ImageFont, ImageFilter

from gen_icon_preview import S, W, SIZE, R, face_base, strings, heart_rt, lerp

SMALL = 256
C_LIGHT = (0x4F, 0x4D, 0xCB)   # 左上
C_DARK = (0x1C, 0x0D, 0x9D)    # 右下


def diag_gradient():
    img = Image.new("RGB", (SMALL, SMALL))
    px = img.load()
    for y in range(SMALL):
        for x in range(SMALL):
            t = (x + y) / (2.0 * (SMALL - 1))
            px[x, y] = lerp(C_LIGHT, C_DARK, t)
    return img.resize((W, W), Image.BICUBIC)


def corner_highlight(img, alpha):
    # 现有高光：中心 (12,12)dp、半径 60dp 的硬边椭圆
    hl = Image.new("L", (W, W), 0)
    ImageDraw.Draw(hl).ellipse([-48 * S, -48 * S, 72 * S, 72 * S], fill=alpha)
    return Image.composite(Image.new("RGB", (W, W), (255, 255, 255)), img, hl)


def radial_glow(img, cx_dp, cy_dp, r_dp, max_alpha):
    # 柔和径向辉光（中心亮，向外线性衰减到 0）
    cx, cy, r = cx_dp / 108 * SMALL, cy_dp / 108 * SMALL, r_dp / 108 * SMALL
    m = Image.new("L", (SMALL, SMALL), 0)
    px = m.load()
    for y in range(SMALL):
        for x in range(SMALL):
            d = math.hypot(x - cx, y - cy) / r
            if d < 1.0:
                px[x, y] = int(max_alpha * (1.0 - d) ** 1.6)
    m = m.resize((W, W), Image.BICUBIC)
    return Image.composite(Image.new("RGB", (W, W), (255, 255, 255)), img, m)


def vignette(img, strength):
    # 边缘暗角：中心 0.45 以外开始压暗，角落最深
    m = Image.new("L", (SMALL, SMALL), 0)
    px = m.load()
    c = (SMALL - 1) / 2.0
    maxd = math.hypot(c, c)
    for y in range(SMALL):
        for x in range(SMALL):
            d = math.hypot(x - c, y - c) / maxd
            v = max(0.0, (d - 0.45) / 0.55)
            px[x, y] = int(255 * strength * v ** 1.5)
    m = m.resize((W, W), Image.BICUBIC)
    return Image.composite(Image.new("RGB", (W, W), (8, 4, 40)), img, m)


def gloss_band(img, alpha):
    # 斜向玻璃高光扫带
    band = Image.new("L", (SMALL, SMALL), 0)
    ImageDraw.Draw(band).rectangle([-60, 30, SMALL + 60, 100], fill=alpha)
    band = band.rotate(24, resample=Image.BICUBIC, center=(SMALL / 2, SMALL / 2))
    band = band.resize((W, W), Image.BICUBIC)
    return Image.composite(Image.new("RGB", (W, W), (255, 255, 255)), img, band)


def draw_fg_mask():
    # 当前定稿图形（同 icon_current）：画到透明层，返回 alpha 掩模
    layer = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    face_base(d)
    strings(d, 30, 34, [(19.5, 42.5), (40.5, 42.5)], 1.4)
    strings(d, 78, 34, [(68.8, 43.8), (87.2, 43.8)], 1.4)
    d.ellipse([R(65), R(40), R(91), R(66)], outline=(255, 255, 255, 255), width=int(R(3)))
    yw = int(R(2.8))
    d.line([(R(74.1), R(47.15)), (R(78), R(52.35))], fill=(255, 255, 255, 255), width=yw)
    d.line([(R(81.9), R(47.15)), (R(78), R(52.35))], fill=(255, 255, 255, 255), width=yw)
    d.line([(R(78), R(52.35)), (R(78), R(60.15))], fill=(255, 255, 255, 255), width=yw)
    d.line([(R(74.49), R(54.95)), (R(81.51), R(54.95))], fill=(255, 255, 255, 255), width=yw)
    d.line([(R(74.49), R(58.2)), (R(81.51), R(58.2))], fill=(255, 255, 255, 255), width=yw)
    heart_rt(d, 18, 42, 24, 2.2)
    return layer.split()[3]


def fg_flat(mask):
    fg = Image.new("RGBA", (W, W), (255, 255, 255, 255))
    fg.putalpha(mask)
    return fg


def fg_lit(mask, top=(255, 255, 255), bottom=(198, 201, 240), y0=26.0, y1=72.0):
    # 前景受光：viewport y26（横梁上方）纯白 → y72（心尖/硬币底）偏蓝灰
    grad = Image.new("RGB", (1, W))
    for y in range(W):
        t = min(1.0, max(0.0, (y / S - y0) / (y1 - y0)))
        grad.putpixel((0, y), lerp(top, bottom, t))
    grad = grad.resize((W, W)).convert("RGBA")
    grad.putalpha(mask)
    return grad


def soft_shadow(mask, dx_dp=1.2, dy_dp=2.0, blur_dp=0.9, alpha=88):
    sh = Image.new("L", (W, W), 0)
    sh.paste(mask.point(lambda a: a * alpha // 255), (int(dx_dp * S), int(dy_dp * S)))
    sh = sh.filter(ImageFilter.GaussianBlur(blur_dp * S))
    layer = Image.new("RGBA", (W, W), (10, 6, 45, 255))
    layer.putalpha(sh)
    return layer


def compose(bg, fg, shadow=None):
    icon = bg.convert("RGBA")
    if shadow is not None:
        icon.alpha_composite(shadow)
    icon.alpha_composite(fg)
    return icon.convert("RGB")


def circle_masked(icon):
    icon = icon.resize((SIZE, SIZE), Image.LANCZOS).convert("RGBA")
    m = Image.new("L", (SIZE, SIZE), 0)
    ImageDraw.Draw(m).ellipse([0, 0, SIZE, SIZE], fill=255)
    out = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    out.paste(icon, (0, 0), m)
    return out


def main():
    mask = draw_fg_mask()

    bg_cur = corner_highlight(diag_gradient(), 0x12)
    bg_l1 = vignette(radial_glow(corner_highlight(diag_gradient(), 0x12), 12, 12, 85, 0x2E), 0.30)
    bg_l3 = gloss_band(bg_l1, 0x0D)

    variants = [
        ("当前", compose(bg_cur, fg_flat(mask))),
        ("L1 背景光影", compose(bg_l1, fg_flat(mask))),
        ("L2 前景受光", compose(bg_l1, fg_lit(mask), soft_shadow(mask))),
        ("L3 玻璃高光", compose(bg_l3, fg_lit(mask), soft_shadow(mask))),
    ]

    cell, pad, label_h = 300, 16, 40
    n = len(variants)
    canvas = Image.new("RGB", (cell * n + pad * (n + 1), (cell + label_h) * 2 + pad * 3), (238, 238, 238))
    cd = ImageDraw.Draw(canvas)
    font = ImageFont.load_default(size=24)
    for i, (name, icon) in enumerate(variants):
        x = pad + i * (cell + pad)
        small = icon.resize((SIZE, SIZE), Image.LANCZOS)
        canvas.paste(small.resize((cell, cell), Image.LANCZOS), (x, pad))
        canvas.paste(circle_masked(icon).resize((cell, cell), Image.LANCZOS), (x, cell + label_h + pad * 2),
                     circle_masked(icon).resize((cell, cell), Image.LANCZOS))
        tb = cd.textbbox((0, 0), name, font=font)
        cd.text((x + cell / 2 - (tb[2] - tb[0]) / 2, cell + pad + 8), name, fill=(60, 60, 60), font=font)
    canvas.save("icon_preview/icon_light_compare.png")
    for name, icon in variants:
        fn = name.split()[0].lower()
        icon.resize((SIZE, SIZE), Image.LANCZOS).save(f"icon_preview/icon_light_{fn}.png")
    print("done -> icon_preview/icon_light_compare.png")


if __name__ == "__main__":
    main()
