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
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.appfunctions.agent.MainActivity
import com.example.appfunctions.agent.data.LlmModel
import com.example.appfunctions.agent.domain.LiveAgentOrchestrator
import com.example.appfunctions.agent.domain.LiveSessionState
import com.example.appfunctions.agent.domain.chat.ManageThreadsUseCase
import com.example.appfunctions.agent.ui.theme.AppFunctionsAgentTheme
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * System-wide [VoiceInteractionSession] hosting the floating Live voice session UI and driving
 * [LiveAgentOrchestrator].
 *
 * Uses [Insets.TOUCHABLE_INSETS_REGION] and non-modal window flags so touches outside the floating
 * Live pill pass directly through to underlying apps (such as `MainActivity` confirmation buttons
 * or target apps like `ChatApp`).
 */
class AgentVoiceInteractionSession(
    context: Context,
    private val liveAgentOrchestrator: LiveAgentOrchestrator,
    private val manageThreadsUseCase: ManageThreadsUseCase,
    mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : VoiceInteractionSession(context), LifecycleOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val sessionScope = CoroutineScope(SupervisorJob() + mainDispatcher)

    private val overlayBounds = Rect()

    private var currentThreadId: String? = null
    private var currentTargetPackageName: String? = null
    private var startupJob: Job? = null
    private var stateMonitorJob: Job? = null

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        configurePassThroughWindow()
    }

    override fun onCreateContentView(): View {
        configurePassThroughWindow()
        window?.window?.decorView?.let { decorView ->
            decorView.setViewTreeLifecycleOwner(this)
            decorView.setViewTreeSavedStateRegistryOwner(this)
        }

        return ComposeView(context).apply {
            setViewTreeLifecycleOwner(this@AgentVoiceInteractionSession)
            setViewTreeSavedStateRegistryOwner(this@AgentVoiceInteractionSession)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AppFunctionsAgentTheme {
                    val liveState by
                        liveAgentOrchestrator.state.collectAsStateWithLifecycle()
                    val agentStatus by
                        liveAgentOrchestrator.status.collectAsStateWithLifecycle()

                    LiveVoiceSessionOverlay(
                        liveSessionState = liveState,
                        agentStatus = agentStatus,
                        onStopClick = ::stopAndHide,
                        onRetryClick = ::retrySession,
                        onOpenChatClick = ::openChat,
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
        val effectiveArgs =
            if (args?.containsKey(AgentVoiceInteractionService.EXTRA_THREAD_ID) == true) {
                AgentVoiceInteractionService.clearPendingSession()
                args
            } else {
                AgentVoiceInteractionService.consumePendingSession() ?: args
            }
        configurePassThroughWindow()
        window?.window?.decorView?.let { decorView ->
            decorView.setViewTreeLifecycleOwner(this)
            decorView.setViewTreeSavedStateRegistryOwner(this)
        }

        if (lifecycleRegistry.currentState != Lifecycle.State.DESTROYED) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }

        val requestedThreadId =
            effectiveArgs?.getString(AgentVoiceInteractionService.EXTRA_THREAD_ID)
        val requestedTargetPkg =
            effectiveArgs?.getString(AgentVoiceInteractionService.EXTRA_TARGET_PACKAGE_NAME)

        startOrAttachSession(
            requestedThreadId = requestedThreadId,
            requestedTargetPackageName = requestedTargetPkg,
        )
    }

    override fun onCloseSystemDialogs() {
        // Keep the floating Live pill visible when the user presses Home to switch to another app
        // (e.g. ChatApp) while a voice conversation is active.
        val state = liveAgentOrchestrator.state.value
        val isActive =
            state is LiveSessionState.Connecting || state is LiveSessionState.Listening
        if (!isActive) {
            super.onCloseSystemDialogs()
        }
    }

    override fun onHide() {
        stateMonitorJob?.cancel()
        stateMonitorJob = null
        startupJob?.cancel()
        startupJob = null
        liveAgentOrchestrator.stopSession()

        if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            }
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        }
        super.onHide()
    }

    override fun onDestroy() {
        stateMonitorJob?.cancel()
        stateMonitorJob = null
        startupJob?.cancel()
        startupJob = null
        liveAgentOrchestrator.stopSession()
        sessionScope.cancel()

        if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.CREATED)) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }
        super.onDestroy()
    }

    internal fun stopAndHide() {
        stateMonitorJob?.cancel()
        stateMonitorJob = null
        startupJob?.cancel()
        startupJob = null
        liveAgentOrchestrator.stopSession()
        hide()
    }

    internal fun retrySession() {
        startOrAttachSession(
            requestedThreadId = currentThreadId,
            requestedTargetPackageName = currentTargetPackageName,
        )
    }

    internal fun openChat() {
        val intent =
            Intent(context, MainActivity::class.java).apply {
                action = MainActivity.ACTION_OPEN_CHAT
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP,
                )
                currentThreadId?.let { putExtra(MainActivity.EXTRA_OPEN_THREAD_ID, it) }
            }
        context.startActivity(intent)
    }

    internal fun updateOverlayBounds(bounds: Rect) {
        if (overlayBounds != bounds) {
            overlayBounds.set(bounds)
            window?.window?.decorView?.let { decorView ->
                decorView.requestLayout()
                decorView.invalidate()
            }
        }
    }

    private fun startOrAttachSession(
        requestedThreadId: String?,
        requestedTargetPackageName: String?,
    ) {
        stateMonitorJob?.cancel()
        startupJob?.cancel()

        val currentState = liveAgentOrchestrator.state.value
        val isCurrentlyRunning =
            currentState is LiveSessionState.Connecting ||
                currentState is LiveSessionState.Listening
        val matchesRunningSession =
            requestedThreadId == null ||
                (
                    requestedThreadId == currentThreadId &&
                        requestedTargetPackageName == currentTargetPackageName
                )

        if (isCurrentlyRunning && matchesRunningSession) {
            monitorSessionState(initiallyActive = true)
            return
        }

        startupJob =
            sessionScope.launch {
                val resolvedThreadId = requestedThreadId ?: resolveOrCreateDefaultThreadId()
                currentThreadId = resolvedThreadId
                currentTargetPackageName = requestedTargetPackageName
                liveAgentOrchestrator.startSession(
                    threadId = resolvedThreadId,
                    targetPackageName = requestedTargetPackageName,
                )
                monitorSessionState(initiallyActive = true)
            }
    }

    private fun monitorSessionState(initiallyActive: Boolean) {
        stateMonitorJob?.cancel()
        stateMonitorJob =
            sessionScope.launch {
                var sawActiveSession = initiallyActive
                liveAgentOrchestrator.state.collect { state ->
                    if (
                        state is LiveSessionState.Connecting ||
                        state is LiveSessionState.Listening
                    ) {
                        sawActiveSession = true
                    } else if (sawActiveSession && state is LiveSessionState.Idle) {
                        hide()
                    }
                }
            }
    }

    private suspend fun resolveOrCreateDefaultThreadId(): String {
        val existingThread = manageThreadsUseCase.getThreads().first().firstOrNull()
        return existingThread?.threadId ?: manageThreadsUseCase.createThread(LlmModel.DEFAULT)
    }

    private fun configurePassThroughWindow() {
        window?.window?.let { win ->
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
    }
}
