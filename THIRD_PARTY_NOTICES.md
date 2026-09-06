# Third-Party Notices

## FaceNet TFLite model (`app/src/main/assets/facenet.tflite`)

- **File origin:** `facenet_int_quantized.tflite` from
  https://github.com/shubham0204/FaceRecognition_With_FaceNet_Android
- **License:** Apache License 2.0 (see that repository's `LICENSE.txt`)
- **Model provenance (per that repo):** Keras FaceNet weights (Inception-
  ResNet-v1, trained with triplet loss on VGGFace2 / MS-Celeb-1M), sourced
  from the `deepface` library, converted to TensorFlow Lite with int8-
  quantized weights and float32 input/output tensors.
- **Modifications:** none — the `.tflite` file is used byte-for-byte as
  downloaded, only renamed to `facenet.tflite`.
- **Usage in this app:** on-device face embedding (Stage 2 of the pipeline),
  wrapped by `FaceEmbedder.kt`.

A copy of the Apache License 2.0 follows:

```
                                 Apache License
                           Version 2.0, January 2004
                        http://www.apache.org/licenses/

   Full text: http://www.apache.org/licenses/LICENSE-2.0
```

## Libraries

| Library | License |
|---|---|
| AndroidX (core, lifecycle, activity, compose) | Apache-2.0 |
| Jetpack Compose | Apache-2.0 |
| Google ML Kit Face Detection | subject to Google's ML Kit Terms of Service; the on-device model runs locally, no data leaves the device |
| TensorFlow Lite / TFLite Support | Apache-2.0 |
| Kotlin Coroutines | Apache-2.0 |
