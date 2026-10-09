#!/usr/bin/env python3
"""
REALISTIC SCRIMS - Fortnite Creative promo trailer pipeline.

Builds every asset (title graphics, original music, original sound effects,
processed voice-over), mixes the audio, composites the 1080x1920 / 60 fps
picture frame-by-frame from the real gameplay source, encodes H.264/AAC and
runs automated QA.

    python3 edit_trailer.py --source "fortnite new video scrims.mp4" \
        --voice en_US-joe-medium.onnx --work /tmp/rs_work

See README.txt for dependencies and details.
"""
import argparse
import json
import math
import shutil
import subprocess
import sys
import wave
from pathlib import Path

import cv2
import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont
from scipy import signal

# --------------------------------------------------------------------------
# Global constants
# --------------------------------------------------------------------------
W, H, FPS, SR = 1080, 1920, 60, 48000
BPM = 120
BEAT = 60.0 / BPM                       # 0.5 s - every major cut sits on this grid
DUR = 32.0                              # final trailer length (s)
NFRAMES = int(round(DUR * FPS))
MAP_NAME = "REALISTIC SCRIMS"
MAP_CODE = "0674-0917-0977"

HERE = Path(__file__).resolve().parent
ASSETS = HERE / "assets"
FONT_ANTON = ASSETS / "fonts" / "Anton-Regular.ttf"
FONT_INTER_XB = ASSETS / "fonts" / "Inter-ExtraBold.otf"

CYAN = (0, 229, 255)            # RGB accent (electric / icy cyan)
CHARCOAL = (11, 14, 19)
WHITE = (255, 255, 255)

rng = np.random.default_rng(7)

# Spans of the SOURCE audio that contain the original creator's narration.
# Game audio is muted inside these windows so the old voice never leaks.
OLD_VO = [(0.0, 4.5), (6.7, 13.5), (17.7, 19.0), (30.9, 35.4)]

# --------------------------------------------------------------------------
# EDIT DECISION LIST
#   t      : (out_start, out_end) in trailer seconds
#   pieces : (out_duration, src_start, src_end[, mode]) or ("F", dur, src_t)
#            mode: "nearest" (default) | "blend" | "accum" (motion blur)
#   zoom   : [(t_rel, zoom)] eased keyframes   center: (x, y) in source px
#   game   : use original gameplay audio for this segment
# --------------------------------------------------------------------------
SEGMENTS = [
    # ---- 1. HOOK ---------------------------------------------------------
    dict(id="h1", t=(0.00, 0.95), pieces=[(0.95, 15.15, 16.10)],
         zoom=[(0, 1.24), (0.30, 1.07), (0.95, 1.10)], game=True),
    dict(id="h2", t=(0.95, 2.70), pieces=[(1.75, 16.10, 16.975)],       # 0.5x on the elimination
         zoom=[(0, 1.16), (0.20, 1.10), (1.75, 1.17)], game=True),
    dict(id="h3", t=(2.70, 3.00), pieces=[("F", 0.30, 16.975)],           # freeze
         zoom=[(0, 1.17), (0.30, 1.27)], desat=0.35, game=False),
    # ---- 2. MAP IDENTITY -------------------------------------------------
    dict(id="i1", t=(3.00, 4.50), pieces=[(1.50, 7.30, 8.80)],
         zoom=[(0, 1.16), (0.25, 1.05), (1.50, 1.08)], game=False),
    dict(id="i2", t=(4.50, 5.75), pieces=[(1.25, 9.85, 11.10)],
         zoom=[(0, 1.06), (1.25, 1.13)], game=False),
    dict(id="i3", t=(5.75, 7.25), pieces=[(1.50, 0.45, 1.95)],
         zoom=[(0, 1.30), (1.50, 1.36)], center=(540, 958), game=False),
    dict(id="i4", t=(7.25, 9.00), pieces=[(1.75, 11.95, 13.70)],
         zoom=[(0, 1.06), (1.45, 1.11), (1.75, 1.20)], game=False),
    # ---- 3. ACTION MONTAGE -----------------------------------------------
    dict(id="m1", t=(9.00, 10.25), pieces=[(1.25, 21.43, 22.68)],
         zoom=[(0, 1.20), (0.20, 1.06), (1.25, 1.09)], game=True),
    dict(id="m2", t=(10.25, 11.75), pieces=[(1.50, 17.00, 18.50)],
         zoom=[(0, 1.14), (0.15, 1.06), (1.50, 1.10)], game=True),
    dict(id="m3", t=(11.75, 12.50), pieces=[(0.75, 19.32, 20.07)],
         zoom=[(0, 1.07), (0.75, 1.14)], game=True),
    dict(id="m4", t=(12.50, 14.00), pieces=[(1.50, 22.55, 24.05)],
         zoom=[(0, 1.15), (0.15, 1.06), (1.50, 1.09)], game=True),
    dict(id="m5", t=(14.00, 15.25), pieces=[(0.40, 18.60, 19.00), (0.85, 19.00, 19.45)],
         zoom=[(0, 1.06), (0.40, 1.08), (0.47, 1.17), (1.25, 1.21)],
         center=(540, 1000), game=True),
    dict(id="m6", t=(15.25, 16.50), pieces=[(1.25, 31.33, 32.58)],
         zoom=[(0, 1.06), (1.25, 1.12)], game=False),
    dict(id="m7", t=(16.50, 17.00), pieces=[(0.50, 24.25, 24.75)],
         zoom=[(0, 1.08), (0.50, 1.16)], game=True),
    # ---- 4. CLIMAX -------------------------------------------------------
    dict(id="c1", t=(17.00, 17.80), pieces=[(0.80, 24.75, 25.55)],
         zoom=[(0, 1.22), (0.25, 1.06), (0.80, 1.08)], game=True),
    dict(id="c2", t=(17.80, 19.85), pieces=[(0.40, 25.55, 25.95),
                                            (1.00, 25.95, 27.55, "accum"),
                                            (0.65, 27.55, 28.20)],
         zoom=[(0, 1.08), (2.05, 1.18)], game=True),
    dict(id="c3", t=(19.85, 20.50), pieces=[(0.65, 28.20, 28.70, "blend")],
         zoom=[(0, 1.18), (0.65, 1.30)], game=True),
    dict(id="c4", t=(20.50, 22.45), pieces=[("F", 0.30, 28.77), (1.65, 28.77, 30.42)],
         zoom=[(0, 1.26), (0.30, 1.18), (0.36, 1.08), (1.95, 1.16)], game=True),
    dict(id="c5", t=(22.45, 23.50), pieces=[(1.05, 30.42, 31.30, "blend")],
         zoom=[(0, 1.06), (1.05, 1.12)], game=True),
    dict(id="c6", t=(23.50, 25.50), pieces=[(2.00, 32.65, 34.43, "blend")],
         zoom=[(0, 1.15), (0.20, 1.05), (2.00, 1.09)], game=False),
    # ---- 5. END CARD (background = real gameplay, slowed + blurred) ------
    dict(id="end", t=(25.50, 32.00), pieces=[(6.50, 25.95, 27.55, "blend")],
         zoom=[(0, 1.10), (6.50, 1.18)], game=False, endcard=True),
]

# Impact accents (absolute trailer time): (t, strength, decay_s)
FLASHES = [(0.00, .45, .07), (0.95, .35, .08), (3.00, .80, .12), (9.00, .85, .12),
           (10.25, .25, .06), (12.50, .30, .06), (14.40, .25, .05), (17.00, .70, .12),
           (20.50, .80, .12), (22.45, .50, .10), (23.50, .60, .12), (25.50, .80, .15),
           (31.00, .45, .15)]
SHAKES = [(0.00, 18, .25), (0.45, 8, .15), (3.00, 16, .25), (9.00, 18, .30),
          (10.25, 8, .12), (12.50, 10, .15), (14.40, 6, .12), (17.00, 16, .30),
          (17.99, 6, .12), (20.50, 22, .45), (20.80, 8, .20), (23.50, 12, .25),
          (25.50, 14, .25), (31.00, 8, .20)]
