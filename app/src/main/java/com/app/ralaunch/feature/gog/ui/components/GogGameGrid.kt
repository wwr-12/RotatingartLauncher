package com.app.ralaunch.feature.gog.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.app.ralaunch.R
import com.app.ralaunch.feature.gog.model.GogGameUi

/**
 * GOG 游戏网格组件
 */
@Composable
fun GogGameGrid(
    games: List<GogGameUi>,
    selectedGame: GogGameUi? = null,
    onGameClick: (GogGameUi) -> Unit,
    modifier: Modifier = Modifier,
    columns: GridCells = GridCells.Adaptive(minSize = 160.dp)
) {
    if (games.isEmpty()) {
        GogEmptyState(
            message = stringResource(R.string.gog_library_empty),
            modifier = modifier
        )
    } else {
        LazyVerticalGrid(
            columns = columns,
            contentPadding = PaddingValues(4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = modifier.fillMaxSize()
        ) {
            items(games, key = { it.id }) { game ->
                GogGameCard(
                    game = game,
                    isSelected = game.id == selectedGame?.id,
                    onClick = { onGameClick(game) }
                )
            }
        }
    }
}

/**
 * GOG 空状态组件
 */
@Composable
fun GogEmptyState(
    message: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.SportsEsports,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
