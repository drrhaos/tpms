#!/usr/bin/env python3
"""Fuzz harness for Aa55SerialProtocol (ticket stability/02-aa55-garbage-rate).

Faithful Python port of:
  - Aa55SerialProtocol.feed / extractFrame / isValidChecksum / checksum / parseFrame
  - SensorValidator.sanitize (minLiveWheelPressureKpa=100, showSpareWheel False/True)
  - AlertEvaluator.evaluate (150 / 350 kPa, 85 C, battery < 10)
plus optional hardening switches (H1 length+code whitelist, H2 byte-wise resync,
H3 "two identical frames per slot within 5 s").

Usage:  python3 aa55_fuzz.py [--quick]      (fixed seeds, deterministic)
"""
import random
import sys
from array import array
from collections import Counter

# ---------------------------------------------------------------- constants (from Kotlin)
H0, H1 = 0x55, 0xAA
FRAME_TIRE_STATE = 0x08
PRESSURE_SCALE = 3.44
TEMP_OFFSET = 50
MAX_FRAME_SIZE = 64
MAX_BUFFER_SIZE = 512
MAX_FRAMES_PER_CHUNK = 16
MAX_SCAN_BYTES = 256
ST_NO_SIGNAL, ST_LEAK, ST_LOWBAT = 0x80, 0x08, 0x10
LABELS = {0x00: "FL", 0x01: "FR", 0x10: "RL", 0x11: "RR", 0x05: "SP"}
KNOWN_LENGTHS = {6, 7, 8, 9}
MIN_LIVE_WHEEL_KPA = 100.0
LOW_KPA, HIGH_KPA, HIGH_TEMP = 150.0, 350.0, 85.0

# realistic traffic model (docs/research/aa55-status-flags.md 1.1: full cycle ~2-2.5 s, 0.4-0.6 s/frame)
FRAME_PERIOD_S = 0.5          # one tire-state frame every 0.5 s => 2 frames/s, 16 B/s
LINE_RATE_BPS = 1920          # 19200 8N1 saturated with garbage


def xor_checksum(fr):
    length = fr[2]
    if length < 2 or length > len(fr):
        return 0
    r = fr[0]
    for i in range(1, length - 1):
        r ^= fr[i]
    return r & 0xFF


def valid_checksum(fr):
    return len(fr) >= 4 and fr[-1] == xor_checksum(fr)


def make_frame(body):
    """body = bytes after 55 AA (without length and checksum)."""
    fr = [H0, H1, len(body) + 4] + list(body) + [0]
    fr[-1] = xor_checksum(fr)
    return bytes(fr)


