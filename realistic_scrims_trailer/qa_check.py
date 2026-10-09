#!/usr/bin/env python3
"""Technical QA of the rendered trailer.

Usage: python3 qa_check.py realistic_scrims_trailer.mp4 WORK_DIR
Prints a report (ffprobe specs, black/freeze detection, loudness, true peak,
VO transcript) and writes contact sheets of the render to WORK_DIR.
"""
import json
import re
import subprocess
import sys

import cv2
import numpy as np


def sh(cmd):
    return subprocess.run(cmd, capture_output=True, text=True)


def main(path, work):
    out = []
    p = json.loads(sh(["ffprobe", "-v", "error", "-show_format", "-show_streams", "-of", "json", path]).stdout)
    v = next(s for s in p["streams"] if s["codec_type"] == "video")
    a = [s for s in p["streams"] if s["codec_type"] == "audio"]
    out.append(f"file size     : {int(p['format']['size']) / 1e6:.1f} MB")
    out.append(f"duration      : {float(p['format']['duration']):.3f} s")
    out.append(f"container     : {p['format']['format_name']}  bitrate {int(p['format']['bit_rate']) / 1e6:.1f} Mb/s")
    out.append(f"video         : {v['codec_name']} {v.get('profile')} {v['width']}x{v['height']} "
               f"DAR {v.get('display_aspect_ratio')} SAR {v.get('sample_aspect_ratio')} "
               f"{v['r_frame_rate']} fps ({v.get('avg_frame_rate')} avg), {v['pix_fmt']}, {v.get('nb_frames')} frames, "
               f"color {v.get('color_space')}/{v.get('color_range')}")
    for s in a:
        out.append(f"audio         : {s['codec_name']} {s['sample_rate']} Hz {s['channels']} ch "
                   f"{int(s.get('bit_rate', 0)) / 1000:.0f} kb/s, {float(s['duration']):.3f} s")

    r = sh(["ffmpeg", "-hide_banner", "-i", path, "-vf", "blackdetect=d=0.05:pix_th=0.06", "-an", "-f", "null", "-"])
    blacks = re.findall(r"black_start:([\d.]+) black_end:([\d.]+)", r.stderr)
    out.append(f"black frames  : {blacks if blacks else 'none'}")
    r = sh(["ffmpeg", "-hide_banner", "-i", path, "-vf", "freezedetect=n=0.002:d=0.3", "-an", "-f", "null", "-"])
    fz = re.findall(r"freeze_start: ([\d.]+).*?freeze_end: ([\d.]+)", r.stderr, re.S)
    out.append(f"frozen >0.3 s : {fz if fz else 'none'}")
    r = sh(["ffmpeg", "-hide_banner", "-i", path, "-af", "ebur128=peak=true", "-vn", "-f", "null", "-"])
    summ = r.stderr[r.stderr.rfind("Summary:"):]
    i = re.search(r"I:\s+(-?[\d.]+) LUFS", summ)
    lra = re.search(r"LRA:\s+([\d.]+) LU", summ)
    tp = re.search(r"Peak:\s+(-?[\d.]+) dBFS", summ)
    out.append(f"loudness      : {i.group(1)} LUFS integrated, LRA {lra.group(1)} LU, true peak {tp.group(1)} dBTP")
    raw = subprocess.run(["ffmpeg", "-v", "error", "-i", path, "-vn", "-f", "f32le", "-ac", "2", "-"],
                         capture_output=True).stdout
    au = np.frombuffer(raw, np.float32).reshape(-1, 2)
    out.append(f"sample peak   : {20 * np.log10(np.abs(au).max()):.2f} dBFS, "
               f"samples >= 0.999: {int((np.abs(au) >= 0.999).sum())}")
    # silence check per second
    sec = [20 * np.log10(np.sqrt((au[i * 48000:(i + 1) * 48000] ** 2).mean()) + 1e-9) for i in range(len(au) // 48000)]
    out.append(f"per-second RMS (dB): {' '.join(f'{x:.0f}' for x in sec)}")

    # contact sheets: every 0.5 s
    cap = cv2.VideoCapture(path)
    fps = cap.get(cv2.CAP_PROP_FPS)
    n = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
    thumbs = []
    for k in range(0, n, int(fps / 2)):
        cap.set(cv2.CAP_PROP_POS_FRAMES, k)
        ok, f = cap.read()
        if not ok:
            continue
        t = cv2.resize(f, (180, 320), interpolation=cv2.INTER_AREA)
        cv2.putText(t, f"{k / fps:.1f}", (3, 16), 0, 0.5, (0, 255, 255), 1)
        thumbs.append(t)
    while len(thumbs) % 13:
        thumbs.append(np.zeros_like(thumbs[0]))
    rows = [np.hstack(thumbs[i:i + 13]) for i in range(0, len(thumbs), 13)]
    for j in range(0, len(rows), 3):
        cv2.imwrite(f"{work}/qa_sheet_{j // 3}.png", np.vstack(rows[j:j + 3]))

    # transcript of final mix (verifies spoken map code)
    try:
        from faster_whisper import WhisperModel
        mono = au.mean(1)
        a16 = np.interp(np.arange(0, len(mono) / 48000 * 16000) * 3, np.arange(len(mono)), mono).astype(np.float32)
        m = WhisperModel("base.en", device="cpu", compute_type="int8")
        segs, _ = m.transcribe(a16, vad_filter=False, initial_prompt="Realistic Scrims")
        out.append("transcript of final mix:")
        for s in segs:
            out.append(f"   {s.start:5.1f}-{s.end:5.1f} {s.text.strip()}")
    except Exception as e:  # pragma: no cover
        out.append(f"transcript: skipped ({e})")
    print("\n".join(out))


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
