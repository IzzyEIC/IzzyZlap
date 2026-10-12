"""Generuje wbudowane dźwięki przypomnień Złap (res/raw/*.wav).

Krótkie, miękkie, nowoczesne „UI sounds” syntezowane od zera – bez praw autorskich.
Uruchom: python tools/make_sounds.py
"""
import math
import struct
import wave
from pathlib import Path

RATE = 44100
PEAK = 0.5  # ok. -6 dBFS: zauważalny, ale nie krzykliwy
OUT = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "res" / "raw"


def tone(freq, start, length, partials, decay, attack=0.004, sweep=None, gain=1.0):
    """Jedna nuta: suma harmonicznych (mnożnik, głośność, szybkość wygaszania)."""
    def sample(t):
        t -= start
        if t < 0 or t > length:
            return 0.0
        env = gain * min(1.0, t / attack) * math.exp(-t / decay)
        f = freq
        if sweep:
            f0, f1, dur = sweep
            f = f0 + (f1 - f0) * min(1.0, t / dur)
        return env * sum(a * math.exp(-t * d) * math.sin(2 * math.pi * f * m * t) for m, a, d in partials)
    return sample


def render(name, voices, duration):
    n = int(RATE * duration)
    raw = [sum(v(i / RATE) for v in voices) for i in range(n)]
    fade = int(RATE * 0.03)
    for i in range(fade):  # łagodne wyciszenie na końcu
        raw[n - 1 - i] *= i / fade
    scale = PEAK / max(abs(x) for x in raw)
    OUT.mkdir(parents=True, exist_ok=True)
    with wave.open(str(OUT / f"{name}.wav"), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(b"".join(struct.pack("<h", int(x * scale * 32767)) for x in raw))
    print(name, f"{duration:.1f}s")


# Marimba: drewniane „ding-dong” w górę – ciepłe, przyjazne
MARIMBA = [(1, 1.0, 0), (3.93, 0.35, 18), (9.2, 0.08, 40)]
render("zlap_marimba", [
    tone(783.99, 0.00, 1.0, MARIMBA, 0.28),   # G5
    tone(1046.50, 0.13, 1.0, MARIMBA, 0.32),  # C6
], 1.2)

# Kropla: dwa miękkie „bloop” z rosnącą wysokością – lekkie, nowoczesne
DROP = [(1, 1.0, 0), (2, 0.15, 25)]
render("zlap_kropla", [
    tone(0, 0.00, 0.4, DROP, 0.09, sweep=(520, 1250, 0.05)),
    tone(0, 0.16, 0.5, DROP, 0.11, sweep=(700, 1650, 0.05)),
], 0.75)

# Iskra: szybkie arpeggio dzwoneczków C-E-G – najbardziej zauważalne
BELL = [(1, 1.0, 0), (2.76, 0.4, 6), (5.4, 0.15, 14)]
render("zlap_iskra", [
    tone(1046.50, 0.00, 1.2, BELL, 0.35),  # C6
    tone(1318.51, 0.09, 1.2, BELL, 0.35),  # E6
    tone(1567.98, 0.18, 1.2, BELL, 0.45),  # G6
], 1.5)

# Szkło: stuknięcie w kieliszek i cichsze echo – czyste, eleganckie
GLASS = [(1, 1.0, 0), (2.32, 0.45, 4), (4.25, 0.2, 9), (6.63, 0.08, 16)]
render("zlap_szklo", [
    tone(1760.00, 0.00, 1.4, GLASS, 0.55),             # A6
    tone(2093.00, 0.22, 1.2, GLASS, 0.45, gain=0.45),  # C7, echo
], 1.6)

# Puls: dwa krótkie, miękkie „tik-tik” jak w nowoczesnym UI – dyskretne, ale wyraźne
SOFT_SQUARE = [(1, 1.0, 0), (3, 0.22, 30), (5, 0.08, 60)]
render("zlap_puls", [
    tone(880.00, 0.00, 0.35, SOFT_SQUARE, 0.07),             # A5
    tone(1174.66, 0.13, 0.45, SOFT_SQUARE, 0.09, gain=0.9),  # D6
], 0.65)

# Świt: ciepły, wolniejszy akord w górę z lekkim „chorusem” – najspokojniejszy
WARM = [(1, 1.0, 0), (2, 0.12, 6)]
render("zlap_swit", [
    tone(587.33, 0.00, 1.6, WARM, 0.6, attack=0.02),             # D5
    tone(589.33, 0.00, 1.6, WARM, 0.6, attack=0.02, gain=0.5),   # D5 lekko rozstrojone
    tone(739.99, 0.15, 1.5, WARM, 0.6, attack=0.02),             # F#5
    tone(880.00, 0.30, 1.4, WARM, 0.7, attack=0.02),             # A5
    tone(882.50, 0.30, 1.4, WARM, 0.7, attack=0.02, gain=0.5),   # A5 lekko rozstrojone
], 1.8)
