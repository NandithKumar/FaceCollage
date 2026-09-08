An on-device Android application that analyzes a portrait video, detects faces, groups appearances belonging to the same person, and generates a shareable Face Collage with appearance counts.

📱 Overview

Face Collage is a fully on-device Android application designed to analyze people appearing in a portrait video.

The app processes video frames, detects faces, generates facial embeddings, groups similar faces together, and creates a visual collage representing the distinct people appearing throughout the video.

Each person gets their own tile in the final collage along with their appearance count.

🔒 No backend. No cloud processing. No network calls.

Everything happens directly on the Android device.

✨ Features
🎥 Process portrait videos directly on Android
👤 Detect multiple faces across video frames
🧠 Generate face embeddings for detected faces
🔗 Group appearances belonging to the same person
🔢 Count how many times each person appears
🖼️ Generate a Face Collage automatically
📤 Create a shareable final result
📱 Fully on-device processing
🔒 No backend or cloud processing required

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
Step 1: Video Frame Sampling

The application extracts frames from the selected portrait video at intervals suitable for processing.

🔹 Step 2: Face Detection

Each sampled frame is analyzed to identify the faces present in the video.

🔹 Step 3: Face Embeddings

For every detected face, the application generates a numerical representation called a face embedding.

🔹 Step 4: Face Clustering

Similar embeddings are compared and grouped together to identify appearances belonging to the same person.

🔹 Step 5: Appearance Counting

The application tracks appearances for each identified person.

🔹 Step 6: Collage Generation

The final result is rendered as a visual collage containing:

👤 One tile per distinct person
🔢 Appearance count
🖼️ Representative face image
---
📸 Results

The application generates a visual cast of the people detected throughout the video.

Example Output 1
<img width="1536" height="1519" alt="image" src="https://github.com/user-attachments/assets/239a201d-1552-416c-86e0-170c5b8d23e2" />

Example Output 2

<img width="688" height="1536" alt="image" src="https://github.com/user-attachments/assets/c28d1b77-8e11-45a7-bc06-a11ba0657f8d" />

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
