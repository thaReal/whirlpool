#!/usr/bin/env python3
"""Whirlpool receiver: accepts songs from the Android capture app and stores them in SQLite."""
import os
import re
import secrets
import sqlite3
import time
from pathlib import Path

from flask import Flask, g, jsonify, request

DATA_DIR = Path(os.environ.get("WHIRLPOOL_DATA", "data"))
DB_PATH = DATA_DIR / "whirlpool.db"
TOKEN_PATH = DATA_DIR / "token.txt"

SCHEMA = """
CREATE TABLE IF NOT EXISTS songs (
  id         INTEGER PRIMARY KEY,
  norm_key   TEXT NOT NULL UNIQUE,
  title      TEXT NOT NULL,
  artist     TEXT NOT NULL,
  first_seen INTEGER NOT NULL,
  last_seen  INTEGER NOT NULL,
  play_count INTEGER NOT NULL DEFAULT 1,
  status     TEXT NOT NULL DEFAULT 'pending',  -- pending | downloaded | failed
  local_path TEXT
);
CREATE TABLE IF NOT EXISTS events (event_id TEXT PRIMARY KEY, received INTEGER NOT NULL);
"""


def norm(s: str) -> str:
    return re.sub(r"[^\w\s]", "", re.sub(r"\s+", " ", s.lower())).strip()


def load_token() -> str:
    DATA_DIR.mkdir(parents=True, exist_ok=True)
    if not TOKEN_PATH.exists():
        TOKEN_PATH.write_text(secrets.token_urlsafe(32))
        TOKEN_PATH.chmod(0o600)
        print(f"Generated API token, saved to {TOKEN_PATH}")
    return TOKEN_PATH.read_text().strip()


def create_app() -> Flask:
    app = Flask(__name__)
    token = load_token()
    with sqlite3.connect(DB_PATH) as c:
        c.executescript(SCHEMA)

    def db() -> sqlite3.Connection:
        if "db" not in g:
            g.db = sqlite3.connect(DB_PATH)
            g.db.row_factory = sqlite3.Row
        return g.db

    @app.teardown_appcontext
    def close_db(_exc):
        conn = g.pop("db", None)
        if conn:
            conn.close()

    def authorized() -> bool:
        supplied = request.headers.get("Authorization", "").removeprefix("Bearer ").strip()
        return secrets.compare_digest(supplied, token)

    @app.get("/api/health")
    def health():
        return jsonify(status="ok")

    @app.post("/api/songs")
    def add_songs():
        if not authorized():
            return jsonify(error="unauthorized"), 401
        songs = (request.get_json(silent=True) or {}).get("songs")
        if not isinstance(songs, list):
            return jsonify(error="expected {'songs': [...]}"), 400
        conn, now = db(), int(time.time())
        counts = dict(added=0, duplicates=0, already_received=0, skipped=0)
        for s in songs:
            title = str(s.get("title", "")).strip() if isinstance(s, dict) else ""
            artist = str(s.get("artist", "")).strip() if isinstance(s, dict) else ""
            if not title or not artist:
                counts["skipped"] += 1
                continue
            event_id = s.get("event_id")
            if event_id and conn.execute(
                "INSERT OR IGNORE INTO events VALUES (?, ?)", (str(event_id), now)
            ).rowcount == 0:
                counts["already_received"] += 1  # phone re-sent a batch we already stored
                continue
            seen = int(s.get("detected_at_ms", now * 1000)) // 1000
            key = f"{norm(artist)}|{norm(title)}"
            if conn.execute("SELECT 1 FROM songs WHERE norm_key=?", (key,)).fetchone():
                conn.execute(
                    "UPDATE songs SET last_seen=MAX(last_seen, ?), play_count=play_count+1 WHERE norm_key=?",
                    (seen, key),
                )
                counts["duplicates"] += 1
            else:
                conn.execute(
                    "INSERT INTO songs (norm_key, title, artist, first_seen, last_seen) VALUES (?,?,?,?,?)",
                    (key, title, artist, seen, seen),
                )
                counts["added"] += 1
        conn.commit()
        return jsonify(counts)

    @app.get("/api/songs")
    def list_songs():
        if not authorized():
            return jsonify(error="unauthorized"), 401
        rows = db().execute(
            "SELECT id, title, artist, first_seen, last_seen, play_count, status FROM songs ORDER BY first_seen DESC"
        ).fetchall()
        return jsonify([dict(r) for r in rows])

    return app


if __name__ == "__main__":
    create_app().run(
        host=os.environ.get("WHIRLPOOL_HOST", "0.0.0.0"),
        port=int(os.environ.get("WHIRLPOOL_PORT", "8787")),
    )
