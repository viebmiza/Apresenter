"""
Ponte local de Speech-to-Text (STT) para o Apresenter usando OpenAI Whisper + sounddevice.
Implementa HTTP + WebSocket (Engine.IO v4 / Socket.IO v4) usando apenas a biblioteca
padrao do Python para evitar dependencias de servidor adicionais.

Exemplos:
    python scripts/whisper_stt_server.py --list-devices
    python scripts/whisper_stt_server.py --model tiny --language pt
    python scripts/whisper_stt_server.py --model small --language pt --device 1 --port 8766
"""

from __future__ import annotations

import argparse
import asyncio
import base64
import hashlib
import json
import queue
import struct
import sys
import threading
import time
import uuid
from datetime import datetime
from typing import Any

import numpy as np
import sounddevice as sd
import whisper

WS_MAGIC = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
COMMON_HALLUCINATIONS = {
    "Obrigado.",
    "Obrigada.",
    "Legendas pela comunidade Amara.org",
    "...",
}


def encode_ws_frame(payload: bytes, opcode: int = 0x1) -> bytes:
    header = bytearray([0x80 | opcode])
    length = len(payload)
    if length < 126:
        header.append(length)
    elif length < 65536:
        header.append(126)
        header.extend(struct.pack("!H", length))
    else:
        header.append(127)
        header.extend(struct.pack("!Q", length))
    return bytes(header) + payload


async def read_ws_frame(reader: asyncio.StreamReader) -> tuple[int, bytes] | None:
    try:
        head = await reader.readexactly(2)
    except (asyncio.IncompleteReadError, ConnectionError):
        return None
    b1, b2 = head[0], head[1]
    opcode = b1 & 0x0F
    masked = (b2 & 0x80) != 0
    length = b2 & 0x7F
    if length == 126:
        length = struct.unpack("!H", await reader.readexactly(2))[0]
    elif length == 127:
        length = struct.unpack("!Q", await reader.readexactly(8))[0]
    mask = await reader.readexactly(4) if masked else b""
    data = bytearray(await reader.readexactly(length))
    if masked:
        for i in range(length):
            data[i] ^= mask[i % 4]
    return opcode, bytes(data)


