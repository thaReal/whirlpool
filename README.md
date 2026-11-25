# Whirlpool (work in progress)

Offline music library built from songs your Pixel identifies with Now Playing.
This drop contains only the **capture** stage: an Android companion app and a Python receiver.

## Receiver (Python 3.10+)
    cd receiver
    python3 -m venv .venv 
    source .venv/bin/activate
    pip install -r requirements.txt
    python receiver.py

First run creates `data/whirlpool.db` and `data/token.txt` (the API token). Listens on `0.0.0.0:8787`
(override with `WHIRLPOOL_HOST`, `WHIRLPOOL_PORT`, `WHIRLPOOL_DATA`). Restrict port 8787 to your LAN
(e.g. `sudo ufw allow from 192.168.0.0/16 to any port 8787`). Check songs: `sqlite3 data/whirlpool.db 'select * from songs'`.
Quick test:
    curl -X POST localhost:8787/api/songs -H "Authorization: Bearer $(cat data/token.txt)" \
      -H 'Content-Type: application/json' -d '{"songs":[{"title":"Test","artist":"Tester"}]}'

## Android app (Kotlin)
1. Android Studio -> New Project -> Empty Views Activity, package `com.whirlpool.capture`, min SDK 26.
2. Replace the manifest and add the four `.kt` files from `android/app/src/main/...`.
3. In `app/build.gradle(.kts)` add: `implementation("androidx.work:work-runtime-ktx:2.9.1")` and `implementation("androidx.core:core-ktx:1.13.1")`.
4. Install on the Pixel. If notification access is greyed out: Settings > Apps > Whirlpool Capture > menu (three dots) > **Allow restricted settings**, then grant access from the app.
5. Enter `http://<ubuntu-lan-ip>:8787` and the token, tap Save. Uploads only happen on Wi-Fi; songs queue until the server is reachable.

## Verifying capture (important)
The Now Playing notification's package, channel and text format are unverified guesses. After a few songs are
identified, the app's main screen shows the last capture, or lists the package/channel names it saw instead. Send those back
and the matching rules can be fixed.
