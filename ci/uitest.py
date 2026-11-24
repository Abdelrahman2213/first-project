#!/usr/bin/env python3
"""
End-to-end check of the Athaan app on an emulator.

Installs the debug APK, walks through the real flows (first launch, language and theme
switches, location permission, every tab, the Fajr muezzin picker, country -> city
picker, playing the adhan, and a real alarm firing), takes a screenshot of each step,
and fails if the app crashes or a check does not hold.

Usage: uitest.py <app-debug.apk> <output-dir>
"""
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

APK, OUT = sys.argv[1], sys.argv[2]
PKG = "com.athaan.app"
os.makedirs(OUT, exist_ok=True)
report = []
failures = []
shot_no = [0]


def adb(*args, check=False, timeout=60, binary=False):
    r = subprocess.run(["adb", *args], capture_output=True, timeout=timeout)
    if check and r.returncode != 0:
        raise RuntimeError(f"adb {' '.join(args)} failed: {r.stderr.decode(errors='replace')}")
    return r.stdout if binary else r.stdout.decode(errors="replace")


def sh(cmd, timeout=60):
    return adb("shell", cmd, timeout=timeout)


def log(msg):
    print(msg, flush=True)
    report.append(msg)


def ok(name, cond, detail=""):
    log(("PASS " if cond else "FAIL ") + name + (f" — {detail}" if detail else ""))
    if not cond:
        failures.append(name)
    return cond


def shot(name):
    shot_no[0] += 1
    fn = f"{shot_no[0]:02d}_{name}.png"
    png = adb("exec-out", "screencap", "-p", binary=True, timeout=30)
    with open(os.path.join(OUT, fn), "wb") as f:
        f.write(png)
    log(f"     screenshot {fn}")


def dump():
    for _ in range(3):
        sh("uiautomator dump /sdcard/ui.xml >/dev/null 2>&1", timeout=40)
        xml = sh("cat /sdcard/ui.xml")
        if xml.strip().startswith("<?xml"):
            try:
                return ET.fromstring(xml)
            except ET.ParseError:
                pass
        time.sleep(1)
    return None


def nodes(_depth=0):
    root = dump()
    ns = [] if root is None else list(root.iter("node"))
    # The emulator is slow on CI: dismiss "X isn't responding" dialogs whenever they appear.
    if _depth == 0:
        for n in ns:
            if "isn't responding" in (n.get("text") or ""):
                for b in ns:
                    if (b.get("text") or "") == "Wait":
                        x, y = center(b)
                        sh(f"input tap {x} {y}")
                        time.sleep(2)
                        return nodes(1)
    return ns


def find(*labels, exact=False):
    """First node whose text or content-desc matches any label."""
    for n in nodes():
        for attr in ("text", "content-desc"):
            v = n.get(attr) or ""
            for lab in labels:
                if (v == lab) if exact else (lab in v):
                    return n
    return None


def center(n):
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", n.get("bounds")))
    return (x1 + x2) // 2, (y1 + y2) // 2


def tap(*labels, wait=1.5, exact=False, tries=6):
    for _ in range(tries):
        n = find(*labels, exact=exact)
        if n is not None:
            x, y = center(n)
            sh(f"input tap {x} {y}")
            time.sleep(wait)
            return True
        time.sleep(1)
    log(f"     (could not find {labels})")
    return False


def tap_edit(wait=1.0):
    """Focus the first text field on screen."""
    for _ in range(4):
        for n in nodes():
            if "EditText" in (n.get("class") or ""):
                x, y = center(n)
                sh(f"input tap {x} {y}")
                time.sleep(wait)
                return True
        time.sleep(1)
    log("     (no text field found)")
    return False


def dismiss_system_dialogs():
    """Accept the notification permission prompt if it is showing."""
    for _ in range(2):
        n = find("to send you notifications", "بإرسال إشعارات")
        if n is None:
            return
        tap("Allow", "السماح", exact=True, wait=1.5)


def wait_for(*labels, timeout=30):
    end = time.time() + timeout
    while time.time() < end:
        if find(*labels) is not None:
            return True
        time.sleep(1.5)
    return False


