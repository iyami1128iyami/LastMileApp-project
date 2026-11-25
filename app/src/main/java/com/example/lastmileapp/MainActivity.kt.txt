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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACTIVITY_RECOGNITION
                ) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        hasLocationPermission = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            hasActivityPermission = perms[Manifest.permission.ACTIVITY_RECOGNITION] == true
        }
    }

    LaunchedEffect(hasLocationPermission, hasActivityPermission) {
        val needed = mutableListOf<String>()
        if (!hasLocationPermission) {
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
            needed.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !hasActivityPermission) {
            needed.add(Manifest.permission.ACTIVITY_RECOGNITION)
        }

        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }

        if (hasLocationPermission) {
            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) {
                    val newPoint = GeoPoint(loc.latitude, loc.longitude)
                    val lastPoint = gameStorage.getLastSavedLocation()
                    if (lastPoint != null) {
                        val moved = distanceBetween(lastPoint, newPoint)
                        if (moved in 10f..5000f) {
                            playerStats = gameStorage.addDistance(moved)
                        }
                    }
                    gameStorage.saveLastLocation(newPoint)
                    currentLocation = newPoint
                }
            }
        }
    }

    DisposableEffect(hasActivityPermission) {
        if (!hasActivityPermission) return@DisposableEffect onDispose {}

        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                val totalSinceBoot = event?.values?.firstOrNull()?.toInt() ?: return
                playerStats = gameStorage.updateStepsFromSensor(totalSinceBoot)
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        if (stepSensor != null) {
            sensorManager.registerListener(listener, stepSensor, SensorManager.SENSOR_DELAY_UI)
        }

        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    LaunchedEffect(currentLocation) {
        statusText = "周辺のシェアサイクルを検索中..."
        val fetched = fetchNearbyHelloCycling(currentLocation.latitude, currentLocation.longitude)
        stations = fetched
        statusText = if (fetched.isNotEmpty()) {
            "周辺800mに ${fetched.size} 件のポートを発見（地図長押しで目的地設定）"
        } else {
            "周辺にポートなし（地図を長押しして目的地を設定）"
        }
    }

    val currentAvatar = getAvatarByKey(playerStats.selectedAvatarKey)

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            tonalElevation = 4.dp,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showAvatarDialog = true }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = currentAvatar.imageResId),
                    contentDescription = currentAvatar.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Lv.${playerStats.level} ${currentAvatar.name}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "🪙 ${playerStats.coins}",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            color = Color(0xFFD97706)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "👟 ${playerStats.totalSteps}歩 / ${(playerStats.totalDistanceMeters / 1000f).let { "%.2f".format(it) }}km",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                        Text(
                            text = "タップでショップ",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { playerStats.currentLevelXp / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                    )
                }
            }
        }

        Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = statusText,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                fontSize = 12.sp
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    MapView(ctx).apply {
                        setTileSource(
                            XYTileSource(
                                "GSI_STD",
                                5, 18, 256, ".png",
                                arrayOf("https://cyberjapandata.gsi.go.jp/xyz/std/")
                            )
                        )
                        setMultiTouchControls(true)
                        controller.setZoom(15.5)
                        controller.setCenter(currentLocation)
                    }
                },
                update = { mapView ->
                    mapView.overlays.clear()

                    val eventsOverlay = MapEventsOverlay(object : MapEventsReceiver {
                        override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean = false
                        override fun longPressHelper(p: GeoPoint?): Boolean {
                            if (p != null) destination = p
                            return true
                        }
                    })
                    mapView.overlays.add(eventsOverlay)

                    val myMarker = Marker(mapView).apply {
                        position = currentLocation
                        title = "${currentAvatar.name} (Lv.${playerStats.level})"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

                        val bitmap = BitmapFactory.decodeResource(context.resources, currentAvatar.imageResId)
                        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 96, 96, true)
                        icon = BitmapDrawable(context.resources, scaledBitmap)
                    }
                    mapView.overlays.add(myMarker)

                    stations.forEach { st ->
                        val m = Marker(mapView).apply {
                            position = GeoPoint(st.lat, st.lon)
                            title = "${st.name} (空き: ${st.bikesAvailable}台)"
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        }
                        mapView.overlays.add(m)
                    }

                    destination?.let { dest ->
                        val destMarker = Marker(mapView).apply {
                            position = dest
                            title = "目的地"
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        }
                        mapView.overlays.add(destMarker)
                    }

                    mapView.controller.animateTo(destination ?: currentLocation)
                    mapView.invalidate()
                }
            )
        }

        destination?.let { dest ->
            val options = remember(currentLocation, dest, stations) {
                buildTransportOptions(currentLocation, dest, stations)
            }
            Surface(
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val distM = distanceBetween(currentLocation, dest).roundToInt()
                        Text(
                            text = "目的地まで 約${distM}m (+15🪙)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        TextButton(onClick = { destination = null }) {
                            Text("閉じる")
                        }
                    }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(options) { opt ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        playerStats = gameStorage.addActionBonus()
                                        openExternalUrl(context, opt.actionUrl)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(12.dp)
                                        .fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(opt.title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                        Text(opt.subtitle, fontSize = 12.sp, color = Color.Gray)
                                    }
                                    Text(
                                        opt.estimate,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAvatarDialog) {
        AlertDialog(
            onDismissRequest = { showAvatarDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("アバターショップ", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("🪙 ${playerStats.coins}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD97706))
                }
            },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(AVATAR_LIST) { item ->
                        val isUnlocked = playerStats.unlockedAvatarKeys.contains(item.key)
                        val isSelected = playerStats.selectedAvatarKey == item.key
                        val canAfford = playerStats.coins >= item.priceCoins

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable(enabled = isUnlocked || canAfford) {
                                    playerStats = gameStorage.buyAndSelectAvatar(item)
                                    showAvatarDialog = false
                                }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isUnlocked) {
                                Image(
                                    painter = painterResource(id = item.imageResId),
                                    contentDescription = item.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .border(1.dp, Color.Gray, CircleShape)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .border(1.dp, Color.LightGray, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("🔒", fontSize = 20.sp)
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.name,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isUnlocked || canAfford) Color.Unspecified else Color.Gray
                                )
                                Text(
                                    text = when {
                                        isSelected -> "使用中"
                                        isUnlocked -> "解放済み（タップで装備）"
                                        canAfford -> "🪙 ${item.priceCoins} で購入可能！"
                                        else -> "🪙 ${item.priceCoins} 必要"
                                    },
                                    fontSize = 12.sp,
                                    color = if (!isUnlocked && canAfford) Color(0xFFD97706) else Color.Gray
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAvatarDialog = false }) {
                    Text("閉じる")
                }
            }
        )
    }
}

fun distanceBetween(a: GeoPoint, b: GeoPoint): Float {
    val result = FloatArray(1)
    Location.distanceBetween(a.latitude, a.longitude, b.latitude, b.longitude, result)
    return result[0]
}

fun buildTransportOptions(
    start: GeoPoint,
    end: GeoPoint,
    stations: List<BikeStation>
): List<TransportOption> {
    val distMeters = distanceBetween(start, end)
    val walkMin = ceil(distMeters / 80.0).toInt().coerceAtLeast(1)

    val nearestBike = stations.filter { it.bikesAvailable > 0 }.minByOrNull { it.distanceMeters }
    val bikeMin = ceil(distMeters / 250.0).toInt().coerceAtLeast(1) +
            if (nearestBike != null) ceil(nearestBike.distanceMeters / 80.0).toInt() else 3

    val extraUnits = ((distMeters - 1096f).coerceAtLeast(0f) / 255f).let { ceil(it).toInt() }
    val taxiYen = 500 + extraUnits * 100
    val taxiMin = ceil(distMeters / 400.0).toInt().coerceAtLeast(2)

    val gmapsBase = "https://www.google.com/maps/dir/?api=1&origin=${start.latitude},${start.longitude}&destination=${end.latitude},${end.longitude}"

    return listOf(
        TransportOption(
            title = "徒歩",
            subtitle = "Googleマップで徒歩ルートを開く",
            estimate = "約${walkMin}分",
            actionUrl = "$gmapsBase&travelmode=walking"
        ),
        TransportOption(
            title = "シェアサイクル (HELLO CYCLING)",
            subtitle = if (nearestBike != null) {
                "最寄り: ${nearestBike.name} (空き${nearestBike.bikesAvailable}台 / ${nearestBike.distanceMeters.roundToInt()}m)"
            } else {
                "周辺に空き自転車が見つかりません（アプリで確認）"
            },
            estimate = "約${bikeMin}分",
            actionUrl = "https://www.hellocycling.jp/app/openapp"
        ),
        TransportOption(
            title = "タクシー (GO)",
            subtitle = "配車アプリを起動して呼ぶ",
            estimate = "約${taxiMin}分 / 約${taxiYen}円",
            actionUrl = "https://go.mo-t.com/"
        ),
        TransportOption(
            title = "バス・公共交通",
            subtitle = "Googleマップでバス・電車ルートを検索",
            estimate = "経路検索",
            actionUrl = "$gmapsBase&travelmode=transit"
        )
    )
}

fun openExternalUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    context.startActivity(intent)
}

