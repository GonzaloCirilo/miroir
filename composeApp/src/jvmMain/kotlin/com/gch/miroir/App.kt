package com.gch.miroir

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.gch.miroir.infrastructure.Device
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.painterResource
import miroir.composeapp.generated.resources.Res
import miroir.composeapp.generated.resources.compose_multiplatform

sealed class Event {
    data object OnRefresh : Event()
}

@Composable
fun App(
    devices: ImmutableList<Device>,
    onEvent: (Event) -> Unit,
) {
    MaterialTheme {
        var showContent by remember { mutableStateOf(false) }
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.primaryContainer)
                .safeContentPadding()
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LazyColumn {
                if (devices.isNotEmpty()){
                    items(devices.size) { index ->
                        Row(modifier = Modifier.background(Color.Green)) {
                            Text("${devices[index].manufacturer} ${devices[index].model}" )
                        }
                    }
                } else {
                    item {
                        Text("No Devices")
                    }
                }

            }
            Button(onClick = { showContent = !showContent }) {
                Text("Press me!")
            }
            Button(onClick = { onEvent(Event.OnRefresh) }) {
                Text("Refresh")
            }
            AnimatedVisibility(showContent) {
                val greeting = remember { Greeting().greet() }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(painterResource(Res.drawable.compose_multiplatform), null)
                    Text("Compose: $greeting")
                }
            }
        }
    }
}