REALISTIC SCRIMS - Fortnite Creative promo trailer
====================================================
Map code: 0674-0917-0977
Output  : realistic_scrims_trailer.mp4 - 1080x1920 (9:16), 60 fps, H.264 High / AAC 320 kb/s 48 kHz, 32.0 s

Fan-made promotional edit for a Fortnite Creative map. Not affiliated with or
endorsed by Epic Games. All gameplay is the creator's own footage, unaltered
in content (no invented eliminations or events).


FILES
-----
realistic_scrims_trailer.mp4   final trailer
voiceover.wav                  narration only, aligned to the trailer timeline (48 kHz / 24-bit)
final_audio.wav                final mix (music + SFX + gameplay audio + narration), -14 LUFS
edit_trailer.py                full, reproducible pipeline (assets -> mix -> picture -> mux)
qa_check.py                    automated technical QA (ffprobe / black / freeze / loudness / ASR)
quality_check.txt              QA report for the delivered file
assets/graphics/*.png          generated title cards, captions and end-card layers (RGBA)
assets/music/                  original music bed (synthesised in code, 120 BPM)
assets/sfx/*.wav               original sound effects (impacts, risers, whooshes, sub drops ...)
assets/vo/line_XX.wav          processed narration lines + vo_timing.json
assets/fonts/                  Anton (SIL OFL 1.1) and Inter ExtraBold (SIL OFL 1.1) + licences
qa/contact_sheet_1s.jpg        one frame per second of the final render


SOURCE FILE (required, not included)
------------------------------------
"fortnite new video scrims.mp4" - 1080x1920, 60 fps, 35.25 s, H.264 + AAC.
The pipeline only READS the source; it is never modified.


DEPENDENCIES
------------
ffmpeg / ffprobe (built with libx264, soxr)       tested with 6.1.1
Python 3.10+ with: numpy, scipy, opencv-python-headless, pillow, piper-tts
Optional for QA speech check: faster-whisper

    pip install numpy scipy opencv-python-headless pillow piper-tts faster-whisper

Voice model (Piper, runs fully offline) - "joe" medium, trained on a CC0 dataset:
    https://huggingface.co/rhasspy/piper-voices/resolve/v1.0.0/en/en_US/joe/medium/en_US-joe-medium.onnx
    https://huggingface.co/rhasspy/piper-voices/resolve/v1.0.0/en/en_US/joe/medium/en_US-joe-medium.onnx.json


REPRODUCE
---------
    python3 edit_trailer.py --source "/path/fortnite new video scrims.mp4" \
        --voice /path/en_US-joe-medium.onnx --work /tmp/rs_work
    python3 qa_check.py realistic_scrims_trailer.mp4

Quick look at individual frames without a full render:
    python3 edit_trailer.py --source ... --voice ... --stills 0.5,3.2,20.6,29.0

Render time is roughly 5-10 minutes on 4 CPU cores. Without --voice the
trailer still renders, just without narration.


HOW IT IS BUILT
---------------
Edit (all timings live in SEGMENTS at the top of edit_trailer.py):
  0.0- 3.0  HOOK      fight #1: enemy jumps the wall, gets hit, ELIMINATED -
                      punch-in, 0.5x slow-mo on the elimination, 0.3 s freeze, "THINK YOU'RE READY?"
  3.0- 9.0  IDENTITY  title slam on the weapon-shop wall, "Round 2 begins" respawn,
                      gold drum-gun ADS - "YOUR MECHANICS. YOUR TEST."
  9.0-17.0  MONTAGE   drop at 9.0; rocket aim, elimination aftermath, builds blown
                      apart, build mode ("BUILD."), ADS firing ("AIM."), rocket shot
 17.0-25.5  CLIMAX    rocket launch -> guided-rocket flight (1.6x speed ramp with real
                      frame-accumulation motion blur) -> impact freeze -> finisher ->
                      elimination beam -> real VICTORY "ROUND 8 OF 100" + "PROVE YOURSELF."
 25.5-32.0  END CARD  blurred real gameplay background, REALISTIC SCRIMS,
                      MAP CODE 0674-0917-0977 (on screen ~6 s, each group pulses as
                      the narrator reads it), PLAY NOW, final hit + fade.

Picture: frame-accurate OpenCV compositor - eased zoom keyframes, impact shakes,
flashes, chromatic-aberration hits, whip-pan transitions with directional blur,
restrained grade (mild contrast, +10 % saturation, cool shadows, vignette, light
sharpening). Source is already 9:16 so composition is preserved; the only crop is
a 1.30x punch-in on the letterboxed respawn shot to remove its black bars.
Slow-motion uses frame repetition / blending - no synthetic frames are claimed.

Audio: music and every SFX are synthesised from scratch in the script (no samples,
no third-party music). Original gameplay audio is used where it is clean; the
source's old narration ("How to get legendary...", "Have fun!", "Let me know...")
is muted out of the gameplay track. Narration = Piper TTS (en_US joe) processed
with high-pass, EQ, de-esser, compressor and limiter; music is ducked ~10.5 dB and
gameplay ~10 dB under the voice. Final mix: two-pass loudnorm to -14 LUFS,
true peak target -2 dBTP before AAC (measured <= -1 dBTP after).


ACTION v2 (sound-effects only, no music)
----------------------------------------
realistic_scrims_trailer_action_v2.mp4   26.75 s, 1080x1920, 60 fps, H.264 / AAC 320k
final_audio_action_v2.wav                final v2 mix (SFX + gameplay + narration), -14 LUFS
sfx_only_action_v2.wav                   the effects stem alone
voiceover_action_v2.wav                  same v1 narration lines, re-placed for the v2 cut
edit_trailer_v2.py                       v2 pipeline (imports helpers from edit_trailer.py)
assets/sfx_v2/                           32 original synthesized effects
assets/graphics_v2/                      v2 title / caption / end-card layers
quality_check_action_v2.txt, qa_v2/      v2 QA report + contact sheet

    python3 edit_trailer_v2.py --source "/path/fortnite new video scrims.mp4" --work /tmp/rs_work
    python3 edit_trailer_v2.py --source ... --print-edl        # timeline
    python3 edit_trailer_v2.py --source ... --audio-only       # remix without re-rendering
    python3 qa_check.py realistic_scrims_trailer_action_v2.mp4 --sheet-dir qa_v2

Voice: v2 reuses assets/vo/line_XX.wav unchanged (Piper en_US-joe-medium, the voice
of v1). No Kokoro configuration exists in this project.

ACTION v3 (refined cuts + SFX sync)
-----------------------------------
realistic_scrims_trailer_action_v3.mp4   26.7 s, 1080x1920, 60 fps - current best version
edit_trailer_v3.py                       v2 pipeline + cut fixes, anchored SFX placement,
                                         new impacts/whooshes, optical-flow whip matching
final_audio_action_v3.wav, sfx_only_action_v3.wav, voiceover_action_v3.wav, assets/sfx_v3/,
assets/graphics_v3/, quality_check_action_v3.txt, qa_v3/

    python3 edit_trailer_v3.py --source "/path/fortnite new video scrims.mp4" --work /tmp/rs_work