class SttBridgeServer:
    def __init__(self, host: str, port: int, language: str) -> None:
        self.host = host
        self.port = port
        self.language = language
        self.session_id = uuid.uuid4().hex[:12]
        self.segments: list[dict[str, Any]] = []
        self.in_progress: str = ""
        self.clients: set[asyncio.StreamWriter] = set()
        self.loop: asyncio.AbstractEventLoop | None = None
        self.started_at = time.monotonic()

    def build_payload(self) -> dict[str, Any]:
        recent = self.segments[-40:]
        last_start = recent[-1]["start"] if recent else 0.0
        last_id = str(recent[-1]["id"]) if recent else "0"
        return {
            "session_id": self.session_id,
            "segment_id": last_id,
            "start_time": last_start,
            "speech_type": "final" if not self.in_progress else "partial",
            "segments": recent,
            "in_progress": self.in_progress,
        }

    async def send_sio_event(self, writer: asyncio.StreamWriter, event: str, data: dict[str, Any]) -> None:
        packet = "42" + json.dumps([event, data], ensure_ascii=False)
        writer.write(encode_ws_frame(packet.encode("utf-8"), opcode=0x1))
        await writer.drain()

    async def broadcast_transcript(self) -> None:
        if not self.clients:
            return
        payload = self.build_payload()
        packet = "42" + json.dumps(["transcription_update", payload], ensure_ascii=False)
        frame = encode_ws_frame(packet.encode("utf-8"), opcode=0x1)
        dead: list[asyncio.StreamWriter] = []
        for writer in list(self.clients):
            try:
                writer.write(frame)
                await writer.drain()
            except Exception:
                dead.append(writer)
        for writer in dead:
            self.clients.discard(writer)

    def push_segment_from_thread(self, text: str, duration_s: float) -> None:
        cleaned = text.strip()
        if not cleaned or cleaned in COMMON_HALLUCINATIONS:
            return
        end_s = round(time.monotonic() - self.started_at, 2)
        start_s = round(max(0.0, end_s - duration_s), 2)
        seg = {
            "id": len(self.segments),
            "timestamp": datetime.now().strftime("%H:%M:%S"),
            "text": cleaned,
            "start": start_s,
            "end": end_s,
            "completed": True,
        }
        self.segments.append(seg)
        self.in_progress = ""
        print(f"[{seg['timestamp']}] {cleaned}", flush=True)
        if self.loop is not None:
            asyncio.run_coroutine_threadsafe(self.broadcast_transcript(), self.loop)

    async def handle_connection(self, reader: asyncio.StreamReader, writer: asyncio.StreamWriter) -> None:
        try:
            raw_head = await reader.readuntil(b"\r\n\r\n")
        except Exception:
            writer.close()
            return

        lines = raw_head.decode("latin1").split("\r\n")
        request_line = lines[0] if lines else ""
        parts = request_line.split(" ")
        path = parts[1] if len(parts) > 1 else "/"
        headers: dict[str, str] = {}
        for line in lines[1:]:
            if ":" in line:
                k, v = line.split(":", 1)
                headers[k.strip().lower()] = v.strip()

        if path.startswith("/api/word-highlighting/words"):
            body = json.dumps({"success": True, "enabled": False, "words": []}).encode("utf-8")
            resp = (
                "HTTP/1.1 200 OK\r\n"
                "Content-Type: application/json\r\n"
                f"Content-Length: {len(body)}\r\n"
                "Connection: close\r\n\r\n"
            ).encode("latin1") + body
            writer.write(resp)
            await writer.drain()
            writer.close()
            return

        ws_key = headers.get("sec-websocket-key")
        if headers.get("upgrade", "").lower() != "websocket" or not ws_key:
            body = b"Apresenter Local Whisper STT Bridge"
            resp = (
                "HTTP/1.1 200 OK\r\n"
                "Content-Type: text/plain; charset=utf-8\r\n"
                f"Content-Length: {len(body)}\r\n"
                "Connection: close\r\n\r\n"
            ).encode("latin1") + body
            writer.write(resp)
            await writer.drain()
            writer.close()
            return

        accept = base64.b64encode(hashlib.sha1((ws_key + WS_MAGIC).encode("latin1")).digest()).decode("latin1")
        handshake = (
            "HTTP/1.1 101 Switching Protocols\r\n"
            "Upgrade: websocket\r\n"
            "Connection: Upgrade\r\n"
            f"Sec-WebSocket-Accept: {accept}\r\n\r\n"
        ).encode("latin1")
        writer.write(handshake)
        await writer.drain()

        eio_open = "0" + json.dumps({
            "sid": uuid.uuid4().hex,
            "upgrades": [],
            "pingInterval": 25000,
            "pingTimeout": 20000,
            "maxPayload": 1000000,
        })
        writer.write(encode_ws_frame(eio_open.encode("utf-8"), opcode=0x1))
        await writer.drain()

        self.clients.add(writer)
        ping_task: asyncio.Task[None] | None = None

        async def pinger() -> None:
            while True:
                await asyncio.sleep(20)
                writer.write(encode_ws_frame(b"2", opcode=0x1))
                await writer.drain()

        try:
            while True:
                frame = await read_ws_frame(reader)
                if frame is None:
                    break
                opcode, payload = frame
                if opcode == 0x8:
                    break
                if opcode == 0x9:
                    writer.write(encode_ws_frame(payload, opcode=0xA))
                    await writer.drain()
                    continue
                if opcode != 0x1:
                    continue

                msg = payload.decode("utf-8", errors="replace")
                if msg == "2":
                    writer.write(encode_ws_frame(b"3", opcode=0x1))
                    await writer.drain()
                elif msg == "3":
                    continue
                elif msg.startswith("40"):
                    ack = "40" + json.dumps({"sid": uuid.uuid4().hex[:12]})
                    writer.write(encode_ws_frame(ack.encode("utf-8"), opcode=0x1))
                    await writer.drain()
                    if ping_task is None:
                        ping_task = asyncio.create_task(pinger())
                elif msg.startswith("42"):
                    try:
                        decoded = json.loads(msg[2:])
                        event_name = decoded[0] if isinstance(decoded, list) and decoded else ""
                    except Exception:
                        event_name = ""
                    if event_name == "request_all_entries":
                        await self.send_sio_event(writer, "transcription_update", self.build_payload())
                    elif event_name == "request_all_translation_entries":
                        await self.send_sio_event(
                            writer,
                            "translation_update",
                            {
                                "session_id": self.session_id,
                                "segments": [],
                                "in_progress": "",
                                "target_language_name": "",
                            },
                        )
        except Exception:
            pass
        finally:
            if ping_task is not None:
                ping_task.cancel()
            self.clients.discard(writer)
            try:
                writer.close()
            except Exception:
                pass


