package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Resolves any gift from an ID, name, emoji, or gift message payload string.
 */
fun resolveGift(identifier: String?): AppGift? {
    if (identifier.isNullOrBlank()) return null
    val clean = identifier.trim()
    // Match exact ID
    ALL_APP_GIFTS.find { it.id.equals(clean, ignoreCase = true) }?.let { return it }
    // Match emoji
    ALL_APP_GIFTS.find { clean.contains(it.emoji) }?.let { return it }
    // Match name in text
    ALL_APP_GIFTS.find { clean.contains(it.name, ignoreCase = true) }?.let { return it }
    // Fallback keyword matching
    return when {
        clean.contains("rose", ignoreCase = true) -> ALL_APP_GIFTS.find { it.id == "gift_rose" }
        clean.contains("heart", ignoreCase = true) -> ALL_APP_GIFTS.find { it.id == "gift_heart" }
        clean.contains("choco", ignoreCase = true) -> ALL_APP_GIFTS.find { it.id == "gift_choco" }
        clean.contains("ice", ignoreCase = true) || clean.contains("cream", ignoreCase = true) -> ALL_APP_GIFTS.find { it.id == "gift_icecream" }
        clean.contains("crown", ignoreCase = true) -> ALL_APP_GIFTS.find { it.id == "gift_crown" }
        clean.contains("letter", ignoreCase = true) -> ALL_APP_GIFTS.find { it.id == "gift_letter" }
        clean.contains("ring", ignoreCase = true) || clean.contains("diamond", ignoreCase = true) -> ALL_APP_GIFTS.find { it.id == "gift_ring" }
        clean.contains("cake", ignoreCase = true) -> ALL_APP_GIFTS.find { it.id == "gift_cake" }
        clean.contains("magic", ignoreCase = true) || clean.contains("wand", ignoreCase = true) -> ALL_APP_GIFTS.find { it.id == "gift_magic" }
        clean.contains("firework", ignoreCase = true) -> ALL_APP_GIFTS.find { it.id == "gift_fireworks" }
        clean.contains("car", ignoreCase = true) -> ALL_APP_GIFTS.find { it.id == "gift_car" }
        clean.contains("yacht", ignoreCase = true) -> ALL_APP_GIFTS.find { it.id == "gift_yacht" }
        clean.contains("jet", ignoreCase = true) || clean.contains("plane", ignoreCase = true) -> ALL_APP_GIFTS.find { it.id == "gift_jet" }
        clean.contains("castle", ignoreCase = true) -> ALL_APP_GIFTS.find { it.id == "gift_castle" }
        clean.contains("galaxy", ignoreCase = true) || clean.contains("universe", ignoreCase = true) -> ALL_APP_GIFTS.find { it.id == "gift_galaxy" }
        else -> null
    }
}

/**
 * Universal 3D Gift Icon Renderer for Gift Pad, Sent Previews, and Message Bubbles
 */
@Composable
fun Gift3DIcon(
    giftId: String,
    emoji: String = "🎁",
    modifier: Modifier = Modifier,
    size: Dp = 36.dp
) {
    val effectiveId = when {
        giftId.isNotBlank() && giftId != "gift_box" -> giftId
        else -> resolveGift(giftId.ifEmpty { emoji })?.id ?: "gift_box"
    }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        when (effectiveId) {
            "gift_rose" -> Rose3DGiftIcon(size = size)
            "gift_heart" -> LoveHeart3DGiftIcon(size = size)
            "gift_choco" -> Chocolates3DGiftIcon(size = size)
            "gift_icecream" -> IceCream3DGiftIcon(size = size)
            "gift_crown" -> RoyalCrown3DGiftIcon(size = size)
            "gift_letter" -> LoveLetter3DGiftIcon(size = size)
            "gift_ring" -> DiamondRing3DGiftIcon(size = size)
            "gift_cake" -> PartyCake3DGiftIcon(size = size)
            "gift_magic" -> MagicWand3DGiftIcon(size = size)
            "gift_fireworks" -> Fireworks3DGiftIcon(size = size)
            "gift_car" -> SportsCar3DGiftIcon(size = size)
            "gift_yacht" -> GoldenYacht3DGiftIcon(size = size)
            "gift_jet" -> PrivateJet3DGiftIcon(size = size)
            "gift_castle" -> RoyalCastle3DGiftIcon(size = size)
            "gift_galaxy" -> GalaxyUniverse3DGiftIcon(size = size)
            else -> Generic3DGiftBoxIcon(emoji = emoji, size = size)
        }
    }
}

/**
 * 1. 3D Velvet Red Rose with Metallic Stem, Multi-Layered Petals & Dewdrop
 */
@Composable
fun Rose3DGiftIcon(modifier: Modifier = Modifier, size: Dp = 36.dp) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        // Ground drop shadow
        drawOval(
            brush = Brush.radialGradient(listOf(Color(0x55000000), Color.Transparent)),
            topLeft = Offset(w * 0.20f, h * 0.86f),
            size = Size(w * 0.60f, h * 0.12f)
        )

        // Metallic Emerald Stem
        val stemPath = Path().apply {
            moveTo(w * 0.50f, h * 0.45f)
            cubicTo(w * 0.46f, h * 0.62f, w * 0.54f, h * 0.78f, w * 0.48f, h * 0.94f)
        }
        drawPath(
            path = stemPath,
            brush = Brush.linearGradient(
                listOf(Color(0xFF81C784), Color(0xFF2E7D32), Color(0xFF1B5E20)),
                start = Offset(0f, h * 0.45f),
                end = Offset(w, h * 0.94f)
            ),
            style = Stroke(width = w * 0.08f, cap = StrokeCap.Round)
        )

        // Stem Thorn
        val thorn = Path().apply {
            moveTo(w * 0.48f, h * 0.70f)
            lineTo(w * 0.38f, h * 0.67f)
            lineTo(w * 0.49f, h * 0.74f)
            close()
        }
        drawPath(thorn, Brush.linearGradient(listOf(Color(0xFF66BB6A), Color(0xFF1B5E20))))

        // Emerald Leaf with Specular Highlight
        val leaf = Path().apply {
            moveTo(w * 0.52f, h * 0.66f)
            cubicTo(w * 0.75f, h * 0.58f, w * 0.88f, h * 0.72f, w * 0.56f, h * 0.80f)
            close()
        }
        drawPath(
            leaf,
            Brush.linearGradient(listOf(Color(0xFFA5D6A7), Color(0xFF43A047), Color(0xFF1B5E20)))
        )
        // Leaf vein
        drawLine(
            Color(0x88FFFFFF),
            start = Offset(w * 0.52f, h * 0.66f),
            end = Offset(w * 0.76f, h * 0.70f),
            strokeWidth = w * 0.02f,
            cap = StrokeCap.Round
        )

        // Rose Calyx Leaves
        val calyx = Path().apply {
            moveTo(w * 0.38f, h * 0.46f)
            lineTo(w * 0.50f, h * 0.52f)
            lineTo(w * 0.62f, h * 0.46f)
            close()
        }
        drawPath(calyx, Brush.linearGradient(listOf(Color(0xFF81C784), Color(0xFF2E7D32))))

        // Deep 3D Shadow Petals (Back Layer)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFB71C1C), Color(0xFF5A000A), Color(0xFF2B0004)),
                center = Offset(w * 0.50f, h * 0.36f),
                radius = w * 0.44f
            ),
            radius = w * 0.38f,
            center = Offset(w * 0.50f, h * 0.35f)
        )

        // Outer Velvet Petals (Left & Right Wings)
        val leftPetal = Path().apply {
            moveTo(w * 0.16f, h * 0.32f)
            cubicTo(w * 0.12f, h * 0.16f, w * 0.45f, h * 0.12f, w * 0.50f, h * 0.28f)
            cubicTo(w * 0.38f, h * 0.48f, w * 0.22f, h * 0.46f, w * 0.16f, h * 0.32f)
            close()
        }
        drawPath(
            leftPetal,
            Brush.linearGradient(listOf(Color(0xFFFF5252), Color(0xFFE53935), Color(0xFF880E4F)))
        )

        val rightPetal = Path().apply {
            moveTo(w * 0.84f, h * 0.32f)
            cubicTo(w * 0.88f, h * 0.16f, w * 0.55f, h * 0.12f, w * 0.50f, h * 0.28f)
            cubicTo(w * 0.62f, h * 0.48f, w * 0.78f, h * 0.46f, w * 0.84f, h * 0.32f)
            close()
        }
        drawPath(
            rightPetal,
            Brush.linearGradient(listOf(Color(0xFFFF1744), Color(0xFFD50000), Color(0xFF4A0008)))
        )

        // Mid-Layer Volumetric Cup Petal
        val cupPetal = Path().apply {
            moveTo(w * 0.24f, h * 0.30f)
            cubicTo(w * 0.28f, h * 0.52f, w * 0.72f, h * 0.52f, w * 0.76f, h * 0.30f)
            cubicTo(w * 0.60f, h * 0.42f, w * 0.40f, h * 0.42f, w * 0.24f, h * 0.30f)
            close()
        }
        drawPath(
            cupPetal,
            Brush.verticalGradient(listOf(Color(0xFFFF8A80), Color(0xFFD50000), Color(0xFF880E4F)))
        )

        // Rose Core Bud Spiral with Velvet Shading
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFFF80AB), Color(0xFFFF1744), Color(0xFF880E4F), Color(0xFF310006)),
                center = Offset(w * 0.46f, h * 0.28f),
                radius = w * 0.22f
            ),
            radius = w * 0.20f,
            center = Offset(w * 0.50f, h * 0.30f)
        )

        // Golden & White Dewdrop Glints
        drawCircle(Color(0xEEFFFFFF), radius = w * 0.035f, center = Offset(w * 0.38f, h * 0.24f))
        drawCircle(Color(0xCCFFFFFF), radius = w * 0.02f, center = Offset(w * 0.42f, h * 0.22f))
        drawCircle(Color(0xAAFFD700), radius = w * 0.025f, center = Offset(w * 0.64f, h * 0.36f))
    }
}

