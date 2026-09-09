package com.example.data

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import kotlin.math.abs

object AvatarHelper {

    // Aesthetic 3D Style Male Placeholder Avatars (Matching sunglasses & hoodie style)
    val MALE_AVATARS = listOf(
        AvatarItem("avatar_male_1", "Lilac Shade Male", R.drawable.ic_avatar_male_1),
        AvatarItem("avatar_male_2", "Sky Blue Shade Male", R.drawable.ic_avatar_male_2),
        AvatarItem("avatar_male_3", "Sage Mint Male", R.drawable.ic_avatar_male_3),
        AvatarItem("avatar_male_4", "Peach Amber Male", R.drawable.ic_avatar_male_4),
        AvatarItem("avatar_male_5", "Slate Lavender Male", R.drawable.ic_avatar_male_5)
    )

    // Aesthetic 3D Style Female Placeholder Avatars (Matching sunglasses, bun & pearl earring style)
    val FEMALE_AVATARS = listOf(
        AvatarItem("avatar_female_1", "Pastel Pink Bun Female", R.drawable.ic_avatar_female_1),
        AvatarItem("avatar_female_2", "Pastel Peach Ponytail Female", R.drawable.ic_avatar_female_2),
        AvatarItem("avatar_female_3", "Pastel Lavender Bun Female", R.drawable.ic_avatar_female_3),
        AvatarItem("avatar_female_4", "Pastel Mint Female", R.drawable.ic_avatar_female_4),
        AvatarItem("avatar_female_5", "Pastel Rose Female", R.drawable.ic_avatar_female_5)
    )

    val DOLL_MASCOTS = listOf(
        DollMascotItem("doll_bear", "Teddy Bear", R.drawable.ic_doll_bear),
        DollMascotItem("doll_cat", "Lucky Kitty", R.drawable.ic_doll_cat),
        DollMascotItem("doll_bunny", "Sweet Bunny", R.drawable.ic_doll_bunny),
        DollMascotItem("doll_panda", "Cute Panda", R.drawable.ic_doll_panda),
        DollMascotItem("doll_cyber", "Cyber Bot", R.drawable.ic_doll_cyber),
        DollMascotItem("doll_princess", "Princess Tiara", R.drawable.ic_doll_princess),
        DollMascotItem("doll_fox", "Sweet Fox", R.drawable.ic_doll_fox),
        DollMascotItem("doll_star", "Star Fairy", R.drawable.ic_doll_star),
        DollMascotItem("doll_female_mascot", "Girl Mascot", R.drawable.img_female_mascot),
        DollMascotItem("doll_male_mascot", "Boy Mascot", R.drawable.img_male_mascot)
    )

    data class AvatarItem(
        val id: String,
        val name: String,
        val drawableRes: Int
    )

    data class DollMascotItem(
        val id: String,
        val name: String,
        val drawableRes: Int
    )

    /**
     * Returns a stable gender-matched aesthetic placeholder avatar drawable based on user's ID, gender, or numericId.
     * Guaranteed never to assign a female avatar to a male user or vice versa!
     */
    fun getDefaultMascotRes(userId: String = "", gender: String = "Male", numericId: Long = 0L): Int {
        val isFemale = gender.equals("Female", ignoreCase = true) || gender.equals("F", ignoreCase = true)
        val hash = if (numericId > 0) abs(numericId.toInt()) else if (userId.isNotBlank()) abs(userId.hashCode()) else 0
        
        return if (isFemale) {
            val index = hash % FEMALE_AVATARS.size
            FEMALE_AVATARS[index].drawableRes
        } else {
            val index = hash % MALE_AVATARS.size
            MALE_AVATARS[index].drawableRes
        }
    }

    /**
     * Resolves an avatar key or mascot key to a drawable resource, or null if it is a real URL
     */
    fun getDrawableForMascotKey(avatarUrl: String): Int? {
        if (avatarUrl.startsWith("avatar:")) {
            val key = avatarUrl.removePrefix("avatar:")
            val maleMatch = MALE_AVATARS.firstOrNull { it.id == key }?.drawableRes
            if (maleMatch != null) return maleMatch
            return FEMALE_AVATARS.firstOrNull { it.id == key }?.drawableRes
        }
        if (avatarUrl.startsWith("mascot:")) {
            val key = avatarUrl.removePrefix("mascot:")
            return DOLL_MASCOTS.firstOrNull { it.id == key }?.drawableRes
        }
        return null
    }

