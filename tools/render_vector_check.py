# 校验 ic_launcher_foreground.xml 的实际渲染效果（只支持本文件用到的 M/L/A/Z 命令）。
# 用法: python tools/render_vector_check.py
import math
import re
import xml.etree.ElementTree as ET
from PIL import Image, ImageDraw

SIZE = 768
SS = 3
W = SIZE * SS
S = W / 108.0

def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))

def make_background():
    c0 = (0x4F, 0x4D, 0xCB)
    c1 = (0x1C, 0x0D, 0x9D)
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

TOKEN = re.compile(r"([MLAZ])|(-?\d*\.?\d+)")

def arc_points(x1, y1, rx, ry, large, sweep, x2, y2, n=48):
    """SVG endpoint -> center 参数化，返回折线点列。"""
    if rx == 0 or ry == 0:
        return [(x2, y2)]
    dx, dy = (x1 - x2) / 2, (y1 - y2) / 2
    x1p, y1p = dx, dy  # 本图标 rot=0
    lam = (x1p ** 2) / (rx ** 2) + (y1p ** 2) / (ry ** 2)
    if lam > 1:
        f = math.sqrt(lam)
        rx, ry = rx * f, ry * f
    num = rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p
    den = rx * rx * y1p * y1p + ry * ry * x1p * x1p
    co = math.sqrt(max(0.0, num / den))
    if large == sweep:
        co = -co
    cxp = co * rx * y1p / ry
    cyp = -co * ry * x1p / rx
    cx, cy = cxp + (x1 + x2) / 2, cyp + (y1 + y2) / 2

    def angle(ux, uy, vx, vy):
        dot = ux * vx + uy * vy
        ln = math.hypot(ux, uy) * math.hypot(vx, vy)
        a = math.acos(max(-1.0, min(1.0, dot / ln)))
        return -a if ux * vy - uy * vx < 0 else a

    a1 = angle(1, 0, (x1p - cxp) / rx, (y1p - cyp) / ry)
    da = angle((x1p - cxp) / rx, (y1p - cyp) / ry, (-x1p - cxp) / rx, (-y1p - cyp) / ry)
    if not sweep and da > 0:
        da -= 2 * math.pi
    elif sweep and da < 0:
        da += 2 * math.pi
    return [
        (cx + rx * math.cos(a1 + da * i / n), cy + ry * math.sin(a1 + da * i / n))
        for i in range(1, n + 1)
    ]

def parse_path(data):
    """返回子路径列表，每个子路径是点列（Z 闭合）。"""
    tokens = [(m.group(1), m.group(2)) for m in TOKEN.finditer(data)]
    paths, pts = [], []
    i, cmd = 0, None
    cur = (0.0, 0.0)
    start = (0.0, 0.0)
    while i < len(tokens):
        letter, num = tokens[i]
        if letter:
            cmd = letter
            i += 1
            if cmd == "Z":
                if pts:
                    pts.append(start)
            continue
        if cmd == "M":
            if pts:
                paths.append(pts)
            x, y = float(num), float(tokens[i + 1][1])
            i += 2
            cur = start = (x, y)
            pts = [cur]
            cmd = "L"
        elif cmd == "L":
            x, y = float(num), float(tokens[i + 1][1])
            i += 2
            cur = (x, y)
            pts.append(cur)
        elif cmd == "A":
            rx, ry, _rot = float(num), float(tokens[i + 1][1]), float(tokens[i + 2][1])
            large, sweep = int(float(tokens[i + 3][1])), int(float(tokens[i + 4][1]))
            x, y = float(tokens[i + 5][1]), float(tokens[i + 6][1])
            i += 7
            seg = arc_points(cur[0], cur[1], rx, ry, large, sweep, x, y)
            pts.extend(seg)
            cur = (x, y)
        else:
            raise ValueError(f"unsupported cmd {cmd}")
    if pts:
        paths.append(pts)
    return paths

def main():
    img = make_background()
    d = ImageDraw.Draw(img)
    tree = ET.parse("app/src/main/res/drawable/ic_launcher_foreground.xml")
    ns = "{http://schemas.android.com/apk/res/android}"
    for p in tree.getroot().iter("vector"):
        continue
    for p in tree.getroot().findall("path"):
        data = p.get(f"{ns}pathData")
        fill = p.get(f"{ns}fillColor")
        stroke = p.get(f"{ns}strokeColor")
        sw = float(p.get(f"{ns}strokeWidth") or 1)
        for pts in parse_path(data):
            sp = [(x * S, y * S) for x, y in pts]
            if fill:
                d.polygon(sp, fill=(255, 255, 255))
            if stroke:
                d.line(sp, fill=(255, 255, 255), width=int(sw * S), joint="curve")
    img = img.resize((SIZE, SIZE), Image.LANCZOS)
    img.save("icon_preview/icon_vector_check.png")
    print("done -> icon_preview/icon_vector_check.png")

if __name__ == "__main__":
    main()
