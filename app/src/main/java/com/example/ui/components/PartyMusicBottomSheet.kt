package com.example.ui.components
import com.example.ui.components.AppToast

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SpatialAudio
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.PartyMusicManager
import com.example.ui.theme.QivoOrange
import com.example.ui.theme.QivoYellow
import kotlinx.coroutines.delay

/**
 * Premium Party Room DJ Music Player Deck.
 * Available to Room Hosts and Seated Party Admins.
 * Features realistic spinning vinyl turntable, neon audio spectrum equalizer,
 * high-res scrub controls, loudspeaker volume boost, and instant DJ party sound effects.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartyMusicBottomSheet(
    isSeated: Boolean,
    onDismissRequest: () -> Unit,
    onTrackChanged: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val trackInfo by PartyMusicManager.trackInfo.collectAsState()
    val isPlaying by PartyMusicManager.isPlaying.collectAsState()

    var currentVolume by remember { mutableFloatStateOf(1.0f) }

    // Position updater ticker
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            PartyMusicManager.updateCurrentPosition()
            delay(500)
        }
    }

    // Permission launcher for reading local audio
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            AppToast.show("Storage permission is required to select audio files")
        }
    }

    // Audio file picker launcher
    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            PartyMusicManager.playLocalMusic(context, uri)
            onTrackChanged(PartyMusicManager.trackInfo.value.title)
            AppToast.show("Streaming track live to party room 🎵")
        }
    }

    fun openAudioPicker() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
            if (!hasPerm) {
                permissionLauncher.launch(Manifest.permission.READ_MEDIA_AUDIO)
                return
            }
        } else {
            val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            if (!hasPerm) {
                permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                return
            }
        }
        audioPickerLauncher.launch("audio/*")
    }

    // Play quick DJ Sound Effect tone cues
    fun playDjSfx(type: String) {
        try {
            val toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
            when (type) {
                "airhorn" -> {
                    toneGen.startTone(ToneGenerator.TONE_DTMF_D, 180)
                }
                "cheer" -> {
                    toneGen.startTone(ToneGenerator.TONE_SUP_RINGTONE, 220)
                }
                "drop" -> {
                    toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 250)
                }
                "applause" -> {
                    toneGen.startTone(ToneGenerator.TONE_DTMF_0, 150)
                }
                "drumroll" -> {
                    toneGen.startTone(ToneGenerator.TONE_DTMF_A, 200)
                }
            }
            AppToast.show("DJ FX: $type 📢")
        } catch (_: Exception) {}
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = Color(0xFF13111E),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState())
                .testTag("party_music_sheet")
        ) {
            // Deck Top Bar Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        Color(0xFFFF9100),
                                        Color(0xFFFF3D00),
                                        Color(0xFFE91E63)
                                    )
                                )
                            )
                            .shadow(8.dp, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SpatialAudio,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Party Room DJ Deck",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0x3300E676))
                                    .border(1.dp, Color(0xFF00E676), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text("HI-FI", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E676))
                            }
                        }
                        Text(
                            text = "Stream high quality audio live into the room",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.65f)
                        )
                    }
                }

                IconButton(
                    onClick = onDismissRequest,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0x22FFFFFF))
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (!isSeated) {
                // Warning if user is not seated
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0x33FF9800)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, QivoOrange)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🎙️", fontSize = 24.sp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Seat Required To Broadcast Music",
                                color = QivoYellow,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Take any of the open mic seats to activate the music player deck.",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            } else {
                // Active Vinyl Deck & DJ Turntable View
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(12.dp, RoundedCornerShape(22.dp)),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1B2E)),
                    border = androidx.compose.foundation.BorderStroke(
                        1.5.dp,
                        Brush.linearGradient(
                            listOf(
                                Color(0xFFBA68C8).copy(alpha = 0.6f),
                                Color(0xFFFF9100).copy(alpha = 0.6f)
                            )
                        )
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (trackInfo.title.isNotBlank()) {
                            // Spinning Vinyl Record Turntable
                            TurntableVinylRecord(
                                isPlaying = isPlaying,
                                trackTitle = trackInfo.title
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            // Glowing Multi-band Audio Visualizer
                            AnimatedMusicEqualizer(isPlaying = isPlaying)

                            Spacer(modifier = Modifier.height(12.dp))

                            // Track Title & Artist
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = trackInfo.title,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    maxLines = 1,
                                    softWrap = false,
                                    textAlign = TextAlign.Center
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Phone Storage • Audio Broadcast",
                                fontSize = 11.sp,
                                color = QivoYellow,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            // Seek Slider & Progress Timestamps
                            val dur = trackInfo.durationMs.coerceAtLeast(1)
                            val cur = trackInfo.currentPositionMs.coerceIn(0, dur)
                            val progress = (cur.toFloat() / dur.toFloat()).coerceIn(0f, 1f)

                            Slider(
                                value = progress,
                                onValueChange = { newProgress ->
                                    val seekMs = (newProgress * dur).toInt()
                                    PartyMusicManager.seekTo(seekMs)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = SliderDefaults.colors(
                                    thumbColor = QivoOrange,
                                    activeTrackColor = QivoOrange,
                                    inactiveTrackColor = Color(0x33FFFFFF)
                                )
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = formatMs(cur),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                                Text(
                                    text = formatMs(dur),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Playback DJ Control Buttons Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Replay -10s
                                IconButton(
                                    onClick = { PartyMusicManager.seekBackward(10000) },
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(Color(0x22FFFFFF))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Replay10,
                                        contentDescription = "Back 10s",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                // Stop Button
                                IconButton(
                                    onClick = { PartyMusicManager.stopMusic() },
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(Color(0x33FF1744))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Stop,
                                        contentDescription = "Stop",
                                        tint = Color(0xFFFF5252),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                // Central Glowing Master Play / Pause Button
                                Box(
                                    modifier = Modifier
                                        .size(62.dp)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.linearGradient(
                                                listOf(
                                                    Color(0xFFFF9100),
                                                    Color(0xFFFF3D00),
                                                    Color(0xFFE91E63)
                                                )
                                            )
                                        )
                                        .border(2.dp, Color.White.copy(alpha = 0.8f), CircleShape)
                                        .clickable { PartyMusicManager.togglePlayPause() }
                                        .shadow(12.dp, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (isPlaying) "Pause" else "Play",
                                        tint = Color.White,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }

                                // Forward +10s
                                IconButton(
                                    onClick = { PartyMusicManager.seekForward(10000) },
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(Color(0x22FFFFFF))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Forward10,
                                        contentDescription = "Forward 10s",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                // Pick New Track
                                IconButton(
                                    onClick = { openAudioPicker() },
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(Color(0x22FFFFFF))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FolderOpen,
                                        contentDescription = "Change Track",
                                        tint = QivoYellow,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Volume Booster Slider
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0x33000000))
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        currentVolume = if (currentVolume > 0f) 0f else 1.0f
                                        PartyMusicManager.setVolume(currentVolume)
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = if (currentVolume == 0f) Icons.Default.VolumeMute else Icons.Default.VolumeUp,
                                        contentDescription = null,
                                        tint = if (currentVolume == 0f) Color(0xFFFF5252) else QivoYellow,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Slider(
                                    value = currentVolume,
                                    onValueChange = {
                                        currentVolume = it
                                        PartyMusicManager.setVolume(it)
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = SliderDefaults.colors(
                                        thumbColor = QivoYellow,
                                        activeTrackColor = QivoYellow,
                                        inactiveTrackColor = Color(0x33FFFFFF)
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${(currentVolume * 100).toInt()}%",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                )
                            }

                        } else {
                            // Empty State with Stylish Vinyl Illustration
                            Box(
                                modifier = Modifier
                                    .size(100.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.radialGradient(
                                            listOf(
                                                Color(0xFF2C2842),
                                                Color(0xFF161424)
                                            )
                                        )
                                    )
                                    .border(2.dp, Color(0x33FFFFFF), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LibraryMusic,
                                    contentDescription = null,
                                    tint = QivoYellow,
                                    modifier = Modifier.size(44.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "No Music Playing",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Select an audio track from your phone storage to play live for all room members.",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { openAudioPicker() },
                                colors = ButtonDefaults.buttonColors(containerColor = QivoOrange),
                                shape = RoundedCornerShape(24.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                                    .testTag("choose_music_file_button")
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Choose Music File From Phone", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("Or play live party beat generator:", fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        PartyMusicManager.playBuiltInPartyTrack(context, "🔥 Electro Party Groove")
                                        onTrackChanged("🔥 Electro Party Groove")
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("🔥 Electro Beat", fontSize = 11.sp, color = QivoYellow, maxLines = 1)
                                }
                                OutlinedButton(
                                    onClick = {
                                        PartyMusicManager.playBuiltInPartyTrack(context, "🌴 Chill Lounge Vibes")
                                        onTrackChanged("🌴 Chill Lounge Vibes")
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("🌴 Chill Lounge", fontSize = 11.sp, color = Color.White, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // DJ Sound Effects Soundboard (Instant Fun Triggers)
            Text(
                text = "⚡ DJ Party Sound FX",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = QivoYellow,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DjSfxChip("📢 Air Horn") { playDjSfx("airhorn") }
                DjSfxChip("👏 Applause") { playDjSfx("applause") }
                DjSfxChip("🥳 Cheers") { playDjSfx("cheer") }
                DjSfxChip("🥁 Drumroll") { playDjSfx("drumroll") }
                DjSfxChip("🔊 Bass Drop") { playDjSfx("drop") }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Information Card
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0x22000000),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x22FFFFFF)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("💡", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Music plays directly through the party room live audio channel so every member hears it clearly.",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.75f)
                    )
                }
            }
        }
    }
}

@Composable
fun DjSfxChip(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF28233D),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x44FFFFFF))
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

/**
 * Animated Turntable Vinyl Record with rotating groove rings
 */
