# FORTNITE MULTIVERSE — Action Transformation Trailer

**Output:** `fortnite_multiverse_action_trailer.mp4` — 1080×1920 (9:16), 30 fps, H.264 (yuv420p) + AAC 48 kHz, ~30.8 s, **sound‑effects only (no music)**.

One recognizable Fortnite character travels through **three visual worlds** as one continuous cinematic experience:

1. **CINEMATIC MOVIE** (0–7.2 s) — the character revealed like an action‑film protagonist: filmic teal/orange grade, cinematic letterbox bars, volumetric fog, floating dust, slow push‑ins, a gold‑weapon hero reveal.
2. **LEGO UNIVERSE** (8.5–15.1 s) — the *same* character, rebuilt as a studded LEGO minifig in a brick world.
3. **FORTNITE GAMEPLAY** (17–30.8 s) — the real character erupts into an aggressive real‑footage montage, landing on a VICTORY payoff and a `MULTIVERSE` end title.

The two **on‑screen transformations are the centerpiece**:
- **Movie → LEGO** (~7.2–8.5 s): energy pulse ring → particle disintegration dissolve → bricks assemble → LEGO, with a glowing dissolve front, micro‑glitch and a white impact flash.
- **LEGO → Fortnite** (~15.1–17.0 s): rapid push‑in → **brick explosion** → cyan energy shockwave + RGB separation + motion‑blur → clean reveal of the real character in a *matching pose* (LEGO pose cut to the same real frame).

Unofficial fan/creator edit. Not affiliated with or endorsed by Epic Games.

---

## Character continuity

The trailer uses the creator's own footage, so the character is literally the **same skin** in every world — only the *rendering style* changes. Identity is preserved by colour and silhouette:

- short dark hair · charcoal‑grey open tactical vest over a white/cream shirt · tattooed forearms · dark trousers
- **gold/brass cylindrical tank back‑bling** (+ gold hip canisters)
- **signature gold weapon** (gold AR / gold pistol)

The LEGO world snaps every brick to a curated LEGO plastic palette, chosen so the vest stays grey, the shirt white, the tanks and weapon gold, and the hair/trousers dark — the minifig is unmistakably the same person.

---

## How it was produced (and honest limitations)

This machine had **ffmpeg, Python 3, NumPy and Pillow** — and **no** Blender, no OpenCV, and no image/video‑generation service. So there is **no true 3‑D or AI‑generated content**: every frame is built from the real gameplay footage through a 2‑D compositing pipeline. The transformations are genuine on‑screen effects (animated dissolve masks, particle/brick simulation, energy rings, RGB split, glitch, flashes) layered over the real frames — not crossfades, and not claimed 3‑D morphs.

Pipeline (`build.py`, stages `extract | video | audio | mux`):

1. **extract** — ffmpeg cuts each shot from the sources with a UI‑safe punch‑in crop (removes the burned‑in map code / round timer / captions) and time‑maps it to the edit timeline at 30 fps.
2. **video** — a NumPy/Pillow renderer applies each world's look, camera moves (push‑in, impact zoom, shake, whip), the two transformations and the brick‑particle system, then encodes **per‑segment clips** (cached, so a single shot can be re‑rendered cheaply).
3. **audio** — `audio.py` synthesizes the entire SFX soundscape from scratch (see below) and places it on a timeline synced to the cuts and transformations.
4. **mux** — concatenates the clips and muxes the SFX with EBU‑R128 loudness normalization.

### Looks (`fx.py`)
- `grade_cinematic` — contrast/teal‑orange tint, bloom, vignette, grain, chroma shift, top/bottom scrim.
- `legoize` — downsample to a brick grid → snap to a clean LEGO plastic palette → moulded **stud + bevel** 3‑D shading + gloss. Identity‑preserving.
- `grade_game` — punchy, readable gameplay grade.
- transition primitives — organic dissolve masks, radial wipes, RGB split, glitch, impact flash.

### Audio (`audio.py`) — **no music, by design**
Procedurally synthesized: deep impacts, sub‑bass drops, risers, reverse sweeps, whooshes, metallic accents, LEGO plastic clicks and brick‑scatter, and layered "transformation hits." Mixed with a light early‑reflection reverb, soft‑limited, and normalized to ‑14 LUFS (TP ≈ ‑1 dBFS). The source clips' own audio is **muted** — they are dominated by the creator's voice narration, which would fight a clean SFX mix.

---

## Deliverables

| File | What it is |
|------|-----------|
| `fortnite_multiverse_action_trailer.mp4` | the final trailer |
| `build.py` | extraction + render + audio + mux pipeline (the editing script) |
| `fx.py` | per‑frame look & transition engine (grade / LEGO / FX) |
| `audio.py` | synthesized cinematic SFX engine (no music) |
| `assets/sfx_trailer.wav` | the rendered SFX soundscape (24‑bit / 48 kHz stereo) |
| `quality_check.txt` | technical QA report of the rendered file |
| `README.md` | this file |

**Not included** (git‑ignored): `source/` (the two creator clips, owned by the creator, too large, never modified), and `work/` / `proto/` / `qa/` intermediates. No Blender project files exist because Blender was not available; all 3‑D‑looking effects are 2‑D composites. No separate image assets are shipped because all graphics (LEGO bricks, studs, particles, title, fog, dust) are generated procedurally at render time.

### Source footage (from the project Google Drive, fetched at full quality)
- `fortnite new video scrims.mp4` — 35.26 s, 1080×1920, 60 fps
- `Timeline 1.mp4` — 29.33 s, 1080×1920, 60 fps

## Reproduce
```
# place the two source clips in ./source/ as scrims.mp4 and timeline.mp4
python3 build.py all           # extract + video + audio + mux
# iterate on a single shot (uses the per-segment cache):
python3 build.py video force=w1_weapon && python3 build.py mux
```
Dependencies: `ffmpeg`/`ffprobe` (libx264, aac, loudnorm), Python 3 with `numpy` + `pillow`. Render time ≈ 9 min on 4 cores (full), seconds per cached shot.

## Edit structure
```
0.0–2.6   HOOK       vault hero reveal (cinematic bars, fog, dust, slow push)
2.6–4.8   WEAPON     turn + gold-weapon hero reveal
4.8–7.2   WALK       weapon-shop walk, push-in -> charge
7.2–8.5   TRANSFORM  MOVIE -> LEGO (pulse / disintegrate / bricks / flash)
8.5–15.1  LEGO       brick-world action: walk, slide, gold-gun, elim pose (+brick particles)
15.1–17.0 TRANSFORM  LEGO -> FORTNITE (brick explosion / shockwave / RGB / reveal)   <- centrepiece
17.0–28.9 MONTAGE    elim -> fight -> rocket -> freeze -> builds -> boom -> speed-ramp elim
28.9–30.8 PAYOFF     VICTORY (round 8) + MULTIVERSE / ONE LEGEND · THREE WORLDS end title
```
