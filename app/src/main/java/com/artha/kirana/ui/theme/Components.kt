package com.artha.kirana.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Flat, hairline-bordered card — canvas + 1px [Line], radius 20 (design brief). [feature] swaps the
 * hairline to white + radius 24. [tone] paints a saturated color-block with black ([Ink]) text.
 */
enum class CardTone { NONE, MINT, VIOLET, YELLOW, WHITE }

@Composable
fun Card(
    modifier: Modifier = Modifier,
    feature: Boolean = false,
    tone: CardTone = CardTone.NONE,
    content: @Composable ColumnScope.() -> Unit,
) {
    val fill = when (tone) {
        CardTone.NONE -> Canvas
        CardTone.MINT -> Mint
        CardTone.VIOLET -> Ultraviolet
        CardTone.YELLOW -> TileYellow
        CardTone.WHITE -> HazardWhite
    }
    val shape = RoundedCornerShape(if (feature) 24.dp else 20.dp)
    val border = when {
        tone != CardTone.NONE -> null
        feature -> BorderStroke(1.dp, LineStrong)
        else -> BorderStroke(1.dp, Line)
    }
    val base = Modifier
        .fillMaxWidth()
        .then(if (border != null) Modifier.border(border, shape) else Modifier)
        .background(fill, shape)
        .padding(18.dp)
    androidx.compose.foundation.layout.Column(modifier = modifier.then(base), content = content)
}

/** Mint pill, black mono UPPERCASE label, radius 24. Pressed → translucent white overlay. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (pressed) Color.White.copy(alpha = 0.85f) else Mint,
            contentColor = Ink,
            disabledContainerColor = Slate,
            disabledContentColor = TextMuted,
        ),
        modifier = modifier.height(52.dp),
    ) {
        Text(text.uppercase(), style = MaterialTheme.typography.labelLarge, color = Ink)
    }
}

/** Outlined mint pill, radius 40 — the loudest pill. Mono UPPERCASE. */
@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(40.dp),
        border = BorderStroke(1.5.dp, Mint),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Mint),
        modifier = modifier.height(52.dp),
    ) {
        Text(text.uppercase(), style = MaterialTheme.typography.labelLarge, color = Mint)
    }
}

/** Mono UPPERCASE eyebrow, 1.6px tracking, mint by default. */
@Composable
fun Kicker(text: String, modifier: Modifier = Modifier, color: Color = Mint) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = modifier,
    )
}

/** Mono UPPERCASE section title, muted. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, color: Color = TextMuted) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = color,
        fontWeight = FontWeight.Bold,
        modifier = modifier,
    )
}

/** Flat solid accent bar (replaces the old gradient stripe). */
@Composable
fun Rule(modifier: Modifier = Modifier, color: Color = Mint, thickness: Int = 3) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(thickness.dp)
            .background(color),
    )
}

/** Alias kept for any caller expecting the old name. */
@Composable
fun RacingStripe(modifier: Modifier = Modifier, color: Color = Mint) = Rule(modifier, color)

/** Small mono UPPERCASE tag chip with a hairline border. */
@Composable
fun Tag(text: String, modifier: Modifier = Modifier, color: Color = Mint) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, color),
        modifier = modifier,
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}
