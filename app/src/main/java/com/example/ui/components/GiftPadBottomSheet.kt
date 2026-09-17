package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.theme.AppTheme
import com.example.ui.theme.QivoOrange
import com.example.ui.theme.QivoYellow

/**
 * Recipient Option for Party Rooms & Group Environments
 */
data class GiftRecipientOption(
    val id: String,
    val name: String,
    val avatarUrl: String = "",
    val badge: String = "",       // e.g. "👑 Host", "Seat 1", "🎤 Mic", "Attendee"
    val isAllOption: Boolean = false
)

/**
 * Data representation of a Gift with values ranging from 10 coins up to 50k coins.
 */
data class AppGift(
    val id: String,
    val name: String,
    val emoji: String,
    val coins: Long,
    val category: GiftCategory,
    val badge: String? = null,
    val description: String = ""
)

enum class GiftCategory(val title: String) {
    ALL("All 🎁"),
    POPULAR("Popular (10-100)"),
    LUXURY("Luxury (200-1k)"),
    MYTHIC("VIP & Mythic (2.5k-50k)")
}

/**
 * Full spectrum of gifts ranging strictly from 10 Coins to 50,000 Coins.
 */
val ALL_APP_GIFTS = listOf(
    // Tier 1: 10 - 100 coins
    AppGift("gift_rose", "Rose", "🌹", 10L, GiftCategory.POPULAR, badge = "10 c"),
    AppGift("gift_heart", "Love Heart", "💖", 20L, GiftCategory.POPULAR, badge = "HOT"),
    AppGift("gift_choco", "Chocolates", "🍫", 30L, GiftCategory.POPULAR),
    AppGift("gift_icecream", "Ice Cream", "🍦", 50L, GiftCategory.POPULAR),
    AppGift("gift_crown", "Royal Crown", "👑", 100L, GiftCategory.POPULAR, badge = "POPULAR"),

    // Tier 2: 200 - 1,000 coins
    AppGift("gift_letter", "Love Letter", "💌", 200L, GiftCategory.LUXURY),
    AppGift("gift_ring", "Diamond Ring", "💍", 300L, GiftCategory.LUXURY, badge = "SHINE"),
    AppGift("gift_cake", "Party Cake", "🎂", 50L, GiftCategory.LUXURY),
    AppGift("gift_magic", "Magic Wand", "✨", 500L, GiftCategory.LUXURY),
    AppGift("gift_fireworks", "Fireworks", "🎆", 1000L, GiftCategory.LUXURY, badge = "1,000 c"),

    // Tier 3: 2,500 - 50,000 coins (50k)
    AppGift("gift_car", "Sports Car", "🏎️", 2500L, GiftCategory.MYTHIC, badge = "2.5K c"),
    AppGift("gift_yacht", "Golden Yacht", "🛥️", 5000L, GiftCategory.MYTHIC, badge = "5K c"),
    AppGift("gift_jet", "Private Jet", "✈️", 10000L, GiftCategory.MYTHIC, badge = "10K c"),
    AppGift("gift_castle", "Royal Castle", "🏰", 25000L, GiftCategory.MYTHIC, badge = "25K VIP"),
    AppGift("gift_galaxy", "Universe Galaxy", "🌌", 50000L, GiftCategory.MYTHIC, badge = "50K MYTHIC")
)

