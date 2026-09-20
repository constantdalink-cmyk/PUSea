package org.example.project

import androidx.compose.runtime.Composable

@Composable
expect fun SetupSystemUI(view: Any?)

expect fun getCurrentTime(): String