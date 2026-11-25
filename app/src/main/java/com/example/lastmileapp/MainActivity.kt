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
    AvatarItem(R.drawable.avatar_3, "街乗りサイクリスト", 120, "avatar_3"),
    AvatarItem(R.drawable.avatar_4, "モビリティマスター", 250, "avatar_4"),
    AvatarItem(R.drawable.avatar_5, "シティクルーズ", 500, "avatar_5"),
    AvatarItem(R.drawable.avatar_6, "ラストワンマイル王", 1000, "avatar_6")
)

fun getAvatarByKey(key: String): AvatarItem {
    return AVATAR_LIST.find { it.key == key } ?: AVATAR_LIST[0]
}

// --- ゲームデータの保存・読み込み ---
class GameStorage(context: Context) {
    private val prefs = context.getSharedPreferences("lastmile_game_prefs", Context.MODE_PRIVATE)

    fun loadStats(): PlayerStats {
        val unlocked = prefs.getStringSet("unlocked_avatars", setOf("avatar_1")) ?: setOf("avatar_1")
        return PlayerStats(
            launchCount = prefs.getInt("launch_count", 0),
            totalDistanceMeters = prefs.getFloat("total_distance", 0f),
            totalSteps = prefs.getInt("total_steps", 0),
            xp = prefs.getInt("xp", 0),
            coins = prefs.getInt("coins", 0),
            selectedAvatarKey = prefs.getString("avatar_key", "avatar_1") ?: "avatar_1",
            unlockedAvatarKeys = unlocked
        )
    }

    fun recordLaunch(): PlayerStats {
        val current = loadStats()
        val updated = current.copy(
            launchCount = current.launchCount + 1,
            xp = current.xp + 20,
            coins = current.coins + 10 // 起動ボーナス +10コイン
        )
        saveStats(updated)
        return updated
    }

    fun addDistance(meters: Float): PlayerStats {
        val current = loadStats()
        val gained = (meters / 10f).toInt() // 10mごとに +1XP & +1コイン
        val updated = current.copy(
            totalDistanceMeters = current.totalDistanceMeters + meters,
            xp = current.xp + gained,
            coins = current.coins + gained
        )
        saveStats(updated)
        return updated
    }

    // 歩数センサーの累計値から増分を計算して加算
    fun updateStepsFromSensor(sensorTotalSteps: Int): PlayerStats {
        val lastSensorVal = prefs.getInt("last_sensor_steps", -1)
        prefs.edit().putInt("last_sensor_steps", sensorTotalSteps).apply()

        val current = loadStats()
        if (lastSensorVal == -1 || sensorTotalSteps < lastSensorVal) {
            return current
        }

        val deltaSteps = sensorTotalSteps - lastSensorVal
        if (deltaSteps <= 0) return current

        val newTotalSteps = current.totalSteps + deltaSteps
        val oldCoinSteps = current.totalSteps / 10
        val newCoinSteps = newTotalSteps / 10
        val gainedCoins = (newCoinSteps - oldCoinSteps).coerceAtLeast(0)

        val updated = current.copy(
            totalSteps = newTotalSteps,
            xp = current.xp + gainedCoins * 2,
            coins = current.coins + gainedCoins
        )
        saveStats(updated)
        return updated
    }

    fun addActionBonus(): PlayerStats {
        val current = loadStats()
        val updated = current.copy(
            xp = current.xp + 30,
            coins = current.coins + 15 // 検索ボーナス +15コイン
        )
        saveStats(updated)
        return updated
    }

    fun buyAndSelectAvatar(item: AvatarItem): PlayerStats {
        val current = loadStats()
        if (current.unlockedAvatarKeys.contains(item.key)) {
            val updated = current.copy(selectedAvatarKey = item.key)
            saveStats(updated)
            return updated
        }
        if (current.coins < item.priceCoins) return current

        val newUnlocked = current.unlockedAvatarKeys + item.key
        val updated = current.copy(
            coins = current.coins - item.priceCoins,
            selectedAvatarKey = item.key,
            unlockedAvatarKeys = newUnlocked
        )
        saveStats(updated)
        return updated
    }

    fun getLastSavedLocation(): GeoPoint? {
        if (!prefs.contains("last_lat")) return null
        val lat = prefs.getFloat("last_lat", 0f).toDouble()
        val lon = prefs.getFloat("last_lon", 0f).toDouble()
        return GeoPoint(lat, lon)
    }

    fun saveLastLocation(point: GeoPoint) {
        prefs.edit()
            .putFloat("last_lat", point.latitude.toFloat())
            .putFloat("last_lon", point.longitude.toFloat())
            .apply()
    }

    private fun saveStats(stats: PlayerStats) {
        prefs.edit()
            .putInt("launch_count", stats.launchCount)
            .putFloat("total_distance", stats.totalDistanceMeters)
            .putInt("total_steps", stats.totalSteps)
            .putInt("xp", stats.xp)
            .putInt("coins", stats.coins)
            .putString("avatar_key", stats.selectedAvatarKey)
            .putStringSet("unlocked_avatars", stats.unlockedAvatarKeys)
            .apply()
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().userAgentValue = "LastMileApp/1.0 (iyami1128iyami)"
        setContent {
            MaterialTheme {
                LastMileAppScreen()
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun LastMileAppScreen() {
    val context = LocalContext.current
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val gameStorage = remember { GameStorage(context) }

    var playerStats by remember { mutableStateOf(gameStorage.recordLaunch()) }
    var showAvatarDialog by remember { mutableStateOf(false) }

    var currentLocation by remember { mutableStateOf(GeoPoint(35.6812, 139.7671)) }
    var destination by remember { mutableStateOf<GeoPoint?>(null) }
    var stations by remember { mutableStateOf<List<BikeStation>>(emptyList()) }
    var statusText by remember { mutableStateOf("地図を長押しして目的地を設定してください") }

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var hasActivityPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_
