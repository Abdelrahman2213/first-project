"""
fx.py - core frame-effects engine for the Fortnite Multiverse trailer.
Pure numpy + Pillow. No external AI / 3D. Deterministic.

Three looks for three "worlds":
  grade_cinematic() : filmic teal/orange hero look (World 1 + grade bed)
  legoize()         : brick-mosaic plastic LEGO look (World 2), identity-preserving
  grade_game()      : punchy readable gameplay look (World 3)
plus transition primitives (dissolve masks, brick particles, flashes, rgb split, glitch).
"""
import numpy as np
from PIL import Image, ImageFilter

H, W = 1920, 1080

# ---------------------------------------------------------------- io
def load(path):
    im = Image.open(path).convert("RGB")
    if im.size != (W, H):
        im = im.resize((W, H), Image.LANCZOS)
    return np.asarray(im).astype(np.float32)

def save(arr, path):
    Image.fromarray(clamp8(arr)).save(path)

def clamp8(arr):
    return np.clip(arr, 0, 255).astype(np.uint8)

def to_img(arr):
    return Image.fromarray(clamp8(arr))

def from_img(im):
    return np.asarray(im.convert("RGB")).astype(np.float32)

# ---------------------------------------------------------------- helpers
def _luma(a):
    return a[..., 0] * 0.299 + a[..., 1] * 0.587 + a[..., 2] * 0.114

def saturate(a, s):
    l = _luma(a)[..., None]
    return l + (a - l) * s

def contrast(a, c, pivot=128.0):
    return (a - pivot) * c + pivot

def gamma(a, g):
    return 255.0 * np.power(np.clip(a / 255.0, 0, 1), g)

_YY, _XX = np.mgrid[0:H, 0:W].astype(np.float32)
_CX, _CY = W / 2.0, H / 2.0
_R = np.sqrt(((_XX - _CX) / _CX) ** 2 + ((_YY - _CY) / _CY) ** 2)  # 0 center -> ~1.4 corner

def vignette(a, strength=0.55, feather=1.15, top_bottom=0.0):
    v = np.clip(1.0 - strength * (np.clip(_R / feather, 0, 1) ** 2.2), 0, 1)
    if top_bottom > 0:  # extra darkening at very top & bottom (kills residual UI)
        ny = np.abs((_YY - _CY) / _CY)  # 0 center -> 1 edge
        band = np.clip((ny - 0.72) / 0.28, 0, 1) ** 1.6
        v = v * (1.0 - top_bottom * band)
    return a * v[..., None]

def add_grain(a, amt=6.0, seed=0):
    rng = np.random.default_rng(seed)
    n = rng.standard_normal((H, W, 1)).astype(np.float32) * amt
    return a + n

def chroma_shift(a, px=2.0):
    out = a.copy()
    sh = int(round(px))
    if sh <= 0:
        return out
    out[:, :, 0] = np.roll(a[:, :, 0], sh, axis=1)   # R right
    out[:, :, 2] = np.roll(a[:, :, 2], -sh, axis=1)  # B left
    return out

def soft_bloom(a, thresh=186.0, blur=14, gain=0.5):
    l = _luma(a)
    mask = np.clip((l - thresh) / (255.0 - thresh), 0, 1)[..., None]
    bright = a * mask
    b = from_img(to_img(bright).filter(ImageFilter.GaussianBlur(blur)))
    return a + b * gain

def tint_shadows_highlights(a, shadow=(-8, 2, 18), highlight=(16, 8, -12)):
    l = (_luma(a) / 255.0)[..., None]
    sh = np.array(shadow, np.float32)
    hi = np.array(highlight, np.float32)
    return a + sh * (1 - l) + hi * l

# ---------------------------------------------------------------- World 1: cinematic
_SCRIM = None
def _scrim(top_h=0.16, bot_h=0.13, top_dark=0.92, bot_dark=0.86):
    """Static cinematic top/bottom darkening scrim (also hides residual burned-in UI)."""
    global _SCRIM
    if _SCRIM is not None:
        return _SCRIM
    g = np.ones((H, W, 1), np.float32)
    yv = _YY[:, :1]  # (H,1)
    th = int(top_h * H); bh = int(bot_h * H)
    tcol = (1 - top_dark) + top_dark * (yv[:th, 0] / th) ** 1.3
    bcol = (1 - bot_dark) + bot_dark * ((H - 1 - yv[H - bh:, 0]) / bh) ** 1.3
    g[:th, :, 0] = tcol[:, None]
    g[H - bh:, :, 0] = bcol[:, None]
    _SCRIM = g
    return _SCRIM

