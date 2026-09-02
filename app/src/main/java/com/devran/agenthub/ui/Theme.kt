package com.devran.agenthub.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.devran.agenthub.model.Accent

@Composable
fun AgentHubTheme(darkTheme: Boolean = true, accent: Accent = Accent.VIOLET, content: @Composable () -> Unit) {
    val primary = when (accent) {
        Accent.VIOLET -> Color(0xFF8B7CFF)
        Accent.CYAN -> Color(0xFF54D6FF)
        Accent.GREEN -> Color(0xFF72E5A3)
        Accent.AMBER -> Color(0xFFFFC857)
        Accent.PINK -> Color(0xFFFF72B6)
    }
    val scheme = if (darkTheme) darkColorScheme(primary = primary, secondary = primary.copy(alpha = 0.82f), tertiary = primary.copy(alpha = 0.66f))
    else lightColorScheme(primary = primary, secondary = primary.copy(alpha = 0.88f), tertiary = primary.copy(alpha = 0.70f))
    MaterialTheme(colorScheme = scheme, content = content)
}