@Composable
fun TurntableVinylRecord(
    isPlaying: Boolean,
    trackTitle: String,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "vinyl_spin")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val activeRotation = if (isPlaying) rotation else 0f

    Box(
        modifier = modifier
            .size(130.dp)
            .shadow(14.dp, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        // Outer Vinyl Body with Groove Rings
        Canvas(modifier = Modifier.fillMaxSize().rotate(activeRotation)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2f

            // Dark Vinyl Base
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(
                        Color(0xFF23222B),
                        Color(0xFF14131A),
                        Color(0xFF09080E)
                    ),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )

            // Groove Rings
            val grooveColors = listOf(
                Color(0x33FFFFFF),
                Color(0x15FFFFFF),
                Color(0x28FFFFFF),
                Color(0x18FFFFFF)
            )
            for (i in 1..4) {
                drawCircle(
                    color = grooveColors[i - 1],
                    radius = radius * (0.35f + i * 0.12f),
                    center = center,
                    style = Stroke(width = 1.2f)
                )
            }
        }

        // Center Album Artwork Badge
        Box(
            modifier = Modifier
                .size(46.dp)
                .rotate(activeRotation)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFFFF9100),
                            Color(0xFFFF3D00),
                            Color(0xFFE91E63)
                        )
                    )
                )
                .border(2.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }

        // Center Spindle Hole
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(Color(0xFF100E17))
                .border(1.dp, Color(0x66FFFFFF), CircleShape)
        )
    }
}