def screen_text():
    return " | ".join((n.get("text") or n.get("content-desc") or "") for n in nodes() if (n.get("text") or n.get("content-desc")))


def start(extras=""):
    sh(f"am start -n {PKG}/.MainActivity {extras}")
    time.sleep(2.5)


def debug(extra):
    start(f"--es {extra}")


def crashed():
    out = adb("logcat", "-d", "-b", "crash")
    return PKG in out, out


def scroll_down():
    sh("input swipe 540 1800 540 700 400")
    time.sleep(1)


# ------------------------------------------------------------------ setup
adb("wait-for-device")
# Let the freshly booted emulator settle before driving the UI.
for _ in range(60):
    if sh("getprop sys.boot_completed").strip() == "1":
        break
    time.sleep(2)
time.sleep(20)
sh("input keyevent KEYCODE_HOME")
time.sleep(5)
sh("settings put global window_animation_scale 1")
sh("cmd location set-location-enabled true")
adb("emu", "geo", "fix", "31.2357", "30.0444")  # Cairo (lng lat)
adb("install", "-r", APK, timeout=180)
adb("logcat", "-c")
log("Installed " + APK)

# ------------------------------------------------------------------ 1. first launch
start()
ok("first screen asks for location", wait_for("السماح بالوصول للموقع", "Allow location access"), screen_text()[:200])
shot("onboarding_arabic_dark")

ok("language switch to English", tap("English") and wait_for("Allow location access"))
shot("onboarding_english")
tap("العربية")
ok("back to Arabic", wait_for("السماح بالوصول للموقع"))

ok("theme switch to light", tap("الوضع الفاتح", wait=1.2))
shot("onboarding_light")
tap("الوضع الليلي", wait=1.2)

# ------------------------------------------------------------------ 2. location permission
tap("السماح بالوصول للموقع", wait=2)
shot("permission_dialog")
granted = tap("While using the app", "أثناء استخدام التطبيق", "Only this time", wait=2)
ok("permission dialog shown and accepted", granted)
# If Google asks to turn on location accuracy, accept.
tap("Turn on", "OK", "تشغيل", tries=2)
time.sleep(4)
dismiss_system_dialogs()
home = wait_for("الصلاة القادمة", timeout=120)
dismiss_system_dialogs()
ok("location found and home screen opened", home, screen_text()[:300])
time.sleep(3)
shot("home_dark")
txt = screen_text()
ok("home shows the five prayers", all(p in txt for p in ["الفجر", "الظهر", "العصر", "المغرب", "العشاء"]), txt[:300])
ok("home shows a place name", ("القاهرة" in txt) or ("Cairo" in txt) or ("مصر" in txt) or ("الجيزة" in txt), txt[:200])
loclog = adb("logcat", "-d", "-s", "AthaanLocation:*", "AthaanSchedule:*")
log("     " + loclog.strip().replace("\n", "\n     ")[-600:])
ok("next adhan scheduled", "next adhan" in loclog)

dismiss_system_dialogs()
# ------------------------------------------------------------------ 3. theme on home
ok("light theme on home", tap("الوضع الفاتح", wait=1.5))
shot("home_light")
tap("الوضع الليلي", wait=1.5)

# ------------------------------------------------------------------ 4. tabs
ok("library tab", tap("المؤذنين", wait=2) and wait_for("مكتبة المؤذنين"))
shot("library")
txt = screen_text()
ok("library lists many muezzins", sum(n in txt for n in ["عبد الباسط", "رفعت", "الحصري", "المنشاوي", "مصطفى إسماعيل", "البنا"]) >= 4, txt[:300])
if tap("معاينة", wait=6):
    shot("library_preview_playing")
    ok("preview mini player", find("اختيار") is not None)
tap("أذان الفجر", wait=1.5)
shot("library_fajr_filter")
tap("الكل", wait=1)

ok("adhan tab", tap("الأذان", wait=2, exact=True) and wait_for("إعدادات الأذان"))
shot("per_prayer")

ok("settings tab", tap("الإعدادات", wait=2, exact=True) and wait_for("طريقة حساب المواقيت"))
shot("settings_top")
txt = screen_text()
shot_app = False
for i in range(10):
    scroll_down()
    txt += screen_text()
    if i in (1, 3):
        shot(f"settings_{i}")
    if not shot_app and find("زخرفة كارت الصلاة") is not None:
        scroll_down()
        shot("settings_appearance")
        txt += screen_text()
        shot_app = True