# ---------------------------------------------------------------- reference port (literal, slow)
class RefParser:
    """Line-by-line port of Aa55SerialProtocol (MutableList<Byte> + removeAt(0))."""

    def __init__(self):
        self.buf = []

    def feed(self, chunk):
        if not chunk:
            return []
        self.buf.extend(chunk)
        if len(self.buf) > MAX_BUFFER_SIZE:
            del self.buf[0:len(self.buf) - MAX_BUFFER_SIZE // 2]
        out, n = [], 0
        while n < MAX_FRAMES_PER_CHUNK:
            fr = self._extract()
            if fr is None:
                break
            n += 1
            out.append(fr)
        return out

    def _extract(self):
        guard = 0
        while len(self.buf) >= 3:
            ok = guard < MAX_SCAN_BYTES
            guard += 1
            if not ok:
                break
            if self.buf[0] != H0 or self.buf[1] != H1:
                self.buf.pop(0)
                continue
            length = self.buf[2]
            if length < 4 or length > MAX_FRAME_SIZE:
                self.buf.pop(0)
                continue
            if len(self.buf) < length:
                return None
            fr = bytes(self.buf[:length])
            for _ in range(length):
                self.buf.pop(0)
            if not valid_checksum(fr):
                continue
            return fr
        if guard >= MAX_SCAN_BYTES:
            self.buf.clear()
        return None


# ---------------------------------------------------------------- fast port (+hardening switches)
class Parser:
    def __init__(self, len_whitelist=False, bytewise_resync=False):
        self.buf = bytearray()
        self.base = 0  # global offset (in fed stream) of buf[0]
        self.lw = len_whitelist
        self.bw = bytewise_resync

    def _drop(self, n):
        del self.buf[:n]
        self.base += n

    def feed(self, chunk):
        self.buf += chunk
        if len(self.buf) > MAX_BUFFER_SIZE:
            self._drop(len(self.buf) - MAX_BUFFER_SIZE // 2)
        out = []
        while len(out) < MAX_FRAMES_PER_CHUNK:
            fr = self._extract()
            if fr is None:
                break
            out.append(fr)
        return out  # list of (offset, frame_bytes)

    def _extract(self):
        buf = self.buf
        guard = 0
        while len(buf) >= 3:
            if guard >= MAX_SCAN_BYTES:
                guard += 1
                break
            guard += 1
            if buf[0] != H0 or buf[1] != H1:
                idx = buf.find(b"\x55\xaa", 1)
                maxrm = len(buf) - 2
                n = maxrm if idx == -1 else min(idx, maxrm)
                n = max(1, min(n, MAX_SCAN_BYTES + 1 - guard))
                guard += n - 1
                self._drop(n)
                continue
            length = buf[2]
            bad_len = length < 4 or length > MAX_FRAME_SIZE or (self.lw and length not in KNOWN_LENGTHS)
            if bad_len:
                self._drop(1)
                continue
            if len(buf) < length:
                return None
            fr = bytes(buf[:length])
            off = self.base
            ok = valid_checksum(fr)
            if not ok:
                self._drop(1 if self.bw else length)
                continue
            self._drop(length)
            return off, fr
        if guard >= MAX_SCAN_BYTES:
            self._drop(len(buf))
        return None


# ---------------------------------------------------------------- parseFrame / evaluate / sanitize
def parse_frame(fr, code_whitelist=False):
    if len(fr) < 8 or fr[2] != FRAME_TIRE_STATE:
        return None
    code = fr[3]
    if code_whitelist and code not in LABELS:
        return None
    p = fr[4] * PRESSURE_SCALE
    t = float(fr[5] - TEMP_OFFSET)
    st = fr[6]
    label = LABELS.get(code, "SENSOR_%02X" % code)
    batt = 5 if st & ST_LOWBAT else 50
    if st & ST_NO_SIGNAL:
        hw = "SENSOR_LOST"
    elif st & ST_LEAK:
        hw = "LEAK(LOW_PRESSURE)"
    elif st & ST_LOWBAT:
        hw = "BATTERY_LOW"
    else:
        hw = None
    thr = None
    if label != "SP":
        if p < LOW_KPA:
            thr = "LOW_PRESSURE"
        elif p > HIGH_KPA:
            thr = "HIGH_PRESSURE"
        elif t > HIGH_TEMP:
            thr = "HIGH_TEMP"
        elif batt < 10:
            thr = "BATTERY_LOW"
    return dict(code=code, label=label, p=p, t=t, st=st, alert=hw or thr)


def sanitize(s, show_spare):
    if s is None:
        return None
    sp = s["label"] == "SP"
    if sp and not show_spare:
        return None
    if not (0.0 <= s["p"] <= 2000.0) or not (-60.0 <= s["t"] <= 150.0):
        return None
    if sp and s["p"] < MIN_LIVE_WHEEL_KPA:
        return None
    return s


# ---------------------------------------------------------------- stream generators
class Stream:
    """Fed byte stream + per-byte tag (frame id or -1 for noise) + per-frame meta."""

    def __init__(self):
        self.data = bytearray()
        self.tag = array("i")
        self.frames = []          # (bytes, kind)
        self.frame_time = []      # seconds
        self.chunks = []          # chunk sizes actually fed

    def add_frame(self, fr, kind, t):
        fid = len(self.frames)
        self.frames.append((fr, kind))
        self.frame_time.append(t)
        self.data += fr
        self.tag.extend([fid] * len(fr))

    def add_noise(self, rnd, n):
        self.data += bytes(rnd.getrandbits(8) for _ in range(n))
        self.tag.extend([-1] * n)


class Wheel:
    def __init__(self, rnd, code, p, t):
        self.rnd, self.code, self.p, self.t = rnd, code, p, t

    def frame(self):
        if self.rnd.random() < 0.05:  # sensor update: receiver cache changes rarely
            self.p = min(255, max(0, self.p + self.rnd.choice((-1, 1))))
            self.t = min(255, max(0, self.t + self.rnd.choice((-1, 1))))
        return make_frame([self.code, self.p, self.t, 0x00])


def gen_traffic(rnd, n_frames, wheels_codes=(0x00, 0x01, 0x10, 0x11), extras=False):
    """Returns list of (frame_bytes, kind, time)."""
    wheels = [Wheel(rnd, c, rnd.randint(0x3C, 0x48), rnd.randint(0x46, 0x55)) for c in wheels_codes]
    out = []
    t = 0.0
    i = 0
    while len(out) < n_frames:
        w = wheels[i % len(wheels)]
        i += 1
        if w.code == 0x05 and extras:
            out.append((make_frame([0x05, 0x46, 0x46, 0x00]), "sp_dummy", t))
        elif w.code == 0x11 and extras and (i // 5) % 50 == 0:
            # dead RR sensor reported with the *real* no-signal bit 0x20
            out.append((make_frame([0x11, w.p, w.t, 0x20]), "nosignal_0x20", t))
        else:
            out.append((w.frame(), "tire", t))
        t += FRAME_PERIOD_S
        if extras:
            r = rnd.random()
            if r < 0.01:   # query-ID answer: 5 x '09' frames (N = 1..5; empty spare = 00 00 00 05)
                for n in range(1, 6):
                    ids = [0, 0, 0, 5] if n == 5 else [rnd.getrandbits(8) for _ in range(4)]
                    out.append((make_frame([n] + ids), "id_09", t))
            elif r < 0.015:
                out.append((make_frame([0x18, rnd.choice(list(LABELS))]), "pair_06_18", t))
            elif r < 0.02:
                out.append((make_frame([0x30, rnd.choice(list(LABELS)), rnd.choice(list(LABELS))]), "swap_07_30", t))
            elif r < 0.03:
                out.append((make_frame([0x00, 0x88]), "hs_06_00_88", t))
            elif r < 0.04:
                out.append((make_frame([0xB5, rnd.getrandbits(8)]), "ack_06_B5", t))
    return out


def build(rnd, traffic, steal_p=0.0, del_q=0.0, burst_p=0.0, chunk_max=64):
    """Serialize traffic; random single-byte deletions (del_q per byte), noise bursts
    (burst_p per frame, 1..64 bytes, inserted at a random point inside the stream), then cut
    into reads of 1..chunk_max bytes, each read stolen with prob steal_p (probe()/parallel read)."""
    raw = bytearray()
    tag = array("i")
    frames, ftime = [], []
    for fr, kind, t in traffic:
        fid = len(frames)
        frames.append((fr, kind))
        ftime.append(t)
        if burst_p and rnd.random() < burst_p:
            cut = rnd.randint(0, len(fr))
            n = rnd.randint(1, 64)
            raw += fr[:cut]
            tag.extend([fid] * cut)
            raw += bytes(rnd.getrandbits(8) for _ in range(n))
            tag.extend([-1] * n)
            raw += fr[cut:]
            tag.extend([fid] * (len(fr) - cut))
        else:
            raw += fr
            tag.extend([fid] * len(fr))
    if del_q:
        keep = bytearray()
        ktag = array("i")
        for b, g in zip(raw, tag):
            if rnd.random() >= del_q:
                keep.append(b)
                ktag.append(g)
        raw, tag = keep, ktag
    s = Stream()
    s.frames, s.frame_time = frames, ftime
    pos = 0
    while pos < len(raw):
        n = rnd.randint(1, chunk_max)
        if not (steal_p and rnd.random() < steal_p):
            s.data += raw[pos:pos + n]
            s.tag.extend(tag[pos:pos + n])
            s.chunks.append(min(n, len(raw) - pos))
        pos += n
    return s


def noise_stream(rnd, nbytes, header_every=0):
    s = Stream()
    data = bytearray(rnd.randbytes(nbytes)) if hasattr(rnd, "randbytes") else bytearray(
        rnd.getrandbits(8) for _ in range(nbytes))
    if header_every:
        for i in range(0, nbytes - 3, header_every):
            data[i:i + 3] = b"\x55\xaa\x08"
    s.data = data
    s.tag = None
    pos = 0
    while pos < nbytes:
        n = rnd.randint(1, 64)
        s.chunks.append(min(n, nbytes - pos))
        pos += n
    return s


# ---------------------------------------------------------------- run + classify
def run(s, len_whitelist=False, bytewise=False, code_whitelist=False, confirm=False, noise_bps=None):
    p = Parser(len_whitelist, bytewise)
    accepted = []
    pos = 0
    for n in s.chunks:
        accepted += p.feed(bytes(s.data[pos:pos + n]))
        pos += n
    tag = s.tag
    nfr = len(s.frames)
    present = Counter(tag) if tag is not None else Counter()
    contiguous = set()
    if tag is not None:
        first = {}
        for i, g in enumerate(tag):
            if g >= 0 and g not in first:
                first[g] = i
        for g, i in first.items():
            ln = len(s.frames[g][0])
            if present[g] == ln and all(tag[i + k] == g for k in range(ln)):
                contiguous.add(g)
    res = Counter()
    fa_codes, fa_alerts, fa_alerts_sp = Counter(), Counter(), Counter()
    kind_acc = Counter()
    got_true = set()
    last = {}  # H3: code -> (payload, time)
    for off, fr in accepted:
        fid = -2
        if tag is not None:
            g = tag[off]
            if g in contiguous and tag[off] == g and s.frames[g][0] == fr and (off == 0 or tag[off - 1] != g):
                fid = g
        sensor = parse_frame(fr, code_whitelist)
        if fid >= 0:
            kind_acc[s.frames[fid][1]] += 1
        if sensor is None:
            if fid < 0:
                res["fa_frame_nonstate"] += 1
            continue
        if confirm:
            if tag is not None:
                fprev = max((tag[off - k] for k in range(1, min(off, 200)) if tag[off - k] >= 0), default=0)
                now = s.frame_time[fid] if fid >= 0 else s.frame_time[fprev]
            else:
                now = off / noise_bps
            key = bytes(fr[4:7])
            prev = last.get(sensor["code"])
            last[sensor["code"]] = (key, now)
            if not (prev and prev[0] == key and now - prev[1] <= 5.0):
                if fid >= 0:
                    res["true_unconfirmed"] += 1
                else:
                    res["fa_blocked_by_confirm"] += 1
                continue
        if fid >= 0:
            got_true.add(fid)
            if sanitize(sensor, False):
                res["true_pass_show0"] += 1
            continue
        res["fa"] += 1
        # benign = garbage frame whose code/pressure/temp/status equal the real frame it was cut from
        # (source = frame owning the tire-code byte; header bytes may come from a damaged predecessor)
        src = tag[off + 3] if tag is not None else -1
        if src >= 0 and s.frames[src][0][2] == 8 and s.frames[src][0][3:7] == fr[3:7]:
            res["fa_benign"] += 1
            continue
        res["harm"] += 1
        fa_codes[sensor["label"] if sensor["label"] in LABELS.values() else "SENSOR_XX"] += 1
        a0, a1 = sanitize(sensor, False), sanitize(sensor, True)
        if a0:
            res["fa_pass_show0"] += 1
            fa_alerts[a0["alert"] or "none"] += 1
        if a1:
            res["fa_pass_show1"] += 1
            if a1["label"] == "SP":
                fa_alerts_sp[a1["alert"] or "none"] += 1
    if tag is not None:
        state_ids = [i for i, (fr, k) in enumerate(s.frames) if fr[2] == 8]
        intact = [i for i in state_ids if i in contiguous]
        res["state_frames"] = len(state_ids)
        res["intact"] = len(intact)
        res["intact_lost"] = sum(1 for i in intact if i not in got_true)
        res["damaged"] = len(state_ids) - len(intact)
        res["hours"] = (s.frame_time[-1] + FRAME_PERIOD_S) / 3600.0
    res["mb"] = len(s.data) / 1e6
    return res, fa_codes, fa_alerts, fa_alerts_sp, kind_acc


CONFIGS = [
    ("base", {}),
    ("H1 len{6..9}+code whitelist", dict(len_whitelist=True, code_whitelist=True)),
    ("H2 byte-wise resync", dict(bytewise=True)),
    ("H1+H2", dict(len_whitelist=True, code_whitelist=True, bytewise=True)),
    ("H3 confirm x2/5s", dict(confirm=True)),
    ("H1+H2+H3", dict(len_whitelist=True, code_whitelist=True, bytewise=True, confirm=True)),
]


def fmt_row(name, cfg, r):
    h = r["harm"]
    per_mb = h / r["mb"] if r["mb"] else 0
    per_h = h / r["hours"] if r.get("hours") else per_mb * LINE_RATE_BPS * 3600 / 1e6
    loss = (r["intact_lost"] / r["intact"] * 100) if r.get("intact") else 0.0
    return (f"| {name} | {cfg} | {r['fa']} | {h} | {per_mb:.2f} | {per_h:.3f} | {r['fa_pass_show0']} | "
            f"{r['fa_pass_show1']} | {loss:.3f}% | {r.get('damaged', 0)} |")


def selftest():
    rnd = random.Random(7)
    tr = gen_traffic(rnd, 20000, (0x00, 0x01, 0x10, 0x11, 0x05), extras=True)
    s = build(rnd, tr, steal_p=0.05, del_q=0.01, burst_p=0.05)
    noise = noise_stream(random.Random(8), 200000, header_every=97)
    for st in (s, noise):
        ref, fast = RefParser(), Parser()
        pos = 0
        a, b = [], []
        for n in st.chunks:
            ch = bytes(st.data[pos:pos + n])
            pos += n
            a += ref.feed(ch)
            b += [fr for _, fr in fast.feed(ch)]
        assert a == b, "fast parser diverges from literal port"
    assert make_frame([0x00, 0x00, 0x47, 0x00]) == bytes.fromhex("55AA08000047 00B0".replace(" ", ""))
    assert make_frame([0x19, 0x00]) == bytes.fromhex("55AA061900E0")
    print("selftest OK (literal port == fast port; checksum vectors from research doc)")


def main():
    quick = "--quick" in sys.argv
    k = 10 if quick else 1
    selftest()
    header = ("| scenario | config | FA all (checksum-ok garbage windows) | FA harmful (values != source frame) | harmful/MB | "
              "harmful/hour | harmful pass validator (spare off) | harmful pass validator (spare on) | "
              "intact-frame loss (H3: incl. delayed) | damaged frames |\n|---|---|---|---|---|---|---|---|---|---|")
    print(header)
    details = []
    scen = [
        ("a noise 10MB", lambda: noise_stream(random.Random(1), 10_000_000 // k), LINE_RATE_BPS),
        ("a2 noise+'55AA08' every 64B (4MB)", lambda: noise_stream(random.Random(2), 4_000_000 // k, 64), LINE_RATE_BPS),
        ("b clean, chunks 1..64", lambda: build(random.Random(3), gen_traffic(random.Random(30), 400_000 // k)), None),
        ("c del 1e-3/B + steal 1% reads", lambda: build(random.Random(4), gen_traffic(random.Random(40), 400_000 // k),
                                                          steal_p=0.01, del_q=1e-3), None),
        ("c' del 1e-2/B + steal 5% reads", lambda: build(random.Random(5), gen_traffic(random.Random(50), 200_000 // k),
                                                           steal_p=0.05, del_q=1e-2), None),
        ("d noise bursts 5%/frame", lambda: build(random.Random(6), gen_traffic(random.Random(60), 200_000 // k),
                                                    burst_p=0.05), None),
        ("e real mix (09/06/07, SP dummy, 0x20)", lambda: build(random.Random(7), gen_traffic(
            random.Random(70), 200_000 // k, (0x00, 0x01, 0x10, 0x11, 0x05), extras=True)), None),
        ("e' real mix + c' damage", lambda: build(random.Random(8), gen_traffic(
            random.Random(80), 200_000 // k, (0x00, 0x01, 0x10, 0x11, 0x05), extras=True),
            steal_p=0.05, del_q=1e-2), None),
    ]
    for name, mk, bps in scen:
        s = mk()
        for cname, cfg in CONFIGS:
            if name.startswith("b") and cname != "base":
                continue
            r, codes, alerts, alerts_sp, kinds = run(s, noise_bps=bps, **cfg)
            print(fmt_row(name, cname, r))
            if cname == "base":
                details.append((name, r, codes, alerts, alerts_sp, kinds))
    print("\nBaseline details:")
    for name, r, codes, alerts, alerts_sp, kinds in details:
        print(f"- {name}: harmful FA by code {dict(codes)}; harmful FA alerts after validator (spare off) {dict(alerts)}; "
              f"harmful SP-FA alerts (spare on) {dict(alerts_sp)}; true frames accepted by kind {dict(kinds)}; "
              f"nonstate garbage frames {r['fa_frame_nonstate']}; H3-related {r['true_unconfirmed']}")


if __name__ == "__main__":
    main()