/**
 * 2. 3D Volumetric Glossy Love Heart with Curved Glass Specular Sheen
 */
@Composable
fun LoveHeart3DGiftIcon(modifier: Modifier = Modifier, size: Dp = 36.dp) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        // Ground drop shadow
        drawOval(
            brush = Brush.radialGradient(listOf(Color(0x55E91E63), Color.Transparent)),
            topLeft = Offset(w * 0.15f, h * 0.82f),
            size = Size(w * 0.70f, h * 0.16f)
        )

        // Main 3D Heart Curve Path
        val heartPath = Path().apply {
            moveTo(w * 0.50f, h * 0.86f)
            cubicTo(w * 0.08f, h * 0.56f, w * 0.04f, h * 0.18f, w * 0.30f, h * 0.15f)
            cubicTo(w * 0.44f, h * 0.15f, w * 0.49f, h * 0.28f, w * 0.50f, h * 0.30f)
            cubicTo(w * 0.51f, h * 0.28f, w * 0.56f, h * 0.15f, w * 0.70f, h * 0.15f)
            cubicTo(w * 0.96f, h * 0.18f, w * 0.92f, h * 0.56f, w * 0.50f, h * 0.86f)
            close()
        }

        // Deep Ambient Rim
        drawPath(
            path = heartPath,
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFFF4081), Color(0xFFE91E63), Color(0xFFC2185B), Color(0xFF4A0018)),
                center = Offset(w * 0.40f, h * 0.36f),
                radius = w * 0.58f
            )
        )

        // Upper Left Specular Arc (Curved Glass Reflection)
        val glassSheen = Path().apply {
            moveTo(w * 0.26f, h * 0.20f)
            cubicTo(w * 0.34f, h * 0.17f, w * 0.42f, h * 0.22f, w * 0.44f, h * 0.28f)
            cubicTo(w * 0.38f, h * 0.35f, w * 0.25f, h * 0.35f, w * 0.22f, h * 0.26f)
            close()
        }
        drawPath(
            glassSheen,
            Brush.linearGradient(
                listOf(Color(0xFFFFFFFF), Color(0x88FFFFFF), Color.Transparent),
                start = Offset(w * 0.22f, h * 0.18f),
                end = Offset(w * 0.44f, h * 0.35f)
            )
        )

        // Lower right bounce light
        val bounceSheen = Path().apply {
            moveTo(w * 0.72f, h * 0.35f)
            cubicTo(w * 0.84f, h * 0.42f, w * 0.75f, h * 0.65f, w * 0.58f, h * 0.74f)
            cubicTo(w * 0.68f, h * 0.62f, w * 0.78f, h * 0.50f, w * 0.72f, h * 0.35f)
            close()
        }
        drawPath(bounceSheen, Brush.linearGradient(listOf(Color(0x66FF80AB), Color.Transparent)))

        // Star Glints
        drawCircle(Color.White, radius = w * 0.04f, center = Offset(w * 0.32f, h * 0.22f))
        drawCircle(Color(0xDDFFD700), radius = w * 0.025f, center = Offset(w * 0.72f, h * 0.20f))
    }
}

/**
 * 3. 3D Luxury Chocolate Box with Satin Gold Ribbons & Gourmet Truffles
 */
@Composable
fun Chocolates3DGiftIcon(modifier: Modifier = Modifier, size: Dp = 36.dp) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        // Ground shadow
        drawOval(
            brush = Brush.radialGradient(listOf(Color(0x66000000), Color.Transparent)),
            topLeft = Offset(w * 0.10f, h * 0.78f),
            size = Size(w * 0.80f, h * 0.18f)
        )

        // 3D Octagonal Luxury Box Body
        val boxPath = Path().apply {
            moveTo(w * 0.24f, h * 0.24f)
            lineTo(w * 0.76f, h * 0.24f)
            lineTo(w * 0.90f, h * 0.45f)
            lineTo(w * 0.84f, h * 0.78f)
            lineTo(w * 0.16f, h * 0.78f)
            lineTo(w * 0.10f, h * 0.45f)
            close()
        }
        drawPath(
            boxPath,
            Brush.linearGradient(
                listOf(Color(0xFF6D4C41), Color(0xFF4E342E), Color(0xFF27150F)),
                start = Offset(w * 0.20f, h * 0.20f),
                end = Offset(w * 0.80f, h * 0.80f)
            )
        )

        // Gold Trim Border
        drawPath(
            boxPath,
            Brush.linearGradient(listOf(Color(0xFFFFECB3), Color(0xFFFFD54F), Color(0xFFFF8F00))),
            style = Stroke(width = w * 0.035f)
        )

        // 4 Truffles inside
        // Truffle 1: Dark Chocolate Swirl
        drawCircle(
            brush = Brush.radialGradient(listOf(Color(0xFF5D4037), Color(0xFF21110C)), center = Offset(w * 0.35f, h * 0.44f), radius = w * 0.12f),
            radius = w * 0.11f,
            center = Offset(w * 0.37f, h * 0.46f)
        )
        // Truffle 2: Gold-Wrapped Hazelnut
        drawCircle(
            brush = Brush.radialGradient(listOf(Color(0xFFFFF9C4), Color(0xFFFFB300), Color(0xFFE65100)), center = Offset(w * 0.61f, h * 0.44f), radius = w * 0.12f),
            radius = w * 0.11f,
            center = Offset(w * 0.63f, h * 0.46f)
        )
        // Truffle 3: White Chocolate Berry
        drawCircle(
            brush = Brush.radialGradient(listOf(Color(0xFFFFF8E1), Color(0xFFFFCCBC), Color(0xFFD84315)), center = Offset(w * 0.35f, h * 0.63f), radius = w * 0.11f),
            radius = w * 0.10f,
            center = Offset(w * 0.37f, h * 0.64f)
        )
        // Truffle 4: Caramel Cube
        drawRoundRect(
            brush = Brush.linearGradient(listOf(Color(0xFFFFE082), Color(0xFFFF8F00), Color(0xFF4E342E))),
            topLeft = Offset(w * 0.54f, h * 0.56f),
            size = Size(w * 0.18f, h * 0.16f),
            cornerRadius = CornerRadius(w * 0.04f, w * 0.04f)
        )

        // Satin Gold Ribbon Crossing Box
        drawRect(
            brush = Brush.linearGradient(listOf(Color(0xFFFFF59D), Color(0xFFFFD54F), Color(0xFFFF8F00))),
            topLeft = Offset(w * 0.45f, h * 0.24f),
            size = Size(w * 0.10f, h * 0.54f)
        )

        // 3D Ribbon Bow Knot in Center
        drawCircle(
            brush = Brush.radialGradient(listOf(Color(0xFFFFFDE7), Color(0xFFFFD54F), Color(0xFFE65100))),
            radius = w * 0.09f,
            center = Offset(w * 0.50f, h * 0.50f)
        )
        // Bow loops
        drawOval(
            brush = Brush.linearGradient(listOf(Color(0xFFFFF59D), Color(0xFFFFB300))),
            topLeft = Offset(w * 0.34f, h * 0.44f),
            size = Size(w * 0.16f, h * 0.12f)
        )
        drawOval(
            brush = Brush.linearGradient(listOf(Color(0xFFFFF59D), Color(0xFFFFB300))),
            topLeft = Offset(w * 0.50f, h * 0.44f),
            size = Size(w * 0.16f, h * 0.12f)
        )
    }
}