    /**
     * Reusable composable to render any user avatar cleanly with strict gender matching
     * and optional animated avatar frame.
     */
    @Composable
    fun UserAvatarImage(
        avatarUrl: String,
        userId: String = "",
        gender: String = "Male",
        numericId: Long = 0L,
        frameId: String = "",
        showFrame: Boolean = true,
        contentDescription: String? = null,
        modifier: Modifier = Modifier,
        contentScale: ContentScale = ContentScale.Crop,
        shape: androidx.compose.ui.graphics.Shape = CircleShape
    ) {
        val context = LocalContext.current
        val defaultRes = getDefaultMascotRes(userId, gender, numericId)
        val mascotRes = getDrawableForMascotKey(avatarUrl)

        val isRound = shape == CircleShape
        val liveFrameState by UserSessionManager.activeFrameFlow.collectAsStateWithLifecycle(
            initialValue = userId to UserSessionManager.getActiveFrameId(context, userId)
        )
        val mySessionUserId = remember(context) { UserSessionManager.getSession(context)?.userId ?: "" }
        val isMe = userId.isNotBlank() && userId == mySessionUserId

        val resolvedFrameId = remember(frameId, userId, liveFrameState, showFrame, isRound, isMe) {
            if (!isRound || !showFrame || frameId.equals("none", ignoreCase = true)) {
                ""
            } else if (frameId.isNotBlank()) {
                // If a specific frame is explicitly requested (e.g., in Store / Bag / Previews), use it directly!
                frameId
            } else if (isMe) {
                if (liveFrameState.first == userId && liveFrameState.second.isNotBlank()) {
                    liveFrameState.second
                } else {
                    UserSessionManager.getActiveFrameId(context, userId)
                }
            } else if (userId.isNotBlank()) {
                // For other users: ONLY show frame if explicitly retrieved for their user ID
                UserSessionManager.getActiveFrameId(context, userId)
            } else {
                ""
            }
        }

        if (resolvedFrameId.isNotBlank()) {
            Box(
                modifier = modifier,
                contentAlignment = Alignment.Center
            ) {
                // Avatar Image inside the frame (72% inner scale so ornate wings/crowns/tiaras render with full fidelity)
                Box(
                    modifier = Modifier
                        .fillMaxSize(0.72f)
                        .clip(shape)
                ) {
                    if (mascotRes != null) {
                        Image(
                            painter = painterResource(id = mascotRes),
                            contentDescription = contentDescription,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(shape),
                            contentScale = contentScale
                        )
                    } else if (avatarUrl.isNotBlank()) {
                        val imageRequest = remember(avatarUrl, defaultRes) {
                            ImageRequest.Builder(context)
                                .data(avatarUrl)
                                .crossfade(true)
                                .error(defaultRes)
                                .build()
                        }
                        AsyncImage(
                            model = imageRequest,
                            contentDescription = contentDescription,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(shape),
                            contentScale = contentScale
                        )
                    } else {
                        Image(
                            painter = painterResource(id = defaultRes),
                            contentDescription = contentDescription,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(shape),
                            contentScale = contentScale
                        )
                    }
                }

                // High-Craft Animated Avatar Frame Overlay
                com.example.ui.components.AvatarFrameRenderer(
                    frameId = resolvedFrameId,
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            Box(
                modifier = modifier.clip(shape),
                contentAlignment = Alignment.Center
            ) {
                if (mascotRes != null) {
                    Image(
                        painter = painterResource(id = mascotRes),
                        contentDescription = contentDescription,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(shape),
                        contentScale = contentScale
                    )
                } else if (avatarUrl.isNotBlank()) {
                    val imageRequest = remember(avatarUrl, defaultRes) {
                        ImageRequest.Builder(context)
                            .data(avatarUrl)
                            .crossfade(true)
                            .error(defaultRes)
                            .build()
                    }
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = contentDescription,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(shape),
                        contentScale = contentScale
                    )
                } else {
                    Image(
                        painter = painterResource(id = defaultRes),
                        contentDescription = contentDescription,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(shape),
                        contentScale = contentScale
                    )
                }
            }
        }
    }
}

