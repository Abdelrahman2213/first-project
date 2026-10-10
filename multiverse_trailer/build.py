"""
build.py - Fortnite Multiverse Action Trailer builder.
Pipeline: extract (ffmpeg, UI-safe punch framing) -> render (numpy/PIL looks,
camera moves, transformations, brick particles -> rawvideo -> H.264) ->
audio (synth SFX, no music) -> mux (+loudnorm).

Stages:  python3 build.py extract | video | audio | mux | all
"""
import os, sys, math, subprocess, shutil
import numpy as np
from PIL import Image, ImageFilter, ImageDraw
import fx
import audio as A

W, H, FPS = 1080, 1920, 30
ROOT = os.path.dirname(os.path.abspath(__file__))
SRC_SCRIMS = os.path.join(ROOT, "source", "scrims.mp4")
SRC_TIME   = os.path.join(ROOT, "source", "timeline.mp4")
WORK = os.path.join(ROOT, "work")
OUT_VIDEO_SILENT = os.path.join(WORK, "video_silent.mp4")
OUT_SFX = os.path.join(ROOT, "assets", "sfx_trailer.wav")
OUT_VO  = os.path.join(ROOT, "assets", "voiceover.wav")
OUT_MIX = os.path.join(ROOT, "assets", "mix_trailer.wav")
VO_DIR  = os.path.join(ROOT, "assets", "vo")
OUT_FINAL = os.path.join(ROOT, "fortnite_multiverse_action_trailer.mp4")

# ----------------------------------------------------------------- timeline
# crop=(cw,ch,cx,cy): UI-safe punch window in the 1080x1920 source (removes
# top map-code/round-timer and bottom captions/watermark), then scaled to 1080x1920.
# world: cine|lego|game|trans1|trans2 . cam: push/shake/impact . accents: flash_in etc.
def seg(name, src, t0, srcdur, outdur, crop, world, **kw):
    d = dict(name=name, src=src, t0=t0, srcdur=srcdur, outdur=outdur, crop=crop, world=world)
    d.update(kw); return d

# default UI-safe crop
C = (786, 1398, 147, 196)      # centred
CL = (786, 1398, 70, 196)      # char biased left
CR = (786, 1398, 224, 196)     # char biased right
CT = (720, 1280, 180, 150)     # tighter / higher (detail)

SEGMENTS = [
  # ---------------- WORLD 1 : CINEMATIC MOVIE (0.0 - 7.2) ----------------
  seg("w1_hook",   SRC_TIME,   1.30, 1.55, 2.60, (700,1245,150,258), "cine",
      cam=dict(push=(1.02,1.14)), accents=dict(fade_in=0.5)),
  seg("w1_weapon", SRC_TIME,   6.90, 1.50, 2.20, (760,1351,210,200), "cine",
      cam=dict(push=(1.0,1.10)), accents=dict(flash_in=0.35)),
  seg("w1_walk",   SRC_SCRIMS, 8.70, 2.00, 2.40, (720,1280,210,230), "cine",
      cam=dict(push=(1.04,1.16))),
  # ---------------- TRANSITION 1 : MOVIE -> LEGO (7.2 - 8.5) ----------------
  seg("t1",        SRC_SCRIMS,10.75, 0.55, 1.30, (720,1280,210,230), "trans1",
      cam=dict(push=(1.08,1.20))),
  # ---------------- WORLD 2 : LEGO (8.5 - 15.1) ----------------
  seg("w2_walk",   SRC_SCRIMS,11.30, 1.65, 1.90, (720,1280,210,230), "lego",
      cam=dict(push=(1.02,1.12)), accents=dict(bricks=10)),
  seg("w2_slide",  SRC_TIME,  10.00, 1.60, 1.80, C, "lego",
      cam=dict(push=(1.0,1.12), shake=0.004), accents=dict(bricks=12)),
  seg("w2_gun",    SRC_TIME,  11.95, 1.00, 1.20, (700,1245,230,230), "lego",
      cam=dict(push=(1.10,1.0)), accents=dict(bricks=8)),
  seg("w2_elim",   SRC_TIME,  14.55, 1.05, 1.60, (820,1458,150,230), "lego",
      cam=dict(push=(1.0,1.16)), accents=dict(bricks=14)),
  # ---------------- TRANSITION 2 : LEGO -> FORTNITE (15.1 - 17.0) ----------------
  seg("t2",        SRC_TIME,  15.00, 0.55, 1.90, (820,1458,150,230), "trans2",
      cam=dict(push=(1.16,1.30))),
  # ---------------- WORLD 3 : GAMEPLAY MONTAGE (17.0 - 32.5) ----------------
  seg("g_elim1",   SRC_TIME,  15.05, 1.45, 1.55, (820,1458,150,230), "game",
      cam=dict(push=(1.12,1.0)), accents=dict(flash_in=0.6, shake=0.006)),
  seg("g_fight",   SRC_SCRIMS,16.45, 1.55, 1.45, C, "game",
      cam=dict(push=(1.0,1.08), shake=0.004), accents=dict(whip_in=0.18)),
  seg("g_rocket",  SRC_SCRIMS,19.20, 0.95, 0.95, (700,1245,200,210), "game",
      cam=dict(impact=(1.22,1.0)), accents=dict(flash_in=0.4)),
  seg("g_freeze",  SRC_SCRIMS,16.60, 0.20, 0.70, C, "game",
      cam=dict(push=(1.14,1.14)), accents=dict(freeze=True, flash_in=0.7, shake=0.008)),
  seg("g_build1",  SRC_SCRIMS,22.60, 1.70, 1.60, C, "game",
      cam=dict(push=(1.0,1.08)), accents=dict(whip_in=0.16)),
  seg("g_build2",  SRC_SCRIMS,26.60, 1.40, 1.30, C, "game",
      cam=dict(push=(1.04,1.12), shake=0.004)),
  seg("g_boom",    SRC_TIME,  26.80, 1.30, 1.25, C, "game",
      cam=dict(push=(1.0,1.10)), accents=dict(flash_in=0.3)),
  seg("g_elim2",   SRC_SCRIMS,29.80, 0.95, 1.45, (760,1351,160,210), "game",
      cam=dict(impact=(1.18,1.02), shake=0.006), accents=dict(speedcut=True)),
  seg("g_victory", SRC_SCRIMS,32.75, 1.55, 1.70, (900,1600,90,180), "game",
      cam=dict(push=(1.08,1.0)), accents=dict(flash_in=0.8, shake=0.010)),
  # ---------------- OUTRO : clean cinematic hero callback ----
  seg("outro",     SRC_TIME,   6.35, 1.10, 1.60, (760,1351,210,200), "cine",
      cam=dict(push=(1.0,1.08)), accents=dict(title=True)),
  # ---------------- END CARD : map code + CTA ----
  seg("endcard",   SRC_TIME,  13.55, 0.20, 7.00, (900,1600,90,180), "endcard",
      cam=dict(push=(1.0,1.06)), accents=dict(freeze=True, fade_out=0.7)),
]

