package com.nutriforma.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nutriforma.app.R
import com.nutriforma.app.domain.tips.MotivationalMessage
import com.nutriforma.app.ui.theme.HerbGreenContainer
import com.nutriforma.app.ui.theme.OnHerbGreenContainer

/** Scheda di complimento, fissa nella pagina (niente popup): il testo arriva già scelto dal ViewModel. */
@Composable
fun MotivationBanner(message: MotivationalMessage, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(16.dp), color = HerbGreenContainer, modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Outlined.EmojiEvents, contentDescription = null, tint = OnHerbGreenContainer)
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.motivation_title),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = OnHerbGreenContainer,
                )
                Text(message.text, style = MaterialTheme.typography.bodyLarge, color = OnHerbGreenContainer)
                message.detail?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = OnHerbGreenContainer)
                }
            }
        }
    }
}
