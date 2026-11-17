"""Generates the alert signals in app/src/main/res/raw (wayfinder alerts 04): own sounds, no licence questions.

alert_fault.wav     — three 2.3 kHz pulses (150 ms on, 100 ms off), the "Неисправность" signal.
alert_attention.wav — a soft two-tone chime (880 Hz then 660 Hz), the "Внимание" signal.

Run: python3 tools/alert_sounds/make_sounds.py
"""
import math
import os
import struct
import wave

RATE = 22050
OUT = os.path.join(os.path.dirname(__file__), "..", "..", "app", "src", "main", "res", "raw")


def tone(freq, ms, volume, fade_ms=8, harmonics=((1, 1.0),)):
    n = int(RATE * ms / 1000)
    fade = int(RATE * fade_ms / 1000)
    out = []
    for i in range(n):
        env = min(1.0, i / fade if fade else 1.0, (n - i) / fade if fade else 1.0)
        v = sum(a * math.sin(2 * math.pi * freq * k * i / RATE) for k, a in harmonics)
        out.append(volume * env * v)
    return out


def chime(freq, ms, volume):
    # Struck bell: fast attack, exponential decay, a little second harmonic.
    n = int(RATE * ms / 1000)
    out = []
    for i in range(n):
        t = i / RATE
        env = min(1.0, i / (RATE * 0.005)) * math.exp(-t * 6.0)
        v = math.sin(2 * math.pi * freq * t) + 0.3 * math.sin(2 * math.pi * 2 * freq * t)
        out.append(volume * env * v / 1.3)
    return out


def silence(ms):
    return [0.0] * int(RATE * ms / 1000)


def write(name, samples):
    path = os.path.join(OUT, name)
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(b"".join(struct.pack("<h", int(max(-1.0, min(1.0, s)) * 32767)) for s in samples))
    print(path, len(samples) / RATE, "s")


fault = []
for _ in range(3):
    fault += tone(2300, 150, 0.85, harmonics=((1, 0.8), (3, 0.2))) + silence(100)
write("alert_fault.wav", fault)

attention = chime(880, 450, 0.7) + chime(660, 700, 0.7)
write("alert_attention.wav", attention)
