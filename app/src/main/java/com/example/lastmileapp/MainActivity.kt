package com.example.lastmileapp

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // assets/*.json からブレインロット・武器・ショップデータをロード
        MasterData.init(applicationContext)
        // Google Maps SDK 初期化（カスタムマーカーアイコン用）
        MapsInitializer.initialize(applicationContext)
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
    var showGachaDialog by remember { mutableStateOf(false) }
    var showShopDialog by remember { mutableStateOf(false) }
    var showDexDialog by remember { mutableStateOf(false) }
    var activeBattle by remember { mutableStateOf<FieldSpawn?>(null) }

    var currentLocation by remember { mutableStateOf(LatLng(35.6812, 139.7671)) }
    var destination by remember { mutableStateOf<LatLng?>(null) }
    var lastCenteredTarget by remember { mutableStateOf<LatLng?>(null) }
    var stations by remember { mutableStateOf<List<BikeStation>>(emptyList()) }
    var fieldSpawns by remember { mutableStateOf<List<FieldSpawn>>(emptyList()) }
    var coinDrops by remember { mutableStateOf<List<CoinDrop>>(emptyList()) }
    var statusText by remember { mutableStateOf("ブレインロットをタップでバトル！長押しで目的地を設定") }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(currentLocation, 15.8f)
    }

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
                    val newPoint = LatLng(loc.latitude, loc.longitude)
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
        if (fieldSpawns.isEmpty()) {
            fieldSpawns = spawnNearbyBrainrots(currentLocation, 6)
            coinDrops = spawnNearbyCoins(currentLocation)
        }
        val fetched = fetchNearbyHelloCycling(currentLocation.latitude, currentLocation.longitude)
        stations = fetched
        statusText = "ブレインウォーク｜敵 ${fieldSpawns.size}体 / 🪙 ${coinDrops.size}個 / 🚲 ${fetched.size}件"
    }

    // 現在地または目的地が更新されたらカメラをスムーズに移動
    LaunchedEffect(currentLocation, destination) {
        val target = destination ?: currentLocation
        if (lastCenteredTarget != target) {
            cameraPositionState.animate(
                update = CameraUpdateFactory.newLatLngZoom(target, 15.8f),
                durationMs = 600
            )
            lastCenteredTarget = target
        }
    }

    val activeBuffs = remember(playerStats) {
        val list = mutableListOf<String>()
        playerStats.companionMonster?.let { c ->
            list.add("⭐お供:${c.emoji}${companionSkillDesc(c)}")
        }
        if (playerStats.nextUncaughtRadar) list.add("🧭未取得確定")
        if (playerStats.nextRareIncense) list.add("✨SR以上確定")
        playerStats.nextHabitatFilter?.let { list.add(if (it == "街") "🏙️街のみ" else "🌴ジャングルのみ") }
        if (playerStats.xpBoostBattlesLeft > 0) list.add("👟XP2倍(残${playerStats.xpBoostBattlesLeft})")
        list
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ヘッダー
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            tonalElevation = 4.dp,
            modifier = Modifier
                .fillMaxWidth()
                .zIndex(2f)
        ) {
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val comp = playerStats.companionMonster
                    if (comp != null) {
                        MonsterAvatar(
                            monster = comp,
                            size = 44.dp,
                            borderWidth = 2.5.dp,
                            emojiFontSize = 22.sp,
                            modifier = Modifier.clickable { showDexDialog = true }
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .border(2.5.dp, Color.White, CircleShape)
                                .clickable { showDexDialog = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🧭", fontSize = 22.sp)
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val upgradeText = if (playerStats.equippedWeaponUpgrade > 0) "+${playerStats.equippedWeaponUpgrade}" else ""
                            Text(
                                text = "Lv.${playerStats.level} ATK:${playerStats.totalAtk} (${playerStats.equippedWeapon.emoji}$upgradeText)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "🪙 ${playerStats.coins}",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp,
                                color = Color(0xFFD97706)
                            )
                        }
                        Text(
                            text = "移動: ${(playerStats.totalDistanceMeters / 1000f).let { "%.2f".format(it) }}km / ${playerStats.totalSteps}歩  図鑑:${playerStats.caughtMonsterIds.size}/${MasterData.brainrots.size}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        LinearProgressIndicator(
                            progress = { playerStats.currentLevelXp / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                        )
                    }
                }

                if (activeBuffs.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "発動中: ${activeBuffs.joinToString(" / ")}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFB45309)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Button(
                        onClick = { showGachaDialog = true },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp)
                    ) {
                        Text("⚔️ガチャ", fontSize = 11.sp)
                    }
                    Button(
                        onClick = { showShopDialog = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp)
                    ) {
                        Text("🛒ショップ", fontSize = 11.sp)
                    }
                    Button(
                        onClick = { showDexDialog = true },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp)
                    ) {
                        Text("📖図鑑", fontSize = 11.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            val usedRadar = playerStats.nextUncaughtRadar
                            val usedIncense = playerStats.nextRareIncense
                            val usedHabitat = playerStats.nextHabitatFilter

                            val newSpawns = spawnNearbyBrainrots(
                                center = currentLocation,
                                count = 6,
                                caughtIds = playerStats.caughtMonsterIds,
                                guaranteeUncaught = usedRadar,
                                guaranteeRare = usedIncense,
                                habitatFilter = usedHabitat
                            )
                            val newCoins = spawnNearbyCoins(currentLocation)
                            fieldSpawns = newSpawns
                            coinDrops = newCoins
                            playerStats = gameStorage.consumeExplorationBuffs()

                            val effectNotes = mutableListOf<String>()
                            if (usedRadar) effectNotes.add("未取得レーダー発動")
                            if (usedIncense) effectNotes.add("SR以上確定")
                            if (usedHabitat != null) effectNotes.add("${usedHabitat}限定")
                            val prefix = if (effectNotes.isNotEmpty()) "【${effectNotes.joinToString("・")}】" else ""

                            statusText = if (newCoins.isNotEmpty()) {
                                "${prefix}探索完了！敵 ${newSpawns.size}体 と 🪙 ${newCoins.size}個 を発見！"
                            } else {
                                "${prefix}探索完了！ブレインロット ${newSpawns.size}体 が出現！"
                            }
                        },
                        modifier = Modifier.weight(0.9f),
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp)
                    ) {
                        Text("🔄探索", fontSize = 11.sp)
                    }
                }
            }
        }

        // サブステータス ＋ 取得済みスキップ切替
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .zIndex(2f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = statusText,
                    fontSize = 11.sp,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    color = if (playerStats.skipCaughtBattle) Color(0xFFDCFCE7) else Color(0xFFE5E7EB),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .border(
                            1.dp,
                            if (playerStats.skipCaughtBattle) Color(0xFF16A34A) else Color.Gray,
                            RoundedCornerShape(12.dp)
                        )
                        .clickable {
                            playerStats = gameStorage.toggleSkipCaughtBattle()
                            statusText = if (playerStats.skipCaughtBattle) {
                                "⚡ 取得済みスキップON（捕獲済みキャラはタップで即決着）"
                            } else {
                                "取得済みスキップOFF（通常バトル）"
                            }
                        }
                ) {
                    Text(
                        text = if (playerStats.skipCaughtBattle) "⚡既得スキップ:ON" else "既得スキップ:OFF",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (playerStats.skipCaughtBattle) Color(0xFF15803D) else Color.DarkGray,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }

        // Googleマップエリア
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clipToBounds()
                .zIndex(0f)
        ) {
            val coinBitmap = remember { createCoinMarkerBitmap() }

            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(isMyLocationEnabled = hasLocationPermission),
                uiSettings = MapUiSettings(
                    myLocationButtonEnabled = hasLocationPermission,
                    zoomControlsEnabled = false,
                    compassEnabled = true
                ),
                onMapLongClick = { latLng ->
                    destination = latLng
                }
            ) {
                // シェアサイクルポート（HELLO CYCLING）
                stations.forEach { st ->
                    val state = remember(st.id) { MarkerState(position = LatLng(st.lat, st.lon)) }
                    Marker(
                        state = state,
                        title = "🚲 ${st.name}",
                        snippet = "空き: ${st.bikesAvailable}台",
                        icon = runCatching {
                            BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
                        }.getOrNull()
                    )
                }

                // フィールド上のドロップコイン（タップで即獲得）
                coinDrops.forEach { drop ->
                    val state = remember(drop.dropId) { MarkerState(position = drop.location) }
                    val coinIcon = remember(drop.dropId) {
                        runCatching { BitmapDescriptorFactory.fromBitmap(coinBitmap) }.getOrNull()
                    }
                    Marker(
                        state = state,
                        title = "🪙 ドロップコイン (+${drop.amount}🪙)",
                        anchor = Offset(0.5f, 0.5f),
                        icon = coinIcon,
                        onClick = {
                            playerStats = gameStorage.addCoins(drop.amount)
                            coinDrops = coinDrops.filter { it.dropId != drop.dropId }
                            statusText = "🪙 フィールドのコイン（+${drop.amount}🪙）を拾った！"
                            true
                        }
                    )
                }

                // フィールド上のブレインロット（タップでバトル / 既得スキップ）
                fieldSpawns.forEach { spawn ->
                    val state = remember(spawn.spawnId) { MarkerState(position = spawn.location) }
                    val monsterIcon = remember(spawn.monster.id) {
                        runCatching {
                            BitmapDescriptorFactory.fromBitmap(
                                createMonsterMarkerBitmap(context, spawn.monster)
                            )
                        }.getOrNull()
                    }
                    Marker(
                        state = state,
                        title = "${spawn.monster.name} [${spawn.monster.rarity}]",
                        anchor = Offset(0.5f, 0.5f),
                        icon = monsterIcon,
                        onClick = {
                            val isCaught = playerStats.caughtMonsterIds.contains(spawn.monster.id)
                            if (playerStats.skipCaughtBattle && isCaught) {
                                val won = simulateQuickBattle(playerStats, spawn.monster)
                                if (won) {
                                    val (updated, gainedCoins, gainedXp) = gameStorage.recordMonsterCatch(spawn.monster)
                                    playerStats = updated
                                    fieldSpawns = fieldSpawns.filter { it.spawnId != spawn.spawnId }
                                    statusText = "⚡スキップ勝利！${spawn.monster.name} (+${gainedCoins}🪙 / +${gainedXp}XP)"
                                } else {
                                    statusText = "⚡スキップ敗北… ${spawn.monster.name} は強すぎた！武器を強化しよう"
                                }
                            } else {
                                activeBattle = spawn
                            }
                            true
                        }
                    )
                }

                // 目的地ピン
                destination?.let { dest ->
                    val destState = remember(dest) { MarkerState(position = dest) }
                    Marker(
                        state = destState,
                        title = "目的地",
                        icon = runCatching {
                            BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
                        }.getOrNull()
                    )
                }
            }
        }

        // 目的地設定時の移動手段比較カード
        destination?.let { dest ->
            val options = remember(currentLocation, dest, stations) {
                buildTransportOptions(currentLocation, dest, stations)
            }
            Surface(
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp)
                    .zIndex(2f)
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

    if (showGachaDialog) {
        WeaponGachaDialog(
            stats = playerStats,
            onPullGacha = {
                val res = gameStorage.pullGacha()
                if (res != null) {
                    playerStats = res.first
                    res.second
                } else null
            },
            onEquipWeapon = { weaponId ->
                playerStats = gameStorage.equipWeapon(weaponId)
            },
            onDismiss = { showGachaDialog = false }
        )
    }

    if (showShopDialog) {
        ItemShopDialog(
            stats = playerStats,
            onBuyItem = { item ->
                val res = gameStorage.buyShopItem(item)
                if (res != null) {
                    playerStats = res.first
                    statusText = res.second
                    res.second
                } else null
            },
            onDismiss = { showShopDialog = false }
        )
    }

    if (showDexDialog) {
        BrainrotDexDialog(
            caughtIds = playerStats.caughtMonsterIds,
            companionId = playerStats.companionMonsterId,
            onSetCompanion = { mId ->
                playerStats = gameStorage.setCompanion(mId)
                val comp = playerStats.companionMonster
                statusText = if (comp != null) {
                    "⭐ ${comp.name} をお供にした！（${companionSkillDesc(comp)}）"
                } else {
                    "お供を外しました"
                }
            },
            onDismiss = { showDexDialog = false }
        )
    }

    activeBattle?.let { spawn ->
        BattleDialog(
            spawn = spawn,
            playerStats = playerStats,
            onConsumeItem = { itemType ->
                playerStats = gameStorage.consumeBattleItem(itemType)
            },
            onWin = {
                val (updated, gainedCoins, gainedXp) = gameStorage.recordMonsterCatch(spawn.monster)
                playerStats = updated
                fieldSpawns = fieldSpawns.filter { it.spawnId != spawn.spawnId }
                statusText = "${spawn.monster.name} をゲット！(+${gainedCoins}🪙 / +${gainedXp}XP)"
                activeBattle = null
            },
            onEscapeOrLose = {
                activeBattle = null
            }
        )
    }
}
