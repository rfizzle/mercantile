#!/usr/bin/env python3
"""Compose the Mercantile balance-scale mod icon as a 128px .glyph grid.

The brand motif is the same standing balance scale as the reputation HUD glyph,
here set in a circular stone medallion with an emerald rim-glow over a dark
green-brickwork field. Geometry (true circles, tiling brick, symmetric scale) is
computed mathematically and emitted as an ASCII-grid .glyph; glyph.py rasterizes
it deterministically, so the source re-renders byte-identically.
"""
import math

N = 128
CX = CY = (N - 1) / 2.0

# ---- palette (Mercantile emerald/gold over green-stone neutrals) -----------
COL = {
    'ink':       'ink',        # #0a0a0a
    # emerald rim glow (alpha falloff)
    'glow1':     '#6ddb94cc',
    'glow2':     '#50c878a0',
    'glow3':     '#50c87850',
    # stone bezel (cool green-grey, lit upper-left)
    'st_sh':     '#14160f',
    'st_dark':   '#262a1f',
    'st_mid':    '#3a4030',
    'st_lit':    '#565e48',
    'st_spec':   '#79836a',
    # green brickwork field
    'br_deep':   '#0c1c13',
    'br':        '#123222',
    'br_lit':    '#19402b',
    'mortar':    '#081610',
    'vig':       '#06120c',     # inner-edge vignette
    # balance scale — emerald ramp (matches hud-scales)
    'em_bri':    'mercantile.emerald-bright',   # #6ddb94 highlight
    'em':        'mercantile.emerald',          # #50c878 body
    'em_dk':     '#2c8a57',     # deeper emerald — facet shadow
    'em_dp':     '#1f6b41',     # deepest emerald — underside
    # gold pivot
    'gold':      'gold',       # #ffd700
    'gold_dk':   '#b8860b',
    'gold_glo':  '#fff3c0',
}

G = [[None] * N for _ in range(N)]


def put(x, y, key):
    xi, yi = int(round(x)), int(round(y))
    if 0 <= xi < N and 0 <= yi < N:
        G[yi][xi] = key


def dist(x, y):
    return math.hypot(x - CX, y - CY)


def ang(x, y):
    return math.atan2(y - CY, x - CX)


R_IN = 46.0
R_OUT = 56.0

# ---- 1. emerald glow halo --------------------------------------------------
for y in range(N):
    for x in range(N):
        d = dist(x, y)
        if R_OUT < d <= R_OUT + 2:
            G[y][x] = 'glow1'
        elif R_OUT + 2 < d <= R_OUT + 4:
            G[y][x] = 'glow2'
        elif R_OUT + 4 < d <= R_OUT + 6.5:
            G[y][x] = 'glow3'

# ---- 2. stone bezel annulus ------------------------------------------------
for y in range(N):
    for x in range(N):
        d = dist(x, y)
        if R_IN <= d <= R_OUT:
            a = ang(x, y)
            shade = math.cos(a - math.radians(225))          # light from UL
            bump = 0.6 * math.sin(a * 8) + 0.4 * math.sin(a * 15 + 1.1)
            base = shade + bump * 0.3
            if d >= R_OUT - 1.2 or d <= R_IN + 1.0:
                G[y][x] = 'ink'
            elif base > 0.85:
                G[y][x] = 'st_spec'
            elif base > 0.25:
                G[y][x] = 'st_lit'
            elif base > -0.35:
                G[y][x] = 'st_mid'
            elif base > -0.8:
                G[y][x] = 'st_dark'
            else:
                G[y][x] = 'st_sh'