shot("settings_bottom")
ok("settings has the new sections", all(k in txt for k in ["زراير الصوت", "زرار القفل", "صلاة الجمعة", "زخرفة كارت الصلاة", "حفظ الإعدادات في ملف"]), txt[:400])

# minute adjustments and iqama
debug("debug_route tuning")
ok("tuning screen", wait_for("تقديم أو تأخير الأذان"))
ok("offset +1 for Fajr", tap("+", wait=1.2, exact=True) and wait_for("تأخير 1 د", timeout=5), screen_text()[:300])
shot("tuning")
tap("−", wait=1.2, exact=True)
debug("debug_route quiet")
ok("quiet hours screen", wait_for("تفعيل وقت الشغل"))
shot("quiet_hours")

# tools tab
debug("debug_route home")
ok("tools tab", tap("الأدوات", wait=2, exact=True) and wait_for("اتجاه القبلة"))
shot("tools")
for route, label, name in [("qibla", "اتجاه القبلة", "qibla"), ("adhkar:am", "أذكار الصباح", "adhkar_morning"),
                           ("tasbih", "السبحة", "tasbih"), ("month", "مواقيت الشهر", "month_times"), ("hijri", "التقويم الهجري", "hijri")]:
    debug("debug_route " + route)
    ok(name + " screen", wait_for(label, timeout=10), screen_text()[:200])
    time.sleep(1)
    shot(name)
debug("debug_route tasbih")
time.sleep(1)
zero = find("0", exact=True)
if zero is not None:
    zx, zy = center(zero)
    sh(f"input tap {zx} {zy}")
    time.sleep(0.4)
    sh(f"input tap {zx} {zy}")
    time.sleep(1)
ok("tasbih counts taps", find("2", exact=True) is not None, screen_text()[:200])
shot("tasbih_counted")

# Counts survive closing the app.
sh(f"am force-stop {PKG}")
time.sleep(1)
start("--es debug_route tasbih")
time.sleep(2)
ok("tasbih count is saved after closing the app", wait_for("باقي 31", timeout=6), screen_text()[:200])
ok("tasbih reset asks first", tap("تصفير", wait=1.5) and wait_for("تصفير العدّاد؟", timeout=5))
tap("ابدأ من الأول", wait=1.5)
ok("tasbih resets to zero", wait_for("باقي 33", timeout=5), screen_text()[:200])

# Adhkar: count, saved progress, reset.
debug("debug_route adhkar:am")
ok("adhkar morning", wait_for("0 من 24 ذكر", timeout=8), screen_text()[:200])
tap("آية الكرسي", wait=1.5)
ok("tapping a dhikr counts it", wait_for("1 من 24 ذكر", timeout=5), screen_text()[:200])
shot("adhkar_counted")
for _ in range(2):
    scroll_down()
shot("adhkar_cards")
sh(f"am force-stop {PKG}")
time.sleep(1)
start("--es debug_route adhkar:am")
ok("adhkar progress is saved after closing the app", wait_for("1 من 24 ذكر", timeout=8), screen_text()[:200])
debug("debug_route adhkar:sleep")
ok("sleep adhkar", wait_for("آخر آيتين من سورة البقرة", timeout=8) or wait_for("المعوذات", timeout=3), screen_text()[:200])
shot("adhkar_sleep")
debug("debug_route adhkar:am")
ok("adhkar reset asks first", tap("ابدأ من الأول", wait=1.5) and wait_for("هيتصفّر العدّ", timeout=5))
tap("ابدأ من الأول", wait=1.5)
ok("adhkar reset to zero", wait_for("0 من 24 ذكر", timeout=5), screen_text()[:200])