# ----------------------------------------------------------------- extraction
def extract():
    if os.path.isdir(WORK): shutil.rmtree(WORK)
    os.makedirs(WORK)
    for s in SEGMENTS:
        d = os.path.join(WORK, s["name"]); os.makedirs(d, exist_ok=True)
        cw, ch, cx, cy = s["crop"]
        nframes = max(1, round(s["outdur"]*FPS))
        if s.get("accents", {}).get("freeze"):
            # single frame, held
            vf = f"crop={cw}:{ch}:{cx}:{cy},scale={W}:{H}"
            subprocess.run(["ffmpeg","-nostdin","-v","error","-y","-ss",f'{s["t0"]:.3f}',
                "-i",s["src"],"-frames:v","1","-vf",vf, os.path.join(d,"src_%05d.png")], check=True)
        else:
            # time-map srcdur -> outdur (speed ramp via setpts), fps=FPS
            spd = s["srcdur"]/s["outdur"]
            vf = (f"crop={cw}:{ch}:{cx}:{cy},scale={W}:{H},"
                  f"setpts=(PTS-STARTPTS)/{spd},fps={FPS}")
            subprocess.run(["ffmpeg","-nostdin","-v","error","-y","-ss",f'{s["t0"]:.3f}',
                "-t",f'{s["srcdur"]:.3f}',"-i",s["src"],"-vf",vf,"-frames:v",str(nframes),
                os.path.join(d,"src_%05d.png")], check=True)
        got = len([f for f in os.listdir(d) if f.endswith(".png")])
        print(f'  extracted {s["name"]:11s} {got:3d} frames  ({s["outdur"]:.2f}s)')

def seg_frames(s):
    d = os.path.join(WORK, s["name"])
    fs = sorted(f for f in os.listdir(d) if f.endswith(".png"))
    n = max(1, round(s["outdur"]*FPS))
    paths = [os.path.join(d, fs[min(i, len(fs)-1)]) for i in range(n)]
    return paths

# ----------------------------------------------------------------- camera
def cam_zoom(a, zoom, dx=0.0, dy=0.0):
    if abs(zoom-1.0) < 1e-3 and dx==0 and dy==0:
        return a
    cw, ch = W/zoom, H/zoom
    x0 = (W-cw)/2 + dx*W; y0 = (H-ch)/2 + dy*H
    x0 = min(max(x0,0), W-cw); y0 = min(max(y0,0), H-ch)
    im = fx.to_img(a).crop((x0,y0,x0+cw,y0+ch)).resize((W,H), Image.LANCZOS)
    return fx.from_img(im)

