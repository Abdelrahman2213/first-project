#!/usr/bin/env python3
"""
REALISTIC SCRIMS - ACTION v2 (sound-effects-only, no music).

Upgrade of edit_trailer.py: faster edit, heavier (but controlled) visual FX,
kinetic titles and a layered, original cinematic SFX soundtrack that replaces the
music bed entirely. The narration is the SAME voice as v1 - the already processed
lines in assets/vo/line_XX.wav are reused unchanged (only re-placed in time).

    python3 edit_trailer_v2.py --source "fortnite new video scrims.mp4" --work /tmp/rs_work

Outputs (v1 files are never touched):
    realistic_scrims_trailer_action_v2.mp4
    final_audio_action_v2.wav, voiceover_action_v2.wav, sfx_only_action_v2.wav
    assets/sfx_v2/*.wav, assets/graphics_v2/*.png
"""
import argparse
import json
import math
import subprocess
import sys
from pathlib import Path

import cv2
import numpy as np
from PIL import Image, ImageDraw, ImageFilter
from scipy import signal

import edit_trailer as v1
from edit_trailer import (W, H, FPS, SR, MAP_NAME, MAP_CODE, CYAN, CHARCOAL, WHITE, OLD_VO,
                          FONT_ANTON, FONT_INTER_XB, run, ease_out, ease_out_back, ease_in_out,
                          keyframes, write_wav, decode_audio, speech_regions, _font, _text, _glow,
                          _stack, _scrim, _underline, to_layer, overlay, _t, lp, hp, bp, sweep_bp,
                          noise, fade, norm, stereo, reverb, SourceCache, src_time_map)

HERE = Path(__file__).resolve().parent
ASSETS = HERE / "assets"
rng = np.random.default_rng(2024)

# ==========================================================================
# EDIT DECISION LIST  (durations chain automatically; times are derived)
#   pieces: (out_dur, src_a, src_b[, mode]) | ("F", out_dur, src_t)
# ==========================================================================
EDL = [
    # ---- HOOK: flash-forward teaser -> fight #1 -> slow-mo elimination -> burst
    dict(id="h0", pieces=[(0.25, 28.80, 29.05)], zoom=[(0, 1.18), (0.25, 1.10)], dark=0.35, game=False),
    dict(id="h1", pieces=[(0.85, 15.20, 16.05)], zoom=[(0, 1.26), (0.22, 1.07), (0.85, 1.10)],
         center=[(0, (600, 900)), (0.85, (550, 960))], game=True),
    dict(id="h2", pieces=[(0.50, 16.05, 16.25)], zoom=[(0, 1.10), (0.50, 1.19)], game=True),   # 0.4x
    dict(id="h2f", pieces=[("F", 0.15, 16.25)], zoom=[(0, 1.24), (0.15, 1.20)], game=False),   # impact freeze
    dict(id="h3", pieces=[(0.40, 16.25, 16.85, "accum")], zoom=[(0, 1.14), (0.40, 1.08)], game=True),
    dict(id="h4", pieces=[(0.35, 16.85, 17.55, "accum")], zoom=[(0, 1.08), (0.35, 1.20)], game=True),
    # ---- MAP IDENTITY
    dict(id="i1", pieces=[(1.05, 7.30, 8.35)], zoom=[(0, 1.20), (0.20, 1.05), (1.05, 1.09)], game=False),
    dict(id="i2", pieces=[(0.55, 9.95, 10.78, "accum")], zoom=[(0, 1.06), (0.55, 1.14)], game=False),
    dict(id="i3", pieces=[(0.75, 0.55, 1.68, "accum")], zoom=[(0, 1.31), (0.75, 1.37)],
         center=[(0, (540, 958))], game=False),
    dict(id="i4", pieces=[(0.60, 20.45, 21.05)], zoom=[(0, 1.18), (0.15, 1.06), (0.60, 1.10)], game=False),
    dict(id="i5", pieces=[(0.55, 11.95, 12.50)], zoom=[(0, 1.06), (0.55, 1.13)], game=False),
    dict(id="i6", pieces=[(0.30, 12.50, 13.40, "accum")], zoom=[(0, 1.10), (0.30, 1.22)], game=False),
    # ---- ACTION MONTAGE (escalating)
    dict(id="m1", pieces=[(0.90, 21.43, 22.33)], zoom=[(0, 1.22), (0.18, 1.06), (0.90, 1.10)], game=True),
    dict(id="m2", pieces=[(0.60, 17.00, 17.60)], zoom=[(0, 1.15), (0.12, 1.06), (0.60, 1.09)], game=True),
    dict(id="m3", pieces=[(0.50, 19.50, 20.05, "blend")], zoom=[(0, 1.07), (0.50, 1.15)], game=True),
    dict(id="m4", pieces=[(1.00, 31.33, 32.43, "blend")], zoom=[(0, 1.06), (1.00, 1.13)], game=False),
    dict(id="m5", pieces=[(1.40, 22.55, 24.05, "blend")], zoom=[(0, 1.16), (0.14, 1.06), (1.40, 1.10)], game=True),
    dict(id="m6", pieces=[(0.40, 17.65, 18.45, "accum")], zoom=[(0, 1.08), (0.40, 1.14)], game=True),
    dict(id="m7", pieces=[(0.30, 18.60, 18.90)], zoom=[(0, 1.12), (0.30, 1.06)], game=False),
    dict(id="m8", pieces=[(0.90, 18.95, 19.40)], zoom=[(0, 1.08), (0.12, 1.18), (0.90, 1.24)],   # 0.5x ADS
         center=[(0, (540, 1000))], game=True),
    dict(id="m9", pieces=[(0.75, 24.00, 24.75)], zoom=[(0, 1.06), (0.75, 1.18)], game=True),
    # ---- CLIMAX
    dict(id="c1", pieces=[(0.70, 24.75, 25.45)], zoom=[(0, 1.26), (0.22, 1.06), (0.70, 1.08)], game=True),
    dict(id="c2", pieces=[(0.50, 25.45, 25.95), (0.85, 25.95, 27.65, "accum"), (0.55, 27.65, 28.20)],
         zoom=[(0, 1.08), (1.90, 1.20)], game=True),
    dict(id="c3", pieces=[(0.65, 28.20, 28.72, "blend")], zoom=[(0, 1.20), (0.65, 1.34)], game=True),
    dict(id="c4f", pieces=[("F", 0.25, 28.77)], zoom=[(0, 1.30), (0.25, 1.20)], game=False),
    dict(id="c4", pieces=[(1.65, 28.77, 30.42)], zoom=[(0, 1.12), (0.10, 1.06), (1.65, 1.15)], game=True),
    dict(id="c5", pieces=[(0.90, 30.42, 31.30, "blend")], zoom=[(0, 1.08), (0.90, 1.14)], game=True),
    dict(id="c6", pieces=[(1.65, 32.65, 34.30, "blend")], zoom=[(0, 1.18), (0.18, 1.05), (1.65, 1.09)], game=False),
    # ---- END CARD (background = real gameplay, slowed + blurred)
    dict(id="end", pieces=[(6.00, 25.95, 27.55, "blend")], zoom=[(0, 1.10), (6.0, 1.18)], game=False, endcard=True),
]
_t0 = 0.0
for _s in EDL:
    _d = sum(p[1] if p[0] == "F" else p[0] for p in _s["pieces"])
    _s["t"] = (round(_t0, 4), round(_t0 + _d, 4))
    _t0 += _d
DUR = round(_t0, 4)
NFRAMES = int(round(DUR * FPS))
SEG = {s["id"]: s for s in EDL}


def T(sid, off=0.0):
    return SEG[sid]["t"][0] + off


def S2T(src, sid=None):
    """Output time at which a given SOURCE time is shown (first match)."""
    for s in EDL if sid is None else [SEG[sid]]:
        acc = s["t"][0]
        for p in s["pieces"]:
            if p[0] == "F":
                acc += p[1]
                continue
            d, a, b = p[0], p[1], p[2]
            if a <= src <= b:
                return acc + (src - a) / (b - a) * d
            acc += d
    raise ValueError(src)


CUTS = [s["t"][0] for s in EDL[1:]]
END0 = T("end")

# ---- narration: SAME processed v1 lines, re-placed for the new cut ---------
VO_PLACE = [(1, T("h1", 0.05)), (2, T("i1", 0.12)), (3, T("i3", 0.40)), (4, T("m1", 0.15)),
            (5, T("m5", -0.05)), (6, T("c2", 0.50)), (7, T("c5", 0.25)), (8, T("end", 0.80))]