# ------------------------------------------------------------------ 5. Fajr picker shows only Fajr recordings
debug("debug_route pick:FAJR")
ok("fajr picker opened", wait_for("تسجيل لأذان الفجر"))
time.sleep(1)
shot("pick_fajr")
txt = screen_text()
ok("fajr picker hides muezzins without a Fajr recording", "الحصري" not in txt and "رفعت" not in txt, txt[:300])
tap_edit()
sh("input text Juhaim")
time.sleep(1.5)
ok("fajr picker lists a Fajr-only recording", find("خالد الجهيم") is not None, screen_text()[:300])

debug("debug_route pick:DHUHR")
ok("dhuhr picker opened", wait_for("تسجيل للأذان العادي"))
time.sleep(1)
shot("pick_dhuhr")
txt = screen_text()
ok("dhuhr picker shows no Fajr badge", "أذان الفجر فقط" not in txt and "+ أذان الفجر" not in txt, txt[:300])
tap_edit()
sh("input text Juhaim")
time.sleep(1.5)
ok("dhuhr picker hides Fajr-only recordings", find("خالد الجهيم") is None and find("لا توجد نتائج") is not None, screen_text()[:300])

debug("debug_route muezzin:dosari")
ok("muezzin page", wait_for("أذان الفجر") and wait_for("الدوسري"))
shot("muezzin_dosari")

# ------------------------------------------------------------------ 5b. user's own recording
debug("debug_route add")
ok("add-muezzin screen", wait_for("إضافة صوت مؤذن"))
shot("add_muezzin")
adb("logcat", "-c")
debug("debug_add_custom صوتي_للتجربة")
time.sleep(12)
ok("custom muezzin added", "custom added custom_" in adb("logcat", "-d", "-s", "AthaanDebug:*"))
debug("debug_route library")
time.sleep(1.5)
ok("custom muezzin in library", wait_for("59 مؤذن"), screen_text()[:120])
shot("library_custom")
adb("logcat", "-c")
debug("debug_play DHUHR")
time.sleep(5)
plog = adb("logcat", "-d", "-s", "AthaanAdhan:*")
ok("Dhuhr plays the user's recording", "muezzin=custom_" in plog and "fajr=false" in plog, plog[-300:])
debug("debug_stop 1")
time.sleep(2)

# ------------------------------------------------------------------ 6. country -> city picker
debug("debug_route city")
ok("country list", wait_for("اختر دولتك"))
shot("countries")
tap_edit()
sh("input text Egy")
time.sleep(1.5)
shot("countries_search")
ok("country search", tap("مصر", wait=2))
ok("city list for Egypt", wait_for("اختر مدينتك"))
shot("cities_egypt")
tap_edit()
sh("input text Alex")
time.sleep(1.5)
shot("cities_search")
ok("choose Alexandria", tap("الإسكندرية", wait=3))
debug("debug_route home")
ok("home shows Alexandria", wait_for("الإسكندرية", timeout=10), screen_text()[:200])
time.sleep(2)
ok("countdown digits are drawn", re.search(r"\d", screen_text()) is not None and "…" not in screen_text(), screen_text()[:300])
shot("home_alexandria")

# ------------------------------------------------------------------ 7. adhan playback (Fajr)
adb("logcat", "-c")
debug("debug_play FAJR")
ok("playing screen", wait_for("إيقاف الأذان", timeout=20))
time.sleep(6)
shot("playing_fajr")
sh("cmd statusbar expand-settings")
time.sleep(3)
shot("notif_media_player")
sh("cmd statusbar expand-notifications")
time.sleep(2)
shot("notif_media_shade")
sh("cmd statusbar collapse")
time.sleep(1.5)
alog = adb("logcat", "-d", "-s", "AthaanAdhan:*")
log("     " + alog.strip()[-400:])
ok("Fajr uses the Fajr recording", "fajr=true" in alog, alog[-300:])
ok("no player error", "player error" not in alog)

# Volume key pauses the adhan while the app is open, and "continue" resumes it.
sh("input keyevent KEYCODE_VOLUME_DOWN")
ok("volume key pauses the adhan (in app)", wait_for("كمّل الأذان", timeout=8), screen_text()[:200])
shot("playing_paused")
ok("volume key logged", "paused by user" in adb("logcat", "-d", "-s", "AthaanService:*", "AthaanAdhan:*"))
tap("كمّل الأذان", wait=2)
ok("adhan resumes", wait_for("إيقاف مؤقت", timeout=8) and "resumed" in adb("logcat", "-d", "-s", "AthaanAdhan:*"))

