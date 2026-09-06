FACE COLLAGE - ANDROID INTERNSHIP ASSIGNMENT



A fully on device Android app that takes a portrait video, detects every face, figures out which appearances belong to the same person, and builds a shareable cast of the video collage, one tile per distinct person, with their appearance count.



No network calls, no backend. Everything (frame sampling, face detection, face embeddings, clustering, collage rendering) runs on device.



BUILD AND SETUP



Step 1: Clone the repo using: git clone https://github.com/NandithKumar/FaceCollage.git

Step 2: Open the project in Android Studio (Giraffe or newer recommended).

Step 3: Let Gradle sync (first sync may take a few minutes, it downloads dependencies).

Step 4: Connect an Android device (API 26 or higher recommended) with USB debugging enabled, or use an emulator.

Step 5: Run gradlew installDebug from the terminal, or click the Run button in Android Studio with your device selected.

Step 6: On first launch, pick a video from your device to process.



EMBEDDING MODEL



Model: FaceNet (Inception-ResNet-v1), trained with triplet loss on VGGFace2 and MS1M.

Format: Converted to TFLite, int8 quantized weights, float32 input and output.

Input: 160x160x3 RGB face crop, using per-image prewhiten standardization.

Output: 128 dimensional embedding vector, L2-normalized so cosine similarity equals dot product.

The model file is bundled at app/src/main/assets/facenet.tflite.



CLUSTERING AND SIMILARITY THRESHOLD



Method: Average-linkage agglomerative clustering. Starts with one cluster per face appearance, repeatedly merges the two clusters whose centroids are most similar by cosine similarity, and stops when the best remaining pair falls below the threshold.

Threshold: 0.62, chosen empirically. Same-person appearances in a clip typically score above roughly 0.70 cosine similarity, while different people typically score below roughly 0.50. 0.62 sits in that gap with margin on both sides.

To retune for different footage, adjust the similarityThreshold value in IdentityClusterer.kt.



KNOWN ISSUES AND TRADEOFFS



The threshold of 0.62 was tuned on the provided sample videos, so very different lighting or camera angle conditions may need retuning.

There is no manual re-labeling of misclassified people yet.

Processing time scales with video length and the number of faces detected.