def grade_cinematic(a, vig=0.62, tb=0.6, grain=4.0, chroma=2.0, bloom=0.55, seed=0, scrim=True):
    a = contrast(a, 1.14)
    a = saturate(a, 0.92)
    a = tint_shadows_highlights(a, shadow=(-10, 0, 22), highlight=(20, 10, -14))  # teal shadows / warm highs
    a = gamma(a, 1.04)
    a = soft_bloom(a, thresh=180, blur=16, gain=bloom)
    a = vignette(a, strength=vig, top_bottom=tb)
    if scrim:
        a = a * _scrim()
    if chroma:
        a = chroma_shift(a, chroma)
    a = add_grain(a, grain, seed)
    return a

# ---------------------------------------------------------------- World 3: gameplay
def grade_game(a, vig=0.42, tb=0.35, grain=3.0, seed=0):
    a = contrast(a, 1.16)
    a = saturate(a, 1.18)
    a = gamma(a, 0.97)
    a = soft_bloom(a, thresh=198, blur=10, gain=0.35)
    a = vignette(a, strength=vig, top_bottom=tb)
    a = add_grain(a, grain, seed)
    return a

# ---------------------------------------------------------------- World 2: LEGO
_STUD_CACHE = {}

def _stud_bevel_overlays(cell):
    """Precompute per-cell stud shading (mult) and bevel edges (add) for the full frame."""
    if cell in _STUD_CACHE:
        return _STUD_CACHE[cell]
    # one cell stud: radial bump, bright top-left highlight, dark bottom-right
    yy, xx = np.mgrid[0:cell, 0:cell].astype(np.float32)
    cx = cy = (cell - 1) / 2.0
    rad = cell * 0.34
    d = np.sqrt((xx - cx) ** 2 + (yy - cy) ** 2)
    disc = np.clip((rad - d) / 2.0 + 0.5, 0, 1)            # 1 inside stud, 0 outside
    # directional light: top-left bright, bottom-right dark
    lightdir = ((cx - xx) + (cy - yy)) / (cell * 1.4)
    shade = 1.0 + disc * (0.44 * np.tanh(lightdir * 2.2))  # mult on stud, 1.0 flat
    # subtle ring edge
    ring = np.clip(1.0 - np.abs(d - rad) / 1.5, 0, 1)
    shade = shade * (1.0 - 0.18 * ring * (1 - disc * 0))
    stud_mult_cell = shade.astype(np.float32)
    # bevel: bright top & left edge, dark bottom & right edge of the brick cell
    bev = np.zeros((cell, cell), np.float32)
    bw = max(1, cell // 10)
    bev[:bw, :] += 22; bev[:, :bw] += 22
    bev[-bw:, :] -= 26; bev[:, -bw:] -= 26
    # tile to full frame
    ny = H // cell + 1
    nx = W // cell + 1
    stud_mult = np.tile(stud_mult_cell, (ny, nx))[:H, :W]
    bevel = np.tile(bev, (ny, nx))[:H, :W]
    _STUD_CACHE[cell] = (stud_mult[..., None], bevel[..., None])
    return _STUD_CACHE[cell]

def _blockify(a, cell):
    im = to_img(a)
    small = im.resize((max(1, W // cell), max(1, H // cell)), Image.BILINEAR)
    return from_img(small.resize((W, H), Image.NEAREST))

def _small(a, cell):
    return from_img(to_img(a).resize((max(1, W // cell), max(1, H // cell)), Image.BILINEAR))

def _up(small, size=(W, H)):
    return from_img(to_img(small).resize(size, Image.NEAREST))

# curated LEGO plastic palette (clean, few colours -> toy look); covers the character + map
LEGO_PALETTE = np.array([
    [ 24,  24,  28],   # black
    [ 70,  74,  82],   # dark bluish grey
    [138, 144, 150],   # light bluish grey (vest)
    [236, 236, 230],   # white (shirt)
    [226, 188, 150],   # nougat / skin
    [196, 150, 110],   # medium nougat (tattoo arm)
    [214, 162,  58],   # pearl gold (tanks/weapon)
    [247, 209,  72],   # bright yellow
    [120,  72,  44],   # reddish brown (wood/brick builds)
    [158,  52,  44],   # dark red (brick)
    [120, 142, 168],   # sand blue (shadowed metal)
    [ 52, 112, 188],   # blue
    [150, 198, 226],   # light sky blue (background)
    [ 92, 162,  78],   # bright green (grass)
    [196, 206, 210],   # very light grey (concrete)
], np.float32)

def _snap_palette(small, pal=LEGO_PALETTE):
    h, w, _ = small.shape
    flat = small.reshape(-1, 1, 3)
    d = np.sum((flat - pal[None, :, :]) ** 2, axis=2)   # (N, K)
    idx = np.argmin(d, axis=1)
    return pal[idx].reshape(h, w, 3)

def legoize(a, cell=17, sat=1.1, plate_light=0.22):
    """Brick-mosaic plastic look. Snaps every brick to a clean LEGO plastic palette
    (so the grey vest stays grey, tanks stay gold) then adds moulded stud/bevel 3D.
    Preserves the character's shapes & colours => same minifig is recognisable."""
    small = _small(a, cell)
    small = saturate(small, sat)
    small = _snap_palette(small)
    plas = _up(small, (W, H))
    stud_mult, bevel = _stud_bevel_overlays(cell)
    out = plas * stud_mult + bevel
    # gentle glossy top sheen
    sheen = np.clip(1.0 - _YY / H, 0, 1)[..., None] * plate_light * 12.0
    out = out + sheen
    out = soft_bloom(out, thresh=228, blur=5, gain=0.20)
    return out

# ---------------------------------------------------------------- transitions
def dissolve_mask(progress, scale=90, seed=7, center_bias=0.0, soft=0.12):
    """Animated organic dissolve mask in [0,1]; 1 => show 'B'. Grows with progress."""
    rng = np.random.default_rng(seed)
    nh, nw = H // scale + 2, W // scale + 2
    noise = rng.random((nh, nw)).astype(np.float32)
    noise = from_img(to_img(np.repeat(noise[..., None] * 255, 3, axis=2)).resize((W, H), Image.BILINEAR))[..., 0] / 255.0
    thr = noise.copy()
    if center_bias:  # dissolve starts at centre and spreads out
        thr = thr * (1 - center_bias) + (_R / 1.4) * center_bias
    m = np.clip((progress - thr) / soft + 0.5, 0, 1)
    return m[..., None]

def radial_wipe(progress, soft=0.10, invert=False):
    r = _R / 1.42
    m = np.clip((progress - r) / soft + 0.5, 0, 1)
    if invert:
        m = 1 - m
    return m[..., None]

def flash(a, amount, color=(255, 255, 255)):
    c = np.array(color, np.float32)
    return a + (c - a) * np.clip(amount, 0, 1)

def rgb_split(a, px):
    out = a.copy()
    s = int(round(px))
    if s == 0:
        return out
    out[:, :, 0] = np.roll(a[:, :, 0], s, axis=1)
    out[:, :, 1] = np.roll(a[:, :, 1], 0, axis=1)
    out[:, :, 2] = np.roll(a[:, :, 2], -s, axis=1)
    return out

def glitch(a, amount, seed=0):
    if amount <= 0:
        return a
    rng = np.random.default_rng(seed)
    out = a.copy()
    nbands = int(2 + amount * 10)
    for _ in range(nbands):
        y0 = rng.integers(0, H - 20)
        hh = rng.integers(6, 46)
        sh = rng.integers(-int(40 * amount) - 1, int(40 * amount) + 1)
        out[y0:y0 + hh] = np.roll(out[y0:y0 + hh], sh, axis=1)
        if rng.random() < 0.5:
            ch = rng.integers(0, 3)
            out[y0:y0 + hh, :, ch] = np.clip(out[y0:y0 + hh, :, ch] * 1.4, 0, 255)
    return out


if __name__ == "__main__":
    import sys
    src = sys.argv[1]
    a = load(src)
    save(grade_cinematic(a.copy()), "proto/out_cinematic.png")
    save(legoize(a.copy(), cell=18), "proto/out_lego18.png")
    save(legoize(a.copy(), cell=24), "proto/out_lego24.png")
    save(grade_game(a.copy()), "proto/out_game.png")
    print("wrote proto/out_*.png for", src)