def list_input_devices() -> None:
    default_in = sd.default.device[0]
    print("Dispositivos de entrada de audio disponiveis:", flush=True)
    for idx, dev in enumerate(sd.query_devices()):
        if dev.get("max_input_channels", 0) > 0:
            marker = " (padrao)" if idx == default_in else ""
            print(f"  [{idx}] {dev['name']} — {dev['max_input_channels']}ch{marker}", flush=True)


def audio_worker(
    server: SttBridgeServer,
    model_name: str,
    device_index: int | None,
    chunk_seconds: float,
    energy_threshold: float,
    stop_event: threading.Event,
) -> None:
    sample_rate = 16000
    chunk_samples = int(sample_rate * chunk_seconds)
    audio_q: queue.Queue[np.ndarray] = queue.Queue()

    print(f"Carregando modelo Whisper '{model_name}'...", flush=True)
    model = whisper.load_model(model_name)
    resolved_idx = sd.default.device[0] if device_index is None else device_index
    dev_name = sd.query_devices(resolved_idx)["name"]
    print(f"Entrada de audio selecionada: [{resolved_idx}] {dev_name}", flush=True)
    print(f"No Apresenter (aba STT), conecte em: http://{server.host}:{server.port}", flush=True)

    def callback(indata: np.ndarray, frames: int, time_info: Any, status: Any) -> None:
        if status:
            print(f"[audio] {status}", file=sys.stderr, flush=True)
        audio_q.put(indata[:, 0].copy())

    buf = np.zeros(0, dtype=np.float32)
    with sd.InputStream(
        device=device_index,
        samplerate=sample_rate,
        channels=1,
        dtype="float32",
        callback=callback,
    ):
        while not stop_event.is_set():
            try:
                block = audio_q.get(timeout=0.5)
            except queue.Empty:
                continue
            buf = np.concatenate([buf, block])
            if len(buf) < chunk_samples:
                continue
            chunk = buf[:chunk_samples]
            buf = buf[chunk_samples:]
            rms = float(np.sqrt(np.mean(chunk * chunk)))
            if rms < energy_threshold:
                continue
            result = model.transcribe(
                chunk,
                language=server.language,
                fp16=False,
                condition_on_previous_text=False,
                no_speech_threshold=0.65,
            )
            text = (result.get("text") or "").strip()
            if text:
                server.push_segment_from_thread(text, chunk_seconds)


async def run_server(args: argparse.Namespace) -> None:
    server = SttBridgeServer(host=args.host, port=args.port, language=args.language)
    server.loop = asyncio.get_running_loop()
    net_server = await asyncio.start_server(server.handle_connection, args.host, args.port)
    stop_event = threading.Event()

    if not args.dry_run:
        t = threading.Thread(
            target=audio_worker,
            args=(server, args.model, args.device, args.chunk_seconds, args.threshold, stop_event),
            daemon=True,
        )
        t.start()
    else:
        print(f"Dry-run ativo em http://{args.host}:{args.port}", flush=True)

    try:
        async with net_server:
            await net_server.serve_forever()
    finally:
        stop_event.set()


def main() -> None:
    parser = argparse.ArgumentParser(description="Ponte local Whisper -> Apresenter STT (Socket.IO)")
    parser.add_argument("--list-devices", action="store_true", help="Lista placas/microfones de entrada e sai")
    parser.add_argument("--device", type=int, default=None, help="Indice da entrada de audio (ver --list-devices)")
    parser.add_argument("--model", default="tiny", help="Modelo Whisper (tiny, base, small, medium)")
    parser.add_argument("--language", default="pt", help="Codigo do idioma (ex.: pt, en, es)")
    parser.add_argument("--host", default="127.0.0.1", help="IP de escuta (use 0.0.0.0 se rodar em outro PC)")
    parser.add_argument("--port", type=int, default=8766, help="Porta HTTP/Socket.IO")
    parser.add_argument("--chunk-seconds", type=float, default=3.5, help="Bloco de audio em segundos")
    parser.add_argument("--threshold", type=float, default=0.008, help="Limiar RMS minimo contra silencio")
    parser.add_argument("--dry-run", action="store_true", help="Inicia apenas o servidor sem capturar audio")
    args = parser.parse_args()

    if args.list_devices:
        list_input_devices()
        return

    try:
        asyncio.run(run_server(args))
    except KeyboardInterrupt:
        print("\nServidor STT encerrado.", flush=True)


if __name__ == "__main__":
    main()