def vo_rel_groups(line):
    info = json.loads((ASSETS / "vo" / "vo_timing.json").read_text())
    d = next(x for x in info if x["line"] == line)
    return [[a - d["start"], b - d["start"]] for a, b in d["groups"]]


def vo_word(line, group):
    start = dict(VO_PLACE)[line]
    return start + vo_rel_groups(line)[group][0]


# ==========================================================================
# VISUAL FX EVENT LIST  (only on real events: cuts, hits, eliminations, launches)
# ==========================================================================
def fx_events():
    E = []

    def add(kind, t, **kw):
        E.append(dict(kind=kind, t=t, **kw))
    # hook
    add("glitch", 0.0, dur=0.25, amt=0.8)
    add("rgb", 0.0, px=10, dec=0.25)
    add("flash", T("h1"), s=0.55, dec=0.07, col=(255, 250, 235))
    add("shake", T("h1"), amp=16, dec=0.22)
    add("rgb", T("h1"), px=8, dec=0.12)
    add("streak", T("h1"), y=980, dec=0.14)
    for src in (15.60, 15.80, 16.00):                       # real hit markers (38 / 63 / 113 dmg)
        add("bump", S2T(src), a=0.035, dec=0.10)
        add("shake", S2T(src), amp=5, dec=0.08)
    add("radial", T("h2f"), s=0.55, dec=0.12)              # ELIMINATED freeze
    add("ring", T("h2f"), x=560, y=1010, dur=0.40)
    add("distort", T("h2f"), x=560, y=1010, dur=0.35, amp=22)
    add("flash", T("h2f"), s=0.45, dec=0.06, col=(255, 240, 160))
    add("rgb", T("h2f"), px=9, dec=0.18)
    add("shake", T("h2f"), amp=18, dec=0.25)
    add("glitch", T("h4", 0.25), dur=0.18, amt=1.0)
    # title + identity
    add("flash", T("i1"), s=0.70, dec=0.10)
    add("shake", T("i1"), amp=18, dec=0.28)
    add("rgb", T("i1"), px=10, dec=0.18)
    add("streak", T("i1"), y=760, dec=0.22)
    add("ring", T("i1"), x=540, y=760, dur=0.45)
    add("whip", T("i2"), d=1, ax="h")
    add("glitch", T("i3", -0.06), dur=0.14, amt=0.8)
    add("flash", T("i4"), s=0.35, dec=0.07)
    add("shake", T("i4"), amp=8, dec=0.12)
    add("whip", T("i5"), d=-1, ax="h")
    add("radial", T("i6"), s=0.45, dec=0.20)
    # montage
    add("flash", T("m1"), s=0.60, dec=0.09)
    add("shake", T("m1"), amp=16, dec=0.25)
    add("rgb", T("m1"), px=8, dec=0.15)
    add("whip", T("m2"), d=1, ax="h")
    add("glitch", T("m3", -0.05), dur=0.12, amt=0.7)
    add("whip", T("m4"), d=1, ax="v")
    add("flash", T("m5"), s=0.30, dec=0.05)
    add("shake", T("m5"), amp=10, dec=0.14)
    add("radial", T("m6"), s=0.35, dec=0.12)
    add("whip", T("m7"), d=-1, ax="h")
    add("bump", T("m8", 0.10), a=0.05, dec=0.12)
    add("rgb", T("m8", 0.10), px=5, dec=0.10)
    add("whip", T("m9"), d=1, ax="v")
    # climax
    add("flash", T("c1"), s=0.75, dec=0.10, col=(255, 240, 200))
    add("shake", T("c1"), amp=20, dec=0.30)
    add("rgb", T("c1"), px=10, dec=0.20)
    add("streak", T("c1"), y=900, dec=0.18)
    add("bump", S2T(25.74, "c2"), a=0.05, dec=0.10)         # real rocket hit burst
    add("shake", S2T(25.74, "c2"), amp=7, dec=0.10)
    add("radial", T("c2", 0.50), s=0.30, dec=0.25)          # speed-ramp
    add("dim", T("c3", 0.40), t1=T("c4f"), amt=0.35)
    add("flash", T("c4f"), s=0.60, dec=0.06, col=(235, 252, 255))   # the finisher
    add("radial", T("c4f"), s=0.60, dec=0.08)
    add("ring", T("c4f"), x=540, y=930, dur=0.50)
    add("distort", T("c4f"), x=540, y=930, dur=0.45, amp=30)
    add("rgb", T("c4f"), px=14, dec=0.25)
    add("shake", T("c4f"), amp=24, dec=0.40)
    add("streak", T("c4f"), y=930, dec=0.25)
    add("shake", T("c4"), amp=10, dec=0.20)
    add("glitch", T("c5", -0.06), dur=0.12, amt=0.6)
    add("flash", T("c6"), s=0.55, dec=0.10)
    add("shake", T("c6"), amp=12, dec=0.22)
    add("streak", T("c6"), y=330, dec=0.20)
    # end card
    add("dim", T("end", -0.20), t1=T("end"), amt=0.55)
    add("flash", T("end"), s=0.65, dec=0.12)
    add("shake", T("end"), amp=14, dec=0.25)
    add("ring", T("end"), x=540, y=680, dur=0.55)
    add("streak", T("end"), y=680, dec=0.25)
    add("rgb", T("end"), px=8, dec=0.15)
    add("flash", DUR - 0.45, s=0.40, dec=0.12)
    add("shake", DUR - 0.45, amp=8, dec=0.20)
    add("ring", DUR - 0.45, x=540, y=1140, dur=0.40)
    return E


FX = fx_events()