/**
 * High-craft Bottom Popup Gift Pad with:
 * - One or Multiple Recipient Selection (or All Seated / Whole Room)
 * - Direct Coin Balance display
 * - "+ Recharge" button linked directly to Wallet Recharge Screen
 * - Range of gifts from 10 coins to 50k coins
 * - Out-of-coins detection with dynamic quantity calculations
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GiftPadBottomSheet(
    currentCoins: Long,
    isExempt: Boolean = false,
    targetRecipientName: String = "",
    recipientOptions: List<GiftRecipientOption> = emptyList(),
    defaultSelectedRecipientIds: Set<String> = emptySet(),
    onDismiss: () -> Unit,
    onRechargeClick: () -> Unit,
    onSendGift: (AppGift) -> Unit,
    onSendGiftToRecipients: ((AppGift, List<GiftRecipientOption>, Long) -> Unit)? = null,
    onInsufficientCoins: (AppGift) -> Unit = {},
    onInsufficientCoinsWithTotal: ((AppGift, Long) -> Unit)? = null
) {
    val colors = AppTheme.colors
    val isDark = colors.isDark
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedCategoryIndex by remember { mutableIntStateOf(0) }
    var selectedGift by remember { mutableStateOf<AppGift?>(ALL_APP_GIFTS.first()) }
    var previewGiftForDetail by remember { mutableStateOf<AppGift?>(null) }

    // Multi-Recipient State
    val individualRecipients = remember(recipientOptions) {
        recipientOptions.filter { !it.isAllOption }
    }
    var selectedRecipientIds by remember(recipientOptions, defaultSelectedRecipientIds) {
        mutableStateOf(
            if (defaultSelectedRecipientIds.isNotEmpty()) {
                defaultSelectedRecipientIds
            } else if (individualRecipients.isNotEmpty()) {
                setOf(individualRecipients.first().id)
            } else {
                emptySet()
            }
        )
    }

    val isAllSelected = remember(selectedRecipientIds, individualRecipients) {
        individualRecipients.isNotEmpty() && selectedRecipientIds.size == individualRecipients.size
    }

    val selectedCount = if (recipientOptions.isEmpty()) 1 else selectedRecipientIds.size.coerceAtLeast(1)
    val curGift = selectedGift
    val totalRequiredCoins = (curGift?.coins ?: 0L) * selectedCount

    val categories = GiftCategory.values()
    val filteredGifts = remember(selectedCategoryIndex) {
        val cat = categories[selectedCategoryIndex]
        if (cat == GiftCategory.ALL) ALL_APP_GIFTS else ALL_APP_GIFTS.filter { it.category == cat }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = if (isDark) Color(0xFF14141B) else Color(0xFFFFFFFF),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (isDark) Color(0xFF3E3E4E) else Color(0xFFD0D0D8))
            )
        },
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        modifier = Modifier.testTag("gift_pad_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            // 1. TOP HEADER ROW: Title + Close Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Gift Pad 🎁",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Black,
                        color = colors.textPrimary
                    )
                    if (recipientOptions.isEmpty() && targetRecipientName.isNotBlank()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "to $targetRecipientName",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(if (isDark) Color(0xFF22222E) else Color(0xFFF0F0F5))
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 2. RECIPIENT SELECTOR (When recipientOptions are provided)
            if (recipientOptions.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isDark) Color(0xFF1B1B26) else Color(0xFFF4F5F9),
                    border = BorderStroke(1.dp, if (isDark) Color(0xFF2A2A3A) else Color(0xFFE2E4E9)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "SEND GIFT TO (${selectedRecipientIds.size}/${individualRecipients.size})",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = QivoOrange,
                                letterSpacing = 0.5.sp
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                // "Whole Room / Select All" Chip
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isAllSelected) QivoOrange else if (isDark) Color(0xFF282838) else Color(0xFFE5E7EB),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            selectedRecipientIds = if (isAllSelected) {
                                                if (individualRecipients.isNotEmpty()) setOf(individualRecipients.first().id) else emptySet()
                                            } else {
                                                individualRecipients.map { it.id }.toSet()
                                            }
                                        }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Groups,
                                            contentDescription = null,
                                            tint = if (isAllSelected) Color.White else colors.textPrimary,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = if (isAllSelected) "All Selected ✓" else "Select All",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isAllSelected) Color.White else colors.textPrimary
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Horizontal Recipient Pills
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            contentPadding = PaddingValues(horizontal = 2.dp)
                        ) {
                            items(individualRecipients, key = { it.id }) { rec ->
                                val isSelected = selectedRecipientIds.contains(rec.id)
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = if (isSelected) {
                                        if (isDark) Color(0xFF382942) else Color(0xFFFFECE0)
                                    } else {
                                        if (isDark) Color(0xFF22222E) else Color(0xFFFFFFFF)
                                    },
                                    border = BorderStroke(
                                        if (isSelected) 1.5.dp else 1.dp,
                                        if (isSelected) QivoOrange else if (isDark) Color(0xFF323242) else Color(0xFFDCDFE4)
                                    ),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .clickable {
                                            selectedRecipientIds = if (isSelected) {
                                                // If only 1 selected, don't unselect completely
                                                if (selectedRecipientIds.size > 1) {
                                                    selectedRecipientIds - rec.id
                                                } else {
                                                    selectedRecipientIds
                                                }
                                            } else {
                                                selectedRecipientIds + rec.id
                                            }
                                        }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        // Avatar / Icon
                                        if (rec.avatarUrl.isNotBlank()) {
                                            AsyncImage(
                                                model = rec.avatarUrl,
                                                contentDescription = rec.name,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(20.dp)
                                                    .clip(CircleShape)
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(20.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isSelected) QivoOrange else Color(0xFF6B7280)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = rec.name.take(1).uppercase(),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(5.dp))

                                        Column {
                                            Text(
                                                text = rec.name,
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) QivoOrange else colors.textPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (rec.badge.isNotBlank()) {
                                                Text(
                                                    text = rec.badge,
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = colors.textSecondary
                                                )
                                            }
                                        }

                                        if (isSelected) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = QivoOrange,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // 3. COIN BALANCE & RECHARGE BANNER
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (isDark) Color(0xFF1F1E2E) else Color(0xFFFFF8E7),
                border = BorderStroke(
                    1.dp,
                    if (isDark) Color(0xFF38354A) else Color(0xFFFFE082)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Coin Balance Display
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Coin3DIcon(size = 24.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = "Coins Balance",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Normal,
                                color = colors.textSecondary
                            )
                            Text(
                                text = "${String.format("%,d", currentCoins)} Coins",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isDark) Color(0xFFFFD54F) else Color(0xFFE65100)
                            )
                        }
                    }

                    // + Recharge Button (Takes user to Recharge Screen)
                    Button(
                        onClick = {
                            onDismiss()
                            onRechargeClick()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = QivoOrange
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier
                            .height(30.dp)
                            .testTag("gift_pad_recharge_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Recharge",
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "Recharge",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 4. CATEGORY FILTER TABS
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                categories.forEachIndexed { index, cat ->
                    val isSelected = selectedCategoryIndex == index
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = if (isSelected) QivoOrange else if (isDark) Color(0xFF22222E) else Color(0xFFF2F2F7),
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { selectedCategoryIndex = index }
                    ) {
                        Text(
                            text = cat.title,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else colors.textPrimary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 5. GIFTS GRID (Scrollable, 4 items per row)
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 210.dp),
                contentPadding = PaddingValues(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                items(filteredGifts, key = { it.id }) { gift ->
                    val isSelected = selectedGift?.id == gift.id
                    val giftTotalCoins = gift.coins * selectedCount
                    val canAfford = currentCoins >= giftTotalCoins

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) {
                            if (isDark) Color(0xFF2E2638) else Color(0xFFFFF0F5)
                        } else {
                            if (isDark) Color(0xFF1B1B24) else Color(0xFFF8F9FA)
                        },
                        border = BorderStroke(
                            if (isSelected) 1.8.dp else 0.8.dp,
                            if (isSelected) QivoOrange else if (isDark) Color(0xFF2A2A38) else Color(0xFFE5E7EB)
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { selectedGift = gift }
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                // Gift Badge (if present)
                                if (gift.badge != null) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (gift.coins >= 10000L) Color(0xFFA37E30) else if (gift.coins >= 1000L) Color(0xFFB08C42) else QivoOrange,
                                        modifier = Modifier.padding(bottom = 2.dp)
                                    ) {
                                        Text(
                                            text = gift.badge,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }

                                Gift3DIcon(
                                    giftId = gift.id,
                                    emoji = gift.emoji,
                                    size = 36.dp
                                )

                                Spacer(modifier = Modifier.height(2.dp))

                                Text(
                                    text = gift.name,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.height(2.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "🪙",
                                        fontSize = 10.sp
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = formatCoins(gift.coins),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (canAfford) Color(0xFFFF9800) else Color(0xFFEF4444)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 6. SEND ACTION BAR WITH RECIPIENT COUNT & MULTIPLIER
            if (curGift != null) {
                val hasEnough = currentCoins >= totalRequiredCoins

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Selected gift info pill & Total Coins Summary (Clickable to preview in large 3D)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                previewGiftForDetail = curGift
                            }
                            .padding(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isDark) Color(0xFF261D33) else Color(0xFFF3E5F5))
                                .border(1.dp, Color(0xFFFFD54F).copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Gift3DIcon(
                                giftId = curGift.id,
                                emoji = curGift.emoji,
                                size = 32.dp
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${curGift.name} ${if (selectedCount > 1) "× $selectedCount" else ""}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Preview 🔍",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFF9800)
                                )
                            }
                            Text(
                                text = if (selectedCount > 1) {
                                    "${String.format("%,d", totalRequiredCoins)} coins (${String.format("%,d", curGift.coins)} × $selectedCount)"
                                } else {
                                    "${String.format("%,d", curGift.coins)} coins"
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (hasEnough) colors.textSecondary else Color(0xFFEF4444)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    if (hasEnough) {
                        // SEND GIFT BUTTON
                        val context = androidx.compose.ui.platform.LocalContext.current
                        Button(
                            onClick = {
                                HapticSoundFeedback.performSuccess(context)
                                HapticSoundFeedback.playGiftSentSound()
                                if (onSendGiftToRecipients != null && recipientOptions.isNotEmpty()) {
                                    val chosenRecipients = individualRecipients.filter { selectedRecipientIds.contains(it.id) }
                                    onSendGiftToRecipients(curGift, chosenRecipients, totalRequiredCoins)
                                } else {
                                    onSendGift(curGift)
                                }
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = QivoOrange
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .height(40.dp)
                                .testTag("send_gift_action_button")
                        ) {
                            Text(
                                text = if (selectedCount > 1) "Send to $selectedCount 🚀" else "Send Gift 🚀",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        }
                    } else {
                        // INSUFFICIENT COINS -> RECHARGE BUTTON
                        Button(
                            onClick = {
                                onDismiss()
                                if (onInsufficientCoinsWithTotal != null) {
                                    onInsufficientCoinsWithTotal(curGift, totalRequiredCoins)
                                } else {
                                    onInsufficientCoins(curGift)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = QivoOrange
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .height(40.dp)
                                .testTag("insufficient_coins_recharge_button")
                        ) {
                            Text(
                                text = "Get Coins 🪙",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        // Interactive 3D Gift Detail Preview Dialog
        val detailGift = previewGiftForDetail
        if (detailGift != null) {
            GiftDetailPreviewDialog(
                gift = detailGift,
                currentCoins = currentCoins,
                onDismiss = { previewGiftForDetail = null },
                onSend = { giftToSend ->
                    previewGiftForDetail = null
                    val req = giftToSend.coins * selectedCount
                    if (currentCoins >= req) {
                        if (onSendGiftToRecipients != null && recipientOptions.isNotEmpty()) {
                            val recipientsToSend = individualRecipients.filter { selectedRecipientIds.contains(it.id) }
                            onSendGiftToRecipients(giftToSend, recipientsToSend, req)
                        } else {
                            onSendGift(giftToSend)
                        }
                    } else {
                        if (onSendGiftToRecipients != null && recipientOptions.isNotEmpty() && onInsufficientCoinsWithTotal != null) {
                            onInsufficientCoinsWithTotal.invoke(giftToSend, req)
                        } else {
                            onInsufficientCoins(giftToSend)
                        }
                    }
                }
            )
        }
    }
}

private fun formatCoins(coins: Long): String {
    return when {
        coins >= 1000L && coins % 1000L == 0L -> "${coins / 1000}k"
        coins >= 1000L -> String.format("%.1fk", coins / 1000.0)
        else -> coins.toString()
    }
}
