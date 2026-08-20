# Scansfer

Send a video from one Android phone to another using nothing but a screen and a
camera. No Wi-Fi, no Bluetooth, no cable, no account, no server.

One phone turns the video into a stream of QR codes and plays them. The other
phone watches through its camera and rebuilds the file. Missed frames are
expected and handled — the receiver simply keeps watching until it has enough.

## How it works

The hard part of sending data over a screen is that the camera misses frames
constantly, and there is no back channel to ask for a retransmission.

Scansfer solves that with a **Luby-transform fountain code**. The sender does not
number the blocks 1..N and hope; it emits an endless stream of coded symbols,
each an XOR of a pseudo-randomly chosen subset of blocks. The receiver collects
*any* sufficiently large subset and peels the original blocks back out. Point the
camera away for ten seconds and nothing breaks — the transfer just takes a little
longer.

The first N symbols are systematic (one per raw block), so a clean read finishes
in exactly N frames; everything after that patches whatever holes remain.

### Frame format

Every QR code carries one self-delimiting, self-verifying frame:

```
offset  size  field
0       3     magic "SXF"
3       1     protocol version
4       1     frame type (0 = manifest, 1 = data)
5       2     body length
7       n     body
7+n     4     CRC32 over bytes [0, 7+n)
```

A manifest frame carries the file name, size, MIME type, block geometry and a
CRC32 of the whole video. It is re-broadcast every 24 frames so the receiver can
join a transfer that is already running. A data frame carries a session id, the
symbol seed, and the coded payload.

Two details that matter more than they look:

- **Bytes go over QR byte mode as ISO-8859-1**, which is a 1:1 byte↔char mapping
  and is ZXing's default for byte mode, so no ECI header is emitted and the bytes
  come back out of a decoder exactly as they went in. `QrRoundTripTest` proves
  this for every profile, including `0x00` and the whole `0x80..0xFF` range.
- **Manifest frames are padded to the data frame length**, so every code in a
  session encodes to the same QR version. A code that resized mid-stream would
  force the receiving camera to refocus every 24 frames.

The CRC on every frame means a torn or garbled read is discarded rather than
corrupting the file, so the receiver can afford to be greedy about what it tries
to decode.

## Speed

QR codes are a low-bandwidth channel, and the app is honest about it. Measured
frame densities:

| Profile  | Block | ECC | QR version | Modules | Ceiling  | 5 MB clip |
|----------|-------|-----|------------|---------|----------|-----------|
| Steady   | 400 B | Q   | 19         | 93      | 4 KB/s   | ~41 min   |
| Balanced | 1 KB  | M   | 26         | 121     | 12 KB/s  | ~14 min   |
| Turbo    | 1.8 KB| L   | 31         | 141     | 27 KB/s  | ~6 min    |

Frame rates are divisors of 60 so each code lands on a whole number of display
refreshes — a code that changes mid-refresh tears and fails its checksum.

The estimates shown in the app apply a 1.9x factor over the raw byte count, which
covers fountain-coding overhead plus a realistic share of frames the camera never
catches. Short clips are the happy path; the send screen says so when a file is
large enough to be a problem.

## Using it

**Sending** — pick a video, choose a speed, tap Start. The screen goes white,
brightness pins to maximum and the sleep timeout is blocked. Keep it running
until the other phone says it's done.

**Receiving** — point the camera at the sender's screen from 15–30 cm so the code
fills the frame. Progress, live throughput and a remaining-time estimate update
as blocks land. When the last block arrives the file is checked against the
manifest CRC32 and saved to `Movies/Scansfer`, then offered for playback or
sharing.

## Build

```bash
./gradlew assembleDebug        # debug APK
./gradlew assembleRelease      # minified release APK
./gradlew testDebugUnitTest    # codec, protocol and QR round-trip tests
```

Requires JDK 17 and the Android SDK (compileSdk 35). Minimum supported device is
Android 10 (API 29), which is what lets the app write to the gallery without any
storage permission — the only permission it asks for is the camera.

## Design notes

- **ML Kit does the detecting** (bundled model, so it works with no network and
  no Play Services download) because it is markedly better than the alternatives
  at locking onto a dense, moving code. Its `rawBytes` is the byte-exact payload.
  A few devices only surface a decoded string; the analyzer notices detections
  that never parse and falls back to ZXing, which always returns raw byte
  segments. This is most of the 25 MB release APK, and worth it for an app whose
  point is working with no network at all.
- **ZXing does the encoding**, one bitmap pixel per QR module, scaled up with
  nearest-neighbour filtering so module edges stay razor sharp for free.
- **The video is memory-mapped, not loaded**, so the fountain encoder can hop
  around a large file without putting it on the heap. On the receiving side
  decoded blocks are streamed straight into MediaStore rather than assembled into
  one big array first.
- **Camera analysis runs at 1080p.** At 720p the modules of a version-31 symbol
  land below one pixel each.