suspend fun fetchNearbyHelloCycling(lat: Double, lon: Double): List<BikeStation> = withContext(Dispatchers.IO) {
    try {
        val infoJson = httpGet("https://api-public.odpt.org/api/v4/gbfs/hellocycling/station_information.json")
        val statusJson = httpGet("https://api-public.odpt.org/api/v4/gbfs/hellocycling/station_status.json")

        val statusMap = mutableMapOf<String, Int>()
        val statusArr = JSONObject(statusJson).getJSONObject("data").getJSONArray("stations")
        for (i in 0 until statusArr.length()) {
            val obj = statusArr.getJSONObject(i)
            statusMap[obj.getString("station_id")] = obj.optInt("num_bikes_available", 0)
        }

        val result = mutableListOf<BikeStation>()
        val infoArr = JSONObject(infoJson).getJSONObject("data").getJSONArray("stations")
        val distResult = FloatArray(1)
        for (i in 0 until infoArr.length()) {
            val obj = infoArr.getJSONObject(i)
            val sLat = obj.getDouble("lat")
            val sLon = obj.getDouble("lon")
            Location.distanceBetween(lat, lon, sLat, sLon, distResult)
            if (distResult[0] <= 800f) {
                val id = obj.getString("station_id")
                result.add(
                    BikeStation(
                        id = id,
                        name = obj.optString("name", "ポート"),
                        lat = sLat,
                        lon = sLon,
                        bikesAvailable = statusMap[id] ?: 0,
                        distanceMeters = distResult[0]
                    )
                )
            }
        }
        result.sortedBy { it.distanceMeters }
    } catch (e: Exception) {
        emptyList()
    }
}

private fun httpGet(urlStr: String): String {
    val conn = URL(urlStr).openConnection() as HttpURLConnection
    conn.connectTimeout = 8000
    conn.readTimeout = 8000
    return conn.inputStream.bufferedReader().use { it.readText() }
}
