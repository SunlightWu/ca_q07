"""Disposable-emulator helper; fixed test token is NOT an app default.
capture requires adb forward tcp:37183 tcp:27183.
replay requires adb reverse tcp:27183 tcp:37184.
"""
import argparse
import hashlib
import hmac
import json
import os
from pathlib import Path
import socket
import struct
import threading
import time

TOKEN = b"0123456789ABCDEF"

def read(s, n):
    b = bytearray()
    while len(b) < n:
        part = s.recv(n - len(b))
        if not part:
            raise EOFError(f"EOF after {len(b)}/{n} bytes")
        b.extend(part)
    return bytes(b)

def proof(role, header, nonce):
    return hmac.new(TOKEN, bytes([role]) + header + nonce, hashlib.sha256).digest()

def connect(channel, session):
    s = socket.create_connection(("127.0.0.1", 37183), timeout=12)
    header = struct.pack(">IIB", 0x5130374D, 1, channel) + session
    s.sendall(header)
    nonce = read(s, 32)
    s.sendall(proof(0, header, nonce))
    assert read(s, 1) == b"\x01"
    assert hmac.compare_digest(read(s, 32), proof(1, header, nonce))
    return s

def capture(path):
    session = os.urandom(16)
    with connect(1, session) as video, connect(2, session) as control:
        prefix = read(video, 68)
        chunks = [prefix]
        print("Device:", prefix[:64].split(b"\0")[0].decode(), "codec:", prefix[64:].decode(), flush=True)
        width = height = frames = 0
        video.settimeout(3)
        for _ in range(35):
            try:
                header = read(video, 12)
            except TimeoutError:
                if frames:
                    break  # scrcpy sends no new frame for a static display.
                raise
            chunks.append(header)
            if header[0] & 0x80:
                _, width, height = struct.unpack(">III", header)
                print("Session:", width, height, flush=True)
            else:
                flags, size = struct.unpack(">QI", header)
                assert 0 < size <= 4 * 1024 * 1024
                chunks.append(read(video, size))
                if not flags & (1 << 62):
                    frames += 1
                if frames == 1:
                    for action in (0, 2, 1):
                        packet = struct.pack(">BBQiiHHHII", 2, action, 0, width // 3, height // 2, width, height, 0 if action == 1 else 65535, 0, 0)
                        control.sendall(packet)
                        time.sleep(0.08)
        path.write_bytes(b"".join(chunks))
        print(json.dumps({"frames": frames, "width": width, "height": height, "bytes": path.stat().st_size, "touch_sent": True}), flush=True)

def authenticate(s):
    s.settimeout(8)
    header = read(s, 25)
    magic, version, channel = struct.unpack(">IIB", header[:9])
    assert magic == 0x5130374D and version == 1
    nonce = os.urandom(32)
    s.sendall(nonce)
    assert hmac.compare_digest(read(s, 32), proof(0, header, nonce))
    s.sendall(b"\x01" + proof(1, header, nonce))
    s.settimeout(None)
    return channel, header[9:]

def replay(path):
    data = path.read_bytes()
    frames = []
    offset = 68
    while offset < len(data):
        h = data[offset:offset+12]
        size = 0 if h[0] & 0x80 else struct.unpack(">I", h[8:12])[0]
        frames.append(data[offset:offset+12+size])
        offset += 12 + size
    def controls(s):
        try:
            while True:
                kind = read(s, 1)[0]
                rest = read(s, 31 if kind == 2 else 13)
                print("CONTROL", kind, rest.hex(), flush=True)
        except Exception as e:
            print("Control ended:", e, flush=True)
    with socket.socket() as server:
        server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        server.bind(("127.0.0.1", 37184))
        server.listen(4)
        print("Replay listening on 37184", flush=True)
        while True:
            video = control = None
            try:
                while True:
                    s, _ = server.accept()
                    channel, session = authenticate(s)
                    if channel == 0:
                        s.close()
                    elif channel == 1:
                        video, sid = s, session
                    elif channel == 2 and video and sid == session:
                        control = s
                        break
                    else:
                        s.close()
                threading.Thread(target=controls, args=(control,), daemon=True).start()
                video.sendall(data[:68])
                for packet in frames:
                    if packet[0] & 0x80:
                        video.sendall(packet)
                        break
                print("Video streaming", flush=True)
                while True:
                    for packet in frames:
                        if packet[0] & 0x80:
                            continue
                        video.sendall(packet)
                        time.sleep(0.05)
            except Exception as e:
                print("Replay session ended:", e, flush=True)
            finally:
                if video: video.close()
                if control: control.close()

if __name__ == "__main__":
    p = argparse.ArgumentParser()
    p.add_argument("mode", choices=("capture", "replay"))
    p.add_argument("file", type=Path)
    args = p.parse_args()
    (capture if args.mode == "capture" else replay)(args.file)
