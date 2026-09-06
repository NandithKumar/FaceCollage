package com.nandu.facecollage.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nandu.facecollage.collage.CollageRenderer
import com.nandu.facecollage.pipeline.VideoProcessingPipeline
import com.nandu.facecollage.pipeline.models.PersonCluster
import com.nandu.facecollage.pipeline.models.ProcessingPhase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AppUiState {
    data object Home : AppUiState
    data class Processing(val phase: ProcessingPhase, val videoLabel: String) : AppUiState
    data class Result(
        val people: List<PersonCluster>,
        val collage: Bitmap,
        val videoLabel: String,
        val totalAppearances: Int
    ) : AppUiState
    data class Error(val message: String) : AppUiState
}

/**
 * Owns the whole detect -> embed -> cluster -> collage flow. Every heavy step
 * runs inside [VideoProcessingPipeline.process], which is itself dispatched
 * off the main thread - this ViewModel just launches it from
 * [viewModelScope] and republishes progress as UI state.
 */
class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<AppUiState>(AppUiState.Home)
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    private var activePipeline: VideoProcessingPipeline? = null

    fun processVideo(uri: Uri, videoLabel: String) {
        activePipeline?.close()
        _state.value = AppUiState.Processing(ProcessingPhase.Idle, videoLabel)

        viewModelScope.launch {
            try {
                val pipeline = VideoProcessingPipeline(getApplication())
                activePipeline = pipeline

                val people = pipeline.process(uri) { phase ->
                    _state.value = AppUiState.Processing(phase, videoLabel)
                }

                _state.value = AppUiState.Processing(ProcessingPhase.BuildingCollage, videoLabel)
                val collage = CollageRenderer.render(people, videoLabel)

                _state.value = AppUiState.Result(
                    people = people,
                    collage = collage,
                    videoLabel = videoLabel,
                    totalAppearances = people.sumOf { it.appearanceCount }
                )
            } catch (t: Throwable) {
                _state.value = AppUiState.Error(t.message ?: "Processing failed unexpectedly.")
            } finally {
                activePipeline?.close()
                activePipeline = null
            }
        }
    }

    fun reset() {
        _state.value = AppUiState.Home
    }

    override fun onCleared() {
        activePipeline?.close()
    }
}