# Volume key with the app in the background (media session).
adb("logcat", "-c")
sh("input keyevent KEYCODE_HOME")
time.sleep(2)
sh("input keyevent KEYCODE_VOLUME_UP")
time.sleep(2)
blog = adb("logcat", "-d", "-s", "AthaanAdhan:*")
ok("volume key pauses the adhan (background)", "paused by user (volume key)" in blog, blog[-300:])
with open(os.path.join(OUT, "notif_paused.txt"), "w") as f:
    f.write(sh("dumpsys notification --noredact | grep -B2 -A30 'pkg=com.athaan.app' | head -120"))
start()
ok("paused screen in app", wait_for("كمّل الأذان", timeout=8))
tap("كمّل الأذان", wait=2)

# Power key: the screen turning off pauses it.
adb("logcat", "-c")
sh("input keyevent KEYCODE_POWER")
time.sleep(3)
plog = adb("logcat", "-d", "-s", "AthaanAdhan:*")
ok("power key pauses the adhan", "paused by user (screen off)" in plog, plog[-300:])
sh("input keyevent KEYCODE_WAKEUP")
time.sleep(1.5)
sh("wm dismiss-keyguard")
sh("input swipe 540 1900 540 600 300")
time.sleep(2)
start()
dismiss_system_dialogs()
ok("ends from the paused screen", tap("إنهاء", wait=3) and find("كمّل الأذان") is None, screen_text()[:200])
dismiss_system_dialogs()
if find("إيقاف الأذان") is not None:
    tap("إيقاف الأذان", wait=3)
dismiss_system_dialogs()
ok("adhan stops", find("إيقاف الأذان") is None, screen_text()[:200])

# ------------------------------------------------------------------ 8. English + light screenshots
debug("debug_lang en")
debug("debug_theme light")
debug("debug_route home")
time.sleep(2)
shot("home_english_light")
debug("debug_route library")
shot("library_english_light")
debug("debug_route settings")
shot("settings_english_light")
debug("debug_lang ar")
debug("debug_theme dark")
debug("debug_route home")

debug("debug_route settings")
time.sleep(1)
scroll_down()
shot("settings_light_bottom")

# Ornaments and light palettes the user can pick.
debug("debug_theme light")
for pal, pat in [("EMERALD", "MIHRAB"), ("TURQUOISE", "CRESCENT"), ("DAWN", "ARABESQUE"), ("INDIGO", "MASHRABIYA")]:
    debug(f"debug_palette {pal}")
    debug(f"debug_pattern {pat}")
    debug("debug_route home")
    time.sleep(2.5)
    shot(f"home_light_{pal.lower()}_{pat.lower()}")
debug("debug_theme dark")
for pat in ["MOSQUE", "MIHRAB", "CRESCENT", "ARABESQUE", "MASHRABIYA"]:
    debug(f"debug_pattern {pat}")
    debug("debug_route home")
    time.sleep(2.5)
    shot(f"home_dark_{pat.lower()}")
debug("debug_palette IVORY")
debug("debug_pattern MOSQUE")
debug("debug_theme dark")
debug("debug_route home")
time.sleep(1)

# ------------------------------------------------------------------ 8b. notifications
def shade(name):
    sh("cmd statusbar expand-notifications")
    time.sleep(3)
    shot(name)
    # Open the expanded card of our notification too.
    for n in nodes():
        if n.get("resource-id") == "android:id/expand_button" and (n.get("content-desc") or "").lower().startswith("expand"):
            x, y = center(n)
            sh(f"input tap {x} {y}")
            time.sleep(2)
            shot(name + "_expanded")
            break
    sh("cmd statusbar collapse")
    time.sleep(1.2)

adb("logcat", "-c")
debug("debug_render 1")
time.sleep(4)
rlog = adb("logcat", "-d", "-s", "AthaanDebug:*")
for n in ["card_big", "card_small", "card_quote", "widget"]:
    ok(f"{n} view inflates like the system does", f"render {n} ok" in rlog, rlog[-500:])
    data = adb("exec-out", "run-as", PKG, "cat", f"files/render_{n}.png", binary=True)
    if data[:4] == b"\x89PNG":
        with open(os.path.join(OUT, f"render_{n}.png"), "wb") as f:
            f.write(data)
