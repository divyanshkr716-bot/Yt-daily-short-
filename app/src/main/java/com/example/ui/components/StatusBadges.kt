package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.JobStatus
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.ShortsRed
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TealAccent

@Composable
fun JobStatusBadge(status: String, modifier: Modifier = Modifier) {
    val (bgColor, textColor, icon) = when (status) {
        JobStatus.COMPLETED -> Triple(
            SuccessGreen.copy(alpha = 0.15f),
            SuccessGreen,
            Icons.Default.CheckCircle
        )
        JobStatus.FAILED -> Triple(
            ErrorRed.copy(alpha = 0.15f),
            ErrorRed,
            Icons.Default.Error
        )
        JobStatus.UPLOADING -> Triple(
            ShortsRed.copy(alpha = 0.15f),
            ShortsRed,
            Icons.Default.Upload
        )
        JobStatus.GENERATED -> Triple(
            TealAccent.copy(alpha = 0.15f),
            TealAccent,
            Icons.Default.PlayArrow
        )
        JobStatus.PROCESSING -> Triple(
            Color(0xFF2196F3).copy(alpha = 0.15f),
            Color(0xFF64B5F6),
            Icons.Default.Refresh
        )
        JobStatus.WAITING_NETWORK -> Triple(
            AmberAccent.copy(alpha = 0.15f),
            AmberAccent,
            Icons.Default.CloudOff
        )
        else -> Triple(
            Color.Gray.copy(alpha = 0.15f),
            Color.LightGray,
            Icons.Default.HourglassEmpty
        )
    }

    Box(
        modifier = modifier
            .background(bgColor, RoundedCornerShape(12.dp))
            .border(1.dp, textColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = status,
                tint = textColor,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = status,
                color = textColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun StatusIndicatorDot(isActive: Boolean, modifier: Modifier = Modifier) {
    val color = if (isActive) SuccessGreen else Color(0xFF616161)
    Box(
        modifier = modifier
            .size(10.dp)
            .background(color, CircleShape)
            .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape)
    )
}
