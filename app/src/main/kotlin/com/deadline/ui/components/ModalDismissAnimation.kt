package com.deadline.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Universal downward dismissal animation helper for Compose ModalBottomSheet.
 * Enforces a smooth downward translation exit animation for all creation modals,
 * bottom sheets, and input pop-ups across the entire application.
 */
@OptIn(ExperimentalMaterial3Api::class)
suspend fun SheetState.smoothDismiss() {
    try {
        if (isVisible) {
            hide()
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        // Fallback gracefully on non-cancellation animation exceptions
    }
}

/**
 * Convenience extension to launch a smooth downward dismissal animation in [scope]
 * and invoke [onDismissed] upon full downward translation completion.
 */
@OptIn(ExperimentalMaterial3Api::class)
fun SheetState.animateDismiss(
    scope: CoroutineScope,
    onDismissed: () -> Unit
) {
    scope.launch {
        try {
            smoothDismiss()
        } finally {
            onDismissed()
        }
    }
}