sh("input keyevent KEYCODE_HOME")
time.sleep(1.5)
kinds = ["PRE_ALERT", "IQAMA", "FRIDAY", "KAHF", "ADHKAR_AM", "SUHOOR", "SILENT", "COUNTDOWN"]
for night in ["yes", "no"]:
    sh(f"cmd uimode night {night}")
    time.sleep(2)
    for kind in (kinds if night == "yes" else ["PRE_ALERT", "KAHF", "COUNTDOWN"]):
        sh(f"am start -n {PKG}/.MainActivity --es debug_notify CLEAR")
        time.sleep(1)
        sh(f"am start -n {PKG}/.MainActivity --es debug_notify {kind}")
        time.sleep(1.5)
        sh("input keyevent KEYCODE_HOME")
        time.sleep(1)
        shade(f"notif_{kind.lower()}_{'dark' if night == 'yes' else 'light'}")
sh("cmd uimode night yes")
sh(f"am start -n {PKG}/.MainActivity --es debug_notify COUNTDOWN_OFF")
time.sleep(1)
nlog = adb("logcat", "-d", "-s", "AthaanDebug:*")
ok("every notification posts", all(f"notify {k} ok" in nlog for k in kinds) and "failed" not in nlog, nlog[-600:])
with open(os.path.join(OUT, "notifications_all.txt"), "w") as f:
    f.write(sh("dumpsys notification --noredact | grep -B2 -A40 'pkg=com.athaan.app' | head -400"))
start("--es debug_notify CLEAR")
time.sleep(1)

# ------------------------------------------------------------------ 9. a real alarm: jump the clock to just before the next adhan
alarms = sh(f"dumpsys alarm | grep -A3 {PKG}")
with open(os.path.join(OUT, "alarms.txt"), "w") as f:
    f.write(alarms)
sched = adb("logcat", "-d", "-s", "AthaanSchedule:*")
m = re.findall(r"next adhan (\w+) at (\d+)", sched)
if not m:
    start()
    time.sleep(2)
    sched = adb("logcat", "-d", "-s", "AthaanSchedule:*")
    m = re.findall(r"next adhan (\w+) at (\d+)", sched)
if m:
    prayer, at = m[-1][0], int(m[-1][1])
    sh("input keyevent KEYCODE_HOME")
    adb("root")
    time.sleep(3)
    adb("wait-for-device")
    sh("settings put global auto_time 0")
    target = at // 1000 - 20
    sh("date -u " + time.strftime("%m%d%H%M%Y.%S", time.gmtime(target)))
    log(f"     clock moved to 20 s before {prayer} adhan ({at})")
    adb("logcat", "-c")
    fired = False
    for _ in range(30):
        time.sleep(3)
        l = adb("logcat", "-d", "-s", "AthaanAlarm:*", "AthaanAdhan:*")
        if "adhan " + prayer in l:
            fired = True
            break
    log("     " + adb("logcat", "-d", "-s", "AthaanAlarm:*", "AthaanAdhan:*").strip()[-500:])
    ok(f"alarm fired and the {prayer} adhan started by itself", fired)
    time.sleep(3)
    shot("alarm_fired")
    with open(os.path.join(OUT, "notifications.txt"), "w") as f:
        f.write(sh("dumpsys notification --noredact | grep -A4 com.athaan.app"))
    start("--es debug_stop 1")
else:
    ok("found next adhan time in log", False)

# ------------------------------------------------------------------ crash check
has_crash, crash_log = crashed()
with open(os.path.join(OUT, "logcat.txt"), "w") as f:
    f.write(adb("logcat", "-d", timeout=60)[-400000:])
ok("no crash", not has_crash, crash_log[-1500:] if has_crash else "")

with open(os.path.join(OUT, "report.txt"), "w") as f:
    f.write("\n".join(report) + "\n\n" + ("ALL PASSED" if not failures else "FAILED: " + ", ".join(failures)) + "\n")
print("\n".join(report))
sys.exit(1 if failures else 0)
