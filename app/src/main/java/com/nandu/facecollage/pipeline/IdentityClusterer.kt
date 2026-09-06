package com.nandu.facecollage.pipeline

import com.nandu.facecollage.pipeline.models.Appearance
import com.nandu.facecollage.pipeline.models.PersonCluster

/**
 * Stage 3 of the pipeline: decides which appearances belong to the same
 * person, purely from their FaceNet embeddings (cosine similarity, both
 * vectors L2-normalized so similarity == dot product).
 *
 * Average-linkage agglomerative clustering: start with one cluster per
 * appearance, repeatedly merge the two clusters whose CENTROIDS are most
 * similar, stop when the best remaining pair is below [similarityThreshold].
 * With ~15-40 appearances per 30s clip this is O(n^2 log n) worst case,
 * trivially fast on-device - no need for anything fancier.
 *
 * [similarityThreshold] = 0.62 was chosen empirically against this FaceNet
 * (128-d, int8-quantized) model: appearances of the same person in the same
 * clip (same lighting/angle range) typically land above ~0.70 cosine
 * similarity, different people typically land below ~0.50. 0.62 sits in the
 * gap with margin on both sides. See README for how to retune it against
 * your own footage.
 */
class IdentityClusterer(
    private val embedder: FaceEmbedder,
    private val similarityThreshold: Float = 0.62f
) {

    fun cluster(appearances: List<Appearance>): List<PersonCluster> {
        if (appearances.isEmpty()) return emptyList()

        data class Cluster(val members: MutableList<Appearance>, var centroid: FloatArray)

        var clusters = appearances.map { Cluster(mutableListOf(it), it.embedding.copyOf()) }

        while (clusters.size > 1) {
            var bestI = -1
            var bestJ = -1
            var bestSim = -2f

            for (i in clusters.indices) {
                for (j in i + 1 until clusters.size) {
                    val sim = embedder.cosineSimilarity(clusters[i].centroid, clusters[j].centroid)
                    if (sim > bestSim) {
                        bestSim = sim
                        bestI = i
                        bestJ = j
                    }
                }
            }

            if (bestSim < similarityThreshold) break

            val merged = Cluster(
                (clusters[bestI].members + clusters[bestJ].members).toMutableList(),
                embedder.average(clusters[bestI].members.map { it.embedding } + clusters[bestJ].members.map { it.embedding })
            )
            clusters = clusters
                .filterIndexed { idx, _ -> idx != bestI && idx != bestJ }
                .toMutableList()
                .apply { add(merged) }
        }

        // Order people by their first appearance in the video, so "Person 1" is
        // whoever shows up first - stable and intuitive for the collage.
        val ordered = clusters.sortedBy { c -> c.members.minOf { it.startMs } }

        return ordered.mapIndexed { idx, c ->
            PersonCluster(
                personIndex = idx + 1,
                appearances = c.members.sortedBy { it.startMs },
                centroidEmbedding = c.centroid
            )
        }
    }
}
