package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.AppAdvertisement
import com.example.ui.theme.QivoYellow
import kotlinx.coroutines.delay

/**
 * Continuous Auto-Swiping Carousel Top Banner for Chat List Screen.
 * - Supports up to 3 advertisements simultaneously.
 * - Shows continuously without arbitrary skip countdowns.
 * - Auto-swipes smoothly every 4 seconds.
 * - User can manually swipe or remove individual banners.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatTopBannerAdCarousel(
    ads: List<AppAdvertisement>,
    modifier: Modifier = Modifier,
    onNavigateAction: (String) -> Unit = {}
) {
    val activeAds = remember(ads) {
        ads.filter { it.isActive && it.imageUrl.isNotBlank() }.take(3)
    }

    if (activeAds.isEmpty()) return

    val dismissedAdIds = remember { mutableStateListOf<String>() }
    val displayAds = activeAds.filter { it.id !in dismissedAdIds }

    if (displayAds.isEmpty()) return

    val pagerState = rememberPagerState(pageCount = { displayAds.size })
    val context = LocalContext.current

    // Auto-swipe every 4.5 seconds if more than 1 ad
    LaunchedEffect(displayAds.size) {
        if (displayAds.size > 1) {
            while (true) {
                delay(4500)
                val nextPage = (pagerState.currentPage + 1) % displayAds.size
                pagerState.animateScrollToPage(nextPage)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("chat_top_banner_carousel")
    ) {
        Column {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth()
            ) { page ->
                val ad = displayAds[page]
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(3.2f)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            if (ad.targetLink.isNotBlank()) {
                                if (ad.targetLink.startsWith("http://") || ad.targetLink.startsWith("https://")) {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(ad.targetLink))
                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(intent)
                                    } catch (_: Exception) {}
                                } else {
                                    onNavigateAction(ad.targetLink)
                                }
                            }
                        },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1B2C)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF))
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // Banner Image
                        AsyncImage(
                            model = ad.imageUrl,
                            contentDescription = ad.title.ifBlank { "Sponsored Banner" },
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )

                        // Bottom subtle gradient for text readability if title exists
                        if (ad.title.isNotBlank() || ad.description.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                                        )
                                    )
                            )

                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                if (ad.title.isNotBlank()) {
                                    Text(
                                        text = ad.title,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (ad.description.isNotBlank()) {
                                    Text(
                                        text = ad.description,
                                        fontSize = 10.sp,
                                        color = Color.White.copy(alpha = 0.85f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        // Top Left "ANNOUNCEMENT" Pill
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(8.dp),
                            shape = RoundedCornerShape(6.dp),
                            color = Color.Black.copy(alpha = 0.65f)
                        ) {
                            Text(
                                text = "ANNOUNCEMENT",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = QivoYellow,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        // Top Right Dismiss Button (Removes this banner item upon tap)
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.6f))
                                .clickable {
                                    dismissedAdIds.add(ad.id)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove Banner",
                                tint = Color.White,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }

            // Carousel Page Indicator Dots (when multiple ads exist)
            if (displayAds.size > 1) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(displayAds.size) { index ->
                        val isSelected = pagerState.currentPage == index
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 3.dp)
                                .size(if (isSelected) 6.dp else 4.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) QivoYellow else Color.White.copy(alpha = 0.4f))
                        )
                    }
                }
            }
        }
    }
}

/**
 * Backward compatibility wrapper for single ad parameter
 */
@Composable
fun ChatTopBannerAd(
    ad: AppAdvertisement?,
    modifier: Modifier = Modifier,
    onNavigateAction: (String) -> Unit = {}
) {
    if (ad != null) {
        ChatTopBannerAdCarousel(
            ads = listOf(ad),
            modifier = modifier,
            onNavigateAction = onNavigateAction
        )
    }
}
