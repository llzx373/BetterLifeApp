# 生成天平图标的 5 个候选版本（不修改任何 res 资源）。
# V1 当前放大版（基准）/ V2 去底座 / V3 去底座去托盘，吊绳直接兜住 / V4 极简框架+更大主体 / V5 主体绝对主导，框架退为陪衬
from PIL import Image, ImageDraw, ImageFont

SIZE = 768
SS = 3
W = SIZE * SS
S = W / 108.0

def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))

def make_background():
    c0 = (0x43, 0xA0, 0x47)
    c1 = (0x1B, 0x5E, 0x20)
    small = 256
    img = Image.new("RGB", (small, small))
    px = img.load()
    for y in range(small):
        for x in range(small):
            t = (x + y) / (2.0 * (small - 1))
            px[x, y] = lerp(c0, c1, t)
    img = img.resize((W, W), Image.BICUBIC)
    hl = Image.new("L", (W, W), 0)
    d = ImageDraw.Draw(hl)
    d.ellipse([-48 * S, -48 * S, 72 * S, 72 * S], fill=0x12)
    white = Image.new("RGB", (W, W), (255, 255, 255))
    return Image.composite(white, img, hl)

def R(v):
    return v * S

WHITE = (255, 255, 255)

def beam(d, x0, y0, x1, y1, r):
    d.rounded_rectangle([R(x0), R(y0), R(x1), R(y1)], radius=R(r), fill=WHITE)

def pillar(d, apex_y, base_y, half_w):
    d.polygon([(R(54), R(apex_y)), (R(54 - half_w), R(base_y)), (R(54 + half_w), R(base_y))], fill=WHITE)

def base(d, x0, y0, x1, y1, r=1.6):
    d.rounded_rectangle([R(x0), R(y0), R(x1), R(y1)], radius=R(r), fill=WHITE)

def strings(d, hx, hy, pts, lw):
    for px, py in pts:
        d.line([(R(hx), R(hy)), (R(px), R(py))], fill=WHITE, width=int(R(lw)))

def pan(d, x0, y0, x1, y1, pw):
    d.arc([R(x0), R(y0), R(x1), R(y1)], 0, 180, fill=WHITE, width=int(R(pw)))

def heart(d, x0, y0, w):
    # 标准构造：菱形 + 两肩各一个圆（圆的直径 = 菱形上边的边长）
    x1 = x0 + w
    cx = (x0 + x1) / 2
    r = 0.3536 * w
    d.polygon([(R(cx), R(y0)), (R(x1), R(y0 + 0.5 * w)),
               (R(cx), R(y0 + w)), (R(x0), R(y0 + 0.5 * w))], fill=WHITE)
    d.ellipse([R(cx - 0.25 * w - r), R(y0 + 0.25 * w - r),
               R(cx - 0.25 * w + r), R(y0 + 0.25 * w + r)], fill=WHITE)
    d.ellipse([R(cx + 0.25 * w - r), R(y0 + 0.25 * w - r),
               R(cx + 0.25 * w + r), R(y0 + 0.25 * w + r)], fill=WHITE)

def rline(d, p1, p2, w, caps=True):
    d.line([(R(p1[0]), R(p1[1])), (R(p2[0]), R(p2[1]))], fill=WHITE, width=int(R(w)))
    if caps:
        r = R(w / 2)
        for p in (p1, p2):
            d.ellipse([R(p[0]) - r, R(p[1]) - r, R(p[0]) + r, R(p[1]) + r], fill=WHITE)

def coin(img, d, bg, cx, cy, r, ring_w, yen_w):
    ring = [R(cx - r), R(cy - r), R(cx + r), R(cy + r)]
    mask = Image.new("L", (W, W), 0)
    dm = ImageDraw.Draw(mask)
    ins = R(ring_w + 0.4)
    dm.ellipse([ring[0] + ins, ring[1] + ins, ring[2] - ins, ring[3] - ins], fill=255)
    img.paste(bg, (0, 0), mask)
    d.ellipse(ring, outline=WHITE, width=int(R(ring_w)))
    yw = int(R(yen_w))
    d.line([(R(cx - 0.30 * r), R(cy - 0.45 * r)), (R(cx), R(cy - 0.05 * r))], fill=WHITE, width=yw)
    d.line([(R(cx + 0.30 * r), R(cy - 0.45 * r)), (R(cx), R(cy - 0.05 * r))], fill=WHITE, width=yw)
    d.line([(R(cx), R(cy - 0.05 * r)), (R(cx), R(cy + 0.55 * r))], fill=WHITE, width=yw)
    d.line([(R(cx - 0.27 * r), R(cy + 0.15 * r)), (R(cx + 0.27 * r), R(cy + 0.15 * r))], fill=WHITE, width=yw)
    d.line([(R(cx - 0.27 * r), R(cy + 0.40 * r)), (R(cx + 0.27 * r), R(cy + 0.40 * r))], fill=WHITE, width=yw)