def hmotion_blur(a, px):
    px = int(px)
    if px <= 1: return a
    acc = np.zeros_like(a); k = 0
    for sh in range(-px, px+1, max(1,px//6)):
        acc += np.roll(a, sh, axis=1); k += 1
    return acc/k

def ease(t): return t*t*(3-2*t)   # smoothstep

# ----------------------------------------------------------------- bricks
_PAL_BRIGHT = [(214,162,58),(247,209,72),(138,144,150),(236,236,230),
               (24,24,28),(158,52,44),(226,188,150),(52,112,188)]
def make_brick(size, color, seed=0):
    s = size
    im = Image.new("RGBA",(s,s),(0,0,0,0)); dr = ImageDraw.Draw(im)
    r,g,b = color
    dr.rounded_rectangle([1,1,s-2,s-2], radius=max(2,s//8), fill=(r,g,b,255))
    # bevel
    dr.line([(2,2),(s-3,2)], fill=(min(255,r+40),min(255,g+40),min(255,b+40),255), width=max(1,s//14))
    dr.line([(2,2),(2,s-3)], fill=(min(255,r+40),min(255,g+40),min(255,b+40),255), width=max(1,s//14))
    dr.line([(2,s-3),(s-3,s-3)], fill=(max(0,r-45),max(0,g-45),max(0,b-45),255), width=max(1,s//14))
    dr.line([(s-3,2),(s-3,s-3)], fill=(max(0,r-45),max(0,g-45),max(0,b-45),255), width=max(1,s//14))
    # stud
    cx=cy=s/2; rr=s*0.26
    dr.ellipse([cx-rr,cy-rr,cx+rr,cy+rr], fill=(min(255,r+18),min(255,g+18),min(255,b+18),255))
    dr.ellipse([cx-rr*0.8,cy-rr*0.8,cx+rr*0.2,cy+rr*0.2], fill=(min(255,r+70),min(255,g+70),min(255,b+70),200))
    return im

_BRICKS = None
def brick_lib():
    global _BRICKS
    if _BRICKS is None:
        _BRICKS = []
        rng = np.random.default_rng(42)
        for i in range(24):
            sz = int(rng.integers(26, 74))
            col = _PAL_BRIGHT[int(rng.integers(0,len(_PAL_BRIGHT)))]
            _BRICKS.append(make_brick(sz, col, i))
    return _BRICKS

class BrickBurst:
    """Bricks flying outward from a centre over the life of a transition."""
    def __init__(self, count, cx=0.5, cy=0.45, speed=1.0, spread=1.0, seed=0, gravity=1.0):
        rng = np.random.default_rng(seed); lib = brick_lib()
        self.p = []
        for i in range(count):
            ang = rng.uniform(0, 2*math.pi)
            spd = rng.uniform(0.35, 1.15)*speed
            self.p.append(dict(
                br=lib[int(rng.integers(0,len(lib)))],
                x=cx*W + rng.uniform(-40,40), y=cy*H + rng.uniform(-40,40),
                vx=math.cos(ang)*spd*W*0.9*spread, vy=math.sin(ang)*spd*H*0.6 - rng.uniform(0.1,0.5)*H,
                rot=rng.uniform(0,360), vrot=rng.uniform(-360,360),
                g=gravity*H*1.4, t0=rng.uniform(0,0.12)))
    def composite(self, a, t, fade_in=0.0, fade_out=1.0):
        im = fx.to_img(a).convert("RGBA")
        for p in self.p:
            tt = t - p["t0"]
            if tt < 0: continue
            x = p["x"] + p["vx"]*tt
            y = p["y"] + p["vy"]*tt + 0.5*p["g"]*tt*tt
            if y > H+120 or x < -120 or x > W+120: continue
            br = p["br"].rotate(p["rot"]+p["vrot"]*tt, expand=True, resample=Image.BILINEAR)
            a_fade = 1.0
            if t < fade_in: a_fade = t/max(fade_in,1e-3)
            if t > fade_out: a_fade = max(0.0, 1-(t-fade_out)/max(1-fade_out,1e-3))
            if a_fade < 1.0:
                al = br.split()[3].point(lambda v: int(v*a_fade)); br.putalpha(al)
            im.alpha_composite(br, (int(x-br.width/2), int(y-br.height/2)))
        return fx.from_img(im)

# ----------------------------------------------------------------- cine atmosphere
_FOG = None
def _fog_tex():
    global _FOG
    if _FOG is None:
        rng = np.random.default_rng(3)
        small = rng.random((24, 14)).astype(np.float32)
        im = fx.to_img(np.repeat(small[..., None]*255, 3, axis=2)).resize((W+200, H), Image.BICUBIC)
        _FOG = fx.from_img(im)[..., 0] / 255.0
    return _FOG

_MOTES = None
def _motes():
    global _MOTES
    if _MOTES is None:
        rng = np.random.default_rng(7)
        _MOTES = [dict(x=rng.uniform(0,W), y=rng.uniform(0,H),
                       r=rng.uniform(1.5,4.5), sp=rng.uniform(8,34),
                       sway=rng.uniform(10,40), ph=rng.uniform(0,6.28),
                       br=rng.uniform(40,120)) for _ in range(46)]
    return _MOTES

_BARS = None
def cine_bars(a):
    """Soft cinematic letterbox bars (dark charcoal, feathered, faint cyan hairline).
    Hides residual burned-in UI in the movie world and reads as a film trailer."""
    global _BARS
    if _BARS is None:
        th = int(0.122*H); bh = int(0.078*H); feint = 22
        mask = np.ones((H,1),np.float32)
        mask[:th,0] = 0.0; mask[H-bh:,0] = 0.0
        # feather inner edges
        for k in range(feint):
            w = k/feint
            mask[th+k,0] = max(mask[th+k,0], w)
            mask[H-bh-1-k,0] = max(mask[H-bh-1-k,0], w)
        bar_rgb = np.array([8,11,16],np.float32)
        line = np.zeros((H,1),np.float32)
        line[th:th+2,0]=1.0; line[H-bh-2:H-bh,0]=1.0
        _BARS = (mask[...,None], bar_rgb, line[...,None])
    mask, bar_rgb, line = _BARS
    out = a*mask + bar_rgb*(1-mask)
    out = out + line*np.array([70,150,190],np.float32)
    return out

def cine_atmosphere(a, t, seed=0):
    # drifting volumetric fog (subtle), stronger low in frame
    fog = _fog_tex()
    sx = int((t*18) % 200)
    f = fog[:, sx:sx+W]
    falloff = np.clip((fx._YY/H - 0.25)/0.75, 0, 1)[..., None]
    a = a + (f[..., None]-0.5) * 26.0 * falloff
    # floating dust motes with parallax + twinkle
    for m in _motes():
        y = (m["y"] - t*m["sp"]) % H
        x = (m["x"] + math.sin(t*0.6 + m["ph"])*m["sway"]) % W
        tw = 0.55 + 0.45*math.sin(t*2.3 + m["ph"])
        rr = int(m["r"]*3)
        x0,x1 = int(max(0,x-rr)), int(min(W,x+rr))
        y0,y1 = int(max(0,y-rr)), int(min(H,y+rr))
        if x1<=x0 or y1<=y0: continue
        yy,xx = np.mgrid[y0:y1, x0:x1]
        g = np.exp(-(((xx-x)**2+(yy-y)**2)/(2*(m["r"]**2))))[...,None]
        a[y0:y1, x0:x1] += g * m["br"] * tw
    return a

# ----------------------------------------------------------------- look dispatch
def apply_world(a, world, idx, seed):
    if world == "cine": return fx.grade_cinematic(a, seed=seed)
    if world == "game": return fx.grade_game(a, seed=seed)
    if world == "lego": return fx.legoize(a, cell=16)
    return a

# ----------------------------------------------------------------- transitions
def render_trans1(frames, s):
    """MOVIE -> LEGO. Energy pulse, particle disintegration dissolve to LEGO of the
    same frame, plastic-click flash, light glitch. On-screen transformation."""
    n = len(frames); out = []
    burst = BrickBurst(40, cx=0.5, cy=0.45, speed=0.8, seed=1, gravity=0.8)
    for i in range(n):
        p = i/(n-1) if n>1 else 1.0
        a = fx.load(frames[i])
        a = cam_zoom(a, lerp(*s["cam"]["push"], ease(p)))
        cine = fx.grade_cinematic(a.copy(), vig=0.6, tb=0.55, seed=i)
        cine = cine_atmosphere(cine, 8.0+p, seed=3)
        cine = cine_bars(cine)       # bars shatter as reality fractures into LEGO
        lego = fx.legoize(a.copy(), cell=16)
        # dissolve from centre outward, growing with p
        m = fx.dissolve_mask(ease(p)*1.15, scale=70, seed=7, center_bias=0.45, soft=0.14)
        frame = cine*(1-m) + lego*m
        # energy pulse ring expanding
        ring_r = ease(p)*1.3
        ring = np.clip(1 - np.abs(fx._R/1.42 - ring_r)/0.07, 0, 1)[...,None]
        frame = frame + ring*np.array([80,180,255],np.float32)*(1-p*0.6)
        # glowing edge of the dissolve front
        edge = np.clip(1-np.abs(m[...,0]-0.5)/0.18,0,1)[...,None]
        frame = frame + edge*np.array([120,200,255],np.float32)*0.7
        # bricks emerge in 2nd half
        if p>0.35:
            frame = burst.composite(frame, (p-0.35)/0.65, fade_in=0.05, fade_out=0.8)
        # micro glitch + flash around the switch
        if 0.42<p<0.62:
            frame = fx.rgb_split(frame, 6*math.sin((p-0.42)/0.2*math.pi))
            frame = fx.glitch(frame, 0.5*math.sin((p-0.42)/0.2*math.pi), seed=i)
        if 0.5<=p<0.58:
            frame = fx.flash(frame, 0.8*(1-abs(p-0.54)/0.04), color=(220,245,255))
        out.append(frame)
    return out

def render_trans2(frames, s):
    """LEGO -> FORTNITE. Camera push-in, big brick EXPLOSION, de-lego dissolve back to
    real footage of the same pose (match), energy wave, RGB split, cyan impact flash,
    whip. The centrepiece metamorphosis."""
    n = len(frames); out = []
    burst = BrickBurst(85, cx=0.5, cy=0.44, speed=1.25, spread=1.2, seed=5, gravity=1.0)
    for i in range(n):
        p = i/(n-1) if n>1 else 1.0
        a = fx.load(frames[i])
        z = lerp(*s["cam"]["push"], ease(p))
        # quick extra impact punch right at reveal
        if p>0.62: z *= 1 + 0.10*math.sin((p-0.62)/0.38*math.pi)
        a = cam_zoom(a, z)
        lego = fx.legoize(a.copy(), cell=16)
        real = fx.grade_game(a.copy(), vig=0.4, tb=0.3, seed=i)
        # hold LEGO, then explode->real after midpoint
        if p < 0.45:
            frame = lego
            if p>0.3:  # charge shimmer
                frame = frame + (np.clip((p-0.3)/0.15,0,1))*np.array([30,70,110],np.float32)
        else:
            q = (p-0.45)/0.55
            m = fx.dissolve_mask(ease(q)*1.2, scale=60, seed=11, center_bias=0.55, soft=0.16)
            frame = lego*(1-m) + real*m
            # energy shockwave ring
            rr = ease(q)*1.4
            ring = np.clip(1-np.abs(fx._R/1.42-rr)/0.06,0,1)[...,None]
            frame = frame + ring*np.array([120,230,255],np.float32)
        # brick explosion across whole transition (peaks at switch)
        bt = np.clip((p-0.35)/0.65,0,1)
        frame = burst.composite(frame, bt, fade_in=0.04, fade_out=0.82)
        # RGB separation ramps then settles
        split = 10*math.sin(min(p/0.75,1.0)*math.pi)
        frame = fx.rgb_split(frame, split)
        # cyan impact flash at the reveal
        if 0.60<=p<0.72:
            frame = fx.flash(frame, 0.95*(1-abs(p-0.66)/0.06), color=(180,250,255))
        if 0.44<p<0.6:
            frame = fx.glitch(frame, 0.6*math.sin((p-0.44)/0.16*math.pi), seed=i)
        # whip motion-blur settle after reveal
        if p>0.72:
            frame = hmotion_blur(frame, 26*(1-(p-0.72)/0.28))
        out.append(frame)
    return out

def lerp(a,b,t): return a+(b-a)*t

# ----------------------------------------------------------------- world render
def render_world(frames, s, gidx0):
    n=len(frames); out=[]; acc=s.get("accents",{}); cam=s.get("cam",{})
    bricks=None
    if acc.get("bricks"):
        bricks=BrickBurst(acc["bricks"], cx=0.5,cy=0.3, speed=0.3, spread=0.6,
                          seed=gidx0, gravity=0.5)
    for i in range(n):
        p = i/(n-1) if n>1 else 1.0
        a = fx.load(frames[i])
        # camera
        if "impact" in cam:
            z0,z1=cam["impact"]; z=lerp(z0,z1,ease(min(p/0.4,1.0)))
        else:
            z=lerp(*cam.get("push",(1.0,1.0)), ease(p))
        dx=dy=0.0
        if cam.get("shake"):
            rng=np.random.default_rng(gidx0*997+i)
            dx=rng.uniform(-1,1)*cam["shake"]; dy=rng.uniform(-1,1)*cam["shake"]
        a=cam_zoom(a,z,dx,dy)
        frame=apply_world(a, s["world"], i, seed=gidx0*13+i)
        if s["world"]=="cine":
            frame=cine_atmosphere(frame, (gidx0+i)/FPS, seed=gidx0)
            frame=cine_bars(frame)
        # whip-in (slide + blur reveal)
        if acc.get("whip_in") and p < acc["whip_in"]:
            q=p/acc["whip_in"]
            frame=hmotion_blur(frame, 30*(1-q))
            frame=cam_zoom(frame, 1.0, dx=(1-q)*0.22)
        # flash / fade accents
        if acc.get("flash_in") and p < 0.12:
            frame=fx.flash(frame, acc["flash_in"]*(1-p/0.12), color=(255,255,255))
        if acc.get("fade_in") and p < acc["fade_in"]/s["outdur"]:
            frame=frame*(p/(acc["fade_in"]/s["outdur"]))
        if acc.get("fade_out"):
            fo=acc["fade_out"]/s["outdur"]
            if p>1-fo: frame=frame*(1-(p-(1-fo))/fo)
        if bricks is not None:
            frame=bricks.composite(frame, p, fade_in=0.1, fade_out=0.7)
        if acc.get("title"):
            frame=draw_title(frame, p)
        out.append(frame)
    return out

# ----------------------------------------------------------------- title
_TITLE_IMG=None
def title_img():
    global _TITLE_IMG
    if _TITLE_IMG is None:
        from PIL import ImageFont
        im=Image.new("RGBA",(W,400),(0,0,0,0)); dr=ImageDraw.Draw(im)
        try:
            f1=ImageFont.truetype(os.path.join(ROOT,"..","realistic_scrims_trailer","assets","fonts","Anton-Regular.ttf"),150)
            f2=ImageFont.truetype("/usr/share/fonts/opentype/inter/InterDisplay-Black.otf",46)
        except Exception:
            f1=ImageFont.load_default(); f2=ImageFont.load_default()
        def ctext(y,t,f,fill):
            w=dr.textlength(t,font=f); dr.text(((W-w)/2+3,y+3),t,font=f,fill=(0,0,0,170))
            dr.text(((W-w)/2,y),t,font=f,fill=fill)
        ctext(10,"MULTIVERSE",f1,(255,255,255,255))
        ctext(250,"ONE LEGEND  /  THREE WORLDS",f2,(120,220,255,255))
        _TITLE_IMG=im
    return _TITLE_IMG
MAP_CODE_GROUPS = ["0674", "0917", "0977"]
_ECFONTS=None
def _ec_fonts():
    global _ECFONTS
    if _ECFONTS is None:
        from PIL import ImageFont
        anton=os.path.join(ROOT,"..","realistic_scrims_trailer","assets","fonts","Anton-Regular.ttf")
        try:
            _ECFONTS=dict(
                code=ImageFont.truetype(anton,132),
                label=ImageFont.truetype("/usr/share/fonts/opentype/inter/InterDisplay-Black.otf",50),
                cta=ImageFont.truetype(anton,66),
                tag=ImageFont.truetype("/usr/share/fonts/opentype/inter/InterDisplay-Bold.otf",34))
        except Exception:
            from PIL import ImageFont as F
            d=F.load_default(); _ECFONTS=dict(code=d,label=d,cta=d,tag=d)
    return _ECFONTS

def draw_endcard(a, p, t_local):
    """Dim cinematic backdrop + animated map-code card. Code groups light up as
    the voice-over reads them."""
    f=_ec_fonts()
    im=fx.to_img(a).convert("RGBA")
    dr=ImageDraw.Draw(im)
    cyan=(120,225,255)
    def center(txt,font,y,fill,a_=255,glow=0,scale=1.0,shadow=True):
        w=dr.textlength(txt,font=font)
        x=(W-w)/2
        if glow>0:
            gl=Image.new("RGBA",(W,H),(0,0,0,0)); gd=ImageDraw.Draw(gl)
            gd.text((x,y),txt,font=font,fill=(cyan[0],cyan[1],cyan[2],int(190*a_/255)))
            gl=gl.filter(ImageFilter.GaussianBlur(glow))
            im.alpha_composite(gl)
        if shadow:
            dr.text((x+3,y+4),txt,font=font,fill=(0,0,0,int(170*a_/255)))
        dr.text((x,y),txt,font=font,fill=(fill[0],fill[1],fill[2],int(a_)))
        return x,w
    # REALISTIC SCRIMS label
    la=ease(np.clip((p-0.05)/0.14,0,1))
    center("REALISTIC SCRIMS", f["label"], H*0.30, (255,255,255), a_=255*la)
    # MAP CODE tag
    center("MAP CODE", f["tag"], H*0.405, cyan, a_=220*ease(np.clip((p-0.12)/0.12,0,1)))
    # Big code with per-group highlight synced to VO (l9 reads groups ~t 1.9/3.2/4.5)
    ca=ease(np.clip((p-0.14)/0.16,0,1))
    if ca>0:
        yc=H*0.455
        groups=MAP_CODE_GROUPS
        # measure total width (groups + dashes)
        dash=" - "
        parts=[]
        for i,g in enumerate(groups):
            parts.append((g,True,i));
            if i<len(groups)-1: parts.append((dash,False,-1))
        tw=sum(dr.textlength(s,font=f["code"]) for s,_,_ in parts)
        x=(W-tw)/2
        windows=[(1.8,3.1),(3.3,4.6),(4.8,6.1)]  # endcard-local seconds per group (synced to l9 VO)
        for s,isg,gi in parts:
            w=dr.textlength(s,font=f["code"])
            if isg:
                lo,hi=windows[gi]; active=lo<=t_local<=hi
                col=(255,240,170) if active else (255,255,255)
                if active:  # glow pop on the group being spoken
                    gl=Image.new("RGBA",(W,H),(0,0,0,0)); gd=ImageDraw.Draw(gl)
                    gd.text((x,yc),s,font=f["code"],fill=(255,220,120,230))
                    im.alpha_composite(gl.filter(ImageFilter.GaussianBlur(16)))
                dr.text((x+3,yc+5),s,font=f["code"],fill=(0,0,0,int(170*ca)))
                dr.text((x,yc),s,font=f["code"],fill=(col[0],col[1],col[2],int(255*ca)))
            else:
                dr.text((x,yc),s,font=f["code"],fill=(cyan[0],cyan[1],cyan[2],int(220*ca)))
            x+=w
        # underline accent bar
        bw=tw*ease(np.clip((p-0.18)/0.2,0,1))
        dr.rectangle([(W-bw)/2, yc+150, (W+bw)/2, yc+156], fill=(cyan[0],cyan[1],cyan[2],int(230*ca)))
    # DROP IN / PLAY NOW CTA pill
    cta_a=ease(np.clip((p-0.6)/0.14,0,1))
    if cta_a>0:
        txt="DROP IN  -  PLAY NOW"
        w=dr.textlength(txt,font=f["cta"]); x=(W-w)/2; y=H*0.60
        pulse=0.5+0.5*math.sin(t_local*4.5)
        dr.rounded_rectangle([x-46,y-14,x+w+46,y+92], radius=48,
            outline=(cyan[0],cyan[1],cyan[2],int(255*cta_a)), width=4,
            fill=(10,16,24,int(150*cta_a)))
        center("DROP IN  -  PLAY NOW", f["cta"], y, (255,255,255), a_=255*cta_a, glow=10*pulse)
    return fx.from_img(im)

def render_endcard(frames, s, gidx0):
    n=len(frames); out=[]; acc=s.get("accents",{}); cam=s.get("cam",{})
    base=fx.load(frames[0])
    base=cam_zoom(base, 1.04)
    # dim, blurred cinematic backdrop
    bg=fx.grade_cinematic(base.copy(), vig=0.8, tb=0.7, bloom=0.3, seed=1)
    bg=fx.from_img(fx.to_img(bg).filter(ImageFilter.GaussianBlur(16)))*0.55
    for i in range(n):
        p=i/(n-1) if n>1 else 1.0; t=i/FPS
        z=lerp(*cam.get("push",(1.0,1.0)), ease(p))
        frame=cam_zoom(bg.copy(), z)
        frame=cine_atmosphere(frame, 40+t, seed=9)
        frame=cine_bars(frame)
        frame=draw_endcard(frame, p, t)
        fo=acc.get("fade_out",0.0)/s["outdur"]
        if fo and p>1-fo: frame=frame*(1-(p-(1-fo))/fo)
        out.append(frame)
    return out

def draw_title(a,p):
    ti=title_img()
    al=ease(np.clip((p-0.2)/0.3,0,1))*np.clip(1-(p-0.85)/0.15,0,1)
    if al<=0: return a
    im=fx.to_img(a).convert("RGBA")
    t2=ti.copy(); t2.putalpha(t2.split()[3].point(lambda v:int(v*al)))
    y=int(H*0.60 - 20*ease(np.clip((p-0.2)/0.3,0,1)))
    im.alpha_composite(t2,(0,y))
    return fx.from_img(im)

# ----------------------------------------------------------------- video assembly
CLIPS = os.path.join(WORK, "clips")

def render_segment(s, gidx):
    frames=seg_frames(s)
    if s["world"]=="trans1": outs=render_trans1(frames,s)
    elif s["world"]=="trans2": outs=render_trans2(frames,s)
    elif s["world"]=="endcard": outs=render_endcard(frames,s,gidx)
    else: outs=render_world(frames,s,gidx)
    return outs

def video(force=None):
    force = force or set()
    os.makedirs(CLIPS, exist_ok=True)
    gidx=0
    for s in SEGMENTS:
        clip=os.path.join(CLIPS, s["name"]+".mp4")
        n=max(1, round(s["outdur"]*FPS))
        if os.path.exists(clip) and s["name"] not in force and "ALL" not in force:
            print(f'    {s["name"]:11s} cached'); gidx+=n; continue
        ff=subprocess.Popen(["ffmpeg","-nostdin","-v","error","-y","-f","rawvideo","-pix_fmt","rgb24",
            "-s",f"{W}x{H}","-r",str(FPS),"-i","pipe:0","-an","-c:v","libx264","-preset","medium",
            "-crf","18","-pix_fmt","yuv420p",clip], stdin=subprocess.PIPE)
        outs=render_segment(s, gidx)
        for fr in outs: ff.stdin.write(fx.clamp8(fr).tobytes())
        ff.stdin.close(); ff.wait(); gidx+=len(outs)
        print(f'    {s["name"]:11s} -> {len(outs):3d} frames  rendered')
    # concat list
    with open(os.path.join(CLIPS,"list.txt"),"w") as fp:
        for s in SEGMENTS: fp.write(f"file '{s['name']}.mp4'\n")
    print("  per-segment clips ready")

# ----------------------------------------------------------------- audio build
def seg_start_times():
    t=0.0; d={}
    for s in SEGMENTS:
        d[s["name"]]=t; t+=round(s["outdur"]*FPS)/FPS
    return d, t

# voice-over script: (line file, segment, offset-in-seg) ; am_fenrir65/puck35 blend
VO_PLAN = [
  ("l1_onelegend","w1_hook",0.6),
  ("l2_loaded",   "w1_weapon",0.9),
  ("l3_rebuilt",  "w2_walk",0.5),
  ("l4_newworld", "w2_gun",0.15),
  ("l5_getsreal", "t2",1.55),            # lands on the Fortnite reveal
  ("l6_dominate", "g_rocket",0.0),
  ("l7_prove",    "g_elim2",0.2),
  ("l8_scrims",   "endcard",0.45),
  ("l9_code",     "endcard",1.20),       # groups spoken ~1.8/3.3/4.8 local
  ("l10_dropin",  "endcard",6.35),
]

def _load_vo_48k(name):
    import soundfile as sf
    a,sr=sf.read(os.path.join(VO_DIR,name+".wav"))
    if a.ndim>1: a=a.mean(1)
    a=a.astype(np.float32)
    if sr!=A.SR:  # linear resample to 48k
        x=np.arange(len(a)); xi=np.linspace(0,len(a)-1,int(len(a)*A.SR/sr))
        a=np.interp(xi,x,a).astype(np.float32)
    # light speech polish: gentle high-pass-ish + soft limit
    a=a-np.convolve(a,np.ones(400)/400,mode="same")*0.6
    pk=np.max(np.abs(a))+1e-6
    return (a/pk*0.85).astype(np.float32)

def build_audio():
    st,total=seg_start_times()
    end=total+0.4
    # ---- voice-over bus (stereo, centre) ----
    vo=np.zeros((int(end*A.SR),2),np.float32)
    vo_env=np.zeros(int(end*A.SR),np.float32)   # 1 where VO active (for ducking)
    for name,segname,off in VO_PLAN:
        if segname not in st: continue
        a=_load_vo_48k(name)
        s0=int((st[segname]+off)*A.SR); e0=min(len(vo),s0+len(a))
        vo[s0:e0,0]+=a[:e0-s0]; vo[s0:e0,1]+=a[:e0-s0]
        # ducking envelope with 120ms attack / 300ms release
        env=np.zeros(e0-s0+1,np.float32); env[:]=1.0
        vo_env[s0:e0]=np.maximum(vo_env[s0:e0], env[:e0-s0])
    # smooth the duck envelope (attack/release)
    k=np.ones(int(0.18*A.SR))/int(0.18*A.SR)
    vo_env=np.convolve(vo_env,k,mode="same")
    vo_env=np.clip(vo_env,0,1)

    tl=A.Timeline(end)
    def at(name,off=0.0): return st[name]+off
    # --- World 1: cinematic bed - low drone impacts + sparse metallic + air
    tl.place(A.impact(1.4,80,30,seed=20,body=1.2), at("w1_hook",0.0), 0.5)
    tl.place(A.whoosh(0.9,seed=21,bright=800), at("w1_hook",0.1), 0.35, pan=-0.3)
    tl.place(A.metallic(0.8,420,seed=22), at("w1_weapon",0.0), 0.4, pan=0.2)
    tl.place(A.impact(1.0,110,34,seed=23), at("w1_weapon",0.0), 0.5)
    tl.place(A.whoosh(0.7,seed=24,bright=1400), at("w1_walk",0.2), 0.4, pan=0.3)
    tl.place(A.riser(1.5,160,1600,seed=25), at("w1_walk",0.4), 0.55)   # into transform
    # --- Transition 1 : movie -> lego (riser climax + plastic clicks + impact)
    tl.place(A.reverse_sweep(0.6,seed=26), at("t1",-0.1), 0.5)
    tl.place(A.plastic_clicks(0.9,18,seed=27), at("t1",0.25), 0.8)
    tl.place(A.transform_hit(seed=28,big=1.0), at("t1",0.55-0.5), 1.0)  # hit lands at switch
    tl.place(A.subdrop(1.0,80,26,seed=29), at("t1",0.55), 0.8)
    # --- World 2: lego - bouncy plastic clicks, brick taps, light whooshes
    tl.place(A.brick_scatter(1.2,20,seed=30), at("w2_walk",0.1), 0.45)
    tl.place(A.plastic_clicks(0.8,10,seed=31), at("w2_slide",0.0), 0.4)
    tl.place(A.whoosh(0.5,seed=32,bright=1800), at("w2_slide",0.2), 0.35, pan=-0.4)
    tl.place(A.plastic_clicks(0.6,8,seed=33), at("w2_gun",0.0), 0.4, pan=0.3)
    tl.place(A.brick_scatter(1.0,16,seed=34), at("w2_elim",0.1), 0.5)
    tl.place(A.riser(1.6,200,2200,seed=35), at("w2_elim",0.2), 0.6)    # into transform 2
    # --- Transition 2 : lego -> fortnite (accel bricks, dropout, huge hit)
    tl.place(A.brick_scatter(1.1,30,seed=36), at("t2",0.1), 0.8)
    tl.place(A.reverse_sweep(0.8,seed=37), at("t2",0.2), 0.6)
    tl.place(A.transform_hit(seed=38,big=1.4), at("t2",0.85-0.5), 1.0)  # lands at reveal ~0.66*1.9
    tl.place(A.subdrop(1.3,95,24,seed=39), at("t2",0.9), 0.95)
    tl.place(A.whoosh(0.6,seed=40,bright=2400), at("t2",1.1), 0.5, pan=0.4)
    # --- World 3: gameplay - impacts/whooshes synced to cuts & key moments
    gcuts=[("g_elim1",0.0,0.7),("g_fight",0.0,0.5),("g_rocket",0.0,0.8),
           ("g_freeze",0.0,0.9),("g_build1",0.0,0.5),("g_build2",0.0,0.5),
           ("g_boom",0.0,0.7),("g_elim2",0.0,0.85),("g_victory",0.0,1.0)]
    for i,(nm,off,g) in enumerate(gcuts):
        tl.place(A.impact(0.8,120,36,seed=50+i,body=g), at(nm,off), 0.5*g+0.3)
        tl.place(A.whoosh(0.4,seed=70+i,bright=1600+i*120), at(nm,off-0.05), 0.3, pan=(-1)**i*0.35)
    tl.place(A.metallic(0.5,900,seed=90), at("g_rocket",0.05), 0.4)       # scope
    tl.place(A.subdrop(1.0,90,28,seed=91), at("g_elim2",0.1), 0.7)        # speed-ramp elim
    tl.place(A.transform_hit(seed=92,big=1.3), at("g_victory",0.0), 0.9)  # victory payoff
    tl.place(A.riser(0.8,300,1800,seed=93), at("g_victory",-0.5), 0.4)
    # --- Outro: clean low impact + long tail
    tl.place(A.impact(1.6,70,26,seed=95,body=1.3), at("outro",0.1), 0.6)
    tl.place(A.metallic(1.0,520,seed=96), at("outro",0.2), 0.3)
    # --- End card: riser into code, soft tick per code group, final CTA hit
    tl.place(A.riser(1.2,180,1400,seed=97), at("endcard",0.1), 0.35)
    tl.place(A.impact(1.0,120,34,seed=98,body=0.9), at("endcard",0.7), 0.5)  # code reveal
    for i,gt in enumerate([1.9,3.4,4.9]):
        tl.place(A.click(0.06,1600+i*200,seed=100+i), at("endcard",gt), 0.4)
        tl.place(A.whoosh(0.3,seed=110+i,bright=1800), at("endcard",gt-0.1), 0.2, pan=(-1)**i*0.3)
    tl.place(A.transform_hit(seed=120,big=1.1), at("endcard",6.2), 0.8)      # on "Drop in"

    # ---- write SFX-only stem, VO stem, and the ducked mix (NO MUSIC) ----
    sfx=tl.buf.copy()
    duck=(1.0 - 0.72*vo_env)[:len(sfx),None]        # pull SFX ~ -11 dB under VO
    mix=sfx*duck + vo[:len(sfx)]*1.18
    # gentle bus reverb on the whole thing + soft limit
    mix=A._reverb(mix, decay=0.3, mix=0.08)
    pk=np.max(np.abs(mix))+1e-6
    mix=np.tanh(mix/max(pk,1.0)*1.05)*0.95
    def _write(buf,path):
        raw=buf.astype('<f4').tobytes()
        subprocess.run(["ffmpeg","-nostdin","-v","error","-y","-f","f32le","-ar",str(A.SR),
            "-ac","2","-i","pipe:0","-c:a","pcm_s24le",path], input=raw, check=True)
    _write(sfx, OUT_SFX); _write(vo[:len(sfx)], OUT_VO); _write(mix, OUT_MIX)
    print(f"  SFX {OUT_SFX} | VO {OUT_VO} | MIX {OUT_MIX}  ({end:.2f}s)")

# ----------------------------------------------------------------- mux
def mux():
    # concat per-segment clips (re-encode to final size) + synced SFX (loudnorm, no music)
    listf=os.path.join(CLIPS,"list.txt")
    subprocess.run(["ffmpeg","-nostdin","-v","error","-y","-f","concat","-safe","0","-i",listf,
        "-i",OUT_MIX,
        "-filter_complex","[1:a]loudnorm=I=-14:TP=-1.5:LRA=11[a]",
        "-map","0:v","-map","[a]",
        "-c:v","libx264","-preset","slow","-crf","22","-maxrate","12M","-bufsize","20M",
        "-pix_fmt","yuv420p","-c:a","aac","-b:a","192k","-ar","48000",
        "-shortest","-movflags","+faststart",OUT_FINAL], check=True)
    print(f"  {OUT_FINAL}")

if __name__=="__main__":
    args=sys.argv[1:]
    stage = args[0] if args else "all"
    force=set()
    for a in args[1:]:
        if a.startswith("force="): force=set(a.split("=",1)[1].split(","))
    os.makedirs(os.path.join(ROOT,"assets"), exist_ok=True)
    if stage in ("extract","all"): print("[extract]"); extract()
    if stage in ("video","all"):   print("[video]");   video(force)
    if stage in ("audio","all"):   print("[audio]");   build_audio()
    if stage in ("mux","all"):     print("[mux]");     mux()
    print("done:", stage)
