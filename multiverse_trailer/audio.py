"""
audio.py - synthesized cinematic SFX soundscape (numpy). NO MUSIC.
Deep impacts, sub-bass hits, risers, reverse sweeps, whooshes, metallic accents,
LEGO plastic clicks, brick scatter, transformation hits. All original, procedural.
Writes a stereo 48 kHz float WAV via ffmpeg (f32le pipe) -> pcm_s24le.
"""
import numpy as np
import subprocess

SR = 48000

def _env(n, a=0.005, d=0.1, s=0.0, r=0.2, sus=1.0):
    a_n, d_n, r_n = int(a*SR), int(d*SR), int(r*SR)
    s_n = max(0, n - a_n - d_n - r_n)
    att = np.linspace(0, 1, max(1, a_n))
    dec = np.linspace(1, sus, max(1, d_n))
    sus_seg = np.full(max(0, s_n), sus)
    rel = np.linspace(sus, 0, max(1, r_n))
    e = np.concatenate([att, dec, sus_seg, rel])
    return e[:n] if len(e) >= n else np.pad(e, (0, n-len(e)))

def _noise(n, seed):
    return np.random.default_rng(seed).standard_normal(n).astype(np.float32)

def _lp(x, cutoff):
    # simple one-pole lowpass
    a = np.exp(-2*np.pi*cutoff/SR)
    y = np.zeros_like(x); prev = 0.0
    # vectorised-ish via lfilter-like loop in chunks (cheap for short sfx)
    for i in range(len(x)):
        prev = (1-a)*x[i] + a*prev
        y[i] = prev
    return y

def _hp(x, cutoff):
    return x - _lp(x, cutoff)

def impact(dur=0.9, f0=120, f1=38, seed=1, click=1.0, body=1.0):
    n = int(dur*SR); t = np.arange(n)/SR
    # pitch-down sine (sub)
    f = f1 + (f0-f1)*np.exp(-t*7)
    phase = 2*np.pi*np.cumsum(f)/SR
    sub = np.sin(phase) * _env(n, 0.001, 0.05, 0, dur*0.9, 1.0) * body
    sub = np.tanh(sub*1.35)                                   # saturate for punch/harmonics
    sub2 = np.sin(phase*0.5) * _env(n, 0.002, 0.08, 0, dur*0.9, 1.0) * body * 0.65  # octave down weight
    # layered transient: bright tick + mid crack
    nz = _noise(n, seed)
    tick = nz * np.exp(-t*240) * 0.8 * click                  # snap
    crack = (nz - _ma(nz, 24)) * np.exp(-t*60) * 0.5 * click  # bandpassed body of the hit
    # decaying filtered-noise tail for weight/room
    tail = _ma(_noise(n, seed+1), 60) * np.exp(-t*3.2) * 0.45 * body
    x = sub*1.0 + sub2 + tick + crack + tail
    return x.astype(np.float32)

def shimmer(dur=0.8, seed=12, f0=2200, f1=6500):
    """Bright rising sparkle - magical transformation sweetener."""
    n=int(dur*SR); t=np.arange(n)/SR
    rng=np.random.default_rng(seed); x=np.zeros(n,np.float32)
    for k in range(7):
        f=f0*(f1/f0)**(t/dur)*rng.uniform(0.85,1.25)
        ph=2*np.pi*np.cumsum(f)/SR
        x+=np.sin(ph)*np.exp(-((t-rng.uniform(0,dur*0.7))**2)/(2*0.08**2))/ (k+1)
    x*=(t/dur)**0.5
    return (x*0.25).astype(np.float32)