def v1(img, d, bg):
    beam(d, 25, 37.5, 83, 41, 1.6)
    pillar(d, 40.5, 72, 7)
    base(d, 40.5, 72, 67.5, 75.5)
    strings(d, 30, 40.5, [(21.5, 58.5), (39.5, 58.5)], 1.5)
    strings(d, 78, 40.5, [(69.5, 60), (86.5, 60)], 1.5)
    pan(d, 21.5, 54, 39.5, 63, 2.0)
    pan(d, 69.5, 55.5, 86.5, 64.5, 2.0)
    coin(img, d, bg, 78, 51, 8.5, 2.6, 1.6)
    heart(d, 23.7, 47.2, 12.4)

def v2(img, d, bg):
    beam(d, 25, 37.5, 83, 41, 1.6)
    pillar(d, 40.5, 75.5, 8)
    strings(d, 30, 40.5, [(21.5, 58.5), (39.5, 58.5)], 1.5)
    strings(d, 78, 40.5, [(69.5, 60), (86.5, 60)], 1.5)
    pan(d, 21.5, 54, 39.5, 63, 2.0)
    pan(d, 69.5, 55.5, 86.5, 64.5, 2.0)
    coin(img, d, bg, 78, 51, 8.5, 2.6, 2.0)
    heart(d, 23.7, 47.2, 12.4)

def v3(img, d, bg):
    beam(d, 25, 37.5, 83, 41, 1.6)
    pillar(d, 40.5, 75.5, 8)
    strings(d, 30, 40.5, [(24.5, 50), (35.5, 50)], 1.5)
    strings(d, 78, 40.5, [(72, 45), (84, 45)], 1.5)
    coin(img, d, bg, 78, 51, 8.5, 2.6, 2.2)
    heart(d, 23.7, 47.2, 12.4)

def v4(img, d, bg):
    beam(d, 24, 37.5, 84, 40, 1.2)
    pillar(d, 39.5, 74, 5)
    strings(d, 30, 40, [(24, 49.5), (36, 49.5)], 1.3)
    strings(d, 77.5, 40, [(71, 45), (84, 45)], 1.3)
    coin(img, d, bg, 77.5, 51.5, 9.2, 2.6, 2.3)
    heart(d, 22.5, 46, 15)

def v5(img, d, bg):
    beam(d, 27, 36.5, 81, 38, 0.8)
    pillar(d, 38, 73, 2.5)
    strings(d, 30, 38, [(24.5, 47), (38.5, 47)], 1.2)
    strings(d, 76, 38, [(68.2, 43.2), (83.8, 43.2)], 1.2)
    coin(img, d, bg, 76, 51, 11, 2.6, 2.5)
    heart(d, 22.5, 44.5, 18)

def v6(img, d, bg):
    # 满版：内容铺到接近图标边缘（仿微信/QQ 的饱满度）
    beam(d, 17, 30.5, 91, 34, 1.7)
    pillar(d, 33.5, 80, 3.4)
    base(d, 46, 80, 62, 83.5, 1.5)
    strings(d, 30, 34, [(21, 43), (39, 43)], 1.4)
    strings(d, 78, 34, [(68.1, 41.1), (87.9, 41.1)], 1.4)
    coin(img, d, bg, 78, 51, 14, 3.0, 2.8)
    heart(d, 18, 42, 24)

def v7(img, d, bg):
    # 现代风：动态雕塑（mobile），无立柱底座，monoline 圆头
    # 顶部挂环 + 短连接
    d.ellipse([R(51.4), R(32.4), R(56.6), R(37.6)], outline=WHITE, width=int(R(1.8)))
    rline(d, (54, 37.6), (54, 43), 2.2, caps=False)
    # 横梁（圆头）
    rline(d, (20, 43), (88, 43), 3.0)
    # 垂直吊绳
    rline(d, (30, 44.5), (30, 49), 2.0, caps=False)
    rline(d, (78, 44.5), (78, 46.5), 2.0, caps=False)
    coin(img, d, bg, 78, 60.5, 14, 3.0, 2.6)
    heart(d, 18, 50, 24)