/**
 * Neon Audio Frequency Equalizer Bar Graph
 */
@Composable
fun AnimatedMusicEqualizer(isPlaying: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "equalizer")

    val h1 by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(320, easing = LinearEasing), RepeatMode.Reverse),
        label = "h1"
    )
    val h2 by transition.animateFloat(
        initialValue = 0.9f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(tween(440, easing = LinearEasing), RepeatMode.Reverse),
        label = "h2"
    )
    val h3 by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(260, easing = LinearEasing), RepeatMode.Reverse),
        label = "h3"
    )
    val h4 by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(510, easing = LinearEasing), RepeatMode.Reverse),
        label = "h4"
    )
    val h5 by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(380, easing = LinearEasing), RepeatMode.Reverse),
        label = "h5"
    )
    val h6 by transition.animateFloat(
        initialValue = 0.75f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(tween(290, easing = LinearEasing), RepeatMode.Reverse),
        label = "h6"
    )

    Row(
        modifier = modifier.height(28.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        listOf(h1, h2, h3, h4, h5, h6).forEachIndexed { index, heightRatio ->
            val barHeight = if (isPlaying) (heightRatio * 28).dp.coerceAtLeast(5.dp) else 5.dp
            val colorList = when (index % 3) {
                0 -> listOf(Color(0xFFFF9100), Color(0xFFFF3D00))
                1 -> listOf(Color(0xFFE040FB), Color(0xFF7C4DFF))
                else -> listOf(Color(0xFF00E676), Color(0xFF00B0FF))
            }
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .height(barHeight)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Brush.verticalGradient(colorList))
            )
        }
    }
}

private fun formatMs(ms: Int): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