CHROMA = [(0.00, 8, .15), (0.95, 5, .20), (3.00, 7, .15), (9.00, 8, .20),
          (17.00, 6, .20), (20.50, 10, .30), (23.50, 5, .15)]
# Whip transitions: (cut_time, direction, axis)
WHIPS = [(3.00, 1, "h"), (4.50, -1, "h"), (5.75, 1, "h"), (7.25, 1, "v"),
         (11.75, 1, "h"), (15.25, -1, "h"), (16.50, 1, "v"), (22.45, 1, "v")]
# Brightness dips before drops: (t0, t1, amount)
DIMS = [(8.55, 9.00, .40), (20.15, 20.50, .30)]

# Voice-over cues: (start_time, text, length_scale)
VO_LINES = [
    (0.40, "This is where your mechanics get tested.", 0.97),
    (3.25, "Jump into Realistic Scrims.", 0.97),
    (5.85, "Every round brings a new challenge.", 0.97),
    (9.20, "No easy fights. No second chances.", 0.97),
    (12.55, "Build fast. Edit faster. Hit your shots.", 0.95),
    (17.95, "Think you can dominate?", 1.00),
    (22.95, "Load up. Lock in. Prove yourself.", 0.97),
    (26.40, "Zero six seven four. Zero nine one seven. Zero nine seven seven.", 1.06),
]


# ==========================================================================
# Small utilities
# ==========================================================================
def run(cmd, **kw):
    return subprocess.run(cmd, check=True, **kw)


def ease_out(x):
    x = min(max(x, 0.0), 1.0)
    return 1 - (1 - x) ** 3


def ease_out_back(x, s=1.7):
    x = min(max(x, 0.0), 1.0) - 1
    return 1 + (s + 1) * x ** 3 + s * x ** 2


def ease_in_out(x):
    x = min(max(x, 0.0), 1.0)
    return x * x * (3 - 2 * x)


def keyframes(kf, t):
    """Eased interpolation through [(t, value)] keyframes."""
    if t <= kf[0][0]:
        return kf[0][1]
    for (t0, v0), (t1, v1) in zip(kf, kf[1:]):
        if t <= t1:
            return v0 + (v1 - v0) * ease_out((t - t0) / max(t1 - t0, 1e-6))
    return kf[-1][1]


