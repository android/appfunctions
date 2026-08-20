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
package com.example.chatapp.wear.uicomponents

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.example.chatapp.ChatViewModel
import com.example.chatapp.wear.R

@Composable
fun WearCallScreen(
    recipientId: String,
    viewModel: ChatViewModel =
        hiltViewModel(
            key = recipientId,
            creationCallback = { factory: ChatViewModel.Factory ->
                factory.create(recipientId)
            },
        ),
    onEndCall: () -> Unit,
) {
    val activeCall by viewModel.activeCall.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.startCall()
    }

    ScreenScaffold {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(text = stringResource(R.string.calling))
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = activeCall?.name ?: viewModel.recipient.name)
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    viewModel.endCall()
                    onEndCall()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
            ) {
                Text("End")
            }
        }
    }
}
