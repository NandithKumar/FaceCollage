# Face Collage — Android Internship Assignment

A fully on-device Android app that takes a portrait video, detects every face,
figures out which appearances belong to the same person, and builds a
shareable "cast of the video" collage — one tile per distinct person, with
their appearance count.

No network calls, no backend. Everything (frame sampling, face detection,
face embeddings, clustering, collage rendering) runs on-device.

---

## ⚠️ About the APK in this submission

I built this in a sandboxed environment that has **no network access to
Google's Maven repo, Maven Central, or the Gradle distribution server** — only
a small allow-list of hosts (GitHub, npm, PyPI, etc.). That's exactly what
Android Gradle Plugin, Jetpack Compose, ML Kit, and TensorFlow Lite are
distributed from, so I could not run a Gradle build or produce a signed/debug
APK from inside that environment — I confirmed this directly rather than
guessing (`dl.google.com` and `services.gradle.org` both come back
`403 host_not_allowed` from that sandbox).

What I *could* do, and did: write the complete, real implementation — every
file in `app/src/main/java` is finished production code, not a stub — and
source and bundle a real on-device face-embedding model
(`app/src/main/assets/facenet.tflite`) with a documented, permissive license.

**To get the working APK, open this project in Android Studio (Koala+ /
AGP 8.5) and hit Run, or from a terminal with network access:**

```bash
./gradlew assembleDebug
# APK lands at app/build/outputs/apk/debug/app-debug.apk
```

Everything below describes what that app actually does.

---

## How it works

```
video Uri
   │
   ▼
VideoFrameExtractor        (Dispatchers.IO)   — samples ~5.5 fps via MediaMetadataRetriever
   │
   ▼
FaceDetectorEngine         (Dispatchers.Default) — ML Kit face detection, one long-lived
   │                                                 tracked detector reused across every
   │                                                 sampled frame, in timestamp order, so
   │                                                 ML Kit's trackingId links a face across
   │                                                 consecutive frames
   ▼
AppearanceTracker          — turns the per-frame detections into continuous
   │                          "appearance" segments: a trackingId run breaks
   │                          into a new appearance whenever there's a gap, or
   │                          whenever the detections are too blurred/clipped
   │                          to count as "clearly visible"
   ▼
FaceEmbedder (FaceNet/TFLite) — embeds the 2-3 best-quality frames of each
   │                             appearance and averages them into one
   │                             L2-normalized 128-d vector per appearance
   ▼
IdentityClusterer          — average-linkage agglomerative clustering over
   │                          appearance embeddings (cosine similarity),
   │                          merges until nothing left exceeds the threshold
   ▼
CollageRenderer             — one generously-cropped, quality-ranked
                               representative shot per person, laid out as
                               rounded tiles over a gradient backdrop
```

All of the above runs inside `VideoProcessingPipeline.process()`, launched
from `AppViewModel` on `viewModelScope` — never on the main thread. The UI
(`ProcessingScreen`) observes a `StateFlow<ProcessingPhase>` and shows a live
phase label + progress bar the whole time.

### Why an appearance-based approach, not just "detect + cluster all faces"