def _ma(x, k):  # vectorized moving-average lowpass
    k = max(1, int(k)); c = np.cumsum(np.insert(x, 0, 0.0))
    y = (c[k:] - c[:-k]) / k
    return np.pad(y, (k//2, len(x)-len(y)-k//2), mode="edge")

def air_bed(dur, seed=0):
    """Continuous cinematic ambience (NOT music): band-limited low rumble + airy
    hiss at very low level, slowly evolving - so the track is never dead-silent."""
    n = int(dur*SR); t = np.arange(n)/SR
    nz = _noise(n, seed)
    rumble = _ma(nz, 340)                       # ~sub rumble
    rumble *= 0.55 + 0.45*np.sin(2*np.pi*0.045*t)
    nz2 = _noise(n, seed+1)
    air = nz2 - _ma(nz2, 16)                     # airy top (highpass)
    air *= 0.5 + 0.5*np.sin(2*np.pi*0.07*t + 1.3)
    return (rumble*0.5 + air*0.03).astype(np.float32)

def compress(x, thresh=0.3, ratio=3.5, makeup=1.5):
    """Simple soft downward compressor for VO presence."""
    a = np.abs(x); out = x.copy()
    over = a > thresh
    out[over] = np.sign(x[over]) * (thresh + (a[over]-thresh)/ratio)
    return (out*makeup).astype(np.float32)

def subdrop(dur=1.2, f0=90, f1=28, seed=2):
    n=int(dur*SR); t=np.arange(n)/SR
    f=f1+(f0-f1)*np.exp(-t*4)
    ph=2*np.pi*np.cumsum(f)/SR
    x=np.sin(ph)*_env(n,0.004,0.2,0,dur,0.8)
    return (x*0.9).astype(np.float32)

def riser(dur=1.4, f0=180, f1=1800, seed=3, noisy=0.6):
    n=int(dur*SR); t=np.arange(n)/SR
    f=f0*(f1/f0)**(t/dur)
    ph=2*np.pi*np.cumsum(f)/SR
    tone=np.sin(ph)
    nz2=_noise(n,seed)
    nz=(nz2 - _ma(nz2,24))*noisy                              # airy rising noise
    amp=(t/dur)**1.8
    x=(tone*0.45+nz)*amp
    # accelerating tick train (builds tension into the hit)
    rng=np.random.default_rng(seed+5)
    ticks=np.zeros(n,np.float32); tt=0.0
    while tt<dur:
        s=int(tt*SR)
        if s<n:
            c=click(0.03,rng.uniform(1800,3400),seed+int(tt*50)+1,sharp=200)
            e=min(n,s+len(c)); ticks[s:e]+=c[:e-s]*(0.3+0.7*(tt/dur))
        tt+=0.16*(1-0.82*(tt/dur))                            # interval shrinks -> accelerando
    return ((x*0.7 + ticks*0.65)*1.45).astype(np.float32)

def reverse_sweep(dur=1.0, seed=4):
    n=int(dur*SR); t=np.arange(n)/SR
    nz=_hp(_noise(n,seed),500)
    amp=((dur-t)/dur)**0.4 * (t/dur < 0.98)
    # build then cut
    amp=(t/dur)**1.5
    x=nz*amp
    x=x[::-1].copy()
    return (x*0.6).astype(np.float32)

def whoosh(dur=0.5, seed=5, pan=0.0, bright=1200):
    n=int(dur*SR); t=np.arange(n)/SR
    nz=_noise(n,seed)
    k=max(2,int(SR/bright))
    band=_ma(nz,k) - _ma(nz, k*5)                            # band-limited air
    amp=np.sin(np.pi*np.clip(t/dur,0,1))**1.4                # smooth swell bell
    swell=0.6+0.4*(t/dur)                                    # brightens as it passes
    x=band*amp*swell*9.0
    return (x*0.7).astype(np.float32)

def click(dur=0.05, f=2200, seed=6, sharp=120):
    n=int(dur*SR); t=np.arange(n)/SR
    tone=np.sin(2*np.pi*f*t)*np.exp(-t*sharp)
    ck=_noise(n,seed)*np.exp(-t*220)*0.5
    return ((tone*0.6+ck)*0.6).astype(np.float32)

def plastic_clicks(dur=0.8, count=14, seed=7, f_lo=1400, f_hi=3200):
    rng=np.random.default_rng(seed)
    n=int(dur*SR); out=np.zeros(n,np.float32)
    for i in range(count):
        at=rng.uniform(0,dur-0.06)
        f=rng.uniform(f_lo,f_hi)
        c=click(0.05,f,seed+i+1,sharp=rng.uniform(90,180))
        s=int(at*SR); e=min(n,s+len(c))
        out[s:e]+=c[:e-s]*rng.uniform(0.5,1.0)
    return (out*0.7).astype(np.float32)

def brick_scatter(dur=1.0, count=26, seed=8):
    # accelerating / decelerating plastic clacks like bricks tumbling
    rng=np.random.default_rng(seed)
    n=int(dur*SR); out=np.zeros(n,np.float32)
    times=np.sort(rng.power(1.6,count))*dur
    for i,at in enumerate(times):
        f=rng.uniform(900,2600)
        c=click(0.055,f,seed+i+10,sharp=rng.uniform(80,160))
        s=int(at*SR); e=min(n,s+len(c))
        out[s:e]+=c[:e-s]*rng.uniform(0.4,0.9)
    return (out*0.7).astype(np.float32)

def metallic(dur=0.6, f=520, seed=9):
    n=int(dur*SR); t=np.arange(n)/SR
    partials=[1,2.76,5.4,8.9]
    x=np.zeros(n,np.float32)
    for k,p in enumerate(partials):
        x+=np.sin(2*np.pi*f*p*t)*np.exp(-t*(4+k*2))/(k+1)
    return (x*_env(n,0.001,0.1,0,dur,0.5)*0.4).astype(np.float32)

def transform_hit(seed=11, big=1.0):
    """Designed cinematic transformation hit: reverse swell -> deep boom +
    sub drop -> metallic ring + rising shimmer tail."""
    total=int(2.0*SR); out=np.zeros(total,np.float32)
    def add(sig, at):
        s=max(0,int(at*SR)); e=min(total,s+len(sig)); out[s:e]+=sig[:e-s]
    add(reverse_sweep(0.6,seed)*1.1, 0.0)          # suck-in before the hit (lands at ~0.6)
    add(impact(1.3, 170, 32, seed+1, click=1.3, body=1.4*big), 0.55)   # the boom
    add(subdrop(1.1, 95, 24, seed+2)*big, 0.57)    # sub weight under it
    add(metallic(0.8, 680, seed+3)*0.8, 0.60)      # metallic ring
    add(shimmer(1.0, seed+4, 2400, 7200)*1.1, 0.62)# rising sparkle tail
    return out

# ---------------- stereo placement + simple reverb tail ----------------
def _stereo(mono, pan=0.0):
    l=mono*np.sqrt((1-pan)/2+0.5*(pan<0)*0);
    lg=np.sqrt(0.5*(1-pan)); rg=np.sqrt(0.5*(1+pan))
    return np.stack([mono*lg, mono*rg],1)

def _fftconv(x, h):
    n=len(x)+len(h)-1
    N=1<<int(np.ceil(np.log2(n)))
    y=np.fft.irfft(np.fft.rfft(x,N)*np.fft.rfft(h,N),N)[:len(x)]
    return y.astype(np.float32)

def _make_ir(decay, seed):
    """Diffuse decaying reverb impulse response (early reflections + dark tail)."""
    L=max(8,int(decay*SR)); t=np.arange(L)/SR
    rng=np.random.default_rng(seed)
    tail=rng.standard_normal(L).astype(np.float32)*np.exp(-t/(decay*0.42))
    tail=np.convolve(tail,np.ones(9)/9,mode="same")          # darken (lowpass)
    for d,g in [(0.009,0.55),(0.017,0.45),(0.027,0.36),(0.041,0.28),(0.059,0.2),(0.083,0.14)]:
        i=int(d*SR)
        if i<L: tail[i]+=g
    return tail

def _reverb(stereo, decay=0.3, mix=0.18):
    """Lush algorithmic reverb via FFT convolution with a generated IR (stereo-decorrelated)."""
    irL=_make_ir(decay,101); irR=_make_ir(decay,202)
    wet=np.stack([_fftconv(stereo[:,0],irL), _fftconv(stereo[:,1],irR)],1)
    wp=np.max(np.abs(wet))+1e-6; sp=np.max(np.abs(stereo))+1e-6
    wet*= sp/wp                                               # match level
    return stereo*(1-mix)+wet*mix

class Timeline:
    def __init__(self, dur):
        self.n=int(dur*SR)
        self.buf=np.zeros((self.n,2),np.float32)
    def place(self, sig, at, gain=1.0, pan=0.0):
        if sig.ndim==1:
            st=_stereo(sig,pan)
        else:
            st=sig
        s=int(at*SR); e=min(self.n, s+len(st))
        if s<self.n:
            self.buf[s:e]+=st[:e-s]*gain
    def render(self, path, reverb=True):
        out=self.buf
        if reverb:
            out=_reverb(out, decay=0.35, mix=0.14)
        # soft clip / limiter
        peak=np.max(np.abs(out))+1e-6
        out=out/max(peak,1.0)
        out=np.tanh(out*1.1)*0.92
        raw=out.astype('<f4').tobytes()
        cmd=["ffmpeg","-nostdin","-v","error","-y","-f","f32le","-ar",str(SR),"-ac","2",
             "-i","pipe:0","-c:a","pcm_s24le",path]
        p=subprocess.run(cmd, input=raw)
        return path

if __name__=="__main__":
    # quick self-test of a few sfx
    tl=Timeline(3.0)
    tl.place(riser(1.2),0.0,0.6)
    tl.place(transform_hit(big=1.2),1.0,1.0)
    tl.place(brick_scatter(1.0),1.2,0.7)
    tl.render("proto/sfx_test.wav")
    print("wrote proto/sfx_test.wav")
