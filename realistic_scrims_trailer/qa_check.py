#!/usr/bin/env python3
"""Automated QA for realistic_scrims_trailer.mp4 -> prints a report (used for quality_check.txt).

    python3 qa_check.py realistic_scrims_trailer.mp4 [--sheet-dir qa]
"""
import argparse
import json
import re
import subprocess
from pathlib import Path


def sh(cmd):
    r = subprocess.run(cmd, capture_output=True, text=True)
    return r.stdout + r.stderr


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("video")
    ap.add_argument("--sheet-dir", default="qa")
    a = ap.parse_args()
    v = a.video
    out = []
    p = json.loads(sh(["ffprobe", "-v", "error", "-show_streams", "-show_format", "-of", "json", v]))
    vs = next(s for s in p["streams"] if s["codec_type"] == "video")
    aus = [s for s in p["streams"] if s["codec_type"] == "audio"]
    out.append("== STREAMS")
    out.append(f"container      : {p['format']['format_name']}  size {int(p['format']['size'])/1e6:.1f} MB  "
               f"overall {int(p['format']['bit_rate'])/1e6:.1f} Mb/s")
    out.append(f"duration       : {float(p['format']['duration']):.3f} s")
    out.append(f"video          : {vs['codec_name']} ({vs.get('profile')}) {vs['width']}x{vs['height']} "
               f"SAR {vs.get('sample_aspect_ratio','1:1')} DAR {vs.get('display_aspect_ratio')} "
               f"{vs['r_frame_rate']} fps (avg {vs['avg_frame_rate']}) {vs['pix_fmt']} "
               f"{vs.get('color_space')}/{vs.get('color_range')} frames {vs.get('nb_frames')}")
    for s in aus:
        out.append(f"audio          : {s['codec_name']} ({s.get('profile')}) {s['sample_rate']} Hz "
                   f"{s['channels']} ch {int(s.get('bit_rate', 0))/1000:.0f} kb/s dur {float(s['duration']):.3f} s")
    if not aus:
        out.append("audio          : MISSING")

    out.append("\n== BLACK FRAMES (blackdetect d>=0.03s, pix_th 0.10)")
    bl = re.findall(r"black_start:([\d.]+) black_end:([\d.]+)", sh(
        ["ffmpeg", "-hide_banner", "-i", v, "-vf", "blackdetect=d=0.03:pix_th=0.10", "-an", "-f", "null", "-"]))
    out += [f"  {float(s):.3f} - {float(e):.3f}" for s, e in bl] or ["  none"]

    out.append("\n== FROZEN PICTURE (freezedetect n=0.002 d=0.25s)")
    fz = sh(["ffmpeg", "-hide_banner", "-i", v, "-vf", "freezedetect=n=0.002:d=0.25", "-an", "-f", "null", "-"])
    st = re.findall(r"freeze_start: ([\d.]+)", fz)
    en = re.findall(r"freeze_end: ([\d.]+)", fz)
    out += [f"  {float(s):.3f} - {float(e):.3f}" for s, e in zip(st, en + ["end"] * len(st))] or ["  none"]

    out.append("\n== LOUDNESS (EBU R128)")
    eb = sh(["ffmpeg", "-hide_banner", "-i", v, "-af", "ebur128=peak=true", "-vn", "-f", "null", "-"])
    summ = eb[eb.rfind("Summary:"):]
    for key in ["I:", "LRA:", "Peak:"]:
        m = re.search(rf"{key}\s+(-?[\d.]+|-inf) (\w+)", summ)
        if m:
            out.append(f"  {key:6s} {m.group(1)} {m.group(2)}")
    ast = sh(["ffmpeg", "-hide_banner", "-i", v, "-af", "astats", "-vn", "-f", "null", "-"])
    ov = ast[ast.rfind("Overall"):]
    for k in ["Peak level dB", "RMS level dB"]:
        m = re.search(rf"{k}: (\S+)", ov)
        out.append(f"  {k}: {m.group(1) if m else 'n/a'}")
    import numpy as np
    pcm = np.frombuffer(subprocess.run(["ffmpeg", "-v", "error", "-i", v, "-f", "f32le", "-"],
                                       capture_output=True).stdout, np.float32)
    out.append(f"  samples at/over full scale (|x| >= 0.999): {int((np.abs(pcm) >= 0.999).sum())}")

    try:
        import numpy as np
        from faster_whisper import WhisperModel
        raw = subprocess.run(["ffmpeg", "-v", "error", "-i", v, "-ac", "1", "-ar", "16000", "-f", "f32le", "-"],
                             capture_output=True).stdout
        audio = np.frombuffer(raw, np.float32).copy()
        segs, _ = WhisperModel("small.en", device="cpu", compute_type="int8").transcribe(audio, beam_size=5)
        out.append("\n== SPEECH RECOGNITION OF FINAL MIX (faster-whisper small.en)")
        text = ""
        for s in segs:
            out.append(f"  [{s.start:5.2f}-{s.end:5.2f}] {s.text.strip()}")
            text += s.text
        digits = re.sub(r"\D", "", text[text.lower().rfind("scrims"):] if "scrims" in text.lower() else text)
        out.append(f"  spoken map code recognised as 067409170977: {'YES' if '067409170977' in digits else 'NO'}")
    except Exception as e:  # optional dependency
        out.append(f"\n== SPEECH RECOGNITION skipped ({e})")

    d = Path(a.sheet_dir)
    d.mkdir(parents=True, exist_ok=True)
    sh(["ffmpeg", "-v", "error", "-y", "-i", v, "-vf",
        "fps=1,scale=216:384,drawtext=fontfile=/usr/share/fonts/opentype/inter/Inter-Bold.otf:"
        "text='%{pts\\:hms}':x=4:y=4:fontsize=16:fontcolor=yellow:box=1:boxcolor=black@0.6,tile=8x4",
        "-frames:v", "1", str(d / "contact_sheet_1s.jpg")])
    out.append(f"\ncontact sheet  : {d / 'contact_sheet_1s.jpg'}")
    print("\n".join(out))


if __name__ == "__main__":
    main()
