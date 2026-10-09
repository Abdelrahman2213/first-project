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
  final_audio.wav                final mixed + loudness-normalised audio (-14 LUFS, <= -1.5 dBTP)
  edit_trailer.py                complete, reproducible editing/rendering pipeline
  quality_check.txt              technical QA report of the rendered file
  qa_check.py                    script that produced the QA measurements (ffprobe, black/freeze detect,
                                 EBU R128, contact sheets, Whisper transcript of the final mix)
  assets/fonts/                  Anton-Regular.ttf (SIL Open Font License, see Anton-OFL.txt)
  assets/graphics/               rendered title / caption / end-card graphics (PNG, BGRA)
  assets/music_original_instrumental.wav   original synthesized phonk instrumental (pre-duck)
  assets/sfx_layer.wav           original synthesized impacts / risers / whooshes layer

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

EDIT STRUCTURE (120 BPM grid, cuts on beats)
---------------------------------------------
  0.0 - 3.0   HOOK       real gold-AR shot + elimination  | "THINK YOU'RE READY?"
  3.0 - 9.0   IDENTITY   spawn-in title reveal, weapon-shop wall, gold weapon ADS
                         | "REALISTIC SCRIMS", "YOUR MECHANICS. YOUR TEST."
  9.0 - 25.0  MONTAGE    BUILD. / EDIT. / AIM. / ELIMINATE. (freeze frame), VICTORY round 4,
                         climb + edit + trap, rocket launcher fire, guided rocket flight
  25.0 - 31.0 CLIMAX     rocket impact elimination (speed ramp), breathing beat,
                         VICTORY round 8 payoff | "PROVE YOURSELF."
  31.0 - 37.0 END CARD   REALISTIC SCRIMS / MAP CODE / 0674-0917-0977 / PLAY NOW
                         (code on screen ~5.4 s; each group highlighted as it is read)

AUDIO
-----
  Music: original phonk-style instrumental (A minor, 120 BPM: distorted 808, cowbell
         melody, clap/hats, pads) synthesized by edit_trailer.py - no third-party music.
  SFX:   original synthesized impacts, risers, whooshes, reverse cymbals, UI blips.
  VO:    Piper TTS (en_US-joe-medium, CC0), EQ + compression + de-essing + light room;
         music ducked ~9 dB under narration.
  Game:  original Fortnite gameplay audio from the source clips, remapped with the same
         time-map as the picture, mixed under the score.
  Master: two-pass EBU R128 loudnorm to -14 LUFS, true-peak limited.