/**
 * 4. 3D Gourmet Ice Cream with Textured Waffle Cone, Dripping Strawberry & Cherry
 */
@Composable
fun IceCream3DGiftIcon(modifier: Modifier = Modifier, size: Dp = 36.dp) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        // Ground shadow
        drawOval(
            brush = Brush.radialGradient(listOf(Color(0x44000000), Color.Transparent)),
            topLeft = Offset(w * 0.25f, h * 0.88f),
            size = Size(w * 0.50f, h * 0.10f)
        )

        // Crispy Waffle Cone
        val cone = Path().apply {
            moveTo(w * 0.26f, h * 0.48f)
            lineTo(w * 0.74f, h * 0.48f)
            lineTo(w * 0.50f, h * 0.94f)
            close()
        }
        drawPath(
            cone,
            Brush.linearGradient(
                listOf(Color(0xFFFFE0B2), Color(0xFFFFB74D), Color(0xFFE65100)),
                start = Offset(w * 0.30f, h * 0.48f),
                end = Offset(w * 0.70f, h * 0.94f)
            )
        )
        // Cone Waffle Diamond Hatching
        drawLine(Color(0x558D6E63), Offset(w * 0.36f, h * 0.52f), Offset(w * 0.54f, h * 0.86f), strokeWidth = w * 0.02f)
        drawLine(Color(0x558D6E63), Offset(w * 0.48f, h * 0.50f), Offset(w * 0.58f, h * 0.78f), strokeWidth = w * 0.02f)
        drawLine(Color(0x558D6E63), Offset(w * 0.64f, h * 0.52f), Offset(w * 0.46f, h * 0.86f), strokeWidth = w * 0.02f)
        drawLine(Color(0x558D6E63), Offset(w * 0.52f, h * 0.50f), Offset(w * 0.42f, h * 0.78f), strokeWidth = w * 0.02f)

        // Bottom Scoop: Berry Strawberry with Drips
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFFF80AB), Color(0xFFFF4081), Color(0xFFC2185B)),
                center = Offset(w * 0.44f, h * 0.42f),
                radius = w * 0.25f
            ),
            radius = w * 0.23f,
            center = Offset(w * 0.50f, h * 0.44f)
        )
        // Strawberry drip over waffle
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(Color(0xFFFF4081), Color(0xFFC2185B))),
            topLeft = Offset(w * 0.38f, h * 0.52f),
            size = Size(w * 0.08f, h * 0.12f),
            cornerRadius = CornerRadius(w * 0.04f, w * 0.04f)
        )

        // Top Scoop: Silky French Vanilla Cream
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFFFFDE7), Color(0xFFFFF59D), Color(0xFFFFD54F), Color(0xFFFFA000)),
                center = Offset(w * 0.44f, h * 0.26f),
                radius = w * 0.22f
            ),
            radius = w * 0.20f,
            center = Offset(w * 0.50f, h * 0.28f)
        )

        // Colorful Sugar Sprinkles
        drawCircle(Color(0xFF00E5FF), radius = w * 0.025f, center = Offset(w * 0.40f, h * 0.28f))
        drawCircle(Color(0xFF76FF03), radius = w * 0.025f, center = Offset(w * 0.60f, h * 0.30f))
        drawCircle(Color(0xFFFF1744), radius = w * 0.025f, center = Offset(w * 0.48f, h * 0.36f))

        // Glossy Maraschino Cherry with Curved Stem
        val stem = Path().apply {
            moveTo(w * 0.50f, h * 0.15f)
            cubicTo(w * 0.58f, h * 0.06f, w * 0.66f, h * 0.08f, w * 0.68f, h * 0.04f)
        }
        drawPath(stem, Brush.linearGradient(listOf(Color(0xFF5D4037), Color(0xFF33691E))), style = Stroke(width = w * 0.035f, cap = StrokeCap.Round))

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFFF5252), Color(0xFFD50000), Color(0xFF5A000A)),
                center = Offset(w * 0.46f, h * 0.14f),
                radius = w * 0.11f
            ),
            radius = w * 0.09f,
            center = Offset(w * 0.50f, h * 0.15f)
        )
        drawCircle(Color.White, radius = w * 0.025f, center = Offset(w * 0.47f, h * 0.13f))
    }
}

/**
 * 5. 3D Royal Imperial Crown with Gold Arches, Velvet Cap & Glowing Gems
 */
@Composable
fun RoyalCrown3DGiftIcon(modifier: Modifier = Modifier, size: Dp = 36.dp) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        // Ground shadow
        drawOval(
            brush = Brush.radialGradient(listOf(Color(0x55000000), Color.Transparent)),
            topLeft = Offset(w * 0.12f, h * 0.78f),
            size = Size(w * 0.76f, h * 0.14f)
        )

        // Royal Purple Velvet Cushion Dome (Inside)
        val cushion = Path().apply {
            moveTo(w * 0.20f, h * 0.65f)
            cubicTo(w * 0.22f, h * 0.32f, w * 0.78f, h * 0.32f, w * 0.80f, h * 0.65f)
            close()
        }
        drawPath(
            cushion,
            Brush.radialGradient(
                colors = listOf(Color(0xFFBA68C8), Color(0xFF7B1FA2), Color(0xFF311B92)),
                center = Offset(w * 0.50f, h * 0.45f),
                radius = w * 0.35f
            )
        )

        // 3D Sculpted Gold Crown Body
        val crown = Path().apply {
            moveTo(w * 0.12f, h * 0.72f)
            lineTo(w * 0.88f, h * 0.72f)
            lineTo(w * 0.92f, h * 0.36f)
            lineTo(w * 0.70f, h * 0.50f)
            lineTo(w * 0.50f, h * 0.18f)
            lineTo(w * 0.30f, h * 0.50f)
            lineTo(w * 0.08f, h * 0.36f)
            close()
        }
        drawPath(
            crown,
            Brush.linearGradient(
                listOf(Color(0xFFFFF9C4), Color(0xFFFFD54F), Color(0xFFFFB300), Color(0xFFE65100)),
                start = Offset(w * 0.10f, h * 0.20f),
                end = Offset(w * 0.90f, h * 0.75f)
            )
        )

        // Heavy Gold Rim Band with Filigree
        drawRoundRect(
            brush = Brush.linearGradient(listOf(Color(0xFFFFFDE7), Color(0xFFFFD54F), Color(0xFFFF8F00), Color(0xFFBF360C))),
            topLeft = Offset(w * 0.10f, h * 0.64f),
            size = Size(w * 0.80f, h * 0.14f),
            cornerRadius = CornerRadius(w * 0.05f, w * 0.05f)
        )

        // Precious Gemstones in Band
        // Central Oval Ruby
        drawOval(
            brush = Brush.radialGradient(listOf(Color(0xFFFF5252), Color(0xFFD50000), Color(0xFF4A0008))),
            topLeft = Offset(w * 0.44f, h * 0.66f),
            size = Size(w * 0.12f, h * 0.10f)
        )
        // Left Sapphire
        drawCircle(
            brush = Brush.radialGradient(listOf(Color(0xFF80D8FF), Color(0xFF0091EA), Color(0xFF01579B))),
            radius = w * 0.045f,
            center = Offset(w * 0.26f, h * 0.71f)
        )
        // Right Sapphire
        drawCircle(
            brush = Brush.radialGradient(listOf(Color(0xFF80D8FF), Color(0xFF0091EA), Color(0xFF01579B))),
            radius = w * 0.045f,
            center = Offset(w * 0.74f, h * 0.71f)
        )

        // Luminous White Pearls on Pinnacle Tips
        drawCircle(
            brush = Brush.radialGradient(listOf(Color(0xFFFFFFFF), Color(0xFFE0E0E0), Color(0xFF9E9E9E))),
            radius = w * 0.055f,
            center = Offset(w * 0.50f, h * 0.18f)
        )
        drawCircle(
            brush = Brush.radialGradient(listOf(Color(0xFFFFFFFF), Color(0xFFE0E0E0), Color(0xFF9E9E9E))),
            radius = w * 0.045f,
            center = Offset(w * 0.08f, h * 0.36f)
        )
        drawCircle(
            brush = Brush.radialGradient(listOf(Color(0xFFFFFFFF), Color(0xFFE0E0E0), Color(0xFF9E9E9E))),
            radius = w * 0.045f,
            center = Offset(w * 0.92f, h * 0.36f)
        )

        // Diamond Sparkles on Peaks
        drawCircle(Color.White, radius = w * 0.025f, center = Offset(w * 0.50f, h * 0.17f))
    }
}