# ==========================================================================
# GRAPHICS v2
# ==========================================================================
def build_graphics(out_dir):
    out_dir.mkdir(parents=True, exist_ok=True)
    A = lambda s: _font(FONT_ANTON, s)
    I = lambda s: _font(FONT_INTER_XB, s)
    L = {}
    L["think"] = _glow(_text("THINK", A(130), WHITE, 6), None, 10)
    L["youre"] = _glow(_text("YOU'RE", A(130), WHITE, 6), None, 10)
    L["ready"] = _glow(_text("READY?", A(250), WHITE, 8), CYAN, 24, 1.4)
    L["t_real"] = _glow(_text("REALISTIC", A(172), WHITE, 16), None, 10)
    L["t_scrims"] = _glow(_text("SCRIMS", A(310), WHITE, 10), CYAN, 28, 1.5)
    L["t_bar"] = _underline(600, 9)
    s1 = _text("YOUR MECHANICS.", A(80), WHITE, 3)
    s2 = _text("YOUR TEST.", A(80), CYAN, 3)
    row = Image.new("RGBA", (s1.width + 28 + s2.width, max(s1.height, s2.height)), (0, 0, 0, 0))
    row.alpha_composite(s1, (0, row.height - s1.height))
    row.alpha_composite(s2, (s1.width + 28, row.height - s2.height))
    L["sub"] = _scrim(_glow(row, None, 8), 70, 34, 170, 30)
    for key, word, size in [("build", "BUILD.", 230), ("aim", "AIM.", 240), ("prove", "PROVE YOURSELF.", 140)]:
        w = _glow(_text(word, A(size), WHITE, 6), CYAN, 22, 1.3)
        L[key] = _scrim(_stack([w, _underline(int(w.width * 0.45), 9)], gap=-50), 50, 0, 110, 40)
    # end card
    L["ec_real"] = _glow(_text("REALISTIC", A(176), WHITE, 16), None, 10)
    L["ec_scrims"] = _glow(_text("SCRIMS", A(300), WHITE, 10), CYAN, 30, 1.5)
    L["ec_label"] = _glow(_text("MAP CODE", I(46), CYAN, 14), CYAN, 10, 0.6)
    code_font = A(120)
    pw, ph = 880, 196
    panel = Image.new("RGBA", (pw + 80, ph + 80), (0, 0, 0, 0))
    gl = Image.new("RGBA", panel.size, (0, 0, 0, 0))
    ImageDraw.Draw(gl).rounded_rectangle((40, 40, 40 + pw, 40 + ph), 34, outline=CYAN + (255,), width=10)
    panel.alpha_composite(gl.filter(ImageFilter.GaussianBlur(14)))
    box = Image.new("RGBA", panel.size, (0, 0, 0, 0))
    ImageDraw.Draw(box).rounded_rectangle((40, 40, 40 + pw, 40 + ph), 34, fill=(10, 14, 20, 232),
                                          outline=CYAN + (255,), width=5)
    panel.alpha_composite(box)
    L["ec_panel"] = panel
    code = _text(MAP_CODE, code_font, WHITE, 4)
    L["ec_code"] = _glow(code, None, 8)
    hi = Image.new("RGBA", L["ec_code"].size, (0, 0, 0, 0))
    hi.paste(CYAN + (255,), (0, 0), L["ec_code"].split()[3])
    L["ec_code_hi"] = hi.filter(ImageFilter.GaussianBlur(16))
    pill = Image.new("RGBA", (520, 150), (0, 0, 0, 0))
    ImageDraw.Draw(pill).rounded_rectangle((10, 10, 510, 140), 65, fill=CYAN + (255,))
    pt = _text("PLAY NOW", A(86), (6, 16, 21, 255), 8)
    pill.alpha_composite(pt, ((pill.width - pt.width) // 2, (pill.height - pt.height) // 2))
    L["ec_play"] = _glow(pill, CYAN, 22, 0.9)
    L["ec_line"] = _underline(330, 6)
    for k, im in L.items():
        im.save(out_dir / f"{k}.png")
    # code group x-ranges (measured from glyph advances) for reveal + highlight
    pad = (L["ec_code"].width - code.width) // 2
    groups, x = [], pad
    for g in MAP_CODE.split("-"):
        gw = code_font.getlength(g) + 4 * (len(g) - 1)
        groups.append((int(x - 12), int(x + gw + 12)))
        x += gw + 4 + code_font.getlength("-") + 8
    return L, groups


# ==========================================================================
# PICTURE FX helpers
# ==========================================================================
_VIG = _LUT = None


def grade(img, dark=0.0):
    """Punchier cinematic grade than v1: more contrast, cool shadows, deeper vignette."""
    global _VIG, _LUT
    if _VIG is None:
        yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
        r = np.sqrt(((xx - W / 2) / (W * 0.62)) ** 2 + ((yy - H / 2) / (H * 0.62)) ** 2)
        v = np.clip(1 - 0.42 * np.clip(r - 0.50, 0, None) ** 1.3 / 0.6, 0.58, 1)
        _VIG = cv2.merge([(v * 255).astype(np.uint8)] * 3)
        x = np.arange(256) / 255.0
        curve = np.clip(0.5 + (x - 0.5) * 1.12, 0, 1)
        curve = curve - 0.025 * (1 - x) ** 3                               # deeper blacks
        sh = np.clip(1 - x * 2.4, 0, 1)
        hl = np.clip((x - 0.6) / 0.4, 0, 1)
        lb = np.clip(curve + 0.040 * sh - 0.010 * hl, 0, 1)
        lg = np.clip(curve + 0.016 * sh, 0, 1)
        lr = np.clip(curve - 0.012 * sh + 0.012 * hl, 0, 1)
        _LUT = (np.stack([lb, lg, lr], 1).reshape(256, 1, 3) * 255).astype(np.uint8)
    sat = 1.14 - dark
    wts = np.array([0.114, 0.587, 0.299], np.float32)
    M = (np.eye(3, dtype=np.float32) * sat + (1 - sat) * np.tile(wts, (3, 1))).astype(np.float32)
    img = cv2.LUT(cv2.transform(img, M), _LUT)
    img = cv2.multiply(img, _VIG, scale=1 / 255)
    if dark:
        img = cv2.convertScaleAbs(img, alpha=1 - dark)
    blur = cv2.GaussianBlur(img, (0, 0), 1.1)
    return cv2.addWeighted(img, 1.32, blur, -0.32, 0)


def env_of(e, t):
    """Exponential decay envelope of an event (0 before it)."""
    u = t - e["t"]
    if u < 0:
        return 0.0
    if "dec" in e:
        return math.exp(-u / e["dec"]) if u < e["dec"] * 6 else 0.0
    if "dur" in e:
        return 1 - u / e["dur"] if u < e["dur"] else 0.0
    return 0.0


def rgb_split(img, px, vertical=False):
    if px < 1:
        return img
    out = img.copy()
    ax = 0 if vertical else 1
    out[..., 0] = np.roll(img[..., 0], -px, axis=ax)
    out[..., 2] = np.roll(img[..., 2], px, axis=ax)
    return out


def glitch(img, amt, seed):
    r = np.random.default_rng(seed)
    out = img.copy()
    for _ in range(int(4 + 10 * amt)):
        h = int(r.integers(8, 90))
        y = int(r.integers(0, H - h))
        dx = int(r.integers(-1, 2) * r.integers(20, int(40 + 220 * amt)))
        band = np.roll(img[y:y + h], dx, axis=1)
        if r.random() < 0.35:
            band = band.copy()
            band[..., 0] = np.roll(band[..., 0], int(r.integers(8, 30)), axis=1)
        if r.random() < 0.18:
            band = cv2.addWeighted(band, 0.55, np.full_like(band, (255, 229, 0)), 0.45, 0)  # cyan (BGR)
        out[y:y + h] = band
    return rgb_split(out, int(4 + 10 * amt))


def radial_blur(img, s, cx=W / 2, cy=H / 2):
    n = 6
    acc = img.astype(np.float32)
    for k in range(1, n):
        z = 1 + 0.06 * s * k / (n - 1)
        M = np.float32([[z, 0, cx - z * cx], [0, z, cy - z * cy]])
        acc += cv2.warpAffine(img, M, (W, H), borderMode=cv2.BORDER_REFLECT101)
    return (acc / n).astype(np.uint8)


_GRID = None


def shock_distort(img, cx, cy, radius, amp):
    global _GRID
    if _GRID is None:
        yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
        _GRID = (xx, yy)
    xx, yy = _GRID
    dx, dy = xx - cx, yy - cy
    dist = np.sqrt(dx * dx + dy * dy) + 1e-3
    d = amp * np.exp(-((dist - radius) / 55.0) ** 2)
    return cv2.remap(img, xx - dx / dist * d, yy - dy / dist * d, cv2.INTER_LINEAR,
                     borderMode=cv2.BORDER_REFLECT101)


def add_ring(img, cx, cy, radius, alpha):
    sc = 4
    c = np.zeros((H // sc, W // sc), np.float32)
    cv2.circle(c, (int(cx / sc), int(cy / sc)), int(radius / sc), 1.0, max(1, int(10 / sc)), cv2.LINE_AA)
    c = cv2.GaussianBlur(c, (0, 0), 2.5) * 2.2 + cv2.GaussianBlur(c, (0, 0), 0.7)
    c = cv2.resize(c, (W, H), interpolation=cv2.INTER_LINEAR)[..., None] * alpha
    col = np.array([255, 235, 140], np.float32)                     # icy cyan-white (BGR)
    return np.clip(img.astype(np.float32) + c * col, 0, 255).astype(np.uint8)


_STREAK = None


def add_streak(img, y, alpha, grow):
    global _STREAK
    if _STREAK is None:
        yy = np.arange(-90, 91, dtype=np.float32)[:, None]
        xx = np.linspace(-1, 1, W, dtype=np.float32)[None, :]
        core = np.exp(-(yy / 3.0) ** 2) + 0.35 * np.exp(-(yy / 22.0) ** 2) + 0.12 * np.exp(-(yy / 70.0) ** 2)
        _STREAK = (core, xx)
    core, xx = _STREAK
    s = core * np.exp(-(xx / max(0.05, grow)) ** 2)
    col = np.array([255, 240, 190], np.float32)
    y0, y1 = max(0, y - 90), min(H, y + 91)
    reg = img[y0:y1].astype(np.float32) + (s[(y0 - (y - 90)):(y0 - (y - 90)) + (y1 - y0)] * alpha)[..., None] * col
    img = img.copy()
    img[y0:y1] = np.clip(reg, 0, 255).astype(np.uint8)
    return img


def glitch_layer(layer, amt, seed):
    rgb, a = layer
    r = np.random.default_rng(seed)
    rgb, a = rgb.copy(), a.copy()
    h = a.shape[0]
    for _ in range(int(3 + 6 * amt)):
        bh = int(r.integers(4, max(6, h // 6)))
        y = int(r.integers(0, max(1, h - bh)))
        dx = int(r.integers(-60, 61) * amt)
        rgb[y:y + bh] = np.roll(rgb[y:y + bh], dx, axis=1)
        a[y:y + bh] = np.roll(a[y:y + bh], dx, axis=1)
    sh = int(6 * amt) + 1
    rgb[..., 0] = np.roll(rgb[..., 0], -sh, axis=1)
    rgb[..., 2] = np.roll(rgb[..., 2], sh, axis=1)
    return rgb, a


# ==========================================================================
# RENDERER
# ==========================================================================
class Renderer:
    def __init__(self, source, layers, groups):
        self.cache = SourceCache(source)
        self.loaded = None
        self.L = {k: to_layer(v) for k, v in layers.items()}
        self.groups = groups
        self.white = np.full((H, W, 3), 255, np.uint8)
        self.code_hi = [vo_word(8, g) for g in range(3)]

    def seg_at(self, t):
        for s in EDL:
            if s["t"][0] <= t < s["t"][1]:
                return s
        return EDL[-1]

    def source_frame(self, seg, t):
        if self.loaded is not seg:
            srcs = [v for p in seg["pieces"] for v in ((p[2],) if p[0] == "F" else (p[1], p[2]))]
            self.cache.load(min(srcs), max(srcs))
            self.loaded = seg
        s, mode, sp = src_time_map(seg, t - seg["t"][0])
        f = s * FPS
        if mode in ("nearest", "freeze"):
            return self.cache.get(int(round(f))).copy()
        if mode == "blend":
            i, w = int(math.floor(f)), f - math.floor(f)
            return cv2.addWeighted(self.cache.get(i), 1 - w, self.cache.get(i + 1), w, 0)
        i0, i1 = int(round(f)), int(round(f + sp))
        acc = np.zeros((H, W, 3), np.float32)
        for i in range(i0, max(i1, i0 + 1)):
            acc += self.cache.get(i)
        return (acc / max(1, i1 - i0)).astype(np.uint8)

    # ---- camera ----------------------------------------------------------
    def camera(self, seg, t):
        rel = t - seg["t"][0]
        z = keyframes(seg["zoom"], rel)
        for e in FX:
            if e["kind"] == "bump":
                u = t - e["t"]
                if 0 <= u < 0.6:
                    z *= 1 + e["a"] * min(1, u / 0.025) * math.exp(-u / e["dec"])
        c = seg.get("center", [(0, (W / 2, H / 2))])
        cx = keyframes([(k, v[0]) for k, v in c], rel)
        cy = keyframes([(k, v[1]) for k, v in c], rel)
        sx = sy = 0.0
        for e in FX:
            if e["kind"] == "shake":
                a = e["amp"] * env_of(e, t)
                if a > 0.2:
                    u = t - e["t"]
                    sx += a * (0.6 * math.sin(2 * math.pi * 19 * u + 1.3) + 0.4 * math.sin(2 * math.pi * 31 * u + .4))
                    sy += a * (0.6 * math.sin(2 * math.pi * 23 * u + 2.1) + 0.4 * math.sin(2 * math.pi * 37 * u + 1.7))
        wx = wy = wb = 0.0
        wax = None
        for e in FX:
            if e["kind"] != "whip":
                continue
            tc, pre, post = e["t"], 4 / FPS, 5 / FPS
            if tc - pre <= t < tc:
                p = (t - (tc - pre)) / pre
                off, wb = -e["d"] * p * p * 0.32, 8 + p * 80
            elif tc <= t < tc + post:
                p = 1 - (t - tc) / post
                off, wb = e["d"] * p * p * 0.32, 8 + p * 80
            else:
                continue
            wax = e["ax"]
            wx, wy = (off * W, 0) if wax == "h" else (0, off * H)
        return z, cx, cy, sx + wx, sy + wy, wb, wax

    # ---- text animation helpers ------------------------------------------
    def slam(self, img, name, t, t0, t1, cx, cy, big=0.6, trail=True, gl=0.0, exit_glitch=True):
        if not (t0 <= t < t1):
            return
        u, r = t - t0, t1 - t
        sc = 1 + big * (1 - ease_out(u / 0.13))
        op = min(1, u / 0.04)
        lay = self.L[name]
        if gl and u < 0.10:
            lay = glitch_layer(lay, gl, int(t * 1000))
        if r < 0.10 and exit_glitch:
            lay = glitch_layer(lay, 1.0, int(t * 1000) + 7)
            op *= r / 0.10
        if trail and u < 0.16:                             # motion trail ghosts
            for k in (3, 2, 1):
                us = max(0, u - k / FPS)
                overlay(img, lay, cx, cy, 1 + big * (1 - ease_out(us / 0.13)) * 1.15, op * 0.16 * (4 - k) / 3)
        overlay(img, lay, cx, cy, sc, op)

    def captions(self, img, t):
        # HOOK kinetic: THINK / YOU'RE / READY?
        h_end = T("h4", 0.30)
        self.slam(img, "think", t, T("h1", 0.08), h_end, 360, 330, big=0.5)
        self.slam(img, "youre", t, T("h1", 0.24), h_end, 720, 330, big=0.5)
        self.slam(img, "ready", t, T("h1", 0.46), h_end, 540, 475, big=0.8, gl=0.8)
        # TITLE: REALISTIC slides in w/ trail + mask, SCRIMS slams, bar wipes
        t0, t1 = T("i1"), T("i3", -0.05)
        if t0 <= t < t1:
            u, r = t - t0, t1 - t
            op = 1 if r > 0.10 else r / 0.10
            x = 540 - 700 * (1 - ease_out(u / 0.16))
            for k in (3, 2, 1):
                if u < 0.22:
                    overlay(img, self.L["t_real"], 540 - 700 * (1 - ease_out(max(0, u - k / FPS) / 0.16)), 560,
                            1, 0.14 * (4 - k) * op)
            lr = self.L["t_real"] if r > 0.10 else glitch_layer(self.L["t_real"], 1, int(t * 999))
            overlay(img, lr, x, 560, 1, op)
            self.slam(img, "t_scrims", t, t0 + 0.08, t1, 540, 790, big=0.55, gl=0.9)
            if u > 0.20:
                overlay(img, self.L["t_bar"], 540, 955, 1, op, reveal=ease_out((u - 0.20) / 0.20))
        # sub caption (mask wipe left -> right)
        t0, t1 = T("i3", 0.05), T("m1", -0.05)
        if t0 <= t < t1:
            u, r = t - t0, t1 - t
            lay = self.L["sub"]
            w = lay[1].shape[1]
            cut = max(2, int(w * ease_out(u / 0.25)))
            op = 1 if r > 0.12 else r / 0.12
            overlay(img, (lay[0][:, :cut], lay[1][:, :cut]), 540 - w / 2 + cut / 2, 1360, 1, op)
        self.slam(img, "build", t, vo_word(5, 0), T("m6", -0.02), 540, 430, big=0.45, gl=0.5)
        self.slam(img, "aim", t, max(T("m8"), vo_word(5, 3)), T("m9", -0.02), 540, 430, big=0.45, gl=0.5)
        self.slam(img, "prove", t, vo_word(7, 2), T("end", -0.02), 540, 1130, big=0.4, gl=0.6)

    def endcard(self, img, t):
        e = t - END0
        L = self.L
        self.slam(img, "ec_real", t, END0, DUR + 1, 540, 530, big=0.35, gl=0.6, exit_glitch=False)
        self.slam(img, "ec_scrims", t, END0 + 0.10, DUR + 1, 540, 760, big=0.6, gl=0.8, exit_glitch=False)
        g = ease_out((e - 0.25) / 0.35)
        if g > 0:
            ln = L["ec_line"]
            w = ln[1].shape[1]
            cut = max(2, int(w * g))
            overlay(img, (ln[0][:, :cut], ln[1][:, :cut]), 500 - cut / 2, 925, 1, 1)
            overlay(img, (ln[0][:, w - cut:], ln[1][:, w - cut:]), 580 + cut / 2, 925, 1, 1)
        u = e - 0.40
        if u > 0:
            overlay(img, L["ec_label"], 540, 1000 + 18 * (1 - ease_out(u / 0.25)), 1, min(1, u / 0.15))
        u = e - 0.50
        if u > 0:
            overlay(img, L["ec_panel"], 540, 1140, 1, 1, reveal=ease_out(u / 0.18))
            cw = L["ec_code"][1].shape[1]
            spans = [(0, (self.groups[0][1] + self.groups[1][0]) // 2),
                     ((self.groups[0][1] + self.groups[1][0]) // 2, (self.groups[1][1] + self.groups[2][0]) // 2),
                     ((self.groups[1][1] + self.groups[2][0]) // 2, cw)]
            for gi, (x0, x1) in enumerate(spans):              # groups (incl. dashes) snap in one by one
                ug = u - 0.16 - gi * 0.12
                if ug <= 0:
                    continue
                lay = L["ec_code"]
                crop = (max(0, x0), min(cw, x1))
                gx = 540 - cw // 2 + (x0 + x1) / 2
                if ug < 0.06:
                    lay = glitch_layer(lay, 0.6, int(t * 1000) + gi)
                overlay(img, lay, gx, 1140, 1 + 0.25 * (1 - ease_out(ug / 0.08)), min(1, ug / 0.03), xcrop=crop)
                if ug < 0.25:
                    overlay(img, L["ec_code_hi"], gx, 1140, 1, 0.9 * (1 - ug / 0.25), xcrop=crop)
            ph = ((e - 1.2) % 1.6) / 0.5                       # glint
            if e > 1.2 and 0 <= ph <= 1:
                x = int(100 + ph * 880)
                band = slice(max(0, x - 36), min(W, x + 36))
                img[1045:1235, band] = np.clip(img[1045:1235, band].astype(np.int16) + 40, 0, 255).astype(np.uint8)
            for gs, (x0, x1) in zip(self.code_hi, self.groups):  # VO-synced highlight
                d = t - gs
                if 0 <= d < 1.1:
                    op = min(1, d / 0.08) * (1 - d / 1.1) * 0.9
                    overlay(img, L["ec_code_hi"], 540 - cw / 2 + (x0 + x1) / 2, 1140, 1, op,
                            xcrop=(max(0, x0), min(cw, x1)))
        u = e - 1.05
        if u > 0:
            pulse = 1 + 0.03 * math.sin(2 * math.pi * 2.0 * e) if u > 0.3 else 1
            overlay(img, L["ec_play"], 540, 1360, (0.6 + 0.4 * ease_out_back(u / 0.18)) * pulse, min(1, u / 0.05))

    def endcard_bg(self, img):
        small = cv2.GaussianBlur(cv2.resize(img, (W // 4, H // 4), interpolation=cv2.INTER_AREA), (0, 0), 5)
        img = cv2.resize(small, (W, H)).astype(np.float32) * 0.34 + np.array(CHARCOAL[::-1], np.float32) * 0.3
        if not hasattr(self, "_g"):
            yy = np.linspace(0, 1, H)[:, None]
            xx = np.linspace(-1, 1, W)[None, :]
            r = np.sqrt(xx ** 2 + ((yy - 0.55) * 1.9) ** 2)
            self._g = (np.clip(1 - r, 0, 1)[..., None] ** 2 * 0.35 * np.array(CYAN[::-1], np.float32) * 0.4,
                       (1 - 0.45 * (np.abs(yy - 0.55) * 1.6) ** 2)[..., None])
        return np.clip(img * self._g[1] + self._g[0], 0, 255).astype(np.uint8)

    def frame(self, k):
        t = k / FPS
        seg = self.seg_at(t)
        img = self.source_frame(seg, t)
        z, cx, cy, ox, oy, wb, wax = self.camera(seg, t)
        M = np.float32([[z, 0, W / 2 + ox - z * cx], [0, z, H / 2 + oy - z * cy]])
        img = cv2.warpAffine(img, M, (W, H), flags=cv2.INTER_LINEAR, borderMode=cv2.BORDER_REFLECT101)
        img = self.endcard_bg(img) if seg.get("endcard") else grade(img, seg.get("dark", 0.0))
        if wb > 1:
            kk = int(wb) | 1
            img = cv2.blur(img, (kk, 1) if wax == "h" else (1, kk))
        for e in FX:
            kind = e["kind"]
            if kind == "radial":
                s = e["s"] * env_of(e, t)
                if s > 0.03:
                    img = radial_blur(img, s)
            elif kind == "distort" and 0 <= t - e["t"] < e["dur"]:
                p = (t - e["t"]) / e["dur"]
                img = shock_distort(img, e["x"], e["y"], 60 + p * 900, e["amp"] * (1 - p))
            elif kind == "glitch" and 0 <= t - e["t"] < e["dur"]:
                img = glitch(img, e["amt"] * (1 - 0.5 * (t - e["t"]) / e["dur"]), k)
            elif kind == "dim" and e["t"] <= t < e["t1"]:
                img = cv2.convertScaleAbs(img, alpha=1 - e["amt"] * ease_in_out((t - e["t"]) / (e["t1"] - e["t"])))
        px = sum(e["px"] * env_of(e, t) for e in FX if e["kind"] == "rgb")
        if px >= 1:
            img = rgb_split(img, int(round(px)))
        for e in FX:
            if e["kind"] == "ring" and 0 <= t - e["t"] < e["dur"]:
                p = (t - e["t"]) / e["dur"]
                img = add_ring(img, e["x"], e["y"], 40 + 820 * ease_out(p), 0.9 * (1 - p) ** 1.5)
            elif e["kind"] == "streak":
                a = env_of(e, t)
                if a > 0.03:
                    img = add_streak(img, e["y"], 0.95 * a, 0.25 + 1.4 * (1 - a))
        if seg.get("endcard"):
            self.endcard(img, t)
        self.captions(img, t)
        fl, col = 0.0, (255, 255, 255)
        for e in FX:
            if e["kind"] == "flash":
                a = e["s"] * env_of(e, t)
                if a > fl:
                    fl, col = a, e.get("col", (255, 255, 255))
        if fl > 0.01:
            img = cv2.addWeighted(img, 1 - fl, np.full_like(img, col[::-1]), fl, 0)
        if t > DUR - 0.30:
            img = cv2.convertScaleAbs(img, alpha=max(0.0, (DUR - t) / 0.30))
        return img


# ==========================================================================
# ORIGINAL SFX LIBRARY (synthesised; no samples, no music)
# ==========================================================================
def _eqlow(x):
    """Phone-friendly bass: remove rumble < 32 Hz, keep harmonics that translate."""
    return hp(x, 32, 2)


def s_boom(d=2.4, f0=95, f1=36, body=1.0, seed=0):
    r = np.random.default_rng(seed)
    t = _t(d)
    f = f1 + (f0 - f1) * np.exp(-t / 0.075)
    x = np.tanh(2.2 * np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t / 0.55))
    nz = r.standard_normal(len(t))
    x = x + body * 0.55 * bp(nz, 90, 700) * np.exp(-t / 0.20) + 0.45 * lp(nz, 4500) * np.exp(-t / 0.030)
    return _eqlow(fade(x, 0.0005, 0.3))


def s_sub(d=0.7, f=52):
    t = _t(d)
    ff = f * (1 + 1.6 * np.exp(-t / 0.018))
    x = np.sin(2 * np.pi * np.cumsum(ff) / SR) * np.exp(-t / 0.22)
    return _eqlow(np.tanh(2.6 * x) * 0.9)                      # saturation -> audible on phones


def s_snap(d=0.12, tone=3200, seed=1):
    r = np.random.default_rng(seed)
    t = _t(d)
    x = hp(r.standard_normal(len(t)), 2500) * np.exp(-t / 0.004) + 0.5 * np.sin(2 * np.pi * tone * t) * np.exp(-t / 0.012)
    return fade(x, 0.0003, 0.02) * 0.9


def s_metal(d=1.2, f0=310, seed=2):
    r = np.random.default_rng(seed)
    t = _t(d)
    x = np.zeros(len(t))
    for ratio, dec, g in [(1, .45, 1), (2.76, .30, .7), (5.40, .18, .5), (8.93, .10, .35), (13.3, .06, .25)]:
        x += g * np.sin(2 * np.pi * f0 * ratio * t + r.random() * 6) * np.exp(-t / dec)
    x += 0.6 * bp(r.standard_normal(len(t)), 1500, 8000) * np.exp(-t / 0.01)
    return fade(x, 0.0003, 0.1) * 0.6


def s_whoosh(d=0.35, lo=300, hi=5200, pan=(-0.9, 0.9), seed=3, power=1.6):
    r = np.random.default_rng(seed)
    t = _t(d)
    x = sweep_bp(r.standard_normal(len(t)), lambda u: lo + (hi - lo) * math.sin(math.pi * u) ** power, q=1.3)
    x = norm(x * np.sin(np.pi * t / d) ** 2, 1)
    p = np.linspace(pan[0], pan[1], len(t))
    return np.stack([x * np.cos((p + 1) * np.pi / 4), x * np.sin((p + 1) * np.pi / 4)], 1)


def s_slice(seed=4):                                           # fast air-slicing
    return s_whoosh(0.16, 1500, 9000, (-0.6, 0.9), seed, 1.0) * 0.9


def s_reverse(d=0.7, seed=5):
    x = s_boom(d + 0.5, 140, 50, 1.2, seed)[:int(d * SR)]
    x = reverb(x, 0.35, 0.4)[:int(d * SR)]
    return fade(norm(x[::-1], 1), 0.02, 0.004)


def s_riser(d=1.2, seed=6, top=7000):
    r = np.random.default_rng(seed)
    t = _t(d)
    nz = sweep_bp(r.standard_normal(len(t)), lambda u: 250 * (top / 250) ** u, q=2.0)
    f = 120 * (6.0 ** (t / d))
    tone = sum(np.sign(np.sin(2 * np.pi * np.cumsum(f * m) / SR)) * g for m, g in [(1, .3), (1.005, .3), (2.01, .15)])
    tone = lp(tone, 3500)
    trem = 0.75 + 0.25 * np.sin(2 * np.pi * np.cumsum(4 + 26 * (t / d) ** 2) / SR)
    x = (norm(nz, 1) * 0.7 + norm(tone, 1) * 0.3) * (t / d) ** 2.0 * trem
    return stereo(fade(x, 0.01, 0.003), width=0.6)


def s_charge(d=0.8):
    t = _t(d)
    f = 180 * (8 ** (t / d))
    x = np.sin(2 * np.pi * np.cumsum(f) / SR) * (0.6 + 0.4 * np.sign(np.sin(2 * np.pi * (12 + 40 * t / d) * t)))
    return stereo(fade(lp(x, 6000) * (t / d) ** 1.5, 0.01, 0.003), width=0.4) * 0.6


def s_glitch(d=0.26, seed=7):
    r = np.random.default_rng(seed)
    n = int(d * SR)
    x = np.zeros(n)
    i = 0
    while i < n:
        L = int(r.integers(int(0.006 * SR), int(0.03 * SR)))
        kind = r.integers(0, 3)
        tt = np.arange(L) / SR
        if kind == 0:
            seg = np.sign(np.sin(2 * np.pi * r.uniform(200, 2500) * tt))
        elif kind == 1:
            seg = np.repeat(r.uniform(-1, 1, L // 24 + 1), 24)[:L]          # sample & hold
        else:
            seg = np.zeros(L)
        x[i:i + L] = seg[:n - i] * r.uniform(0.3, 1)
        i += L
    x = np.round(x * 6) / 6                                                   # bit-crush
    return stereo(fade(bp(x, 300, 9000), 0.002, 0.01), width=0.8) * 0.55


def s_stinger(seed=8):
    r = np.random.default_rng(seed)
    out = np.zeros(int(0.36 * SR))
    for k, f in enumerate([2400, 1800, 1200, 2700]):
        t = _t(0.07)
        b = np.sin(2 * np.pi * f * t + 3 * np.sin(2 * np.pi * f * 1.5 * t)) * np.exp(-t / 0.025)
        i = int(k * 0.055 * SR)
        out[i:i + len(b)] += b
    return stereo(fade(out, 0.001, 0.05), width=0.5) * 0.45


def s_hitconfirm(f=2300):
    t = _t(0.16)
    x = (np.sin(2 * np.pi * f * t) + 0.6 * np.sin(2 * np.pi * f * 1.5 * t)) * np.exp(-t / 0.03)
    x[:int(0.004 * SR)] += hp(noise(0.004), 3000) * 0.8
    return fade(x, 0.0003, 0.02) * 0.5


def s_gun(seed=9):
    r = np.random.default_rng(seed)
    t = _t(0.25)
    x = (np.sin(2 * np.pi * (60 + 140 * np.exp(-t / 0.01)) * t) * np.exp(-t / 0.05) * 0.9 +
         bp(r.standard_normal(len(t)), 1200, 7000) * np.exp(-t / 0.018))
    return _eqlow(np.tanh(1.8 * fade(x, 0.0003, 0.03)) * 0.7)


def s_build(seed=10):
    r = np.random.default_rng(seed)
    t = _t(0.09)
    clk = np.sin(2 * np.pi * 4200 * t) * np.exp(-t / 0.004)
    clack = bp(r.standard_normal(len(t)), 500, 2500) * np.exp(-t / 0.015)
    thud = np.sin(2 * np.pi * 140 * t) * np.exp(-t / 0.02)
    return fade(clk * 0.5 + clack * 0.6 + thud * 0.5, 0.0003, 0.01) * 0.8


def s_rumble(d=1.4, seed=11):
    r = np.random.default_rng(seed)
    t = _t(d)
    x = lp(r.standard_normal(len(t)), 140, 4) * 6 * (0.6 + 0.4 * np.sin(2 * np.pi * 3 * t))
    x += 0.4 * np.sin(2 * np.pi * 48 * t) * (0.5 + 0.5 * np.sin(2 * np.pi * 0.7 * t))
    return _eqlow(stereo(fade(x * np.minimum(1, t / 0.3), 0.01, 0.25), width=0.5) * 0.6)


def s_pulse(f=58):
    t = _t(0.28)
    x = np.sin(2 * np.pi * f * (1 + 0.8 * np.exp(-t / 0.01)) * t) * np.exp(-t / 0.07)
    x += 0.25 * hp(noise(0.28), 2000) * np.exp(-t / 0.006)
    return _eqlow(np.tanh(2 * x) * 0.8)


def s_tick(f=3600):
    t = _t(0.05)
    return fade(np.sin(2 * np.pi * f * t) * np.exp(-t / 0.008), 0.0003, 0.005) * 0.45


def s_downer(d=0.5):
    t = _t(d)
    f = 900 * (0.1 ** (t / d))
    x = np.sin(2 * np.pi * np.cumsum(f) / SR) * 0.4 + 0.6 * norm(sweep_bp(noise(d), lambda u: 5000 * 0.06 ** u, 1.0), 1)
    return stereo(fade(x * np.exp(-t / (d * 0.6)), 0.003, 0.05), width=0.5) * 0.7


def tail(x, wet=0.30, dec=0.45, length=1.4):
    y = reverb(x if x.ndim == 2 else stereo(x), wet, dec)
    return y[:int((len(x) / SR + length) * SR)]


def build_sfx(out_dir):
    out_dir.mkdir(parents=True, exist_ok=True)
    S = {
        "boom_xl": tail(s_boom(2.8, 110, 33, 1.2, 1), 0.35, 0.7, 1.6),
        "boom_l": tail(s_boom(2.2, 100, 38, 1.0, 2), 0.28, 0.55),
        "boom_m": tail(s_boom(1.4, 120, 45, 0.8, 3), 0.22, 0.4, 0.8),
        "sub_a": s_sub(0.7, 52), "sub_b": s_sub(0.5, 64),
        "snap_a": s_snap(0.12, 3200, 1), "snap_b": s_snap(0.10, 4400, 2), "snap_c": s_snap(0.14, 2400, 3),
        "metal_a": tail(s_metal(1.2, 310, 2), 0.25, 0.5, 0.8), "metal_b": tail(s_metal(0.9, 470, 5), 0.2, 0.4, 0.6),
        "whoosh_a": s_whoosh(0.34, 300, 5200, (-0.9, 0.9), 3), "whoosh_b": s_whoosh(0.42, 250, 4200, (0.9, -0.9), 13),
        "whoosh_c": s_whoosh(0.26, 600, 7000, (-0.5, 0.8), 23, 1.2), "whoosh_long": s_whoosh(0.65, 180, 3800, (-1, 1), 33),
        "slice_a": s_slice(4), "slice_b": s_slice(14),
        "reverse_a": s_reverse(0.7, 5), "reverse_s": s_reverse(0.35, 15),
        "riser_1": s_riser(1.2, 6), "riser_2": s_riser(2.4, 16, 9000), "charge": s_charge(0.8),
        "glitch_a": s_glitch(0.26, 7), "glitch_b": s_glitch(0.18, 17), "glitch_c": s_glitch(0.32, 27),
        "stinger": s_stinger(8), "hit_a": s_hitconfirm(2300), "hit_b": s_hitconfirm(2750),
        "gun": s_gun(9), "build": s_build(10), "build_b": s_build(20),
        "rumble": s_rumble(1.4, 11), "pulse": s_pulse(58), "tick": s_tick(3600), "tick_b": s_tick(2900),
        "downer": s_downer(0.5),
    }
    for k, v in S.items():
        v = v if v.ndim == 2 else stereo(v)
        S[k] = norm(v, 0.95)
        write_wav(out_dir / f"{k}.wav", S[k], bits=16)
    return S


# ---- onsets of REAL gameplay events (gunfire, build placements) -----------
def onsets(src_audio, a, b, min_gap=0.075, k=2.2):
    i0, i1 = int(a * SR), int(b * SR)
    x = hp(src_audio[i0:i1].mean(1), 1800)
    hop = int(0.004 * SR)
    n = len(x) // hop
    e = np.sqrt((x[:n * hop].reshape(n, hop) ** 2).mean(1) + 1e-12)
    flux = np.maximum(0, np.diff(20 * np.log10(e), prepend=-120))
    thr = np.median(flux) + k * flux.std()
    out, last = [], -1
    for i in range(1, n - 1):
        if flux[i] > thr and flux[i] >= flux[i - 1] and flux[i] >= flux[i + 1] and e[i] > 0.01:
            ts = a + i * hop / SR
            if ts - last >= min_gap:
                out.append(round(ts, 3))
                last = ts
    return out


def sfx_cues(src_audio):
    C = []

    def c(t, name, g=1.0, duck=True):
        C.append((t, name, g, duck))

    def big_hit(t, boom="boom_l", g=1.0, metal=None, pre="reverse_a", gap=0.0):
        L = dict(reverse_a=0.7, reverse_s=0.35)[pre]
        c(t - gap - L, pre, 0.55 * g)            # swell ends where the dramatic silence starts
        c(t, "snap_a", 0.75 * g)
        c(t, boom, 0.95 * g)
        c(t, "sub_a", 0.70 * g)
        if metal:
            c(t, metal, 0.40 * g)

    # OPENING: low rumble + glitch teaser -> sharp impact + whoosh
    c(0.0, "rumble", 0.70, False)
    c(0.0, "glitch_a", 0.55)
    c(T("h1") - 0.35, "reverse_s", 0.6)
    c(T("h1"), "snap_b", 0.8); c(T("h1"), "boom_m", 0.85); c(T("h1"), "sub_b", 0.6)
    c(T("h1") + 0.02, "whoosh_c", 0.45)
    for i, src in enumerate((15.60, 15.80, 16.00)):                     # real hit markers
        c(S2T(src), "hit_a" if i < 2 else "hit_b", 0.55)
    c(T("h2") - 0.02, "downer", 0.55)
    # ELIMINATION #1 (layered)
    c(T("h2f") - 0.30, "whoosh_c", 0.40)
    c(T("h2f"), "snap_a", 0.85); c(T("h2f"), "metal_a", 0.55); c(T("h2f"), "sub_a", 0.65); c(T("h2f"), "boom_m", 0.6)
    c(T("h3"), "slice_a", 0.6)
    c(T("h4") + 0.20, "glitch_b", 0.6)
    # TITLE: silence (dropout) -> deep hit + metal + tail
    big_hit(T("i1"), "boom_xl", 1.0, "metal_b", "reverse_s", gap=0.14)
    c(T("i1") + 0.08, "snap_c", 0.6)
    c(T("i2") - 0.08, "whoosh_a", 0.5)
    c(T("i3") - 0.06, "glitch_c", 0.55)
    c(T("i4"), "snap_b", 0.6); c(T("i4"), "sub_b", 0.5); c(T("i4") + 0.03, "stinger", 0.45)
    c(T("i5") - 0.08, "whoosh_b", 0.45)
    c(T("i6"), "slice_b", 0.65)
    c(T("m1") - 0.35, "reverse_s", 0.55)
    # MONTAGE: rhythmic motif - low pulse on every cut, alternating air slices / snaps
    big_hit(T("m1"), "boom_l", 0.85, None, "reverse_s")
    motif = [("m2", "whoosh_a", "snap_b"), ("m3", "glitch_b", "snap_c"), ("m4", "whoosh_b", "snap_a"),
             ("m5", "slice_a", "metal_b"), ("m6", "slice_b", "snap_b"), ("m7", "whoosh_c", "snap_c"),
             ("m8", "reverse_s", "snap_a"), ("m9", "whoosh_a", "snap_b")]
    for sid, w, s in motif:
        c(T(sid) - (0.30 if w.startswith("reverse") else 0.10), w, 0.45)
        c(T(sid), s, 0.55)
        c(T(sid), "pulse", 0.55)
    # escalating ticks + pulses into the launch (rapid transient pattern, accelerating)
    t, step = T("m9"), 0.18
    while t < T("c1") - 0.04:
        c(t, "tick", 0.40); c(t, "pulse", 0.35)
        t += step
        step = max(0.06, step * 0.78)
    c(T("m9"), "charge", 0.55)
    # LAUNCH
    big_hit(T("c1"), "boom_l", 1.0, "metal_a", "reverse_s")
    c(T("c1") + 0.05, "whoosh_long", 0.5)
    c(S2T(25.74, "c2"), "metal_b", 0.45); c(S2T(25.74, "c2"), "snap_c", 0.45)
    c(T("c2", 0.40), "riser_2", 0.55)
    c(T("c2", 0.50), "slice_a", 0.5)
    for k in range(3):                                                   # heartbeat before the finisher
        c(T("c3") + k * 0.16, "pulse", 0.40 + 0.08 * k)
    # FINISHER: dropout silence, then the heaviest layered impact
    big_hit(T("c4f"), "boom_xl", 1.1, "metal_a", "reverse_a", gap=0.22)
    c(T("c4f"), "glitch_c", 0.45)
    c(T("c4"), "whoosh_long", 0.45)
    c(T("c5") - 0.06, "glitch_a", 0.45); c(T("c5"), "stinger", 0.40)
    c(T("c6"), "snap_a", 0.7); c(T("c6"), "boom_m", 0.75); c(T("c6"), "sub_b", 0.55)
    c(T("c6"), "metal_b", 0.35)
    c(vo_word(7, 2), "snap_c", 0.45)
    # END: reduce -> deep reveal hit, digital accents on code, final impact
    big_hit(END0, "boom_xl", 1.0, "metal_b", "reverse_a", gap=0.20)
    c(END0 + 0.10, "snap_b", 0.6)
    c(END0 + 0.40, "tick_b", 0.5)
    c(END0 + 0.50, "whoosh_c", 0.35)
    for gi in range(3):
        c(END0 + 0.66 + gi * 0.12, "tick", 0.6); c(END0 + 0.66 + gi * 0.12, "glitch_b", 0.25)
    c(END0 + 1.05, "snap_a", 0.55); c(END0 + 1.05, "sub_b", 0.45)
    for g in range(3):
        c(vo_word(8, g), "tick_b", 0.30)
    big_hit(DUR - 0.45, "boom_xl", 0.95, "metal_a", "reverse_s")
    # REAL gameplay accents: gunfire & build placements detected in the source audio
    for seg_id, a, b, name in [("h1", 15.20, 16.05, "gun"), ("m2", 17.00, 17.60, "gun"),
                               ("m8", 19.00, 19.40, "gun"), ("m5", 22.55, 24.05, "build")]:
        for ts in onsets(src_audio, a, b)[:10]:
            c(S2T(ts, seg_id), name if name == "gun" else ("build" if len(C) % 2 else "build_b"),
              0.35 if name == "gun" else 0.55)
    return sorted(C)


# dropouts (silence before major moments): (start, end) -> SFX + game muted, tails cut
DROPOUTS = [(T("i1") - 0.14, T("i1") - 0.005), (T("c4f") - 0.22, T("c4f") - 0.005), (END0 - 0.20, END0 - 0.005)]


# ==========================================================================
# AUDIO MIX (no music)
# ==========================================================================
def place(bus, t, x, g):
    i = int(round(t * SR))
    if i < 0:
        x, i = x[-i:], 0
    n = min(len(x), len(bus) - i)
    if n > 0:
        bus[i:i + n] += x[:n] * g


def gain_curve(n, windows, floor=0.0, ramp=0.012):
    gcur = np.ones(n)
    for a, b in windows:
        i, j = int(a * SR), int(b * SR)
        gcur[i:j] = floor
    k = int(ramp * SR)
    return np.convolve(gcur, np.ones(k) / k, mode="same")


def build_game(src):
    mask = np.ones(len(src))
    for a, b in OLD_VO:
        mask[int(a * SR):min(int(b * SR), len(src))] = 0
    mask = np.convolve(mask, np.ones(int(0.03 * SR)) / int(0.03 * SR), mode="same")
    src = src * mask[:, None]
    out = np.zeros((int(DUR * SR), 2))
    for seg in EDL:
        if not seg.get("game"):
            continue
        t0, t1 = seg["t"]
        n = int(round((t1 - t0) * SR))
        rel = np.arange(0, n, 64) / SR
        info = [src_time_map(seg, r) for r in rel]
        st = np.interp(np.arange(n), np.arange(0, n, 64)[:len(info)], [i[0] for i in info])
        frz = np.repeat([i[1] == "freeze" for i in info], 64)[:n]
        idx = np.clip(st * SR, 0, len(src) - 2)
        i0 = np.floor(idx).astype(int)
        fr = (idx - i0)[:, None]
        ch = src[i0] * (1 - fr) + src[i0 + 1] * fr
        ch[frz] = 0
        place(out, t0, fade(ch, 0.006, 0.012), 1.0)
    return out


def compress(x, thr_db=-16, ratio=2.5, att=0.004, rel=0.12):
    lvl = np.abs(x).max(1) if x.ndim == 2 else np.abs(x)
    hop = 96
    n = len(lvl) // hop
    pk = lvl[:n * hop].reshape(n, hop).max(1)
    env = np.zeros(n)
    a1, r1 = math.exp(-hop / (att * SR)), math.exp(-hop / (rel * SR))
    v = 0.0
    for i, s in enumerate(pk):
        v = a1 * v + (1 - a1) * s if s > v else r1 * v + (1 - r1) * s
        env[i] = v
    db = 20 * np.log10(env + 1e-9)
    gr = np.where(db > thr_db, (thr_db - db) * (1 - 1 / ratio), 0)
    g = np.interp(np.arange(len(lvl)), np.arange(n) * hop, 10 ** (gr / 20))
    return x * (g[:, None] if x.ndim == 2 else g)


def build_audio(source, work, sfx):
    N = int(DUR * SR)
    src = decode_audio(source)
    # narration: the SAME v1 voice lines, unchanged, placed on the new cut
    vo = np.zeros(N)
    for line, t in VO_PLACE:
        x = decode_audio(ASSETS / "vo" / f"line_{line:02d}.wav", mono=True)
        place(vo, t, x, 1.0)
    write_wav(HERE / "voiceover_action_v2.wav", vo)
    duck = np.clip(v1.envelope(vo, 0.012, 0.25) / 0.05, 0, 1)
    # SFX bus
    bus = np.zeros((N + 4 * SR, 2))
    cues = sfx_cues(src)
    for t, name, g, dk in cues:
        if g > 0:
            place(bus, t, sfx[name], g)
    bus = bus[:N]
    drop = gain_curve(N, DROPOUTS, 0.0)
    sfx_bus = bus * (1 - 0.60 * duck)[:, None] * drop[:, None]          # ~ -8 dB under words, back between phrases
    sfx_bus = compress(hp(sfx_bus, 30), -14, 2.2)
    write_wav(HERE / "sfx_only_action_v2.wav", norm(sfx_bus, 0.89))
    game = build_game(src) * (1 - 0.55 * duck)[:, None] * drop[:, None]
    mix = 1.50 * sfx_bus + 1.00 * game + 1.20 * stereo(vo)
    mix = np.tanh(mix * 0.85) / 0.85
    mix[:int(0.005 * SR)] *= np.linspace(0, 1, int(0.005 * SR))[:, None]
    fo = int(0.30 * SR)
    mix[-fo:] *= np.linspace(1, 0, fo)[:, None] ** 1.5
    tmp = work / "v2_premaster.wav"
    write_wav(tmp, norm(mix, 0.89))
    meas = run(["ffmpeg", "-hide_banner", "-i", str(tmp), "-af", "loudnorm=I=-14:TP=-2:LRA=11:print_format=json",
                "-f", "null", "-"], capture_output=True, text=True).stderr
    m = json.loads(meas[meas.rfind("{"):meas.rfind("}") + 1])
    af = ("loudnorm=I=-14:TP=-2:LRA=11:linear=true:"
          f"measured_I={m['input_i']}:measured_TP={m['input_tp']}:measured_LRA={m['input_lra']}:"
          f"measured_thresh={m['input_thresh']}:offset={m['target_offset']},"
          "alimiter=limit=0.78:attack=3:release=60:level=disabled:latency=1")
    run(["ffmpeg", "-v", "error", "-y", "-i", str(tmp), "-af", af, "-ar", str(SR), "-c:a", "pcm_s24le",
         str(HERE / "final_audio_action_v2.wav")])
    (work / "v2_cues.json").write_text(json.dumps(cues, indent=0))
    return cues


# ==========================================================================
def render(source, layers, groups, out, stills=None):
    r = Renderer(source, layers, groups)
    if stills:
        out.mkdir(parents=True, exist_ok=True)
        for ts in stills:
            cv2.imwrite(str(out / f"v2_{ts:06.2f}.jpg"), r.frame(int(round(ts * FPS))), [cv2.IMWRITE_JPEG_QUALITY, 92])
        return
    enc = subprocess.Popen(
        ["ffmpeg", "-v", "error", "-y", "-f", "rawvideo", "-pix_fmt", "bgr24", "-s", f"{W}x{H}", "-r", str(FPS),
         "-i", "-", "-vf", "scale=out_color_matrix=bt709:out_range=tv,format=yuv420p",
         "-c:v", "libx264", "-preset", "slow", "-crf", "17", "-profile:v", "high", "-level", "4.2",
         "-g", "120", "-bf", "3", "-colorspace", "bt709", "-color_primaries", "bt709", "-color_trc", "bt709",
         "-color_range", "tv", str(out)], stdin=subprocess.PIPE)
    for k in range(NFRAMES):
        enc.stdin.write(r.frame(k).tobytes())
        if k % 150 == 0:
            print(f"  frame {k}/{NFRAMES}", flush=True)
    enc.stdin.close()
    if enc.wait() != 0:
        raise RuntimeError("encoder failed")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--source", required=True)
    ap.add_argument("--work", default=str(HERE / "_work"))
    ap.add_argument("--stills")
    ap.add_argument("--audio-only", action="store_true")
    ap.add_argument("--print-edl", action="store_true")
    a = ap.parse_args()
    if a.print_edl:
        for s in EDL:
            print(f"{s['id']:4s} {s['t'][0]:6.2f}-{s['t'][1]:6.2f}  {s['pieces']}")
        print("DUR", DUR, "VO", VO_PLACE)
        return
    src = Path(a.source).resolve()
    work = Path(a.work)
    work.mkdir(parents=True, exist_ok=True)
    layers, groups = build_graphics(ASSETS / "graphics_v2")
    if a.stills:
        render(src, layers, groups, work / "stills_v2", [float(x) for x in a.stills.split(",")])
        return
    print("[1/4] sfx library")
    sfx = build_sfx(ASSETS / "sfx_v2")
    print("[2/4] audio (sfx + gameplay + narration, no music)")
    build_audio(src, work, sfx)
    if not a.audio_only:
        print("[3/4] picture")
        render(src, layers, groups, work / "video_only_v2.mp4")
    print("[4/4] mux")
    run(["ffmpeg", "-v", "error", "-y", "-i", str(work / "video_only_v2.mp4"), "-i", str(HERE / "final_audio_action_v2.wav"),
         "-map", "0:v:0", "-map", "1:a:0", "-c:v", "copy", "-c:a", "aac", "-b:a", "320k", "-ar", str(SR), "-ac", "2",
         "-shortest", "-movflags", "+faststart", "-metadata", f"title={MAP_NAME} - Action v2 - {MAP_CODE}",
         str(HERE / "realistic_scrims_trailer_action_v2.mp4")])
    print("done")


if __name__ == "__main__":
    main()