def write_wav(path, x, sr=SR, bits=24):
    x = np.asarray(x, dtype=np.float64)
    if x.ndim == 1:
        x = x[:, None]
    x = np.clip(x, -1, 1)
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(path), "wb") as w:
        w.setnchannels(x.shape[1])
        w.setsampwidth(bits // 8)
        w.setframerate(sr)
        if bits == 16:
            w.writeframes((x * 32767).astype("<i2").tobytes())
        else:
            i = (x * 8388607).astype("<i4")
            b = i.view(np.uint8).reshape(-1, 4)[:, :3]
            w.writeframes(b.tobytes())


def decode_audio(path, extra_filter=None, mono=False):
    af = ["-af", extra_filter] if extra_filter else []
    out = run(["ffmpeg", "-v", "error", "-i", str(path), *af, "-ac", "1" if mono else "2",
               "-ar", str(SR), "-f", "f32le", "-"], capture_output=True).stdout
    a = np.frombuffer(out, np.float32).astype(np.float64)
    return a if mono else a.reshape(-1, 2)


def speech_regions(x, thresh_db=-38, min_gap=0.11):
    """Return [(start, end)] seconds of speech in a mono signal."""
    hop = int(0.01 * SR)
    n = len(x) // hop
    rms = np.sqrt(np.mean(x[:n * hop].reshape(n, hop) ** 2, axis=1) + 1e-12)
    on = 20 * np.log10(rms) > thresh_db
    regs, start = [], None
    for i, v in enumerate(on):
        if v and start is None:
            start = i
        elif not v and start is not None:
            regs.append([start * 0.01, i * 0.01]); start = None
    if start is not None:
        regs.append([start * 0.01, n * 0.01])
    merged = []
    for r in regs:
        if merged and r[0] - merged[-1][1] < min_gap:
            merged[-1][1] = r[1]
        else:
            merged.append(r)
    return merged


# ==========================================================================
# 1. GRAPHICS  (PIL -> RGBA layers, saved as PNG assets)
# ==========================================================================
def _font(path, size):
    return ImageFont.truetype(str(path), size)


def _text(text, font, fill, tracking=0):
    asc, desc = font.getmetrics()
    if tracking == 0:
        bb = font.getbbox(text)
        img = Image.new("RGBA", (bb[2] + 4, asc + desc + 4), (0, 0, 0, 0))
        ImageDraw.Draw(img).text((2, 2), text, font=font, fill=fill)
    else:
        widths = [font.getlength(c) for c in text]
        total = int(sum(widths) + tracking * (len(text) - 1)) + 4
        img = Image.new("RGBA", (total, asc + desc + 4), (0, 0, 0, 0))
        d, x = ImageDraw.Draw(img), 2
        for c, w in zip(text, widths):
            d.text((x, 2), c, font=font, fill=fill)
            x += w + tracking
    return img.crop(img.getbbox())


def _glow(img, color, radius, strength=1.0, shadow=True):
    """Text + soft colored glow + subtle dark shadow, on a padded canvas."""
    pad = radius * 3
    base = Image.new("RGBA", (img.width + 2 * pad, img.height + 2 * pad), (0, 0, 0, 0))
    a = img.split()[3]
    out = Image.new("RGBA", base.size, (0, 0, 0, 0))
    if shadow:
        sh = Image.new("RGBA", base.size, (0, 0, 0, 0))
        sh.paste((0, 0, 0, 255), (pad, pad + radius // 4), a)
        sh = sh.filter(ImageFilter.GaussianBlur(radius * 0.6))
        sh.putalpha(sh.split()[3].point(lambda v: int(v * 0.75)))
        out = Image.alpha_composite(out, sh)
    if color is not None:
        gl = Image.new("RGBA", base.size, (0, 0, 0, 0))
        gl.paste(color + (255,), (pad, pad), a)
        gl = gl.filter(ImageFilter.GaussianBlur(radius))
        gl.putalpha(gl.split()[3].point(lambda v: min(255, int(v * strength))))
        out = Image.alpha_composite(out, gl)
    top = Image.new("RGBA", base.size, (0, 0, 0, 0))
    top.paste(img, (pad, pad), img)
    return Image.alpha_composite(out, top)


def _stack(items, gap=0, align="center"):
    w = max(i.width for i in items)
    h = sum(i.height for i in items) + gap * (len(items) - 1)
    out = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    y = 0
    for i in items:
        x = (w - i.width) // 2 if align == "center" else 0
        out.alpha_composite(i, (x, y))
        y += i.height + gap
    return out


def _scrim(img, pad_x=90, pad_y=70, alpha=150, blur=45):
    """Soft dark charcoal backing so text stays readable over busy footage."""
    w, h = img.width + 2 * pad_x, img.height + 2 * pad_y
    s = Image.new("RGBA", (w + 4 * blur, h + 4 * blur), (0, 0, 0, 0))
    ImageDraw.Draw(s).rounded_rectangle((2 * blur, 2 * blur, 2 * blur + w, 2 * blur + h),
                                        radius=h // 2, fill=CHARCOAL + (alpha,))
    s = s.filter(ImageFilter.GaussianBlur(blur))
    s.alpha_composite(img, ((s.width - img.width) // 2, (s.height - img.height) // 2))
    return s


def _underline(width, height=10):
    bar = Image.new("RGBA", (width, height), CYAN + (255,))
    return _glow(bar, CYAN, 14, 1.2, shadow=False)


def build_graphics(out_dir):
    out_dir.mkdir(parents=True, exist_ok=True)
    A = lambda s: _font(FONT_ANTON, s)
    I = lambda s: _font(FONT_INTER_XB, s)
    L = {}

    # Hook: THINK YOU'RE / READY?
    l1 = _glow(_text("THINK YOU'RE", A(118), WHITE, 4), None, 10)
    l2 = _glow(_text("READY?", A(236), WHITE, 6), CYAN, 22, 1.3)
    L["hook"] = _scrim(_stack([l1, l2], gap=-40), 70, 20, 120, 40)

    # Title: REALISTIC / SCRIMS
    t1 = _glow(_text("REALISTIC", A(168), WHITE, 14), None, 10)
    t2 = _glow(_text("SCRIMS", A(300), WHITE, 10), CYAN, 26, 1.4)
    bar = _underline(560, 9)
    L["title"] = _scrim(_stack([t1, t2, bar], gap=-46), 60, 10, 150, 45)

    # Sub caption: YOUR MECHANICS. YOUR TEST.
    s1 = _text("YOUR MECHANICS.", A(78), WHITE, 3)
    s2 = _text("YOUR TEST.", A(78), CYAN, 3)
    row = Image.new("RGBA", (s1.width + 28 + s2.width, max(s1.height, s2.height)), (0, 0, 0, 0))
    row.alpha_composite(s1, (0, row.height - s1.height))
    row.alpha_composite(s2, (s1.width + 28, row.height - s2.height))
    L["sub"] = _scrim(_glow(row, None, 8), 70, 34, 165, 30)

    for key, word, size in [("build", "BUILD.", 220), ("aim", "AIM.", 230),
                            ("prove", "PROVE YOURSELF.", 138)]:
        w = _glow(_text(word, A(size), WHITE, 6), CYAN, 22, 1.25)
        L[key] = _scrim(_stack([w, _underline(int(w.width * 0.45), 9)], gap=-50), 50, 0, 105, 40)

    # ---- End card -------------------------------------------------------
    e1 = _glow(_text("REALISTIC", A(176), WHITE, 16), None, 10)
    e2 = _glow(_text("SCRIMS", A(300), WHITE, 10), CYAN, 30, 1.5)
    L["ec_title"] = _stack([e1, e2], gap=-58)
    L["ec_label"] = _glow(_text("MAP CODE", I(46), CYAN, 14), CYAN, 10, 0.6)

    code_font = A(120)
    code = _text(MAP_CODE, code_font, WHITE, 4)
    pw, ph = 880, 196
    panel = Image.new("RGBA", (pw + 80, ph + 80), (0, 0, 0, 0))
    glow = Image.new("RGBA", panel.size, (0, 0, 0, 0))
    ImageDraw.Draw(glow).rounded_rectangle((40, 40, 40 + pw, 40 + ph), 34, outline=CYAN + (255,), width=10)
    glow = glow.filter(ImageFilter.GaussianBlur(14))
    panel.alpha_composite(glow)
    box = Image.new("RGBA", panel.size, (0, 0, 0, 0))
    ImageDraw.Draw(box).rounded_rectangle((40, 40, 40 + pw, 40 + ph), 34,
                                          fill=(10, 14, 20, 228), outline=CYAN + (255,), width=5)
    panel.alpha_composite(box)
    L["ec_panel"] = panel
    L["ec_code"] = _glow(code, None, 8)
    # cyan glow copy of the code used for the per-group VO highlight
    hi = Image.new("RGBA", L["ec_code"].size, (0, 0, 0, 0))
    hi.paste(CYAN + (255,), (0, 0), L["ec_code"].split()[3])
    L["ec_code_hi"] = hi.filter(ImageFilter.GaussianBlur(16))

    pill = Image.new("RGBA", (520, 150), (0, 0, 0, 0))
    ImageDraw.Draw(pill).rounded_rectangle((10, 10, 510, 140), 65, fill=CYAN + (255,))
    ptxt = _text("PLAY NOW", A(86), (6, 16, 21, 255), 8)
    pill.alpha_composite(ptxt, ((pill.width - ptxt.width) // 2, (pill.height - ptxt.height) // 2))
    L["ec_play"] = _glow(pill, CYAN, 22, 0.9, shadow=True)
    L["ec_line"] = _underline(330, 6)

    for k, im in L.items():
        im.save(out_dir / f"{k}.png")

    # Group x-ranges of the code (for the VO-synced highlight), relative to ec_code
    pad = (L["ec_code"].width - code.width) // 2
    groups, x = [], pad
    for gi, g in enumerate(MAP_CODE.split("-")):
        gw = code_font.getlength(g) + 4 * (len(g) - 1)
        groups.append((int(x - 12), int(x + gw + 12)))
        x += gw + 4 + code_font.getlength("-") + 4 + 4
    meta = {"code_groups": groups}
    (out_dir / "layout.json").write_text(json.dumps(meta, indent=1))
    return L, meta


def to_layer(img):
    """PIL RGBA -> (premultiplied BGR float32, alpha float32)."""
    a = np.asarray(img, dtype=np.float32) / 255.0
    alpha = a[..., 3]
    bgr = a[..., [2, 1, 0]] * alpha[..., None] * 255.0
    return bgr, alpha


def overlay(frame, layer, cx, cy, scale=1.0, opacity=1.0, reveal=1.0, xcrop=None):
    if opacity <= 0.003 or reveal <= 0.003:
        return
    rgb, a = layer
    if xcrop is not None:
        rgb, a = rgb[:, xcrop[0]:xcrop[1]], a[:, xcrop[0]:xcrop[1]]
    if abs(scale - 1) > 1e-3:
        rgb = cv2.resize(rgb, None, fx=scale, fy=scale, interpolation=cv2.INTER_LINEAR)
        a = cv2.resize(a, None, fx=scale, fy=scale, interpolation=cv2.INTER_LINEAR)
    h, w = a.shape
    if reveal < 1:                     # horizontal wipe from the centre
        keep = int(w * reveal / 2)
        m = np.zeros(w, np.float32)
        m[max(0, w // 2 - keep):w // 2 + keep] = 1
        a = a * m
        rgb = rgb * m[None, :, None]
    x0, y0 = int(round(cx - w / 2)), int(round(cy - h / 2))
    fx0, fy0, fx1, fy1 = max(x0, 0), max(y0, 0), min(x0 + w, W), min(y0 + h, H)
    if fx1 <= fx0 or fy1 <= fy0:
        return
    lx0, ly0 = fx0 - x0, fy0 - y0
    sa = a[ly0:ly0 + fy1 - fy0, lx0:lx0 + fx1 - fx0] * opacity
    sr = rgb[ly0:ly0 + fy1 - fy0, lx0:lx0 + fx1 - fx0] * opacity
    reg = frame[fy0:fy1, fx0:fx1].astype(np.float32)
    frame[fy0:fy1, fx0:fx1] = np.clip(reg * (1 - sa[..., None]) + sr, 0, 255).astype(np.uint8)


# ==========================================================================
# 2. SOUND: original synthesized music + SFX (no third-party samples)
# ==========================================================================
def _t(d):
    return np.arange(int(d * SR)) / SR


def _sos(kind, f, order=2):
    return signal.butter(order, f, kind, fs=SR, output="sos")


def lp(x, f, o=2):
    return signal.sosfilt(_sos("low", f, o), x, axis=0)


def hp(x, f, o=2):
    return signal.sosfilt(_sos("high", f, o), x, axis=0)


def bp(x, lo, hi, o=2):
    return signal.sosfilt(_sos("band", [lo, hi], o), x, axis=0)


def sweep_bp(x, fc_of_frac, q=1.5, block=256):
    out, zi = np.zeros_like(x), None
    nb = max(1, math.ceil(len(x) / block))
    for b in range(nb):
        fc = fc_of_frac(b / max(1, nb - 1))
        lo, hi = fc / (1 + 0.5 / q), min(fc * (1 + 0.5 / q), SR / 2 - 200)
        sos = _sos("band", [lo, hi])
        if zi is None:
            zi = np.zeros((sos.shape[0], 2))
        seg = x[b * block:(b + 1) * block]
        out[b * block:b * block + len(seg)], zi = signal.sosfilt(sos, seg, zi=zi)
    return out


def noise(d):
    return rng.standard_normal(int(d * SR))


def fade(x, fi=0.003, fo=0.01):
    x = x.copy()
    a, b = int(fi * SR), int(fo * SR)
    if a:
        x[:a] *= np.linspace(0, 1, a)[:, None] if x.ndim == 2 else np.linspace(0, 1, a)
    if b:
        x[-b:] *= np.linspace(1, 0, b)[:, None] if x.ndim == 2 else np.linspace(1, 0, b)
    return x


def norm(x, peak=0.9):
    m = np.abs(x).max()
    return x * (peak / m) if m > 0 else x


def stereo(x, pan=0.0, width=0.0):
    g_l, g_r = math.cos((pan + 1) * math.pi / 4), math.sin((pan + 1) * math.pi / 4)
    if x.ndim == 2:
        return x * np.array([g_l, g_r]) * math.sqrt(2)
    out = np.stack([x * g_l, x * g_r], 1) * math.sqrt(2)
    if width:
        d = int(0.011 * SR)
        out[d:, 1] = out[d:, 1] * (1 - width) + out[:-d, 1] * width
    return out


_IR = None


def reverb(x, wet=0.25, decay=0.5, dur=1.8):
    global _IR
    if _IR is None:
        t = _t(dur)
        _IR = np.stack([lp(noise(dur) * np.exp(-t / decay), 5500),
                        lp(noise(dur) * np.exp(-t / decay), 5500)], 1)
        _IR /= np.sqrt((_IR ** 2).sum(0))
    xs = x if x.ndim == 2 else stereo(x)
    pad = np.concatenate([xs, np.zeros((len(_IR), 2))])
    tail = np.stack([signal.fftconvolve(pad[:, c], _IR[:, c])[:len(pad)] for c in range(2)], 1)
    return pad * (1 - wet) + tail * wet * 2.2


# ---- drum & synth voices ---------------------------------------------------
def v_kick(hard=1.0):
    t = _t(0.5)
    f = 44 + 120 * np.exp(-t / 0.032)
    x = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t / (0.17 * hard))
    x[:200] += hp(noise(200 / SR), 2500) * np.linspace(.6, 0, 200)
    return np.tanh(2.4 * x) * 0.95


def v_808(freq, dur):
    t = _t(dur + 0.08)
    f = freq * (1 + 0.8 * np.exp(-t / 0.022))
    env = np.minimum(1, t / 0.004) * np.exp(-t / max(dur * 0.9, 0.2))
    env[-int(0.08 * SR):] *= np.linspace(1, 0, int(0.08 * SR))
    x = np.sin(2 * np.pi * np.cumsum(f) / SR) * env
    x = np.tanh(3.2 * x) * 0.8 + 0.25 * np.tanh(9 * x) * env
    return lp(x, 1100)


def v_clap():
    out = np.zeros(int(0.35 * SR))
    for k, d in enumerate([0, 0.011, 0.022]):
        n = noise(0.3) * np.exp(-_t(0.3) / (0.012 if k < 2 else 0.11))
        i = int(d * SR)
        out[i:i + len(n)] += n[:len(out) - i]
    return np.tanh(1.5 * bp(out, 850, 3200)) * 0.9


def v_snare():
    t = _t(0.22)
    x = bp(noise(0.22), 1200, 7000) * np.exp(-t / 0.07) + 0.6 * np.sin(2 * np.pi * 190 * t) * np.exp(-t / 0.05)
    return np.tanh(1.4 * x) * 0.8


def v_hat(open_=False):
    d = 0.3 if open_ else 0.06
    t = _t(d)
    return hp(noise(d), 7500, 4) * np.exp(-t / (0.11 if open_ else 0.018))


def v_cowbell(f, d=0.32):
    t = _t(d)
    sq = np.sign(np.sin(2 * np.pi * f * t)) + 0.8 * np.sign(np.sin(2 * np.pi * f * 1.4983 * t))
    env = np.exp(-t / 0.075) * 0.8 + 0.2 * np.exp(-t / 0.3)
    return bp(sq * env, 450, 4200) * 0.5


def v_pad(freqs, d):
    t = _t(d)
    x = np.zeros((len(t), 2))
    for f in freqs:
        for k, det in enumerate([-0.12, 0.0, 0.11]):
            ph = rng.random()
            saw = 2 * ((f * (1 + det / 100) * t + ph) % 1) - 1
            x[:, k % 2] += saw
            x[:, (k + 1) % 2] += saw * 0.5
    env = np.minimum(1, t / 0.6) * np.minimum(1, (d - t) / 0.8)
    return lp(x, 1500) * env[:, None] * 0.08


# ---- SFX -------------------------------------------------------------------
def sfx_impact(size=1.0):
    d = 2.6
    t = _t(d)
    f = 30 + 70 * np.exp(-t / 0.09)
    boom = np.tanh(2.0 * np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t / (0.75 * size)))
    crack = lp(noise(d), 3800) * np.exp(-t / 0.045)
    body = bp(noise(d), 120, 900) * np.exp(-t / 0.22)
    x = boom * 1.0 + crack * 0.55 + body * 0.6
    return norm(reverb(fade(x), 0.22, 0.6)[:int(3.2 * SR)], 0.95)


def sfx_subdrop(d=1.6):
    t = _t(d)
    f = 28 + 62 * np.exp(-t / 0.35)
    return fade(np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t / 0.7), 0.002, 0.2) * 0.9


def sfx_riser(d):
    t = _t(d)
    nz = sweep_bp(noise(d), lambda u: 300 * (9000 / 300) ** u, q=2.2)
    f = 140 * (8.0 ** (t / d))
    tone = np.sin(2 * np.pi * np.cumsum(f) / SR) + 0.5 * np.sin(2 * np.pi * np.cumsum(f * 1.007) / SR)
    env = (t / d) ** 2.2
    x = (norm(nz, 1) * 0.75 + tone * 0.25) * env
    return stereo(fade(x, 0.01, 0.004), width=0.6) * 0.8


def sfx_whoosh(d=0.45, pan_from=-0.8, pan_to=0.8):
    t = _t(d)
    x = sweep_bp(noise(d), lambda u: 350 + 3800 * math.sin(math.pi * u) ** 1.5, q=1.2)
    env = np.sin(np.pi * t / d) ** 2
    x = norm(x * env, 1)
    pan = np.linspace(pan_from, pan_to, len(t))
    return np.stack([x * np.cos((pan + 1) * np.pi / 4), x * np.sin((pan + 1) * np.pi / 4)], 1) * 0.9


def sfx_revcym(d=1.0):
    t = _t(d)
    x = hp(noise(d), 4500) * np.exp(-t / 0.45) + 0.4 * bp(noise(d), 7000, 12000) * np.exp(-t / 0.3)
    return stereo(fade(norm(x[::-1], 1), 0.01, 0.003), width=0.7) * 0.6


def sfx_texthit():
    t = _t(0.35)
    x = (np.sin(2 * np.pi * 95 * t) * np.exp(-t / 0.07) +
         0.35 * np.sin(2 * np.pi * 1850 * t) * np.exp(-t / 0.025) +
         0.3 * hp(noise(0.35), 3000) * np.exp(-t / 0.006))
    return norm(reverb(fade(x), 0.15, 0.3)[:int(0.8 * SR)], 0.8)


def sfx_tick():
    t = _t(0.12)
    return stereo(np.sin(2 * np.pi * 2400 * t) * np.exp(-t / 0.02) * 0.5)


def sfx_downer(d=0.6):
    t = _t(d)
    f = 700 * (0.12 ** (t / d))
    tone = np.sin(2 * np.pi * np.cumsum(f) / SR)
    nz = sweep_bp(noise(d), lambda u: 4000 * (0.08 ** u), q=1.0)
    x = (tone * 0.5 + norm(nz, 1) * 0.5) * np.exp(-t / (d * 0.6))
    return stereo(fade(x), width=0.5) * 0.7


def build_sfx(out_dir):
    out_dir.mkdir(parents=True, exist_ok=True)
    S = {
        "impact_big": sfx_impact(1.3), "impact_mid": sfx_impact(0.8),
        "subdrop": stereo(sfx_subdrop()), "riser_1s5": sfx_riser(1.5),
        "riser_0s5": sfx_riser(0.5), "riser_2s6": sfx_riser(2.6),
        "whoosh_a": sfx_whoosh(0.42), "whoosh_b": sfx_whoosh(0.5, 0.8, -0.8),
        "revcym_1s": sfx_revcym(1.0), "texthit": sfx_texthit(),
        "tick": sfx_tick(), "downer": sfx_downer(),
    }
    for k, v in S.items():
        S[k] = v if v.ndim == 2 else stereo(v)
        write_wav(out_dir / f"{k}.wav", S[k], bits=16)
    return S


# Cue sheet: (time, sfx, gain)
SFX_CUES = [
    (0.00, "impact_big", .80), (0.00, "subdrop", .50), (0.44, "texthit", .35),
    (0.93, "downer", .55), (1.50, "riser_1s5", .45), (2.00, "revcym_1s", .55),
    (3.00, "impact_big", .85), (3.00, "subdrop", .55),
    (4.46, "whoosh_a", .45), (5.71, "whoosh_b", .45), (7.20, "whoosh_a", .45),
    (8.00, "revcym_1s", .60), (8.50, "riser_0s5", .45),
    (9.00, "impact_big", .85), (9.00, "subdrop", .60),
    (10.22, "whoosh_b", .40), (11.70, "whoosh_a", .40), (12.50, "texthit", .55),
    (13.97, "whoosh_b", .35), (14.40, "texthit", .45), (15.20, "whoosh_a", .40),
    (16.45, "whoosh_b", .40), (16.50, "riser_0s5", .40),
    (17.00, "impact_mid", .75), (17.80, "riser_2s6", .55), (19.50, "revcym_1s", .60),
    (20.50, "impact_big", 1.0), (20.50, "subdrop", .75), (20.78, "whoosh_a", .35),
    (22.40, "whoosh_b", .45), (23.50, "impact_mid", .70),
    (25.50, "impact_big", .85), (25.50, "subdrop", .55), (26.05, "whoosh_a", .30),
    (31.00, "impact_big", .80), (31.00, "subdrop", .50),
]


# ---- music arrangement -----------------------------------------------------
CHORDS = [  # (808 root Hz, pad chord Hz, cowbell motif {step: Hz})
    (55.00, [220.0, 261.6, 329.6], {0: 659.3, 3: 523.3, 6: 659.3, 8: 587.3, 10: 523.3, 11: 493.9, 14: 440.0}),
    (43.65, [174.6, 220.0, 261.6], {0: 698.5, 3: 523.3, 6: 698.5, 8: 659.3, 10: 523.3, 11: 440.0, 14: 523.3}),
    (49.00, [196.0, 246.9, 293.7], {0: 587.3, 3: 493.9, 6: 587.3, 8: 523.3, 10: 493.9, 11: 392.0, 14: 493.9}),
    (41.20, [164.8, 207.7, 246.9], {0: 659.3, 3: 493.9, 6: 415.3, 8: 493.9, 10: 587.3, 11: 523.3, 14: 493.9}),
]


def build_music(out_path):
    N = int((DUR + 3) * SR)
    drums, bass, bell, pad = (np.zeros((N, 2)) for _ in range(4))
    step = BEAT / 4

    def put(buf, t, x, g=1.0, pan=0.0):
        x = stereo(x, pan) if x.ndim == 1 else x
        i = int(round(t * SR))
        if i >= N:
            return
        n = min(len(x), N - i)
        buf[i:i + n] += x[:n] * g

    K, CL, SN, HC, HO = v_kick(), v_clap(), v_snare(), v_hat(), v_hat(True)

    def bar_chord(t):
        return CHORDS[int(max(0, (t - 3.0)) // (4 * BEAT)) % 4]

    def groove(t0, t1, full, bell_lp=None, bell_g=0.22):
        t = t0
        while t < t1 - 1e-6:
            root, chord, motif = bar_chord(t)
            for s in range(16):
                ts = t + s * step
                if ts >= t1 - 1e-6:
                    break
                kicks = [0, 3, 6, 10, 11] if full else [0, 6, 10]
                if s in kicks:
                    put(drums, ts, K, 0.95)
                    nxt = [k for k in kicks if k > s]
                    ln = ((nxt[0] if nxt else 16) - s) * step
                    put(bass, ts, v_808(root * (2 if s == 11 else 1), ln), 0.55)
                if s in (4, 12):
                    put(drums, ts, CL, 0.55)
                if full or s % 2 == 0:
                    acc = 1.0 if s % 4 == 0 else 0.6
                    put(drums, ts, HC, 0.16 * acc, pan=0.25 if s % 2 else -0.15)
                if full and s in (7, 15):
                    put(drums, ts, HO, 0.10, pan=0.3)
                if full and s >= 14 and int(round(t / (4 * BEAT))) % 2 == 1:
                    for r in range(2):
                        put(drums, ts + r * step / 2, HC, 0.12, pan=0.35)
                if s in motif:
                    c = v_cowbell(motif[s])
                    if bell_lp:
                        c = lp(c, bell_lp(ts))
                    put(bell, ts, c, bell_g, pan=-0.2 if s % 2 else 0.2)
            put(pad, t, v_pad(chord, 4 * BEAT + 0.6), 1.0)
            t += 16 * step

    def roll(t0, t1, start_div=2, end_div=8, g0=0.15, g1=0.5):
        t = t0
        while t < t1 - 1e-4:
            u = (t - t0) / (t1 - t0)
            div = start_div * (end_div / start_div) ** u
            put(drums, t, SN, g0 + (g1 - g0) * u, pan=0.1)
            t += BEAT / div

    # HOOK 0-3: drone 808 + pad + filtered hats + snare roll into title
    put(bass, 0.0, v_808(55.0, 2.6), 0.6)
    put(pad, 0.0, v_pad([220.0, 261.6, 329.6], 3.2), 1.2)
    for s in range(16, 24):
        put(drums, s * step, HC, 0.06 + 0.01 * (s - 16), pan=0.2)
    roll(2.0, 2.95, 2, 8, 0.10, 0.45)
    # IDENTITY 3-9: groove A, cowbell filter opens
    groove(3.0, 8.75, False, bell_lp=lambda ts: 900 + 5000 * ((ts - 3) / 5.75) ** 1.5, bell_g=0.24)
    # MONTAGE 9-17: full drop, snare fill 16-17
    groove(9.0, 16.0, True)
    groove(16.0, 17.0, False, bell_g=0.18)
    roll(16.0, 17.0, 4, 8, 0.15, 0.40)
    # CLIMAX BUILD 17-20.5: four-on-the-floor + accelerating roll, no bass
    for b in range(7):
        put(drums, 17.0 + b * BEAT, K, 0.75)
    roll(18.0, 20.4, 2, 16, 0.10, 0.55)
    put(pad, 17.0, v_pad([220.0, 261.6, 329.6], 3.5), 1.4)
    # IMPACT / FINISHER / VICTORY 20.5-25.5 (freeze = breathing room)
    put(bass, 20.5, v_808(55.0, 1.4), 0.65)
    groove(21.0, 25.5, True)
    # END CARD 25.5-31: lighter groove so the code read-out stays clear
    groove(25.5, 31.0, False, bell_g=0.14)
    put(bass, 31.0, v_808(55.0, 1.0), 0.6)
    put(pad, 31.0, v_pad([220.0, 261.6, 329.6], 1.2), 1.0)

    mix = drums * 1.0 + bass * 1.0 + bell * 1.0 + pad * 1.0
    mix = reverb(mix, 0.12, 0.35)[:N]
    mix = np.tanh(mix * 1.3) / 1.3
    mix = mix[:int(DUR * SR)]
    mix = fade(mix, 0.0, 0.9)
    mix = norm(mix, 0.85)
    write_wav(out_path, mix)
    return mix


# ==========================================================================
# 3. VOICE-OVER (Piper TTS, local) + processing
# ==========================================================================
VO_CHAIN = ("aresample=48000:resampler=soxr,highpass=f=80,"
            "equalizer=f=230:t=q:w=1.1:g=-2.5,equalizer=f=120:t=q:w=1.0:g=1.5,"
            "equalizer=f=3300:t=q:w=1.2:g=2.5,deesser=i=0.45:m=0.5:f=0.5:s=o,"
            "acompressor=threshold=-20dB:ratio=3:attack=6:release=90:makeup=3,"
            "alimiter=limit=0.9:level=disabled:latency=1")


def build_vo(voice_model, work, out_dir):
    if not voice_model or not Path(voice_model).exists():
        print("!! voice model not found - voice-over skipped")
        return None, []
    raw_dir = work / "vo_raw"
    raw_dir.mkdir(parents=True, exist_ok=True)
    out_dir.mkdir(parents=True, exist_ok=True)
    track = np.zeros(int(DUR * SR))
    info = []
    for i, (t0, text, ls) in enumerate(VO_LINES, 1):
        raw = raw_dir / f"line_{i:02d}.wav"
        run([sys.executable, "-m", "piper", "-m", str(voice_model), "--length-scale", str(ls),
             "-f", str(raw)], input=text.encode(), capture_output=True)
        x = decode_audio(raw, VO_CHAIN, mono=True)
        regs = speech_regions(x)
        a, b = max(0, int((regs[0][0] - 0.03) * SR)), int((regs[-1][1] + 0.08) * SR)
        x = fade(x[a:b], 0.01, 0.05)
        x *= 10 ** (-17 / 20) / (np.sqrt(np.mean(x ** 2)) + 1e-9)      # level-match lines
        x = np.clip(x, -0.98, 0.98)
        write_wav(out_dir / f"line_{i:02d}.wav", x)
        i0 = int(t0 * SR)
        n = min(len(x), len(track) - i0)
        track[i0:i0 + n] += x[:n]
        regs = speech_regions(x)
        info.append(dict(line=i, text=text, start=t0, end=round(t0 + len(x) / SR, 3),
                         groups=[[round(t0 + r[0], 3), round(t0 + r[1], 3)] for r in regs]))
    (out_dir / "vo_timing.json").write_text(json.dumps(info, indent=1))
    return track, info


def group_starts(info, line, n):
    """Start times of the n most separated speech groups of a VO line."""
    regs = next(d["groups"] for d in info if d["line"] == line)
    if len(regs) <= n:
        return [r[0] for r in regs]
    gaps = sorted(range(1, len(regs)), key=lambda k: regs[k][0] - regs[k - 1][1], reverse=True)[:n - 1]
    return [regs[0][0]] + [regs[k][0] for k in sorted(gaps)]


# ==========================================================================
# 4. AUDIO MIX
# ==========================================================================
def src_time_map(seg, t_rel):
    """Map a time inside a segment to (source_time, mode, speed)."""
    acc = 0.0
    for p in seg["pieces"]:
        if p[0] == "F":
            if t_rel < acc + p[1] or p is seg["pieces"][-1]:
                return p[2], "freeze", 0.0
            acc += p[1]
            continue
        d, a, b = p[0], p[1], p[2]
        mode = p[3] if len(p) > 3 else "nearest"
        if t_rel < acc + d or p is seg["pieces"][-1]:
            sp = (b - a) / d
            return a + (t_rel - acc) * sp, mode, sp
        acc += d
    raise ValueError


def build_game_audio(source):
    src = decode_audio(source)
    mask = np.ones(len(src))
    for a, b in OLD_VO:                         # mute the old narration
        i, j = int(a * SR), min(int(b * SR), len(src))
        mask[i:j] = 0
    mask = np.convolve(mask, np.ones(int(0.03 * SR)) / int(0.03 * SR), mode="same")
    src = src * mask[:, None]
    out = np.zeros((int(DUR * SR), 2))
    for seg in SEGMENTS:
        if not seg.get("game"):
            continue
        t0, t1 = seg["t"]
        n = int(round((t1 - t0) * SR))
        rel = np.arange(n) / SR
        st = np.array([src_time_map(seg, r)[0] for r in rel[::64]])
        st = np.interp(np.arange(n), np.arange(0, n, 64)[:len(st)], st)
        idx = np.clip(st * SR, 0, len(src) - 2)
        i0 = np.floor(idx).astype(int)
        fr = (idx - i0)[:, None]
        chunk = src[i0] * (1 - fr) + src[i0 + 1] * fr       # varispeed (pitch follows speed)
        frz = np.array([src_time_map(seg, r)[1] == "freeze" for r in rel[::64]])
        frz = np.repeat(frz, 64)[:n]
        chunk[frz] = 0
        chunk = fade(chunk, 0.008, 0.012)
        a = int(round(t0 * SR))
        out[a:a + n] += chunk[:len(out) - a]
    return out


def envelope(x, attack=0.015, release=0.35):
    x = np.abs(x if x.ndim == 1 else x.mean(1))
    hop = 240
    n = len(x) // hop
    e = x[:n * hop].reshape(n, hop).max(1)
    out = np.zeros(n)
    ga, gr = math.exp(-hop / (attack * SR)), math.exp(-hop / (release * SR))
    v = 0.0
    for i, s in enumerate(e):
        v = ga * v + (1 - ga) * s if s > v else gr * v + (1 - gr) * s
        out[i] = v
    return np.interp(np.arange(len(x)), np.arange(n) * hop, out)


def mix_audio(music, sfx, game, vo, out_path, work):
    N = int(DUR * SR)
    sfx_bus = np.zeros((N + SR * 4, 2))
    for t, name, g in SFX_CUES:
        x = sfx[name]
        i = int(round(t * SR))
        sfx_bus[i:i + len(x)] += x * g
    sfx_bus = sfx_bus[:N]
    vo_st = np.zeros((N, 2)) if vo is None else stereo(vo[:N])
    duck = np.zeros(N) if vo is None else np.clip(envelope(vo[:N]) / 0.06, 0, 1)
    music_g = 0.50 * (1 - 0.70 * duck)                   # ~ -10.5 dB under the voice
    game_g = 0.55 * (1 - 0.68 * duck)                    # ~ -10 dB
    sfx_g = 0.75 * (1 - 0.45 * duck)
    mix = music[:N] * music_g[:, None] + game * game_g[:, None] + sfx_bus * sfx_g[:, None] + vo_st * 1.25
    # gentle bus glue + soft clip safety
    mix = np.tanh(mix * 0.9) / 0.9
    fo = int(0.35 * SR)
    mix[-fo:] *= np.linspace(1, 0, fo)[:, None] ** 1.5
    fi = int(0.005 * SR)                                  # avoids an AAC overshoot on sample 0
    mix[:fi] *= np.linspace(0, 1, fi)[:, None]
    tmp = work / "mix_premaster.wav"
    write_wav(tmp, norm(mix, 0.89))
    # two-pass EBU R128 loudness normalisation (-14 LUFS, -2 dBTP) for social platforms
    meas = run(["ffmpeg", "-hide_banner", "-i", str(tmp), "-af",
                "loudnorm=I=-14:TP=-2:LRA=11:print_format=json", "-f", "null", "-"],
               capture_output=True, text=True).stderr
    m = json.loads(meas[meas.rfind("{"):meas.rfind("}") + 1])
    af = ("loudnorm=I=-14:TP=-2:LRA=11:linear=true:"
          f"measured_I={m['input_i']}:measured_TP={m['input_tp']}:measured_LRA={m['input_lra']}:"
          f"measured_thresh={m['input_thresh']}:offset={m['target_offset']},"
          "alimiter=limit=0.78:attack=3:release=60:level=disabled:latency=1")
    run(["ffmpeg", "-v", "error", "-y", "-i", str(tmp), "-af", af, "-ar", str(SR),
         "-c:a", "pcm_s24le", str(out_path)])
    return mix


# ==========================================================================
# 5. PICTURE
# ==========================================================================
class SourceCache:
    def __init__(self, path):
        self.path, self.frames, self.base = str(path), [], 0

    def load(self, s0, s1):
        i0 = max(0, int(math.floor(s0 * FPS)) - 2)
        n = int(math.ceil(s1 * FPS)) + 3 - i0
        raw = run(["ffmpeg", "-v", "error", "-ss", f"{i0 / FPS:.6f}", "-i", self.path,
                   "-frames:v", str(n), "-an",
                   "-vf", "scale=in_color_matrix=bt709:in_range=tv,format=bgr24",
                   "-f", "rawvideo", "-"], capture_output=True).stdout
        fs = W * H * 3
        self.frames = [np.frombuffer(raw[k * fs:(k + 1) * fs], np.uint8).reshape(H, W, 3)
                       for k in range(len(raw) // fs)]
        self.base = i0

    def get(self, idx):
        k = int(np.clip(idx - self.base, 0, len(self.frames) - 1))
        return self.frames[k]


_VIG = None
_LUT = None


def grade(img, desat=0.0):
    global _VIG, _LUT
    if _VIG is None:
        yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
        r = np.sqrt(((xx - W / 2) / (W * 0.62)) ** 2 + ((yy - H / 2) / (H * 0.62)) ** 2)
        v = np.clip(1 - 0.30 * np.clip(r - 0.55, 0, None) ** 1.4 / 0.6, 0.68, 1)
        _VIG = cv2.merge([(v * 255).astype(np.uint8)] * 3)
        x = np.arange(256) / 255.0
        curve = 0.5 + (x - 0.5) * 1.07                       # mild contrast
        curve = np.clip(curve, 0, 1)
        shadow = np.clip(1 - x * 2.4, 0, 1)
        lut_b = np.clip(curve + 0.030 * shadow, 0, 1)        # cool / teal shadows
        lut_g = np.clip(curve + 0.012 * shadow, 0, 1)
        lut_r = np.clip(curve - 0.008 * shadow, 0, 1)
        _LUT = np.stack([lut_b, lut_g, lut_r], 1).reshape(256, 1, 3)
        _LUT = (_LUT * 255).astype(np.uint8)
    sat = 1.10 - desat
    wts = np.array([0.114, 0.587, 0.299], np.float32)
    M = (np.eye(3, dtype=np.float32) * sat + (1 - sat) * np.tile(wts, (3, 1))).astype(np.float32)
    img = cv2.transform(img, M)
    img = cv2.LUT(img, _LUT)
    img = cv2.multiply(img, _VIG, scale=1 / 255)
    blur = cv2.GaussianBlur(img, (0, 0), 1.1)
    return cv2.addWeighted(img, 1.30, blur, -0.30, 0)


def decay(events, t):
    out = []
    for te, s, d in events:
        if 0 <= t - te < d * 6:
            out.append((te, s * math.exp(-(t - te) / d)))
    return out


def shake_offset(t):
    dx = dy = 0.0
    for te, a in decay(SHAKES, t):
        u = t - te
        dx += a * (0.6 * math.sin(2 * math.pi * 17 * u + 1.3) + 0.4 * math.sin(2 * math.pi * 29 * u + 0.4))
        dy += a * (0.6 * math.sin(2 * math.pi * 21 * u + 2.1) + 0.4 * math.sin(2 * math.pi * 31 * u + 1.7))
    return dx, dy


def whip_state(t):
    """(dx, dy, blur_px) for whip-pan transitions around cuts."""
    for tc, d, ax in WHIPS:
        pre, post = 4 / FPS, 5 / FPS
        if tc - pre <= t < tc:
            p = (t - (tc - pre)) / pre
            off, bl = -d * (p ** 2) * 0.30, 6 + p * 70
        elif tc <= t < tc + post:
            p = 1 - (t - tc) / post
            off, bl = d * (p ** 2) * 0.30, 6 + p * 70
        else:
            continue
        return (off * W, 0, bl, ax) if ax == "h" else (0, off * H, bl, ax)
    return 0, 0, 0, None


class Renderer:
    def __init__(self, source, layers, meta, vo_info):
        self.cache = SourceCache(source)
        self.loaded = None
        self.L = {k: to_layer(v) for k, v in layers.items()}
        self.meta = meta
        self.vo_info = vo_info
        self.white = np.full((H, W, 3), 255, np.uint8)
        # caption timing follows the actual voice-over where available
        build_t, aim_t, prove_t = 12.55, 14.45, 24.25
        if vo_info:
            g5 = group_starts(vo_info, 5, 3)
            build_t, aim_t = g5[0], max(14.40, g5[2])
            prove_t = group_starts(vo_info, 7, 3)[2]
        self.captions = [
            ("hook", 0.33, 2.72, 540, 410, "slam"),
            ("title", 3.00, 5.55, 540, 700, "title"),
            ("sub", 5.95, 8.70, 540, 1340, "rise"),
            ("build", build_t, min(build_t + 1.3, 13.95), 540, 440, "pop"),
            ("aim", aim_t, 15.22, 540, 440, "pop"),
            ("prove", prove_t, 25.45, 540, 1130, "pop"),
        ]
        self.code_hi = group_starts(vo_info, 8, 3) if vo_info else [26.5, 28.0, 29.5]

    def seg_at(self, t):
        for s in SEGMENTS:
            if s["t"][0] <= t < s["t"][1]:
                return s
        return SEGMENTS[-1]

    def source_frame(self, seg, t):
        if self.loaded is not seg:
            srcs = [p[2] if p[0] == "F" else p[1] for p in seg["pieces"]] + \
                   [p[2] if p[0] == "F" else p[2] for p in seg["pieces"]]
            self.cache.load(min(srcs), max(srcs))
            self.loaded = seg
        rel = t - seg["t"][0]
        s, mode, sp = src_time_map(seg, rel)
        f = s * FPS
        if mode in ("nearest", "freeze"):
            return self.cache.get(int(round(f))).copy()
        if mode == "blend":
            i, w = int(math.floor(f)), f - math.floor(f)
            return cv2.addWeighted(self.cache.get(i), 1 - w, self.cache.get(i + 1), w, 0)
        # accum: average every source frame covered by this output frame (motion blur)
        i0, i1 = int(round(f)), int(round(f + sp))
        acc = np.zeros((H, W, 3), np.float32)
        for i in range(i0, max(i1, i0 + 1)):
            acc += self.cache.get(i)
        return (acc / max(1, i1 - i0)).astype(np.uint8)

    def caption_anim(self, kind, u, r):
        if kind == "slam":
            sc = 1.0 + 0.6 * (1 - ease_out(u / 0.12))
            op = min(1, u / 0.05)
        elif kind == "title":
            sc = 1.0 + 0.25 * (1 - ease_out(u / 0.25)) + 0.03 * u
            op = min(1, u / 0.08)
        elif kind == "rise":
            sc = 1.0
            op = min(1, u / 0.25)
        else:
            sc = 0.7 + 0.3 * ease_out_back(u / 0.16)
            op = min(1, u / 0.05)
        if r < 0.14:
            op *= max(0, r / 0.14)
            sc *= 1 + 0.05 * (1 - r / 0.14)
        dy = 40 * (1 - ease_out(u / 0.3)) if kind == "rise" else 0
        return sc, op, dy

    def endcard(self, frame, t):
        e = t - 25.5
        L = self.L
        # title slam
        sc = 1.0 + 0.30 * (1 - ease_out(e / 0.18)) + 0.008 * e
        overlay(frame, L["ec_title"], 540, 680, sc, min(1, e / 0.06))
        # accent lines growing outwards
        g = ease_out((e - 0.15) / 0.4)
        if g > 0:
            ln = L["ec_line"]
            w = ln[1].shape[1]
            cut = max(2, int(w * g))
            overlay(frame, (ln[0][:, :cut], ln[1][:, :cut]), 540 - 40 - cut / 2, 925, 1, 1)
            overlay(frame, (ln[0][:, w - cut:], ln[1][:, w - cut:]), 540 + 40 + cut / 2, 925, 1, 1)
        u = e - 0.45
        if u > 0:
            overlay(frame, L["ec_label"], 540, 1000 + 20 * (1 - ease_out(u / 0.3)), 1, min(1, u / 0.25))
        u = e - 0.60
        if u > 0:
            rev = ease_out(u / 0.25)
            overlay(frame, L["ec_panel"], 540, 1140, 1, 1, reveal=rev)
            overlay(frame, L["ec_code"], 540, 1140, 1, min(1, max(0, (u - 0.12) / 0.12)))
            # glint sweeping across the panel every 2 s
            ph = ((e - 0.9) % 2.0) / 0.55
            if 0 <= ph <= 1 and e > 0.9:
                x = int(100 + ph * 880)
                band = slice(max(0, x - 40), min(W, x + 40))
                reg = frame[1045:1235, band].astype(np.float32)
                frame[1045:1235, band] = np.clip(reg * 1.0 + 38, 0, 255).astype(np.uint8)
            # VO-synced highlight of each code group
            for gs, (x0, x1) in zip(self.code_hi, self.meta["code_groups"]):
                d = t - gs
                if 0 <= d < 1.1:
                    op = min(1, d / 0.08) * (1 - d / 1.1) * 0.9
                    cw = L["ec_code_hi"][1].shape[1]
                    overlay(frame, L["ec_code_hi"], 540 - cw / 2 + (x0 + x1) / 2, 1140, 1, op,
                            xcrop=(max(0, x0), min(cw, x1)))
                    overlay(frame, L["ec_code"], 540 - cw / 2 + (x0 + x1) / 2, 1140, 1, op * 0.5,
                            xcrop=(max(0, x0), min(cw, x1)))
        u = e - 1.0
        if u > 0:
            pulse = 1 + 0.025 * math.sin(2 * math.pi * (t / BEAT) / 2) if u > 0.3 else 1
            sc = (0.7 + 0.3 * ease_out_back(u / 0.2)) * pulse
            overlay(frame, L["ec_play"], 540, 1360, sc, min(1, u / 0.06))

    def endcard_bg(self, img):
        small = cv2.resize(img, (W // 4, H // 4), interpolation=cv2.INTER_AREA)
        small = cv2.GaussianBlur(small, (0, 0), 5)
        img = cv2.resize(small, (W, H), interpolation=cv2.INTER_LINEAR)
        img = (img.astype(np.float32) * 0.36 + np.array(CHARCOAL[::-1], np.float32) * 0.30)
        if not hasattr(self, "_ecgrad"):
            yy = np.linspace(0, 1, H)[:, None]
            xx = np.linspace(-1, 1, W)[None, :]
            r = np.sqrt(xx ** 2 + ((yy - 0.57) * 1.9) ** 2)
            glow = np.clip(1 - r, 0, 1) ** 2 * 0.35
            dark = 1 - 0.45 * (np.abs(yy - 0.55) * 1.6) ** 2
            self._ecgrad = (glow[..., None] * np.array(CYAN[::-1], np.float32) * 0.35, dark[..., None])
        img = img * self._ecgrad[1] + self._ecgrad[0]
        return np.clip(img, 0, 255).astype(np.uint8)

    def frame(self, k):
        t = k / FPS
        seg = self.seg_at(t)
        img = self.source_frame(seg, t)
        z = keyframes(seg["zoom"], t - seg["t"][0])
        cx, cy = seg.get("center", (W / 2, H / 2))
        sx, sy = shake_offset(t)
        wx, wy, wb, wax = whip_state(t)
        M = np.float32([[z, 0, W / 2 + sx + wx - z * cx], [0, z, H / 2 + sy + wy - z * cy]])
        img = cv2.warpAffine(img, M, (W, H), flags=cv2.INTER_LINEAR, borderMode=cv2.BORDER_REFLECT101)
        if seg.get("endcard"):
            img = self.endcard_bg(img)
        else:
            img = grade(img, seg.get("desat", 0.0))
        if wb > 1:
            k_ = int(wb) | 1
            img = cv2.blur(img, (k_, 1) if wax == "h" else (1, k_))
        for t0, t1, amt in DIMS:
            if t0 <= t < t1:
                img = cv2.convertScaleAbs(img, alpha=1 - amt * ease_in_out((t - t0) / (t1 - t0)))
        ch = sum(a for _, a in decay([(a, b, c) for a, b, c in CHROMA], t))
        if ch >= 1:
            s = int(round(ch))
            img = img.copy()
            img[:, s:, 0] = img[:, :-s, 0]
            img[:, :-s, 2] = img[:, s:, 2]
        if seg.get("endcard"):
            self.endcard(img, t)
        for name, t0, t1, cx_, cy_, kind in self.captions:
            if t0 <= t < t1:
                sc, op, dy = self.caption_anim(kind, t - t0, t1 - t)
                overlay(img, self.L[name], cx_, cy_ + dy, sc, op)
        fl = min(1.0, sum(a for _, a in decay(FLASHES, t)))
        if fl > 0.01:
            img = cv2.addWeighted(img, 1 - fl, self.white, fl, 0)
        if t > DUR - 0.40:                                   # clean fade to black
            img = cv2.convertScaleAbs(img, alpha=max(0.0, (DUR - t) / 0.40))
        return img


def render_video(source, layers, meta, vo_info, out_path, stills=None):
    r = Renderer(source, layers, meta, vo_info)
    if stills:
        out_path.mkdir(parents=True, exist_ok=True)
        for ts in stills:
            k = int(round(ts * FPS))
            cv2.imwrite(str(out_path / f"still_{ts:06.2f}.jpg"), r.frame(k), [cv2.IMWRITE_JPEG_QUALITY, 92])
        return
    enc = subprocess.Popen(
        ["ffmpeg", "-v", "error", "-y", "-f", "rawvideo", "-pix_fmt", "bgr24", "-s", f"{W}x{H}",
         "-r", str(FPS), "-i", "-",
         "-vf", "scale=out_color_matrix=bt709:out_range=tv,format=yuv420p",
         "-c:v", "libx264", "-preset", "slow", "-crf", "17", "-profile:v", "high", "-level", "4.2",
         "-g", "120", "-bf", "3", "-colorspace", "bt709", "-color_primaries", "bt709",
         "-color_trc", "bt709", "-color_range", "tv", str(out_path)], stdin=subprocess.PIPE)
    for k in range(NFRAMES):
        enc.stdin.write(r.frame(k).tobytes())
        if k % 120 == 0:
            print(f"  frame {k}/{NFRAMES}", flush=True)
    enc.stdin.close()
    if enc.wait() != 0:
        raise RuntimeError("encoder failed")


def mux(video, audio, out):
    run(["ffmpeg", "-v", "error", "-y", "-i", str(video), "-i", str(audio),
         "-map", "0:v:0", "-map", "1:a:0", "-c:v", "copy", "-c:a", "aac", "-b:a", "320k",
         "-ar", str(SR), "-ac", "2", "-shortest", "-movflags", "+faststart",
         "-metadata", f"title={MAP_NAME} - Map Code {MAP_CODE}", str(out)])


# ==========================================================================
# main
# ==========================================================================
def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--source", required=True, help="original gameplay MP4 (read-only)")
    ap.add_argument("--voice", help="Piper .onnx voice model (e.g. en_US-joe-medium.onnx)")
    ap.add_argument("--work", default=str(HERE / "_work"), help="scratch dir for temp files")
    ap.add_argument("--out", default=str(HERE), help="deliverables dir")
    ap.add_argument("--stills", help="comma separated times: render preview stills only")
    ap.add_argument("--audio-only", action="store_true",
                    help="rebuild audio and re-mux using the existing <work>/video_only.mp4")
    args = ap.parse_args()

    src = Path(args.source).resolve()
    if not src.exists():
        sys.exit(f"source not found: {src}")
    out, work = Path(args.out), Path(args.work)
    work.mkdir(parents=True, exist_ok=True)

    print("[1/6] graphics")
    layers, meta = build_graphics(ASSETS / "graphics")
    print("[2/6] voice-over")
    vo, vo_info = build_vo(args.voice, work, ASSETS / "vo")
    if args.stills:
        render_video(src, layers, meta, vo_info, work / "stills",
                     [float(s) for s in args.stills.split(",")])
        return
    if vo is not None:
        write_wav(out / "voiceover.wav", vo)
    print("[3/6] music + sfx")
    sfx = build_sfx(ASSETS / "sfx")
    music = build_music(ASSETS / "music" / "realistic_scrims_theme.wav")
    print("[4/6] audio mix")
    game = build_game_audio(src)
    mix_audio(music, sfx, game, vo, out / "final_audio.wav", work)
    if not args.audio_only:
        print("[5/6] picture")
        render_video(src, layers, meta, vo_info, work / "video_only.mp4")
    print("[6/6] mux")
    mux(work / "video_only.mp4", out / "final_audio.wav", out / "realistic_scrims_trailer.mp4")
    print("done ->", out / "realistic_scrims_trailer.mp4")


if __name__ == "__main__":
    main()