def v8(img, d, bg):
    # 现代风：抽象支点（空心三角顶住横梁），无立柱底座
    rline(d, (20, 43), (88, 43), 3.0)
    # 空心三角支点（顶点接触横梁底部）
    rline(d, (54, 44.5), (47, 58), 2.4, caps=False)
    rline(d, (54, 44.5), (61, 58), 2.4, caps=False)
    rline(d, (47, 58), (61, 58), 2.4)
    rline(d, (30, 44.5), (30, 49), 2.0, caps=False)
    rline(d, (78, 44.5), (78, 46.5), 2.0, caps=False)
    coin(img, d, bg, 78, 60.5, 14, 3.0, 2.6)
    heart(d, 18, 50, 24)

def v9(img, d, bg):
    # V6 满版 + 拟脸化，按三庭五眼校准：
    # 眉(横梁) y32 -> 鼻底 y57 -> 下巴 ~y82 三段均分；嘴在鼻底下 1/3 处，浅弧微笑
    beam(d, 17, 30.5, 91, 34, 1.7)
    pillar(d, 33.5, 57, 3.0)              # 鼻子，收短到鼻底 y57
    # 嘴：基本平，带一点点微笑（极浅的弧，两端略微上扬）
    d.arc([R(41), R(63.5), R(67), R(69.5)], 15, 165, fill=WHITE, width=int(R(3.2)))
    strings(d, 30, 34, [(19.5, 44), (40.5, 44)], 1.4)
    strings(d, 78, 34, [(68.8, 43.8), (87.2, 43.8)], 1.4)
    coin(img, d, bg, 78, 53, 13, 3.0, 2.8)
    heart(d, 18, 44, 24)   # 下移，质心 ≈ y52.8，与硬币中心 y53 对齐

def heart_rt(d, x0, y0, w, tr):
    # 方案1：心尖圆化——两腰在切点截断，底部用与两腰相切的圆弧过渡
    x1 = x0 + w
    cx = (x0 + x1) / 2
    r = 0.3536 * w
    k = tr / 1.41421356  # 切点到尖端的水平/垂直距离
    d.polygon([(R(cx), R(y0)), (R(x1), R(y0 + 0.5 * w)),
               (R(cx + k), R(y0 + w - k)), (R(cx - k), R(y0 + w - k)),
               (R(x0), R(y0 + 0.5 * w))], fill=WHITE)
    d.ellipse([R(cx - 0.25 * w - r), R(y0 + 0.25 * w - r),
               R(cx - 0.25 * w + r), R(y0 + 0.25 * w + r)], fill=WHITE)
    d.ellipse([R(cx + 0.25 * w - r), R(y0 + 0.25 * w - r),
               R(cx + 0.25 * w + r), R(y0 + 0.25 * w + r)], fill=WHITE)
    # 相切圆：圆心在中轴上，距两腰均为 tr
    cyb = y0 + w - 2 * k
    d.ellipse([R(cx - tr), R(cyb - tr), R(cx + tr), R(cyb + tr)], fill=WHITE)

def heart_flat(img, x0, y0, w, squeeze=0.82):
    # 方案2：整体压扁（画到临时层再纵向压缩）
    layer = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    ld = ImageDraw.Draw(layer)
    heart(ld, x0, y0, w)
    bbox = layer.getbbox()
    crop = layer.crop(bbox)
    crop = crop.resize((crop.width, int(crop.height * squeeze)), Image.LANCZOS)
    img.paste(crop, (bbox[0], bbox[1]), crop)

def face_base(d):
    # V9 定稿的五官框架：眉/鼻/嘴
    beam(d, 17, 30.5, 91, 34, 1.7)
    pillar(d, 33.5, 57, 3.0)
    d.arc([R(41), R(63.5), R(67), R(69.5)], 15, 165, fill=WHITE, width=int(R(3.2)))

