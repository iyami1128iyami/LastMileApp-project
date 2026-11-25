package com.example.lastmileapp

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.ceil
import kotlin.math.roundToInt

// --- データモデル ---
data class BikeStation(
    val id: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val bikesAvailable: Int,
    val distanceMeters: Float
)

data class TransportOption(
    val title: String,
    val subtitle: String,
    val estimate: String,
    val actionUrl: String
)

data class AvatarItem(
    val imageResId: Int,
    val name: String,
    val priceCoins: Int, // 解放に必要なコイン数
    val key: String
)

data class PlayerStats(
    val launchCount: Int,
    val totalDistanceMeters: Float,
    val totalSteps: Int,
    val xp: Int,
    val coins: Int,
    val selectedAvatarKey: String,
    val unlockedAvatarKeys: Set<String>
) {
    val level: Int get() = (xp / 100) + 1
    val currentLevelXp: Int get() = xp % 100
}

val AVATAR_LIST = listOf(
    AvatarItem(R.drawable.avatar_1, "さんぽ初心者", 0, "avatar_1"),
    AvatarItem(R.drawable.avatar_2, "ダッシュランナー", 50, "avatar_2"),
    AvatarItem
