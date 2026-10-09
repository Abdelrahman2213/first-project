REALISTIC SCRIMS - Promo Trailer (TikTok / YouTube Shorts / Instagram Reels)
=============================================================================

Map:       REALISTIC SCRIMS (Fortnite Creative)
Map code:  0674-0917-0977
Output:    realistic_scrims_trailer.mp4  - 1080x1920 (9:16), 60 fps, H.264 High + AAC 48 kHz, ~37 s

Unofficial fan/creator promo. Not affiliated with or endorsed by Epic Games.

DELIVERABLES
------------
  realistic_scrims_trailer.mp4   final trailer
  voiceover.wav                  narration track (aligned to the trailer timeline, 48 kHz / 24-bit)
  final_audio.wav                final mixed + loudness-normalised audio (-14 LUFS, <= -2 dBTP)
  edit_trailer.py                complete, reproducible editing/rendering pipeline
  quality_check.txt              technical QA report of the rendered file
  qa_check.py                    script that produced the QA measurements (ffprobe, black/freeze detect,
                                 EBU R128, contact sheets, Whisper transcript of the final mix)
  assets/fonts/                  Anton-Regular.ttf (SIL Open Font License, see Anton-OFL.txt)
  assets/graphics/               rendered title / caption / end-card graphics (PNG, BGRA)
  assets/sfx_layer.wav           original synthesized sound-design layer (all cued effects)
  assets/ambience_bed.wav        original non-musical air/rumble bed

SOURCE FOOTAGE (not included - too large; never modified)
----------------------------------------------------------
Both from the project Google Drive:
  "fortnite new video scrims.mp4"   35.26 s, 1080x1920, 60 fps, AAC stereo   (--scrims)
  "Timeline 1.mp4"                  29.33 s, 1080x1920, 60 fps, AAC stereo   (--timeline)

Both sources are already 9:16 at 60 fps, so the trailer is native 60 fps with
no reframing, stretching or frame interpolation. Brief slow-motion moments use
frame blending of real neighbouring frames (no invented motion).

Both sources are already-edited clips containing the creators' own narration
(and burned-in captions in "Timeline 1"). The edit only uses ranges without
burned-in captions, and original game audio is only taken from windows where
that narration is silent (see GAME_AUDIO_WINDOWS in edit_trailer.py).

DEPENDENCIES
------------
  ffmpeg / ffprobe (with libx264, aac, loudnorm, alimiter)
  Python 3.10+ with: numpy, scipy, opencv-python-headless, pillow, piper-tts
     pip install numpy scipy opencv-python-headless pillow piper-tts
  Fonts: Anton (bundled in assets/fonts), Inter Display Black
         (/usr/share/fonts/opentype/inter/InterDisplay-Black.otf - change FONT_INTER if elsewhere)
  Piper voice "en_US-joe-medium" (dataset licence: CC0):
     https://huggingface.co/rhasspy/piper-voices/tree/main/en/en_US/joe/medium
     download en_US-joe-medium.onnx and en_US-joe-medium.onnx.json into one folder

REPRODUCE
---------
  python3 edit_trailer.py \
      --scrims   "/path/fortnite new video scrims.mp4" \
      --timeline "/path/Timeline 1.mp4" \
      --voices   /path/to/piper_voices \
      --work     /tmp/rs_build \
      --out      .

  Optional: --stills 0.5,3.2,31.5   renders only PNG stills (layout check)
            --audio-only            builds voiceover.wav / final_audio.wav only

  Render time: ~3-5 min on 4 CPU cores. Intermediates go to --work.

EDIT STRUCTURE (cuts on a 0.5 s grid)
-------------------------------------
  0.0 - 3.0   HOOK       real gold-AR shot + elimination  | "THINK YOU'RE READY?"
  3.0 - 9.0   IDENTITY   spawn-in title reveal, weapon-shop wall, gold weapon ADS
                         | "REALISTIC SCRIMS", "YOUR MECHANICS. YOUR TEST."
  9.0 - 25.0  MONTAGE    BUILD. / EDIT. / AIM. / ELIMINATE. (freeze frame), VICTORY round 4,
                         climb + edit + trap, rocket launcher fire, guided rocket flight
  25.0 - 31.0 CLIMAX     rocket impact elimination (speed ramp), breathing beat,
                         VICTORY round 8 payoff | "PROVE YOURSELF."
  31.0 - 37.0 END CARD   REALISTIC SCRIMS / MAP CODE / 0674-0917-0977 / PLAY NOW
                         (code on screen ~5.4 s; each group highlighted as it is read)

AUDIO (no music - sound design only)
------------------------------------
  There is deliberately no music. The soundtrack is voice + real gameplay audio + original
  sound effects synthesized by edit_trailer.py, each cued to an on-screen event:
    0.0  hit on the first frame, kill-confirm ping on the elimination, heartbeat + inhale
    3.0  low "braam" + digital materialise sparkle on the spawn-in title reveal
    8.0  weapon rack click on the gold weapon; whooshes / swishes on every cut
    9-14 caption slams (BUILD. / EDIT.), scope whirr + lock-on click (AIM.)
    14.5 time-stop freeze: inhale, hit, kill-confirm ping, ear ring, release swish
    16.5 / 29.0 shimmer hits on both VICTORY screens
    17.6 suspense clock ticking faster over the climb + long riser into the launch
    21.0 rocket launch blast + debris, engine roar, accelerating heartbeat, inhale
    25.0 explosion: braam, debris, kill-confirm, ear ring; game audio muffled
         ("shell-shock") during the slow-motion, then real time snaps back
    31.0 end card braam, data blips as the code types in, UI confirm on PLAY NOW
  Bed:   non-musical wind/rumble so the cut never drops into dead silence.
  VO:    Piper TTS (en_US-joe-medium, CC0), EQ + compression + de-essing + light room.
  Game:  original Fortnite gameplay audio from the source clips, remapped with the same
         time-map as the picture (only where the creators' narration is silent).
  Mix:   bed -8 dB, SFX -8 dB and game -6 dB under narration so every line stays on top.
  Master: two-pass EBU R128 loudnorm to -14 LUFS, true-peak limited (<= -2 dBTP after AAC).