def v10(img, d, bg):
    # 方案1+4：圆化心尖 + 上移补偿（心尖落在硬币底缘稍上方）
    face_base(d)
    strings(d, 30, 34, [(19.5, 42.5), (40.5, 42.5)], 1.4)
    strings(d, 78, 34, [(68.8, 43.8), (87.2, 43.8)], 1.4)
    coin(img, d, bg, 78, 53, 13, 3.0, 2.8)
    heart_rt(d, 18, 42, 24, 2.2)

def v11(img, d, bg):
    # 方案2：心形压扁，底缘与硬币齐平
    face_base(d)
    strings(d, 30, 34, [(19.5, 46.5), (40.5, 46.5)], 1.4)
    strings(d, 78, 34, [(68.8, 43.8), (87.2, 43.8)], 1.4)
    coin(img, d, bg, 78, 53, 13, 3.0, 2.8)
    heart_flat(img, 18, 46, 24)

def v12(img, d, bg):
    # 方案3：硬币放大下移，底缘对齐心尖 y68
    face_base(d)
    strings(d, 30, 34, [(19.5, 44), (40.5, 44)], 1.4)
    strings(d, 78, 34, [(68.1, 44.1), (87.9, 44.1)], 1.4)
    coin(img, d, bg, 78, 54, 14, 3.0, 2.8)
    heart(d, 18, 44, 24)

def v13(img, d, bg):
    # 方案5：加托盘接住心尖和硬币
    face_base(d)
    strings(d, 30, 34, [(19.5, 44), (40.5, 44)], 1.4)
    strings(d, 78, 34, [(68.8, 43.8), (87.2, 43.8)], 1.4)
    coin(img, d, bg, 78, 53, 13, 3.0, 2.8)
    heart(d, 18, 44, 24)
    pan(d, 16, 62, 44, 72, 2.2)
    pan(d, 64, 60, 92, 70, 2.2)

def v14(img, d, bg):
    # 方案4 单独：心脏上移，心尖略高于硬币底缘（overshoot 补偿）
    face_base(d)
    strings(d, 30, 34, [(19.5, 42), (40.5, 42)], 1.4)
    strings(d, 78, 34, [(68.8, 43.8), (87.2, 43.8)], 1.4)
    coin(img, d, bg, 78, 53, 13, 3.0, 2.8)
    heart(d, 18, 41.5, 24)

VARIANTS = [("V1", v1), ("V2", v2), ("V3", v3), ("V4", v4), ("V5", v5), ("V6", v6),
            ("V7", v7), ("V8", v8), ("V9", v9)]
VARIANTS2 = [("V10", v10), ("V11", v11), ("V12", v12), ("V13", v13), ("V14", v14)]

def main():
    bg = make_background()

    def render(variants):
        icons = []
        for name, fn in variants:
            icon = bg.copy()
            d = ImageDraw.Draw(icon)
            fn(icon, d, bg)
            icon = icon.resize((SIZE, SIZE), Image.LANCZOS)
            icon.save(f"icon_preview/icon_{name.lower()}.png")
            icons.append((name, icon))
            if name in ("V9", "V10", "V11", "V12", "V13", "V14"):
                m = Image.new("L", (SIZE, SIZE), 0)
                ImageDraw.Draw(m).ellipse([0, 0, SIZE, SIZE], fill=255)
                rd = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
                rd.paste(icon, (0, 0), m)
                rd.save(f"icon_preview/icon_{name.lower()}_round.png")
        return icons

    def compare(icons, path):
        cell = 300
        pad = 16
        label_h = 36
        n = len(icons)
        canvas = Image.new("RGB", (cell * n + pad * (n + 1), cell + label_h + pad * 2), (238, 238, 238))
        font = ImageFont.load_default(size=24)
        cd = ImageDraw.Draw(canvas)
        for i, (name, icon) in enumerate(icons):
            x = pad + i * (cell + pad)
            canvas.paste(icon.resize((cell, cell), Image.LANCZOS), (x, pad))
            tb = cd.textbbox((0, 0), name, font=font)
            cd.text((x + cell / 2 - (tb[2] - tb[0]) / 2, cell + pad + 6), name, fill=(60, 60, 60), font=font)
        canvas.save(path)

    compare(render(VARIANTS), "icon_preview/icon_compare.png")
    compare(render(VARIANTS2), "icon_preview/icon_compare2.png")
    print("done -> icon_preview/")

if __name__ == "__main__":
    main()
