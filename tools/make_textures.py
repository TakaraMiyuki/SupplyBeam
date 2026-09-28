#!/usr/bin/env python3
"""SupplyBeam 材质生成脚本：箱子 / 光柱 / 地面光环 / 模组图标。

全部贴图按 4x 超采样绘制后 LANCZOS 缩小，获得平滑的抗锯齿边缘。
输出直接写入 src/main/resources/assets/supplybeam/。
"""
import math
import random
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/supplybeam"
TEX = ASSETS / "textures/entity"
TEX.mkdir(parents=True, exist_ok=True)

random.seed(20260928)


def clamp(v, lo=0.0, hi=1.0):
    return max(lo, min(hi, v))


def smoothstep(edge0, edge1, x):
    t = clamp((x - edge0) / (edge1 - edge0))
    return t * t * (3 - 2 * t)


def dist_to_segment(px, py, ax, ay, bx, by):
    dx, dy = bx - ax, by - ay
    l2 = dx * dx + dy * dy
    if l2 == 0:
        return math.hypot(px - ax, py - ay)
    t = clamp(((px - ax) * dx + (py - ay) * dy) / l2)
    return math.hypot(px - (ax + t * dx), py - (ay + t * dy))


# ============================================================
# 1) 补给箱 crate.png —— 128×128，四个 64×64 贴图格
#    每格对应一个三角面：金属面板 + 棱边高光 + 铆钉 + 中央小窗（半透明，透出光核）
#    窗格不做全透明（保底 alpha），避免远处/暗处看起来像"缺面"
# ============================================================