/**
 * 6. 3D Love Letter Envelope with Gold Foil Border, Folded Letter & Ruby Wax Seal
 */
@Composable
fun LoveLetter3DGiftIcon(modifier: Modifier = Modifier, size: Dp = 36.dp) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        // Ground shadow
        drawOval(
            brush = Brush.radialGradient(listOf(Color(0x44000000), Color.Transparent)),
            topLeft = Offset(w * 0.10f, h * 0.80f),
            size = Size(w * 0.80f, h * 0.14f)
        )

        // 3D Envelope Base Pocket
        drawRoundRect(
            brush = Brush.linearGradient(
                listOf(Color(0xFFFFFFFF), Color(0xFFFFF3E0), Color(0xFFFFE0B2)),
                start = Offset(w * 0.10f, h * 0.28f),
                end = Offset(w * 0.90f, h * 0.80f)
            ),
            topLeft = Offset(w * 0.10f, h * 0.30f),
            size = Size(w * 0.80f, h * 0.52f),
            cornerRadius = CornerRadius(w * 0.07f, w * 0.07f)
        )

        // Gold Foil Border Trim
        drawRoundRect(
            brush = Brush.linearGradient(listOf(Color(0xFFFFECB3), Color(0xFFFFD54F), Color(0xFFFF8F00))),
            topLeft = Offset(w * 0.10f, h * 0.30f),
            size = Size(w * 0.80f, h * 0.52f),
            cornerRadius = CornerRadius(w * 0.07f, w * 0.07f),
            style = Stroke(width = w * 0.035f)
        )

        // Folded Love Letter Peeking Out
        drawRoundRect(
            brush = Brush.linearGradient(listOf(Color(0xFFFFFFFF), Color(0xFFFCE4EC))),
            topLeft = Offset(w * 0.20f, h * 0.16f),
            size = Size(w * 0.60f, h * 0.30f),
            cornerRadius = CornerRadius(w * 0.04f, w * 0.04f)
        )
        // Gold cursive letter lines
        drawLine(Color(0x88FFB300), Offset(w * 0.28f, h * 0.22f), Offset(w * 0.72f, h * 0.22f), strokeWidth = w * 0.02f)
        drawLine(Color(0x88FFB300), Offset(w * 0.28f, h * 0.28f), Offset(w * 0.64f, h * 0.28f), strokeWidth = w * 0.02f)

        // Envelope Open Flap Geometry
        val flap = Path().apply {
            moveTo(w * 0.10f, h * 0.30f)
            lineTo(w * 0.50f, h * 0.58f)
            lineTo(w * 0.90f, h * 0.30f)
            close()
        }
        drawPath(
            flap,
            Brush.linearGradient(
                listOf(Color(0xFFFFE0B2), Color(0xFFFFCC80), Color(0xFFFFA726)),
                start = Offset(w * 0.50f, h * 0.30f),
                end = Offset(w * 0.50f, h * 0.58f)
            )
        )

        // 3D Ruby Red Wax Seal Medallion
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFFF5252), Color(0xFFD50000), Color(0xFF5A000A)),
                center = Offset(w * 0.48f, h * 0.56f),
                radius = w * 0.14f
            ),
            radius = w * 0.13f,
            center = Offset(w * 0.50f, h * 0.58f)
        )
        // Embossed gold heart in wax seal
        val sealHeart = Path().apply {
            moveTo(w * 0.50f, h * 0.62f)
            cubicTo(w * 0.42f, h * 0.56f, w * 0.40f, h * 0.52f, w * 0.45f, h * 0.50f)
            cubicTo(w * 0.48f, h * 0.50f, w * 0.50f, h * 0.53f, w * 0.50f, h * 0.53f)
            cubicTo(w * 0.50f, h * 0.53f, w * 0.52f, h * 0.50f, w * 0.55f, h * 0.50f)
            cubicTo(w * 0.60f, h * 0.52f, w * 0.58f, h * 0.56f, w * 0.50f, h * 0.62f)
            close()
        }
        drawPath(sealHeart, Brush.linearGradient(listOf(Color(0xFFFFF9C4), Color(0xFFFFD54F))))

        // Floating Gold Sparkle Stars
        drawCircle(Color(0xFFFFD700), radius = w * 0.035f, center = Offset(w * 0.82f, h * 0.20f))
        drawCircle(Color(0xFFFF80AB), radius = w * 0.03f, center = Offset(w * 0.16f, h * 0.20f))
    }
}

/**
 * 7. 3D Sparkling Brilliant Diamond Ring with Platinum Band & Multi-Faceted Gem
 */
@Composable
fun DiamondRing3DGiftIcon(modifier: Modifier = Modifier, size: Dp = 36.dp) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        // Ground shadow
        drawOval(
            brush = Brush.radialGradient(listOf(Color(0x44000000), Color.Transparent)),
            topLeft = Offset(w * 0.20f, h * 0.86f),
            size = Size(w * 0.60f, h * 0.12f)
        )

        // Heavy Polished Platinum Band
        drawCircle(
            brush = Brush.linearGradient(
                listOf(Color(0xFFFFFFFF), Color(0xFFECEFF1), Color(0xFFB0BEC5), Color(0xFF78909C), Color(0xFFCFD8DC)),
                start = Offset(w * 0.20f, h * 0.40f),
                end = Offset(w * 0.80f, h * 0.85f)
            ),
            radius = w * 0.28f,
            center = Offset(w * 0.50f, h * 0.60f),
            style = Stroke(width = w * 0.11f)
        )

        // Solitaire 6-Prong Crown Mount
        val prongLeft = Path().apply {
            moveTo(w * 0.32f, h * 0.42f)
            lineTo(w * 0.26f, h * 0.34f)
        }
        val prongRight = Path().apply {
            moveTo(w * 0.68f, h * 0.42f)
            lineTo(w * 0.74f, h * 0.34f)
        }
        drawPath(prongLeft, Color(0xFFECEFF1), style = Stroke(width = w * 0.04f, cap = StrokeCap.Round))
        drawPath(prongRight, Color(0xFFECEFF1), style = Stroke(width = w * 0.04f, cap = StrokeCap.Round))

        // Brilliant Cut 3D Diamond Crown & Pavilion
        val diamond = Path().apply {
            moveTo(w * 0.30f, h * 0.30f)
            lineTo(w * 0.70f, h * 0.30f)
            lineTo(w * 0.84f, h * 0.42f)
            lineTo(w * 0.50f, h * 0.68f)
            lineTo(w * 0.16f, h * 0.42f)
            close()
        }
        drawPath(
            diamond,
            Brush.linearGradient(
                listOf(Color(0xFFE1F5FE), Color(0xFF81D4FA), Color(0xFF0288D1), Color(0xFF01579B)),
                start = Offset(w * 0.30f, h * 0.25f),
                end = Offset(w * 0.60f, h * 0.65f)
            )
        )

        // Internal Geometric Facets
        // Table Upper Flat
        val table = Path().apply {
            moveTo(w * 0.36f, h * 0.30f)
            lineTo(w * 0.64f, h * 0.30f)
            lineTo(w * 0.58f, h * 0.42f)
            lineTo(w * 0.42f, h * 0.42f)
            close()
        }
        drawPath(table, Brush.linearGradient(listOf(Color(0xFFFFFFFF), Color(0xFFB3E5FC))))

        // Lower Pavilion Facets
        drawLine(Color(0x99FFFFFF), Offset(w * 0.42f, h * 0.42f), Offset(w * 0.50f, h * 0.68f), strokeWidth = w * 0.02f)
        drawLine(Color(0x99FFFFFF), Offset(w * 0.58f, h * 0.42f), Offset(w * 0.50f, h * 0.68f), strokeWidth = w * 0.02f)
        drawLine(Color(0x77FFFFFF), Offset(w * 0.16f, h * 0.42f), Offset(w * 0.42f, h * 0.42f), strokeWidth = w * 0.015f)
        drawLine(Color(0x77FFFFFF), Offset(w * 0.84f, h * 0.42f), Offset(w * 0.58f, h * 0.42f), strokeWidth = w * 0.015f)

        // Brilliant Star Ray Glint
        val glintCenter = Offset(w * 0.34f, h * 0.30f)
        drawLine(Color.White, Offset(glintCenter.x - w * 0.12f, glintCenter.y), Offset(glintCenter.x + w * 0.12f, glintCenter.y), strokeWidth = w * 0.035f, cap = StrokeCap.Round)
        drawLine(Color.White, Offset(glintCenter.x, glintCenter.y - h * 0.12f), Offset(glintCenter.x, glintCenter.y + h * 0.12f), strokeWidth = w * 0.035f, cap = StrokeCap.Round)
        drawCircle(Color(0xFF00E5FF), radius = w * 0.04f, center = glintCenter)
    }
}

