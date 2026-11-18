/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.appfunctions.agent.voice

import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.appfunctions.agent.MainActivity
import com.example.appfunctions.agent.ui.theme.AppFunctionsAgentTheme
import com.example.appfunctions.agent.ui.voice.LiveOverlayUiEvent
import com.example.appfunctions.agent.ui.voice.LiveVoiceOverlayStateHolder
import com.example.appfunctions.agent.ui.voice.LiveVoiceSessionOverlay
import kotlinx.coroutines.launch

/**
 * Thin Android [VoiceInteractionSession] window host rendering [LiveVoiceSessionOverlay] from
 * [LiveVoiceOverlayStateHolder] and configuring pass-through touch insets outside the floating pill.
 */
class AgentVoiceInteractionSession(
    context: Context,
    private val stateHolder: LiveVoiceOverlayStateHolder,
) : VoiceInteractionSession(context), LifecycleOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val overlayBounds = Rect()

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        configurePassThroughWindow()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                stateHolder.uiState.collect { uiState ->
                    if (!uiState.isVisible) {
                        hide()
                    }
                }
            }
        }
    }

    override fun onCreateContentView(): View {
        configurePassThroughWindow()
        val decorView = window?.window?.decorView
        if (decorView != null) {
            decorView.setViewTreeLifecycleOwner(this)
            decorView.setViewTreeSavedStateRegistryOwner(this)
        }

        return ComposeView(context).apply {
            setViewTreeLifecycleOwner(this@AgentVoiceInteractionSession)
            setViewTreeSavedStateRegistryOwner(this@AgentVoiceInteractionSession)
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
            )
            setContent {
                AppFunctionsAgentTheme {
                    val uiState by stateHolder.uiState.collectAsStateWithLifecycle()
                    LiveVoiceSessionOverlay(
                        uiState = uiState,
                        onEvent = stateHolder::onEvent,
                        onOpenChat = { threadId ->
                            context.startActivity(
                                MainActivity.createOpenChatIntent(context, threadId),
                            )
                        },
                        onOverlayBoundsChanged = ::updateOverlayBounds,
                    )
                }
            }
        }
    }

    override fun onComputeInsets(outInsets: Insets) {
        super.onComputeInsets(outInsets)
        outInsets.contentInsets.setEmpty()
        outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_REGION
        outInsets.touchableRegion.set(overlayBounds)
    }

    override fun onShow(
        args: Bundle?,
        showFlags: Int,
    ) {
        super.onShow(args, showFlags)
        configurePassThroughWindow()
        val decorView = window?.window?.decorView
        if (decorView != null) {
            decorView.setViewTreeLifecycleOwner(this)
            decorView.setViewTreeSavedStateRegistryOwner(this)
        }

        stateHolder.onEvent(
            LiveOverlayUiEvent.OnSessionShown(
                threadId = args?.getString(EXTRA_THREAD_ID),
                targetPackageName = args?.getString(EXTRA_TARGET_PACKAGE_NAME),
            ),
        )

        if (lifecycleRegistry.currentState != Lifecycle.State.DESTROYED) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }
    }

    override fun onCloseSystemDialogs() {
        if (!stateHolder.uiState.value.keepVisibleOnSystemDialogsClose) {
            super.onCloseSystemDialogs()
        }
    }

    override fun onHide() {
        if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            }
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        }
        stateHolder.onEvent(LiveOverlayUiEvent.OnSessionHidden)
        super.onHide()
    }

    override fun onDestroy() {
        stateHolder.close()
        if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.CREATED)) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }
        super.onDestroy()
    }

    @VisibleForTesting
    internal fun updateOverlayBounds(bounds: Rect) {
        if (overlayBounds != bounds) {
            overlayBounds.set(bounds)
            val decorView = window?.window?.decorView
            if (decorView != null) {
                decorView.requestLayout()
                decorView.invalidate()
            }
        }
    }

    private fun configurePassThroughWindow() {
        val win = window?.window ?: return
        win.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        win.addFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        )
        win.setBackgroundDrawableResource(android.R.color.transparent)
        win.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
    }

    companion object {
        const val EXTRA_THREAD_ID = "com.example.appfunctions.agent.extra.THREAD_ID"
        const val EXTRA_TARGET_PACKAGE_NAME =
            "com.example.appfunctions.agent.extra.TARGET_PACKAGE_NAME"

        /**
         * Creates a [Bundle] of session arguments for `Activity.showAssist(Bundle)` targeting
         * [threadId] and optional [targetPackageName].
         */
        fun createSessionArgs(
            threadId: String,
            targetPackageName: String? = null,
        ): Bundle =
            Bundle().apply {
                putString(EXTRA_THREAD_ID, threadId)
                if (targetPackageName != null) {
                    putString(EXTRA_TARGET_PACKAGE_NAME, targetPackageName)
                }
            }
    }
}
