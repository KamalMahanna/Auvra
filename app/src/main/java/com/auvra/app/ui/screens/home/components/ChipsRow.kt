package com.auvra.app.ui.screens.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.auvra.app.data.model.HomeChip

@Composable
fun ChipsRow(
    chips: List<HomeChip>,
    selectedChip: HomeChip?,
    onChipClick: (HomeChip) -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false
) {
    if (chips.isEmpty() && isLoading) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(5) {
                Box(
                    modifier = Modifier
                        .height(32.dp)
                        .width(72.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        )
                )
            }
        }
        return
    }

    if (chips.isEmpty()) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        chips.forEach { chip ->
            val isSelected = selectedChip?.title == chip.title
            FilterChip(
                selected = isSelected,
                onClick = { onChipClick(chip) },
                label = {
                    Text(
                        text = chip.title,
                        style = MaterialTheme.typography.labelLarge
                    )
                },
                shape = RoundedCornerShape(16.dp),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    labelColor = MaterialTheme.colorScheme.onSurface,
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                ),
                border = null
            )
        }
    }
}

