#!/usr/bin/env python3
"""Synthesizes the reactor alarm and SCRAM klaxon sounds used by the machine effects.

Both are drawn from plain waveforms, so they are original to this port. Requires NumPy and an ffmpeg with a
Vorbis encoder:

    python3 mods/extremereactors/scripts/generate_sounds.py [--root <sounds dir>]
"""

import argparse
import pathlib
import subprocess
import sys
import tempfile
import wave

import numpy as np

DEFAULT_ROOT = (pathlib.Path(__file__).resolve().parents[1]
                / "src/main/resources/assets/bigreactors/sounds/effects")
RATE = 44100


def envelope(samples, attack, release):
    env = np.ones(samples)
    a, r = int(attack * RATE), int(release * RATE)
    env[:a] = np.linspace(0.0, 1.0, a)
    env[samples - r:] = np.linspace(1.0, 0.0, r)
    return env


def square_ish(frequency, seconds, harmonics=(1, 3, 5, 7)):
    """A soft square wave: odd harmonics with falling amplitude."""
    t = np.arange(int(seconds * RATE)) / RATE
    phase = 2 * np.pi * np.cumsum(np.broadcast_to(frequency, t.shape)) / RATE
    return sum(np.sin(h * phase) / h for h in harmonics)


def alarm():
    """Two alternating tones, 0.3 s each, looping cleanly over 1.2 s."""
    parts = []
    for frequency in (880.0, 660.0, 880.0, 660.0):
        tone = square_ish(frequency, 0.3)
        parts.append(tone * envelope(len(tone), 0.01, 0.03))
    return 0.35 * np.concatenate(parts)


def klaxon():
    """Two rising horn blasts with a gap."""
    blasts = []
    for _ in range(2):
        seconds = 0.75
        t = np.arange(int(seconds * RATE)) / RATE
        frequency = 180.0 + 140.0 * np.minimum(1.0, t / 0.35)
        horn = square_ish(frequency, seconds, harmonics=(1, 2, 3, 4, 5, 6))
        horn *= envelope(len(horn), 0.04, 0.12)
        blasts.append(horn)
        blasts.append(np.zeros(int(0.15 * RATE)))
    return 0.3 * np.concatenate(blasts)


def write_ogg(path, signal):
    pcm = (np.clip(signal, -1.0, 1.0) * 32767).astype(np.int16)
    with tempfile.TemporaryDirectory() as tmp:
        wav = pathlib.Path(tmp) / "sound.wav"
        with wave.open(str(wav), "wb") as out:
            out.setnchannels(1)
            out.setsampwidth(2)
            out.setframerate(RATE)
            out.writeframes(pcm.tobytes())
        subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", str(wav), "-c:a", "libvorbis", "-q:a", "4",
                        "-map_metadata", "-1", "-fflags", "+bitexact", str(path)], check=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--root", type=pathlib.Path, default=DEFAULT_ROOT)
    args = parser.parse_args()
    args.root.mkdir(parents=True, exist_ok=True)
    write_ogg(args.root / "alarm.ogg", alarm())
    write_ogg(args.root / "scram.ogg", klaxon())
    print(f"Wrote alarm.ogg and scram.ogg under {args.root}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
