package com.example.ui.components

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class InAppToastData(
    val id: Long,
    val message: String,
    val isLong: Boolean = false
)

object AppToast {
    private val _toastFlow = MutableStateFlow<InAppToastData?>(null)
    val toastFlow: StateFlow<InAppToastData?> = _toastFlow.asStateFlow()

    private var activeJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    @Volatile
    private var lastMessage: String = ""
    @Volatile
    private var lastTimestamp: Long = 0L

    /**
     * Shows an in-app toast message. Dismisses automatically and only lives inside the app.
     */
    fun show(message: String?, isLong: Boolean = false) {
        val text = message?.trim() ?: return
        if (text.isBlank()) return

        val now = System.currentTimeMillis()
        if (text == lastMessage && (now - lastTimestamp) < 1200L) {
            return
        }
        lastMessage = text
        lastTimestamp = now

        scope.launch {
            activeJob?.cancel()
            val toastData = InAppToastData(
                id = now,
                message = text,
                isLong = isLong
            )
            _toastFlow.value = toastData

            activeJob = launch {
                val displayDuration = if (isLong) 3500L else 2200L
                delay(displayDuration)
                if (_toastFlow.value?.id == toastData.id) {
                    _toastFlow.value = null
                }
            }
        }
    }

    /**
     * Compatibility helper overload accepting a Context.
     */
    fun show(context: Context?, message: String?, isLong: Boolean = false) {
        show(message, isLong)
    }

    /**
     * Dismisses any active in-app toast immediately.
     */
    fun dismiss() {
        scope.launch {
            activeJob?.cancel()
            _toastFlow.value = null
        }
    }
}

/**
 * Top-level Composable Host that renders in-app toasts cleanly above all content.
 * Since this lives inside the Compose tree, it disappears instantly when the user leaves or minimizes the app.
 */
@Composable
fun InAppToastHost(
    modifier: Modifier = Modifier
) {
    val currentToast by AppToast.toastFlow.collectAsState()
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(9999f)
            .navigationBarsPadding()
            .imePadding()
            .padding(bottom = 72.dp, start = 20.dp, end = 20.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        AnimatedVisibility(
            visible = currentToast != null,
            enter = fadeIn(animationSpec = tween(220)) +
                    scaleIn(initialScale = 0.88f, animationSpec = tween(220)) +
                    slideInVertically(initialOffsetY = { it / 3 }, animationSpec = tween(220)),
            exit = fadeOut(animationSpec = tween(180)) +
                    scaleOut(targetScale = 0.88f, animationSpec = tween(180)) +
                    slideOutVertically(targetOffsetY = { it / 3 }, animationSpec = tween(180))
        ) {
            currentToast?.let { toast ->
                Surface(
                    modifier = Modifier
                        .widthIn(min = 120.dp, max = 340.dp)
                        .shadow(
                            elevation = 10.dp,
                            shape = RoundedCornerShape(50.dp),
                            spotColor = Color(0x88000000),
                            ambientColor = Color(0x66000000)
                        )
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null
                        ) {
                            AppToast.dismiss()
                        },
                    shape = RoundedCornerShape(50.dp),
                    color = Color.Transparent,
                    border = BorderStroke(
                        width = 1.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0x55FFFFFF),
                                Color(0x22FFFFFF)
                            )
                        )
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xF01C1C24),
                                        Color(0xF0121218)
                                    )
                                )
                            )
                            .padding(horizontal = 20.dp, vertical = 11.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = toast.message,
                            color = Color(0xFFF8FAFC),
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }
    }
}