/**
 * 8. 3D Two-Tier Celebration Cake with Ganache Drips, Whipped Rosettes & Candle Flame
 */
@Composable
fun PartyCake3DGiftIcon(modifier: Modifier = Modifier, size: Dp = 36.dp) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        // Ground shadow
        drawOval(
            brush = Brush.radialGradient(listOf(Color(0x55000000), Color.Transparent)),
            topLeft = Offset(w * 0.10f, h * 0.84f),
            size = Size(w * 0.80f, h * 0.14f)
        )

        // Bottom Cake Tier (Rich Strawberry Sponge)
        drawRoundRect(
            brush = Brush.linearGradient(
                listOf(Color(0xFFF8BBD0), Color(0xFFF06292), Color(0xFFAD1457)),
                start = Offset(w * 0.12f, h * 0.55f),
                end = Offset(w * 0.88f, h * 0.85f)
            ),
            topLeft = Offset(w * 0.12f, h * 0.56f),
            size = Size(w * 0.76f, h * 0.30f),
            cornerRadius = CornerRadius(w * 0.07f, w * 0.07f)
        )
        // Chocolate Drip on bottom tier
        val dripPath = Path().apply {
            moveTo(w * 0.12f, h * 0.56f)
            cubicTo(w * 0.24f, h * 0.68f, w * 0.34f, h * 0.56f, w * 0.44f, h * 0.68f)
            cubicTo(w * 0.54f, h * 0.56f, w * 0.66f, h * 0.70f, w * 0.76f, h * 0.56f)
            lineTo(w * 0.88f, h * 0.56f)
            lineTo(w * 0.88f, h * 0.50f)
            lineTo(w * 0.12f, h * 0.50f)
            close()
        }
        drawPath(dripPath, Brush.verticalGradient(listOf(Color(0xFF4E342E), Color(0xFF27150F))))

        // Top Cake Tier (Golden Vanilla)
        drawRoundRect(
            brush = Brush.linearGradient(
                listOf(Color(0xFFFFF9C4), Color(0xFFFFF176), Color(0xFFFBC02D), Color(0xFFE65100)),
                start = Offset(w * 0.24f, h * 0.34f),
                end = Offset(w * 0.76f, h * 0.56f)
            ),
            topLeft = Offset(w * 0.24f, h * 0.34f),
            size = Size(w * 0.52f, h * 0.22f),
            cornerRadius = CornerRadius(w * 0.06f, w * 0.06f)
        )

        // Whipped Cream Rosettes on top tier
        drawCircle(Color.White, radius = w * 0.05f, center = Offset(w * 0.32f, h * 0.34f))
        drawCircle(Color.White, radius = w * 0.05f, center = Offset(w * 0.50f, h * 0.34f))
        drawCircle(Color.White, radius = w * 0.05f, center = Offset(w * 0.68f, h * 0.34f))

        // Striped Party Candle
        drawRoundRect(
            brush = Brush.linearGradient(listOf(Color(0xFF80D8FF), Color(0xFF0091EA), Color(0xFF01579B))),
            topLeft = Offset(w * 0.46f, h * 0.18f),
            size = Size(w * 0.08f, h * 0.16f),
            cornerRadius = CornerRadius(w * 0.02f, w * 0.02f)
        )
        // Gold stripe on candle
        drawLine(Color(0xFFFFD54F), Offset(w * 0.46f, h * 0.24f), Offset(w * 0.54f, h * 0.22f), strokeWidth = w * 0.02f)

        // Dynamic 3D Candle Flame
        // Outer warm halo
        drawCircle(
            brush = Brush.radialGradient(listOf(Color(0x88FFD54F), Color.Transparent)),
            radius = w * 0.16f,
            center = Offset(w * 0.50f, h * 0.11f)
        )
        // Teardrop flame body
        val flame = Path().apply {
            moveTo(w * 0.50f, h * 0.04f)
            cubicTo(w * 0.42f, h * 0.10f, w * 0.42f, h * 0.17f, w * 0.50f, h * 0.17f)
            cubicTo(w * 0.58f, h * 0.17f, w * 0.58f, h * 0.10f, w * 0.50f, h * 0.04f)
            close()
        }
        drawPath(
            flame,
            Brush.radialGradient(
                listOf(Color(0xFFFFFFFF), Color(0xFFFFFF00), Color(0xFFFF6D00), Color(0xFFD50000)),
                center = Offset(w * 0.50f, h * 0.14f),
                radius = w * 0.09f
            )
        )
    }
}

/**
 * 9. 3D Celestial Magic Wand with Gold Filigree, 5-Point Star & Orbital Glow
 */
@Composable
fun MagicWand3DGiftIcon(modifier: Modifier = Modifier, size: Dp = 36.dp) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        // Wand Shaft (Midnight Obsidian & Royal Purple)
        drawLine(
            brush = Brush.linearGradient(
                listOf(Color(0xFFE1BEE7), Color(0xFFBA68C8), Color(0xFF6A1B9A), Color(0xFF311B92)),
                start = Offset(w * 0.15f, h * 0.90f),
                end = Offset(w * 0.65f, h * 0.35f)
            ),
            start = Offset(w * 0.15f, h * 0.90f),
            end = Offset(w * 0.65f, h * 0.35f),
            strokeWidth = w * 0.09f,
            cap = StrokeCap.Round
        )

        // Spiraling Gold Filigree around shaft
        drawLine(
            Color(0xFFFFD700),
            Offset(w * 0.28f, h * 0.76f),
            Offset(w * 0.36f, h * 0.68f),
            strokeWidth = w * 0.025f,
            cap = StrokeCap.Round
        )
        drawLine(
            Color(0xFFFFD700),
            Offset(w * 0.45f, h * 0.57f),
            Offset(w * 0.53f, h * 0.48f),
            strokeWidth = w * 0.025f,
            cap = StrokeCap.Round
        )

        // Radiant Celestial Star Ambient Aura
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color(0xAAFFF9C4), Color(0x66FFD54F), Color(0x2200E5FF), Color.Transparent),
                center = Offset(w * 0.70f, h * 0.28f),
                radius = w * 0.30f
            ),
            radius = w * 0.28f,
            center = Offset(w * 0.70f, h * 0.28f)
        )

        // Elliptical Orbital Ring
        drawOval(
            brush = Brush.linearGradient(listOf(Color(0xFF00E5FF), Color(0xFFFF4081), Color(0xFFFFD700))),
            topLeft = Offset(w * 0.44f, h * 0.18f),
            size = Size(w * 0.52f, h * 0.20f),
            style = Stroke(width = w * 0.035f)
        )

        // 3D Faceted 5-Point Celestial Golden Star
        val star = Path().apply {
            val cx = w * 0.70f
            val cy = w * 0.28f
            val rOuter = w * 0.22f
            val rInner = w * 0.09f
            for (i in 0 until 10) {
                val r = if (i % 2 == 0) rOuter else rInner
                val angle = Math.toRadians((i * 36.0 - 90.0)).toFloat()
                val x = cx + r * cos(angle.toDouble()).toFloat()
                val y = cy + r * sin(angle.toDouble()).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        drawPath(
            star,
            Brush.radialGradient(
                listOf(Color(0xFFFFFFFF), Color(0xFFFFF59D), Color(0xFFFFD54F), Color(0xFFFF8F00)),
                center = Offset(w * 0.68f, h * 0.26f),
                radius = w * 0.22f
            )
        )

        // Floating Stardust Particles
        drawCircle(Color(0xFF00E5FF), radius = w * 0.04f, center = Offset(w * 0.36f, h * 0.24f))
        drawCircle(Color(0xFFFF4081), radius = w * 0.035f, center = Offset(w * 0.88f, h * 0.48f))
        drawCircle(Color(0xFF76FF03), radius = w * 0.025f, center = Offset(w * 0.85f, h * 0.14f))
    }
}

/**
 * 10. 3D Luxury Firework Rocket with Exploding Starburst & Radiant Trails
 */
