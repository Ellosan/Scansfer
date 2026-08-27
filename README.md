# Scansfer

Send a photo, video or any other file from one Android phone to another using
nothing but a screen and a camera. No Wi-Fi, no Bluetooth, no cable, no account,
no server.

One phone turns the file into a stream of QR codes and plays them. The other
phone watches through its camera and rebuilds it. Missed frames are expected and
handled — the receiver simply keeps watching until it has enough.

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
CRC32 of the whole file. It is re-broadcast every 24 frames so the receiver can
join a transfer that is already running. A data frame carries a session id, the
symbol seed, and the coded payload.

What kind of thing is being sent is **derived from the manifest's MIME type**,
not sent as its own field — which is why photo support in 2.0 and arbitrary files
in 2.2 both landed without touching the frame format. The transfer itself is
bytes in, identical bytes out, so a photo keeps its EXIF metadata and orientation
and a file arrives byte-for-byte.

One caveat on mixing versions: a 2.2 sender offering a plain file to a pre-2.2
receiver will have it filed as a video, because older builds only knew those two
kinds. Photos and videos interoperate in every direction; plain files need 2.2 at
both ends.

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
frame densities, with estimates for a 2 MB photo and a 5 MB clip:

| Profile  | Block | ECC | QR version | Modules | Ceiling  | 2 MB photo | 5 MB clip |
|----------|-------|-----|------------|---------|----------|------------|-----------|
| Steady   | 400 B | Q   | 19         | 93      | 4 KB/s   | ~17 min    | ~41 min   |
| Balanced | 1 KB  | M   | 26         | 121     | 12 KB/s  | ~5.5 min   | ~14 min   |
| Turbo    | 1.8 KB| L   | 31         | 141     | 27 KB/s  | ~2.5 min   | ~6 min    |

Photos are the comfortable case; videos are usable but ask for patience.

Frame rates are divisors of 60 so each code lands on a whole number of display
refreshes — a code that changes mid-refresh tears and fails its checksum.

The estimates shown in the app apply a 1.9x factor over the raw byte count, which
covers fountain-coding overhead plus a realistic share of frames the camera never
catches. Short clips are the happy path; the send screen says so when a file is
large enough to be a problem.

## Using it

**Sending** — the send screen has two tabs. *Photo or video* uses the system
photo picker; *File* uses the document picker and takes anything at all. Choose a
speed, then tap Start. The screen goes white, brightness pins to maximum and the sleep timeout is
blocked. Keep it running until the other phone says it's done.

**Receiving** — point the camera at the sender's screen from 15–30 cm so the code
fills the frame. Progress, live throughput and a remaining-time estimate update
as blocks land. When the last block arrives the file is checked against the
manifest CRC32 and saved by kind — `Pictures/Scansfer`, `Movies/Scansfer`, or
`Downloads/Scansfer` for anything that is neither — then offered for opening or
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

Every dependency is FLOSS, which keeps the app eligible for F-Droid: AndroidX and
CameraX (Apache-2.0), zxing-cpp and ZXing (Apache-2.0), Accompanist (Apache-2.0).
No Google Play Services, Firebase or ML Kit.

## Premium

Sending photos and videos is free. The File tab is unlocked with a one-off code,
verified entirely on device.

The app ships `app/src/main/res/raw/unlock_codes.bin`, a sorted table of
truncated SHA-256 digests — one per issued code. Redeeming hashes the entered
code and binary-searches that table, so there is no server to call, nothing to be
down, and no network permission needed. The codes themselves are never in this
repository and cannot be recovered from the hashes.

This is not, and cannot be, tamper-proof. The source is public and MIT licensed,
so anyone willing to edit one line and rebuild can remove the check. The gate
exists to make paying the easy path for ordinary users, not to make bypassing it
impossible — which is not achievable for open-source software, and pretending
otherwise would only mean shipping something more annoying that fails just as
surely.

Receiving is deliberately never gated. Whoever receives a file did not choose
what was sent, and refusing to accept it would punish the wrong person.

Codes are not tied to a device, so they survive a reinstall or a new phone. That
also means a code can be passed on; that trade favours the person who paid.

## Releasing

Every published APK is signed with `scansfer.jks`. Android will only install an
update over an existing app when both are signed with the same key, so anyone who
installed a previous build would have to uninstall first if a release ever went
out signed with a different one. Keep the keystore backed up and out of this
repository.

F-Droid is unaffected either way — it builds from source and signs with its own
key — but the APKs attached to GitHub releases are installed directly, so for
those the key matters.

To cut a release:

```bash
# 1. Raise versionCode and versionName in app/build.gradle.kts, and add
#    fastlane/metadata/android/en-US/changelogs/<new versionCode>.txt

# 2. Build, then align and sign
./gradlew clean assembleRelease testDebugUnitTest
zipalign -p -f 4 \
  app/build/outputs/apk/release/app-release-unsigned.apk Scansfer-<version>.apk
apksigner sign --ks scansfer.jks --ks-key-alias scansfer Scansfer-<version>.apk

# 3. Confirm it is signed with the expected key before publishing
apksigner verify --print-certs Scansfer-<version>.apk
```

The certificate digest should read
`18:07:C7:30:FD:1B:0A:84:C2:6F:73:C8:E2:D2:63:17:C0:B3:BC:87:E7:85:39:A7:6D:D5:18:D1:CB:19:D9:52`.
If it does not, the wrong key was used and the build must not be published.

Then merge to `main`, tag the release (`git tag v<version> && git push origin
v<version>`), and attach the signed APK to it. F-Droid watches the tags and picks
the new version up on its own.

## Design notes

- **zxing-cpp does the detecting.** It reads CameraX frames directly and returns
  the raw byte payload, so there is no charset round trip to get wrong. It is
  Apache-2.0 and entirely offline — the app requests no permission but the camera.
- **ZXing (Java) does the encoding**, one bitmap pixel per QR module, scaled up
  with nearest-neighbour filtering so module edges stay razor sharp for free.
  Keeping the pure-Java encoder is deliberate: it is what lets the QR round trip
  be tested on the JVM, with no device or native library in the loop.
- **The file is memory-mapped, not loaded**, so the fountain encoder can hop
  around a large one without putting it on the heap. On the receiving side
  decoded blocks are streamed straight into MediaStore rather than assembled into
  one big array first.
- **Camera analysis runs at 1080p.** At 720p the modules of a version-31 symbol
  land below one pixel each.
- **Photo previews go through `ImageDecoder`**, which applies EXIF orientation
  while it downsamples, so the thumbnail is never sideways the way a raw
  `BitmapFactory` decode would be.

## License

MIT — see [LICENSE](LICENSE).

## Version history

- **2.2.0** — send any file, not just photos and videos. The send screen gains a
  tab for it, and received files land in `Downloads/Scansfer`.

- **2.1.3** — store screenshots for the F-Droid listing.

- **2.1.2** — new app icon: camera scan brackets framing transfer arrows, used
  for the launcher and in the app's own header.
- **2.1.0** — replaced ML Kit with zxing-cpp for barcode detection. The app is
  now fully FLOSS and F-Droid eligible; as a side effect the `INTERNET` and
  `ACCESS_NETWORK_STATE` permissions disappeared (they came from ML Kit's
  telemetry transport) and the release APK shrank from 25 MB to under 10 MB.
- **2.0.0** — photo support. The picker now takes photos and videos, received
  files route to `Pictures/` or `Movies/` by kind, and the send and receive
  screens name what they are handling. No protocol change.
- **1.0** — initial release, video only.