def make_crate():
    S = 256  # 每格 4x 超采样（64px 格）
    img = Image.new("RGBA", (S * 2, S * 2), (0, 0, 0, 0))
    px = img.load()

    edges_up = [((0.5, 0.0), (0.0, 1.0)), ((0.5, 0.0), (1.0, 1.0)), ((0.0, 1.0), (1.0, 1.0))]

    for cell in range(4):
        cx, cy = (cell % 2) * S, (cell // 2) * S
        apex_up = cell < 2
        bright = 1.0 if cell < 2 else 0.86
        pattern = cell % 2

        for sy in range(S):
            for sx in range(S):
                u = (sx + 0.5) / S
                v = (sy + 0.5) / S
                uu = u
                vv = v if apex_up else 1.0 - v

                # ---- 金属底色：垂直渐变 + 拉丝纹理 + 噪声 ----
                base = 0.70 + 0.13 * (1.0 - vv)
                base += 0.03 * math.sin(uu * math.pi * (10 + pattern * 5) + vv * 14.0)
                base += random.uniform(-0.015, 0.015)

                # ---- 棱边：细暗描线 + 外圈焊接缝高光 ----
                d_edge = min(dist_to_segment(uu, vv, *e[0], *e[1]) for e in edges_up)
                if d_edge < 0.020:
                    base -= 0.16 * (1.0 - d_edge / 0.020)
                elif d_edge < 0.09:
                    base += 0.26 * smoothstep(0.09, 0.020, d_edge)

                # ---- 内部分割棱（每格两条，形成三段面板） ----
                for panel_v in ((0.38, 0.72) if pattern == 0 else (0.45, 0.66)):
                    if abs(vv - panel_v) < 0.010:
                        base -= 0.13
                    elif abs(vv - panel_v) < 0.035:
                        base += 0.07 * smoothstep(0.035, 0.010, abs(vv - panel_v))

                # ---- 三角角落铆钉 ----
                for corner in ((0.5, 0.0), (0.0, 1.0), (1.0, 1.0)):
                    d = math.hypot(uu - corner[0], vv - corner[1])
                    if d < 0.030:
                        base += 0.30 * (1.0 - d / 0.030)      # 铆钉亮点
                    elif d < 0.055:
                        base -= 0.10 * (1.0 - (d - 0.030) / 0.025)  # 铆钉凹座

                # ---- 中央菱形窗（半透明透光 + 亮圈） ----
                dw = abs(uu - 0.5) + abs(vv - 0.58)
                if dw < 0.10:
                    alpha = 70 + int(140 * smoothstep(0.06, 0.10, dw))   # 中心也保留透光底
                    base += 0.45 * smoothstep(0.10, 0.06, dw)            # 窗圈提亮
                else:
                    alpha = 255

                g = clamp(base * bright)
                r = int(g * 255)
                px[cx + sx, cy + sy] = (r, r, r, alpha)

    img = img.resize((128, 128), Image.LANCZOS)
    img.save(TEX / "crate.png")


# ============================================================
# 2) 光柱 beam.png —— 32×256：中央亮柱 + 侧向流光条纹，上下端渐隐
# ============================================================

def make_beam():
    W, H = 128, 1024  # 4x
    img = Image.new("RGBA", (W, H))
    px = img.load()
    streaks = [(random.uniform(0.30, 0.70), random.uniform(0.02, 0.045), random.uniform(0.10, 0.22))
               for _ in range(4)]

    for y in range(H):
        fy = y / H
        for x in range(W):
            fx = x / W
            # 水平高斯主柱
            b = math.exp(-((fx - 0.5) ** 2) / (2 * 0.16 ** 2)) * 0.85
            # 流光条纹
            for cxs, sig, amp in streaks:
                b += math.exp(-((fx - cxs) ** 2) / (2 * sig ** 2)) * amp
            # 纵向能量波动（长波长，让光柱不死板）
            b *= 0.92 + 0.08 * math.sin(fy * math.pi * 7 + 1.7) * math.sin(fy * math.pi * 3)
            b += random.uniform(-0.012, 0.012)
            # 上下端渐隐
            fade = smoothstep(0.0, 0.06, fy) * smoothstep(1.0, 0.94, fy)
            b *= fade
            g = int(clamp(b) * 255)
            px[x, y] = (g, g, g, 255)

    img = img.resize((32, 256), Image.LANCZOS)
    img.save(TEX / "beam.png")


# ============================================================
# 3) 地面光环 ring.png —— 64×64：径向光环 + 十二道辐条
# ============================================================

def make_ring():
    S = 256
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    px = img.load()
    cx = cy = S / 2

    for y in range(S):
        for x in range(S):
            dxn = (x - cx) / S
            dyn = (y - cy) / S
            rr = math.hypot(dxn, dyn) * 2.0     # 0..1（贴图半宽=0.5）
            if rr > 1.0:
                px[x, y] = (0, 0, 0, 0)
                continue
            ang = math.atan2(dyn, dxn)
            # 主环 + 内外柔边
            b = math.exp(-((rr - 0.78) ** 2) / (2 * 0.05 ** 2)) * 0.95
            b += math.exp(-((rr - 0.62) ** 2) / (2 * 0.10 ** 2)) * 0.28
            # 内盘微光
            b += 0.10 * (1.0 - smoothstep(0.0, 0.55, rr))
            # 十二道辐条
            spokes = (math.cos(ang * 12) * 0.5 + 0.5) ** 3
            b += 0.14 * spokes * smoothstep(0.45, 0.85, rr)
            b += random.uniform(-0.01, 0.01)
            g = int(clamp(b) * 255)
            px[x, y] = (g, g, g, g)

    img = img.resize((64, 64), Image.LANCZOS)
    img.save(TEX / "ring.png")


# ============================================================
# 4) 模组图标 icon.png —— 128×128：夜空 + 光柱 + 金色八面体
# ============================================================

def make_icon():
    S = 512
    img = Image.new("RGBA", (S, S))
    px = img.load()
    cx = S * 0.5

    # 夜空径向渐变 + 星点
    for y in range(S):
        for x in range(S):
            d = math.hypot(x - S / 2, y - S / 2) / (S * 0.75)
            t = clamp(1.0 - d)
            r = int(11 + 16 * t)
            g = int(18 + 26 * t)
            b = int(32 + 44 * t)
            px[x, y] = (r, g, b, 255)
    for _ in range(90):
        sx, sy = random.randrange(S), random.randrange(S)
        b = random.randint(70, 190)
        px[sx, sy] = (b, b, min(255, b + 40), 255)

    glow = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    gpx = glow.load()
    # 光柱（白-青，中央亮两侧散）
    for y in range(S):
        fy = y / S
        fade = smoothstep(0.0, 0.10, fy) * smoothstep(1.0, 0.55, fy)
        for x in range(S):
            fx = abs(x - cx) / S
            core = math.exp(-(fx ** 2) / (2 * 0.030 ** 2)) * 0.95
            halo = math.exp(-(fx ** 2) / (2 * 0.085 ** 2)) * 0.38
            a = clamp(core + halo) * fade
            if a > 0.01:
                cr = int(255 * a + 90 * a * (1 - a))
                cg = int(235 * a + 140 * a * (1 - a))
                cb = int(200 * a + 255 * a * (1 - a))
                old = gpx[x, y]
                gpx[x, y] = (min(255, old[0] + cr), min(255, old[1] + cg), min(255, old[2] + cb), 255)
    img = Image.alpha_composite(img, glow)

    d = ImageDraw.Draw(img)

    # 金色八面体：中心 (0.5, 0.62)，水平半径 0.20，垂直半高 0.24
    ox, oy = S * 0.5, S * 0.62
    rx, ry = S * 0.20, S * 0.24
    top = (ox, oy - ry)
    bot = (ox, oy + ry)
    east = (ox + rx, oy)
    west = (ox - rx, oy)
    north = (ox, oy - rx * 0.9)   # 后顶点（略上移，伪 3D）
    south = (ox, oy + rx * 0.9)   # 前顶点

    gold_hi = (255, 226, 138)
    gold_mid = (251, 191, 36)
    gold_lo = (188, 132, 20)
    d.polygon([top, east, south], fill=gold_mid, outline=(255, 240, 190), width=3)
    d.polygon([top, south, west], fill=gold_lo, outline=(255, 240, 190), width=3)
    d.polygon([top, west, north], fill=gold_lo, outline=(222, 176, 66), width=3)
    d.polygon([top, north, east], fill=gold_mid, outline=(222, 176, 66), width=3)
    d.polygon([bot, south, east], fill=gold_lo, outline=(150, 104, 14), width=3)
    d.polygon([bot, west, south], fill=(150, 104, 14), outline=(120, 84, 10), width=3)
    d.polygon([bot, north, west], fill=(120, 84, 10), outline=(100, 70, 8), width=3)
    d.polygon([bot, east, north], fill=gold_lo, outline=(150, 104, 14), width=3)

    # 顶部高光点 + 四芒星
    d.ellipse([ox - 10, oy - ry * 0.55, ox + 10, oy - ry * 0.55 + 20], fill=(255, 246, 214))
    star_c = (ox, oy - ry - S * 0.045)
    sl = S * 0.055
    for angle in (0, math.pi / 2):
        dxs, dys = math.cos(angle) * sl, math.sin(angle) * sl
        d.line([star_c[0] - dxs, star_c[1] - dys, star_c[0] + dxs, star_c[1] + dys],
               fill=(255, 240, 190), width=5)
    d.ellipse([star_c[0] - 9, star_c[1] - 9, star_c[0] + 9, star_c[1] + 9], fill=(255, 250, 230))

    img = img.resize((128, 128), Image.LANCZOS).convert("RGBA")
    img.save(ASSETS / "icon.png")


if __name__ == "__main__":
    make_crate()
    make_beam()
    make_ring()
    make_icon()
    print("textures written to", ASSETS)
