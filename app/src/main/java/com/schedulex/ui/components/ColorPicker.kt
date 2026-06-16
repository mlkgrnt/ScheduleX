package com.schedulex.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val courseColors = listOf(
    "#4FC3F7", "#81C784", "#FFB74D", "#E57373",
    "#BA68C8", "#4DB6AC", "#FFD54F", "#7986CB",
    "#F06292", "#AED581", "#4DD0E1", "#FF8A65"
)

@Composable
fun ColorPicker(
    selectedColor: String,
    onColorSelected: (String) -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val borderColor = if (isDark) Color.White else Color.Black

    // 分两行，每行6个
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (rowIndex in 0..1) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                val rowColors = courseColors.drop(rowIndex * 6).take(6)
                rowColors.forEach { colorHex ->
                    val color = Color(android.graphics.Color.parseColor(colorHex))
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(color)
                            .then(
                                if (colorHex == selectedColor) {
                                    Modifier.border(3.dp, borderColor, CircleShape)
                                } else Modifier
                            )
                            .clickable { onColorSelected(colorHex) }
                    )
                }
            }
        }
    }
}