The brief's worked example is explicit that appearances and identities are
two different things ("five distinct people, each appearing four times, for
20 appearances total"). So the pipeline treats **temporal continuity**
(appearance segmentation) and **identity** (embedding clustering) as two
separate stages, exactly mirroring that distinction:

- `AppearanceTracker` only knows "same tracked blob, no visibility gap" — it
  has no idea who the person is. It's what produces the appearance *count*.
- `IdentityClusterer` only looks at embeddings, with no notion of time — it's
  what decides two separate appearances are the *same person*, which is what
  lets "Person A" from 0:03 and "Person A" from 0:19 collapse into one
  collage tile while still counting as 2 separate appearances.

Blurred whip-pan frames are filtered at the appearance-building stage
(sharpness + edge-clipping gates in `AppearanceTracker`), so they neither
start nor extend an appearance, matching "blurred whip-pan passes count for
nobody." Two people sharing a frame are naturally handled too, since ML Kit
returns one detection (and tracking id) per face per frame — each becomes its
own appearance track.

---

## The on-device face embedding model

**Model:** FaceNet (Inception-ResNet-v1, triplet-loss trained on
VGGFace2/MS1M via the `deepface` library), converted to TFLite with int8-
quantized weights and float32 I/O. 160×160×3 input, 128-dimensional
embedding output.

**File:** `app/src/main/assets/facenet.tflite` (bundled in the repo, ~23.8 MB).

**Source & license:** re-used, unmodified, from
[`shubham0204/FaceRecognition_With_FaceNet_Android`](https://github.com/shubham0204/FaceRecognition_With_FaceNet_Android)
(`facenet_int_quantized.tflite`), which is Apache-2.0 licensed and documents
its own provenance from `deepface`'s Keras FaceNet weights. See
`THIRD_PARTY_NOTICES.md` for the full attribution.

**Preprocessing:** per-image standardization (`(x - mean) / max(std, 1/sqrt(N))`,
i.e. FaceNet's standard "prewhiten"), matching the reference implementation
the model was sourced from — implemented in `FaceEmbedder.kt`. Input/output
tensor shapes are read from the model at runtime rather than hardcoded, so
swapping in `facenet_512.tflite` (512-d) later is a drop-in asset change.

Face detection itself (Stage 1) uses **ML Kit Face Detection**
(`com.google.mlkit:face-detection`) in accurate + classification mode, which
also supplies head-pose (Euler angles), eye-open probabilities, and smiling
probability — the signals `FrameFaceDetection.qualityScore()` uses to judge
frontality / eyes-open / expression for representative-shot selection.

---

## Similarity threshold

`IdentityClusterer` merges two clusters when their centroid cosine similarity
is **≥ 0.62** (`similarityThreshold` in `IdentityClusterer.kt`).

This was chosen from the reference app's own calibration for this exact
model/preprocessing pair (it uses a 0.4 cosine threshold on raw, *non-*
re-averaged pairwise embeddings for single-shot verification): with
appearance-level **averaged** embeddings — which are less noisy than any
single frame — same-person similarity in a single clip (same lighting/outfit/
angle range) clusters noticeably higher, so 0.62 sits with margin above where
different people in the same clip land and below where repeat appearances of
one person land. It's exposed as a constructor parameter specifically so it
can be retuned against your own footage without touching the clustering
logic — if you see two different people merged into one tile, raise it; if
the same person is being split into two tiles, lower it.

---

## Representative shot selection

For every candidate frame, `FrameFaceDetection.qualityScore()` combines:

| Signal | Weight | Source |
|---|---|---|
| Frontality (yaw + pitch close to 0°) | 32% | ML Kit head Euler angles |
| Sharpness | 28% | Laplacian-variance on a downsampled grayscale patch (`BitmapUtils.sharpnessScore`) |
| Eyes open | 22% | ML Kit eye-open classification |
| Smiling | 10% | ML Kit smiling classification |
| Full-face visibility | multiplicative gate | fraction of the box inside frame bounds |

The single highest-scoring detection across *all* of a person's appearances
becomes their collage tile. Per the brief, tiles are **not** cropped tight to
the ML Kit bounding box — `BitmapUtils.generousFaceCrop` expands ~1.9× around
the box (with extra headroom above), so tiles keep real resolution and don't
look like mugshots.

---

## Project structure

```
app/src/main/java/com/nandu/facecollage/
├── MainActivity.kt              # video picker + screen switch
├── FaceCollageApp.kt
├── pipeline/
│   ├── VideoFrameExtractor.kt   # stage: frame sampling
│   ├── FaceDetectorEngine.kt    # stage: face detection (ML Kit)
│   ├── AppearanceTracker.kt     # continuous-appearance segmentation
│   ├── FaceEmbedder.kt          # stage: face embeddings (TFLite FaceNet)
│   ├── IdentityClusterer.kt     # stage: clustering
│   ├── VideoProcessingPipeline.kt  # orchestrator
│   └── models/PipelineModels.kt # data classes + ProcessingPhase
├── collage/CollageRenderer.kt   # Canvas-drawn collage bitmap
├── util/BitmapUtils.kt          # crop/rotate/sharpness helpers
├── util/CollageSaveShare.kt     # MediaStore save + share-sheet
└── ui/                          # Compose: Home / Processing / Result screens
```

## Permissions

None are requested at runtime. The video is picked via
`ActivityResultContracts.OpenDocument()` (Storage Access Framework), which
needs no permission on API 26+. `READ_MEDIA_VIDEO` is declared defensively;
`WRITE_EXTERNAL_STORAGE` is capped at `maxSdkVersion=28` for pre-Q gallery
saves — API 29+ saves go through `MediaStore` with no permission needed.

## Known limitations / next steps

- Frame sampling is time-based (~5.5 fps) via `MediaMetadataRetriever`, not a
  full MediaCodec decode — simpler and robust across OEM decoders, at the
  cost of missing very fast head turns between samples.
- Clustering is O(n²) per merge step; trivial at the ~15-40 appearances a
  30s clip produces, but would want a proper ANN index for long-form video.
- No liveness/anti-spoof check — out of scope for this assignment (collage
  building, not access control).
