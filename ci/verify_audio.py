"""Transcribe adhan recordings with Whisper and check which ones contain the Fajr phrase
"الصلاة خير من النوم". Usage: verify_audio.py <shard> <shards> <out.json>"""
import json, os, re, sys, subprocess, urllib.request
from faster_whisper import WhisperModel

shard, shards, out = int(sys.argv[1]), int(sys.argv[2]), sys.argv[3]
items = [(c["id"], c["kind"], c["url"]) for c in json.load(open("ci/candidates.json"))]
items = items[shard::shards]

def norm(s):
    s = re.sub(r"[ً-ْـ]", "", s)
    s = s.replace("أ", "ا").replace("إ", "ا").replace("آ", "ا").replace("ة", "ه").replace("ى", "ي")
    return s

model = WhisperModel("medium", device="cpu", compute_type="int8", cpu_threads=4)
res = []
for mid, kind, url in items:
    fn = f"/tmp/{mid}_{kind}.mp3"
    try:
        urllib.request.urlretrieve(url, fn)
        segs, info = model.transcribe(fn, language="ar", beam_size=5, vad_filter=False, condition_on_previous_text=False)
        text = " ".join(s.text for s in segs)
    except Exception as ex:
        text = f"ERROR {ex}"
    n = norm(text)
    r = dict(id=mid, kind=kind, url=url,
             fajr_phrase=bool(re.search(r"خير م|خيرم|خم من|من النو|من النع|الصلاه خ", n)),
             has_falah=("فلاح" in n),
             has_hayya=("حي علي" in n or "حي على" in n),
             text=text[:1500])
    print(json.dumps(r, ensure_ascii=False), flush=True)
    res.append(r)
json.dump(res, open(out, "w"), ensure_ascii=False, indent=1)
