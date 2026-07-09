package com.willyshare.willykez.ui.common

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.runtime.Composable

/**
 * Thin wrapper around [PredictiveBackHandler] so screens get Android 14+'s gesture-driven
 * back preview (the little shrink/slide as you drag from the edge) instead of the back
 * action just firing instantly with no visual lead-in.
 */
@Composable
fun SparkPredictiveBackHandler(
    enabled: Boolean = true,
    onBack: () -> Unit,
) {
    PredictiveBackHandler(enabled = enabled) { progress ->
        try {
            progress.collect {
                // Collected so the system can drive the predictive-back preview animation;
                // we don't need per-frame progress ourselves yet.
            }
            onBack()
        } catch (_: kotlin.coroutines.cancellation.CancellationException) {
            // Gesture was cancelled (user dragged back in) - don't navigate.
        }
    }
}