# ---- 3. green brickwork field ----------------------------------------------
BRH, BRW = 8, 16
for y in range(N):
    for x in range(N):
        d = dist(x, y)
        if d >= R_IN - 1.0:
            continue
        row = int((y - (CY - R_IN)) // BRH)
        off = (BRW // 2) if (row % 2) else 0
        my = ((y - (CY - R_IN)) % BRH) < 1          # horizontal mortar
        mx = ((x - off) % BRW) < 1                   # vertical mortar
        if my or mx:
            G[y][x] = 'mortar'
        else:
            # subtle per-brick tone variation
            tone = (row * 3 + int((x - off) // BRW)) % 5
            G[y][x] = 'br_lit' if tone == 0 else ('br_deep' if tone == 3 else 'br')
        # inner-edge vignette so the scale pops off the field
        if d > R_IN - 5:
            G[y][x] = 'vig' if not (my or mx) else 'mortar'

# inner rim shadow ring (depth under the bezel lip)
for y in range(N):
    for x in range(N):
        d = dist(x, y)
        if R_IN - 1.5 <= d < R_IN:
            G[y][x] = 'ink'

# ---- 4. balance scale ------------------------------------------------------
# Drawn into its own layer so we can ink-outline the silhouette cleanly.
S = {}


def sput(x, y, key):
    xi, yi = int(round(x)), int(round(y))
    if 0 <= xi < N and 0 <= yi < N:
        S[(xi, yi)] = key


def vbar_tone(x, x0, x1):
    """Emerald ramp across a vertical bar: left lit -> right shadow."""
    t = (x - x0) / max(1e-6, (x1 - x0))
    if t < 0.18:
        return 'em_bri'
    if t < 0.55:
        return 'em'
    if t < 0.82:
        return 'em_dk'
    return 'em_dp'


def hbar_tone(y, y0, y1):
    """Emerald ramp down a horizontal bar: top lit -> bottom shadow."""
    t = (y - y0) / max(1e-6, (y1 - y0))
    if t < 0.25:
        return 'em_bri'
    if t < 0.6:
        return 'em'
    if t < 0.85:
        return 'em_dk'
    return 'em_dp'


# beam (horizontal), gentle taper toward the ends
Y_BEAM = CY - 22
BEAM_HALF = 31
for x in range(int(CX - BEAM_HALF), int(CX + BEAM_HALF) + 1):
    taper = 1.0 - 0.25 * (abs(x - CX) / BEAM_HALF)   # thins at the tips
    top = Y_BEAM - 2.0 * taper
    bot = Y_BEAM + 2.0 * taper
    for y in range(int(round(top)), int(round(bot)) + 1):
        sput(x, y, hbar_tone(y, top, bot))

# central post (vertical)
POST_TOP = Y_BEAM
POST_BOT = CY + 28
PX0, PX1 = CX - 2.5, CX + 2.5
for y in range(int(round(POST_TOP)), int(round(POST_BOT)) + 1):
    for x in range(int(round(PX0)), int(round(PX1)) + 1):
        sput(x, y, vbar_tone(x, PX0, PX1))

# flared base + foot bar
BASE_TOP = CY + 24
BASE_BOT = CY + 36
for y in range(int(round(BASE_TOP)), int(round(BASE_BOT)) + 1):
    t = (y - BASE_TOP) / (BASE_BOT - BASE_TOP)
    half = 3 + t * 13                                 # widen downward
    for x in range(int(round(CX - half)), int(round(CX + half)) + 1):
        sput(x, y, hbar_tone(y, BASE_TOP, BASE_BOT))
# foot slab
for y in range(int(round(BASE_BOT)) - 1, int(round(BASE_BOT)) + 2):
    for x in range(int(round(CX - 15)), int(round(CX + 15)) + 1):
        sput(x, y, hbar_tone(y, BASE_BOT - 1, BASE_BOT + 1))

# pans (shallow bowls) hung under the beam tips
PAN_R = 13.0
PAN_DROP = 6.0                                         # squash to a shallow dish
for side in (-1, 1):
    xp = CX + side * BEAM_HALF
    yp = CY + 2
    # chain from beam tip down to the pan rim
    for y in range(int(round(Y_BEAM + 2)), int(round(yp)) + 1):
        sput(xp, y, 'em_dk')
        sput(xp + side, y, 'em_dp')
    # bowl: lower half of a squashed ellipse
    for y in range(int(round(yp)), int(round(yp + PAN_DROP)) + 2):
        for x in range(int(round(xp - PAN_R)), int(round(xp + PAN_R)) + 1):
            ex = (x - xp) / PAN_R
            ey = (y - yp) / PAN_DROP
            if ex * ex + ey * ey <= 1.0 and y >= yp:
                if y <= yp + 0.6:
                    sput(x, y, 'em_bri')               # bright rim
                elif y >= yp + PAN_DROP - 0.6:
                    sput(x, y, 'em_dp')
                else:
                    sput(x, y, hbar_tone(y, yp, yp + PAN_DROP))

# gold fulcrum block at the beam centre
for y in range(int(round(Y_BEAM - 2)), int(round(Y_BEAM + 3))):
    for x in range(int(round(CX - 3)), int(round(CX + 3)) + 1):
        t = ((x - (CX - 3)) + (y - (Y_BEAM - 2))) / 9.0
        sput(x, y, 'gold' if t < 0.55 else 'gold_dk')

# gold pivot spine + finial knob on top
KNOB_Y = Y_BEAM - 12
for y in range(int(round(KNOB_Y)), int(round(Y_BEAM)) + 1):
    sput(CX - 1, y, 'gold')
    sput(CX, y, 'gold')
    sput(CX + 1, y, 'gold_dk')
for y in range(N):
    for x in range(N):
        dd = math.hypot(x - CX, y - KNOB_Y)
        if dd <= 4.2:
            if dd > 3.4:
                sput(x, y, 'gold_dk')
            elif (x - CX) + (y - KNOB_Y) < -1.2:
                sput(x, y, 'gold_glo')
            elif (x - CX) + (y - KNOB_Y) > 2.0:
                sput(x, y, 'gold_dk')
            else:
                sput(x, y, 'gold')

# ---- 5. ink-outline the scale silhouette, then composite -------------------
for (x, y) in list(S.keys()):
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        nx, ny = x + dx, y + dy
        if (nx, ny) not in S and 0 <= nx < N and 0 <= ny < N:
            G[ny][nx] = 'ink'
for (x, y), key in S.items():
    G[y][x] = key

# ---- emit .glyph -----------------------------------------------------------
pool = "@$%&*+=oOxX0123456789abcdefghijklmnpqrstuvwzABCDEFGHIJKLMNPQRSTUVWZ?!~^"
used = []
for row in G:
    for c in row:
        if c and c not in used:
            used.append(c)
assert len(used) <= len(pool), f"too many colors: {len(used)}"
key2ch = {k: pool[i] for i, k in enumerate(used)}

lines = ["# Mercantile balance-scale mod icon — generated by icon.gen.py",
         f"size: {N}",
         "kind: icon",
         # The size ladder minted from this one 128px grid.
         "ships: art/icon-128.png",
         "ships: src/main/resources/assets/mercantile/icon.png 256",
         "ships: site/assets/icon.png 256",
         "ships: art/icon-512.png 512",
         "", "legend:", "  . transparent"]
for k in used:
    lines.append(f"  {key2ch[k]} {COL[k]}")
lines.append("")
lines.append("frame:")
for row in G:
    lines.append("  " + "".join(key2ch[c] if c else "." for c in row))

OUT = "art/glyphs/icon.glyph"
with open(OUT, "w") as f:
    f.write("\n".join(lines) + "\n")
print(f"wrote {OUT}  ({len(used)} colors)")