@Composable
fun Fireworks3DGiftIcon(modifier: Modifier = Modifier, size: Dp = 36.dp) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        // Exploding Starburst trails in background
        val burstCenter = Offset(w * 0.50f, h * 0.24f)
        for (angleDeg in listOf(0, 45, 90, 135, 180, 225, 270, 315)) {
            val rad = Math.toRadians(angleDeg.toDouble())
            val startDist = w * 0.12f
            val endDist = w * 0.38f
            val x1 = burstCenter.x + (startDist * cos(rad)).toFloat()
            val y1 = burstCenter.y + (startDist * sin(rad)).toFloat()
            val x2 = burstCenter.x + (endDist * cos(rad)).toFloat()
            val y2 = burstCenter.y + (endDist * sin(rad)).toFloat()
            drawLine(
                brush = Brush.linearGradient(listOf(Color(0xFFFFD700), Color(0xFFFF4081), Color(0xFF00E5FF))),
                start = Offset(x1, y1),
                end = Offset(x2, y2),
                strokeWidth = w * 0.035f,
                cap = StrokeCap.Round
            )
        }

        // Sparkling burst dots
        drawCircle(Color(0xFF00E5FF), radius = w * 0.04f, center = Offset(w * 0.20f, h * 0.14f))
        drawCircle(Color(0xFFFF4081), radius = w * 0.04f, center = Offset(w * 0.80f, h * 0.14f))
        drawCircle(Color(0xFFFFD700), radius = w * 0.045f, center = Offset(w * 0.50f, h * 0.05f))

        // Metallic Firework Rocket Cylinder
        val rocket = Path().apply {
            moveTo(w * 0.38f, h * 0.80f)
            lineTo(w * 0.62f, h * 0.80f)
            lineTo(w * 0.62f, h * 0.44f)
            lineTo(w * 0.50f, h * 0.22f)
            lineTo(w * 0.38f, h * 0.44f)
            close()
        }
        drawPath(
            rocket,
            Brush.linearGradient(
                listOf(Color(0xFFFF5252), Color(0xFFFF1744), Color(0xFFD50000), Color(0xFF880E4F)),
                start = Offset(w * 0.38f, h * 0.22f),
                end = Offset(w * 0.62f, h * 0.80f)
            )
        )

        // Conical Brass Nosecone
        val cone = Path().apply {
            moveTo(w * 0.38f, h * 0.44f)
            lineTo(w * 0.50f, h * 0.22f)
            lineTo(w * 0.62f, h * 0.44f)
            close()
        }
        drawPath(cone, Brush.linearGradient(listOf(Color(0xFFFFF9C4), Color(0xFFFFD54F), Color(0xFFFF8F00))))

        // Diagonal Golden Spiral Stripe on rocket
        drawRect(
            brush = Brush.linearGradient(listOf(Color(0xFFFFF59D), Color(0xFFFFB300))),
            topLeft = Offset(w * 0.38f, h * 0.56f),
            size = Size(w * 0.24f, h * 0.09f)
        )

        // Sizzling Fuse Spark
        drawLine(
            Color(0xFF8D6E63),
            Offset(w * 0.50f, h * 0.80f),
            Offset(w * 0.50f, h * 0.94f),
            strokeWidth = w * 0.03f,
            cap = StrokeCap.Round
        )
        drawCircle(
            brush = Brush.radialGradient(listOf(Color(0xFFFFFFFF), Color(0xFFFFD600), Color(0xFFFF3D00))),
            radius = w * 0.05f,
            center = Offset(w * 0.50f, h * 0.94f)
        )
    }
}

/**
 * 11. 3D Aerodynamic Scarlet Hypercar with Carbon Accents & Xenon Headlights
 */
@Composable
fun SportsCar3DGiftIcon(modifier: Modifier = Modifier, size: Dp = 36.dp) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        // Ground shadow
        drawOval(
            brush = Brush.radialGradient(listOf(Color(0x66000000), Color.Transparent)),
            topLeft = Offset(w * 0.04f, h * 0.74f),
            size = Size(w * 0.92f, h * 0.16f)
        )

        // Aerodynamic Hypercar Body (Metallic Crimson)
        val carBody = Path().apply {
            moveTo(w * 0.06f, h * 0.66f)
            cubicTo(w * 0.12f, h * 0.52f, w * 0.28f, h * 0.46f, w * 0.40f, h * 0.32f)
            lineTo(w * 0.68f, h * 0.32f)
            cubicTo(w * 0.80f, h * 0.46f, w * 0.90f, h * 0.54f, w * 0.96f, h * 0.66f)
            lineTo(w * 0.96f, h * 0.72f)
            lineTo(w * 0.06f, h * 0.72f)
            close()
        }
        drawPath(
            carBody,
            Brush.linearGradient(
                listOf(Color(0xFFFF5252), Color(0xFFFF1744), Color(0xFFD50000), Color(0xFF4A000A)),
                start = Offset(w * 0.20f, h * 0.30f),
                end = Offset(w * 0.80f, h * 0.75f)
            )
        )

        // Panoramic Cockpit Windshield (Tinted Sky Reflection)
        val windshield = Path().apply {
            moveTo(w * 0.42f, h * 0.34f)
            lineTo(w * 0.66f, h * 0.34f)
            lineTo(w * 0.74f, h * 0.48f)
            lineTo(w * 0.32f, h * 0.48f)
            close()
        }
        drawPath(
            windshield,
            Brush.linearGradient(
                listOf(Color(0xFFE1F5FE), Color(0xFF80D8FF), Color(0xFF0091EA), Color(0xFF0D47A1)),
                start = Offset(w * 0.35f, h * 0.34f),
                end = Offset(w * 0.70f, h * 0.48f)
            )
        )
        // Windshield Glass Glint
        drawLine(
            Color(0xAAFFFFFF),
            Offset(w * 0.44f, h * 0.36f),
            Offset(w * 0.52f, h * 0.46f),
            strokeWidth = w * 0.025f,
            cap = StrokeCap.Round
        )

        // Carbon Fiber Splitter & Side Skirt
        drawRoundRect(
            brush = Brush.linearGradient(listOf(Color(0xFF424242), Color(0xFF212121))),
            topLeft = Offset(w * 0.05f, h * 0.70f),
            size = Size(w * 0.90f, h * 0.05f),
            cornerRadius = CornerRadius(w * 0.02f, w * 0.02f)
        )

        // High-Performance Wheels (Front & Rear)
        // Rear Wheel
        drawCircle(Color(0xFF1E1E1E), radius = w * 0.12f, center = Offset(w * 0.26f, h * 0.70f))
        drawCircle(
            brush = Brush.radialGradient(listOf(Color(0xFFEEEEEE), Color(0xFF757575))),
            radius = w * 0.07f,
            center = Offset(w * 0.26f, h * 0.70f)
        )
        // Yellow Caliper
        drawCircle(Color(0xFFFFD600), radius = w * 0.035f, center = Offset(w * 0.28f, h * 0.68f))

        // Front Wheel
        drawCircle(Color(0xFF1E1E1E), radius = w * 0.12f, center = Offset(w * 0.76f, h * 0.70f))
        drawCircle(
            brush = Brush.radialGradient(listOf(Color(0xFFEEEEEE), Color(0xFF757575))),
            radius = w * 0.07f,
            center = Offset(w * 0.76f, h * 0.70f)
        )
        drawCircle(Color(0xFFFFD600), radius = w * 0.035f, center = Offset(w * 0.78f, h * 0.68f))

        // Xenon LED Headlamp with Radiant Beam
        drawOval(
            brush = Brush.radialGradient(listOf(Color(0xFFFFFFFF), Color(0xFF80D8FF), Color(0xFF00B0FF))),
            topLeft = Offset(w * 0.90f, h * 0.58f),
            size = Size(w * 0.08f, h * 0.08f)
        )
    }
}

/**
 * 12. 3D Luxury Mega-Yacht in Champagne Gold on Azure Ocean Waves
 */
