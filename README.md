# 📱 FaceCollage

### On-device face detection, identification, and clustering for Android

FaceCollage is a fully **on-device Android application** that analyzes a portrait video, detects faces, identifies appearances belonging to the same person, and generates a shareable **cast collage**.

Each distinct person gets one tile in the collage along with their appearance count.

🔒 **No network calls. No backend. No cloud processing.**

Everything runs directly on the Android device, including:

* 🎞️ Frame sampling
* 👤 Face detection
* 🧠 Face embedding generation
* 🔗 Identity clustering
* 🖼️ Collage rendering

---

# 🚀 How It Works

The processing pipeline is:

```text
Portrait Video
      ↓
Frame Sampling
      ↓
ML Kit Face Detection
      ↓
Face Quality Filtering
      ↓
FaceNet Embeddings (TensorFlow Lite)
      ↓
Agglomerative Clustering
      ↓
Distinct People
      ↓
Collage Generation
```

The app processes frames from the selected video and detects faces using **Google ML Kit**.

Each detected face is converted into a numerical representation called a **face embedding** using a quantized **FaceNet TensorFlow Lite model**.

These embeddings are then compared and grouped using **agglomerative clustering** to determine which appearances belong to the same person.

Finally, the app generates a collage containing one tile for each distinct person.

---

# 🛠️ Technologies Used

* **Kotlin**
* **Android SDK**
* **Google ML Kit Face Detection**
* **TensorFlow Lite**
* **FaceNet**
* **Computer Vision**
* **Agglomerative Clustering**
* **On-device Machine Learning**

---

# 📦 Build and Setup

### 1️⃣ Clone the repository

```bash
git clone https://github.com/NandithKumar/FaceCollage.git
```

### 2️⃣ Open the project

Open the project using **Android Studio Giraffe or newer**.

### 3️⃣ Sync Gradle

Allow Gradle to sync and download the required dependencies.

> The first sync may take a few minutes.

### 4️⃣ Connect a device or emulator

Use:

* An Android device with **USB debugging enabled**, or
* An Android emulator

**API 26 or higher is recommended.**

### 5️⃣ Run the application

You can either:

* Click the ▶️ **Run** button in Android Studio, or
* Run the following command:

```bash
./gradlew installDebug
```

### 6️⃣ Select a video

When the application launches, select a portrait video from your device to begin processing.

---

# 🧠 Face Embedding Model

### Model

**FaceNet (Inception-ResNet-v1)**

The model generates a numerical embedding representing the identity-related features of a detected face.

### Model Details

* **Training:** Triplet loss using VGGFace2 and MS1M datasets
* **Format:** TensorFlow Lite
* **Quantization:** INT8 weights
* **Input:** Float32
* **Output:** Float32
* **Input Size:** `160 × 160 × 3` RGB face crop
* **Embedding Size:** `128-dimensional vector`
* **Preprocessing:** Per-image prewhiten standardization
* **Normalization:** L2 normalization

After normalization, cosine similarity can be calculated efficiently using the dot product of embedding vectors.

### Model Location

```text
app/src/main/assets/facenet.tflite
```

---

# 🔗 Face Clustering

The application uses **average-linkage agglomerative clustering** to group different appearances belonging to the same person.

### How it works

1. Each face appearance initially starts as its own cluster.
2. The algorithm compares clusters using cosine similarity.
3. The two most similar clusters are repeatedly merged.
4. Clustering stops when the best remaining similarity falls below the configured threshold.

---

# 🎯 Similarity Threshold

The current similarity threshold is:

```text
0.62
```

This value was chosen empirically using the provided sample videos.

Typical observations:

| Face Comparison   | Typical Cosine Similarity |
| ----------------- | ------------------------: |
| Same person       |               Above ~0.70 |
| Different people  |               Below ~0.50 |
| Current threshold |                  **0.62** |

The threshold can be adjusted for different types of footage.

To retune it, modify:

```text
similarityThreshold
```

inside:

```text
IdentityClusterer.kt
```

---

# ⚡ Key Challenge: Appearance Segmentation

One of the most challenging parts of the project was handling different appearances of the same person.

Video frames can contain:

* Motion blur
* Rapid camera movement
* Whip-pans
* Different face angles
* Temporary occlusions

Poor-quality frames can cause the same person to appear as multiple identities.

The pipeline therefore filters unsuitable appearances before clustering, helping reduce incorrect identity splitting.

---

# ⚠️ Known Issues and Trade-offs

* The `0.62` similarity threshold was tuned using the provided sample videos and may require adjustment for significantly different lighting or camera angles.
* Manual correction or relabeling of incorrectly clustered people is not currently available.
* Processing time increases with video length and the number of detected faces.

---

# 🔒 Privacy

FaceCollage is designed to run entirely **on-device**.

* ❌ No cloud processing
* ❌ No backend
* ❌ No network calls required
* ✅ Video processing stays on the user's device

---

# 👨‍💻 Author

**Nandith Kumar**

🔗 GitHub: https://github.com/NandithKumar

---

⭐ If you found this project interesting, consider giving the repository a star!
