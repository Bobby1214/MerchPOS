package com.bobbykong.merchpos.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.bobbykong.merchpos.data.model.Product
import com.bobbykong.merchpos.ui.theme.categoryTint

/**
 * Shrinks a little while pressed and springs back: the "squishy" button feel.
 * graphicsLayer only redraws, it doesn't re-measure the layout, so it is cheap.
 */
@Composable
fun Modifier.pressBounce(interactionSource: InteractionSource, pressedScale: Float = 0.94f): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "pressBounce",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** The product photo, or a pastel box with its first letter when there is no photo. */
@Composable
fun ProductPicture(product: Product, modifier: Modifier = Modifier) {
    val path = product.imagePath
    if (path != null) {
        ProductImage(path, modifier)
    } else {
        val tint = categoryTint(product.category)
        Box(modifier.background(tint.container), contentAlignment = Alignment.Center) {
            Text(
                product.name.take(1).uppercase(),
                style = MaterialTheme.typography.headlineMedium,
                color = tint.content,
            )
        }
    }
}

/** A small rounded tag, e.g. "Set" or "Sold out". */
@Composable
fun Pill(text: String, container: Color, content: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = content,
        modifier = modifier
            .background(container, CircleShape)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}