@Composable
fun GoldenYacht3DGiftIcon(modifier: Modifier = Modifier, size: Dp = 36.dp) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        // Translucent Azure Waves (Background layer)
        val waveBack = Path().apply {
            moveTo(w * 0.05f, h * 0.82f)
            cubicTo(w * 0.25f, h * 0.76f, w * 0.45f, h * 0.86f, w * 0.65f, h * 0.78f)
            cubicTo(w * 0.80f, h * 0.84f, w * 0.92f, h * 0.78f, w * 0.98f, h * 0.82f)
            lineTo(w * 0.98f, h * 0.95f)
            lineTo(w * 0.05f, h * 0.95f)
            close()
        }
        drawPath(waveBack, Brush.verticalGradient(listOf(Color(0xFF0091EA), Color(0xFF01579B))))

        // Sleek Champagne Gold Hull
        val hull = Path().apply {
            moveTo(w * 0.08f, h * 0.58f)
            lineTo(w * 0.88f, h * 0.58f)
            cubicTo(w * 0.85f, h * 0.75f, w * 0.76f, h * 0.82f, w * 0.70f, h * 0.82f)
            lineTo(w * 0.18f, h * 0.82f)
            close()
        }
        drawPath(
            hull,
            Brush.linearGradient(
                listOf(Color(0xFFFFF9C4), Color(0xFFFFD54F), Color(0xFFFFB300), Color(0xFFBF360C)),
                start = Offset(w * 0.10f, h * 0.55f),
                end = Offset(w * 0.85f, h * 0.82f)
            )
        )

        // Superstructure Deck 1 (Pearl White & Teak)
        drawRoundRect(
            brush = Brush.linearGradient(listOf(Color(0xFFFFFFFF), Color(0xFFECEFF1), Color(0xFFB0BEC5))),
            topLeft = Offset(w * 0.25f, h * 0.42f),
            size = Size(w * 0.46f, h * 0.18f),
            cornerRadius = CornerRadius(w * 0.04f, w * 0.04f)
        )

        // Flybridge Deck 2 with Observation Glass
        drawRoundRect(
            brush = Brush.linearGradient(listOf(Color(0xFFFFFFFF), Color(0xFFCFD8DC))),
            topLeft = Offset(w * 0.32f, h * 0.30f),
            size = Size(w * 0.30f, h * 0.14f),
            cornerRadius = CornerRadius(w * 0.03f, w * 0.03f)
        )

        // Tinted Panoramic Bridge Glass Windows
        drawRect(Color(0xFF0288D1), topLeft = Offset(w * 0.36f, h * 0.33f), size = Size(w * 0.10f, h * 0.07f))
        drawRect(Color(0xFF0288D1), topLeft = Offset(w * 0.48f, h * 0.33f), size = Size(w * 0.10f, h * 0.07f))

        // Radar Mast on Top
        drawLine(Color(0xFFFFD700), Offset(w * 0.46f, h * 0.30f), Offset(w * 0.46f, h * 0.22f), strokeWidth = w * 0.03f)
        drawOval(Color.White, topLeft = Offset(w * 0.42f, h * 0.20f), size = Size(w * 0.08f, h * 0.04f))

        // Foaming Ocean Crest Waves (Foreground)
        drawLine(
            brush = Brush.linearGradient(listOf(Color(0xFF80D8FF), Color(0xFF00E5FF), Color(0xFFFFFFFF))),
            start = Offset(w * 0.04f, h * 0.82f),
            end = Offset(w * 0.96f, h * 0.82f),
            strokeWidth = w * 0.045f,
            cap = StrokeCap.Round
        )
    }
}

/**
 * 13. 3D Executive Private Jet with Swept Wings & Cyan Turbofan Thrust
 */
@Composable
fun PrivateJet3DGiftIcon(modifier: Modifier = Modifier, size: Dp = 36.dp) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        // Cyan Jet Propulsion Thrust Flare (Rear)
        drawCircle(
            brush = Brush.radialGradient(listOf(Color(0xFFFFFFFF), Color(0xFF00E5FF), Color(0xFF0091EA), Color.Transparent)),
            radius = w * 0.16f,
            center = Offset(w * 0.12f, h * 0.74f)
        )

        // Main Aerodynamic Fuselage (Pearl White & Royal Navy)
        val fuselage = Path().apply {
            moveTo(w * 0.88f, h * 0.22f)
            cubicTo(w * 0.65f, h * 0.34f, w * 0.35f, h * 0.50f, w * 0.16f, h * 0.66f)
            lineTo(w * 0.10f, h * 0.76f)
            lineTo(w * 0.24f, h * 0.72f)
            cubicTo(w * 0.46f, h * 0.56f, w * 0.72f, h * 0.38f, w * 0.88f, h * 0.22f)
            close()
        }
        drawPath(
            fuselage,
            Brush.linearGradient(
                listOf(Color(0xFFFFFFFF), Color(0xFFECEFF1), Color(0xFFCFD8DC), Color(0xFF1A237E)),
                start = Offset(w * 0.85f, h * 0.22f),
                end = Offset(w * 0.15f, h * 0.75f)
            )
        )

        // Cockpit Windshield (Gleaming Cyan Tint)
        val cockpit = Path().apply {
            moveTo(w * 0.80f, h * 0.27f)
            lineTo(w * 0.72f, h * 0.34f)
            lineTo(w * 0.75f, h * 0.37f)
            lineTo(w * 0.83f, h * 0.30f)
            close()
        }
        drawPath(cockpit, Brush.linearGradient(listOf(Color(0xFF80D8FF), Color(0xFF0288D1))))

        // Swept-Back Main Wing with Vertical Winglet
        val mainWing = Path().apply {
            moveTo(w * 0.52f, h * 0.46f)
            lineTo(w * 0.26f, h * 0.18f)
            lineTo(w * 0.28f, h * 0.14f) // winglet
            lineTo(w * 0.36f, h * 0.40f)
            close()
        }
        drawPath(
            mainWing,
            Brush.linearGradient(
                listOf(Color(0xFF42A5F5), Color(0xFF1976D2), Color(0xFF0D47A1)),
                start = Offset(w * 0.26f, h * 0.18f),
                end = Offset(w * 0.52f, h * 0.46f)
            )
        )

        // Rear Stabilizer Tail Fin
        val tailFin = Path().apply {
            moveTo(w * 0.20f, h * 0.65f)
            lineTo(w * 0.14f, h * 0.48f)
            lineTo(w * 0.10f, h * 0.52f)
            lineTo(w * 0.12f, h * 0.72f)
            close()
        }
        drawPath(tailFin, Brush.linearGradient(listOf(Color(0xFF1E88E5), Color(0xFF0D47A1))))

        // Turbofan Engine Cowling
        drawRoundRect(
            brush = Brush.linearGradient(listOf(Color(0xFFFFFFFF), Color(0xFF78909C))),
            topLeft = Offset(w * 0.15f, h * 0.64f),
            size = Size(w * 0.14f, h * 0.09f),
            cornerRadius = CornerRadius(w * 0.03f, w * 0.03f)
        )
    }
}

/**
 * 14. 3D Fairytale Royal Castle with Amethyst Spires, Golden Banners & Portcullis
 */
@Composable
fun RoyalCastle3DGiftIcon(modifier: Modifier = Modifier, size: Dp = 36.dp) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        // Ground Clouds / Mist at base
        drawOval(
            brush = Brush.radialGradient(listOf(Color(0x99ECEFF1), Color.Transparent)),
            topLeft = Offset(w * 0.10f, h * 0.82f),
            size = Size(w * 0.80f, h * 0.16f)
        )

        // Grand Central Keep Wall
        drawRect(
            brush = Brush.linearGradient(
                listOf(Color(0xFFCFD8DC), Color(0xFF90A4AE), Color(0xFF546E7A)),
                start = Offset(w * 0.26f, h * 0.44f),
                end = Offset(w * 0.74f, h * 0.86f)
            ),
            topLeft = Offset(w * 0.26f, h * 0.44f),
            size = Size(w * 0.48f, h * 0.42f)
        )

        // Left Citadel Tower
        drawRoundRect(
            brush = Brush.linearGradient(listOf(Color(0xFFECEFF1), Color(0xFFB0BEC5), Color(0xFF607D8B))),
            topLeft = Offset(w * 0.12f, h * 0.32f),
            size = Size(w * 0.20f, h * 0.54f),
            cornerRadius = CornerRadius(w * 0.02f, w * 0.02f)
        )

        // Right Citadel Tower
        drawRoundRect(
            brush = Brush.linearGradient(listOf(Color(0xFFECEFF1), Color(0xFFB0BEC5), Color(0xFF607D8B))),
            topLeft = Offset(w * 0.68f, h * 0.32f),
            size = Size(w * 0.20f, h * 0.54f),
            cornerRadius = CornerRadius(w * 0.02f, w * 0.02f)
        )

        // Amethyst-Purple Conical Spires
        // Left Spire
        val leftSpire = Path().apply {
            moveTo(w * 0.10f, h * 0.32f)
            lineTo(w * 0.34f, h * 0.32f)
            lineTo(w * 0.22f, h * 0.10f)
            close()
        }
        drawPath(leftSpire, Brush.linearGradient(listOf(Color(0xFFCE93D8), Color(0xFF8E24AA), Color(0xFF4A148C))))

        // Right Spire
        val rightSpire = Path().apply {
            moveTo(w * 0.66f, h * 0.32f)
            lineTo(w * 0.90f, h * 0.32f)
            lineTo(w * 0.78f, h * 0.10f)
            close()
        }
        drawPath(rightSpire, Brush.linearGradient(listOf(Color(0xFFCE93D8), Color(0xFF8E24AA), Color(0xFF4A148C))))

        // Central High Spire
        val centerSpire = Path().apply {
            moveTo(w * 0.38f, h * 0.44f)
            lineTo(w * 0.62f, h * 0.44f)
            lineTo(w * 0.50f, h * 0.18f)
            close()
        }
        drawPath(centerSpire, Brush.linearGradient(listOf(Color(0xFFE1BEE7), Color(0xFFAB47BC), Color(0xFF6A1B9A))))

        // Golden Finials & Banners atop spires
        drawCircle(Color(0xFFFFD700), radius = w * 0.035f, center = Offset(w * 0.50f, h * 0.18f))
        drawCircle(Color(0xFFFFD700), radius = w * 0.03f, center = Offset(w * 0.22f, h * 0.10f))
        drawCircle(Color(0xFFFFD700), radius = w * 0.03f, center = Offset(w * 0.78f, h * 0.10f))

        // Grand Arched Portcullis Doorway (Glowing warm golden torchlight from within)
        val gate = Path().apply {
            moveTo(w * 0.40f, h * 0.86f)
            lineTo(w * 0.40f, h * 0.64f)
            cubicTo(w * 0.40f, h * 0.56f, w * 0.60f, h * 0.56f, w * 0.60f, h * 0.64f)
            lineTo(w * 0.60f, h * 0.86f)
            close()
        }
        drawPath(gate, Brush.verticalGradient(listOf(Color(0xFFFFD54F), Color(0xFFFF8F00), Color(0xFF4E342E))))
    }
}

/**
 * 15. 3D Cosmic Galaxy Universe with Dual Nebula Spiral Arms & Star Cluster
 */
@Composable
fun GalaxyUniverse3DGiftIcon(modifier: Modifier = Modifier, size: Dp = 36.dp) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        // Deep Space Cosmic Nebula Glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFE040FB), Color(0xFF7C4DFF), Color(0xFF304FFE), Color(0xFF000000), Color.Transparent),
                center = Offset(w * 0.50f, h * 0.50f),
                radius = w * 0.48f
            ),
            radius = w * 0.48f,
            center = Offset(w * 0.50f, h * 0.50f)
        )

        // Inclined Planetary / Asteroid Ring System
        drawOval(
            brush = Brush.linearGradient(
                listOf(Color(0xFFFFD700), Color(0xFFFF4081), Color(0xFF00E5FF), Color(0xFF7C4DFF)),
                start = Offset(w * 0.08f, h * 0.32f),
                end = Offset(w * 0.92f, h * 0.68f)
            ),
            topLeft = Offset(w * 0.06f, h * 0.32f),
            size = Size(w * 0.88f, h * 0.36f),
            style = Stroke(width = w * 0.08f)
        )

        // Spiral Nebula Arm 1
        val spiralArm1 = Path().apply {
            moveTo(w * 0.50f, h * 0.50f)
            cubicTo(w * 0.65f, h * 0.30f, w * 0.85f, h * 0.35f, w * 0.88f, h * 0.50f)
        }
        drawPath(spiralArm1, Brush.linearGradient(listOf(Color(0xFF00E5FF), Color(0xFF7C4DFF))), style = Stroke(width = w * 0.07f, cap = StrokeCap.Round))

        // Spiral Nebula Arm 2
        val spiralArm2 = Path().apply {
            moveTo(w * 0.50f, h * 0.50f)
            cubicTo(w * 0.35f, h * 0.70f, w * 0.15f, h * 0.65f, w * 0.12f, h * 0.50f)
        }
        drawPath(spiralArm2, Brush.linearGradient(listOf(Color(0xFFFF4081), Color(0xFFE040FB))), style = Stroke(width = w * 0.07f, cap = StrokeCap.Round))

        // Blazing White Star Core (Supermassive Singularity)
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color(0xFFFFFFFF), Color(0xFF80D8FF), Color(0xFF651FFF), Color.Transparent),
                center = Offset(w * 0.50f, h * 0.50f),
                radius = w * 0.22f
            ),
            radius = w * 0.20f,
            center = Offset(w * 0.50f, h * 0.50f)
        )

        // Twinkling Constellation Stars
        drawCircle(Color.White, radius = w * 0.045f, center = Offset(w * 0.22f, h * 0.22f))
        drawCircle(Color(0xFFFFD700), radius = w * 0.035f, center = Offset(w * 0.78f, h * 0.74f))
        drawCircle(Color(0xFF00E5FF), radius = w * 0.04f, center = Offset(w * 0.82f, h * 0.24f))
        drawCircle(Color(0xFFFF4081), radius = w * 0.035f, center = Offset(w * 0.18f, h * 0.78f))
    }
}

/**
 * 16. Fallback / Generic Opulent 3D Isometric Gift Box with Satin Gold Ribbons & Volumetric Bow
 */
@Composable
fun Generic3DGiftBoxIcon(
    emoji: String = "🎁",
    modifier: Modifier = Modifier,
    size: Dp = 36.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        // Ground shadow
        drawOval(
            brush = Brush.radialGradient(listOf(Color(0x55000000), Color.Transparent)),
            topLeft = Offset(w * 0.12f, h * 0.82f),
            size = Size(w * 0.76f, h * 0.16f)
        )

        // 3D Gift Box Main Container (Emerald & Jade Gradient)
        drawRoundRect(
            brush = Brush.linearGradient(
                listOf(Color(0xFF00E676), Color(0xFF00C853), Color(0xFF007E33), Color(0xFF004D20)),
                start = Offset(w * 0.15f, h * 0.36f),
                end = Offset(w * 0.85f, h * 0.84f)
            ),
            topLeft = Offset(w * 0.15f, h * 0.36f),
            size = Size(w * 0.70f, h * 0.48f),
            cornerRadius = CornerRadius(w * 0.08f, w * 0.08f)
        )

        // 3D Overhanging Box Lid
        drawRoundRect(
            brush = Brush.linearGradient(
                listOf(Color(0xFF69F0AE), Color(0xFF00C853), Color(0xFF007E33)),
                start = Offset(w * 0.10f, h * 0.22f),
                end = Offset(w * 0.90f, h * 0.38f)
            ),
            topLeft = Offset(w * 0.10f, h * 0.22f),
            size = Size(w * 0.80f, h * 0.18f),
            cornerRadius = CornerRadius(w * 0.07f, w * 0.07f)
        )

        // Satin Gold Ribbon - Vertical
        drawRect(
            brush = Brush.linearGradient(listOf(Color(0xFFFFF9C4), Color(0xFFFFD54F), Color(0xFFFF8F00))),
            topLeft = Offset(w * 0.43f, h * 0.22f),
            size = Size(w * 0.14f, h * 0.62f)
        )

        // Satin Gold Ribbon - Horizontal on box body
        drawRect(
            brush = Brush.linearGradient(listOf(Color(0xFFFFECB3), Color(0xFFFFB300), Color(0xFFE65100))),
            topLeft = Offset(w * 0.15f, h * 0.54f),
            size = Size(w * 0.70f, h * 0.12f)
        )

        // Volumetric 3D Satin Ribbon Bow (Left Loop, Right Loop, Center Knot)
        drawOval(
            brush = Brush.linearGradient(listOf(Color(0xFFFFFDE7), Color(0xFFFFD54F), Color(0xFFFF8F00))),
            topLeft = Offset(w * 0.24f, h * 0.08f),
            size = Size(w * 0.26f, h * 0.18f)
        )
        drawOval(
            brush = Brush.linearGradient(listOf(Color(0xFFFFFDE7), Color(0xFFFFD54F), Color(0xFFFF8F00))),
            topLeft = Offset(w * 0.50f, h * 0.08f),
            size = Size(w * 0.26f, h * 0.18f)
        )
        // Center Bow Knot
        drawCircle(
            brush = Brush.radialGradient(listOf(Color(0xFFFFFFFF), Color(0xFFFFD54F), Color(0xFFE65100))),
            radius = w * 0.09f,
            center = Offset(w * 0.50f, h * 0.18f)
        )

        // Sparkling Glints
        drawCircle(Color.White, radius = w * 0.035f, center = Offset(w * 0.28f, h * 0.26f))
        drawCircle(Color(0xFFFFD700), radius = w * 0.03f, center = Offset(w * 0.78f, h * 0.44f))
    }
}
