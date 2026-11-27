package com.example.lastmileapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import com.google.android.gms.maps.model.LatLng
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

fun createMonsterMarkerBitmap(context: Context, monster: BrainrotMonster): Bitmap {
    val size = 116
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
    }
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = rarityColorInt(monster.rarity)
        style = Paint.Style.STROKE
        strokeWidth = 9f
    }
    val rect = RectF(6f, 6f, size - 6f, size - 6f)
    canvas.drawOval(rect, bgPaint)

    val assetBmp = MonsterImageLoader.getBitmap(context, monster)
    if (assetBmp != null) {
        val saveCount = canvas.save()
        val clipPath = Path().apply { addOval(rect, Path.Direction.CW) }
        canvas.clipPath(clipPath)

        val srcSize = minOf(assetBmp.width, assetBmp.height)
        val srcLeft = (assetBmp.width - srcSize) / 2
        val srcTop = (assetBmp.height - srcSize) / 2
        val srcRect = Rect(srcLeft, srcTop, srcLeft + srcSize, srcTop + srcSize)
        canvas.drawBitmap(
            assetBmp,
            srcRect,
            rect,
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        )
        canvas.restoreToCount(saveCount)
    } else {
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 54f
            textAlign = Paint.Align.CENTER
        }
        val fm = textPaint.fontMetrics
        val y = size / 2f - (fm.ascent + fm.descent) / 2f
        canvas.drawText(monster.emoji, size / 2f, y, textPaint)
    }

    canvas.drawOval(rect, borderPaint)
    return bitmap
}

fun createCoinMarkerBitmap(): Bitmap {
    val size = 96
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#FFFBEB")
        style = Paint.Style.FILL
    }
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#F59E0B")
        style = Paint.Style.STROKE
        strokeWidth = 7f
    }
    val rect = RectF(6f, 6f, size - 6f, size - 6f)
    canvas.drawOval(rect, bgPaint)
    canvas.drawOval(rect, borderPaint)

    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 44f
        textAlign = Paint.Align.CENTER
    }
    val fm = textPaint.fontMetrics
    val y = size / 2f - (fm.ascent + fm.descent) / 2f
    canvas.drawText("🪙", size / 2f, y, textPaint)

    return bitmap
}

fun rollWeaponGacha(): Weapon {
    val r = Random.nextInt(100)
    val pool = when {
        r < 5 -> MasterData.weapons.filter { it.rarity == "SSR" }
        r < 20 -> MasterData.weapons.filter { it.rarity == "SR" }
        r < 55 -> MasterData.weapons.filter { it.rarity == "R" }
        else -> MasterData.weapons.filter { it.rarity == "N" && it.id != "w_hand" }
    }
    return pool.ifEmpty { MasterData.weapons }.random()
}

fun spawnNearbyBrainrots(
    center: LatLng,
    count: Int = 6,
    caughtIds: Set<String> = emptySet(),
    guaranteeUncaught: Boolean = false,
    guaranteeRare: Boolean = false,
    habitatFilter: String? = null
): List<FieldSpawn> {
    if (MasterData.brainrots.isEmpty()) return emptyList()

    val basePool = if (habitatFilter != null) {
        MasterData.brainrots.filter { it.habitat == habitatFilter }
    } else {
        MasterData.brainrots
    }
    val effectivePool = basePool.ifEmpty { MasterData.brainrots }

    val selectedMonsters = mutableListOf<BrainrotMonster>()

    if (guaranteeUncaught) {
        val uncaughtPool = effectivePool.filter { !caughtIds.contains(it.id) }
        if (uncaughtPool.isNotEmpty()) {
            selectedMonsters.add(uncaughtPool.random())
        }
    }

    if (guaranteeRare && selectedMonsters.size < count) {
        val rarePool = effectivePool.filter { it.rarity == "SR" || it.rarity == "SSR" }
        if (rarePool.isNotEmpty()) {
            selectedMonsters.add(rarePool.random())
        }
    }

    while (selectedMonsters.size < count) {
        val roll = Random.nextInt(100)
        val rarityTarget = when {
            roll < 8  -> "SSR"
            roll < 24 -> "SR"
            roll < 56 -> "R"
            else      -> "N"
        }
        val pool = effectivePool.filter { it.rarity == rarityTarget }.ifEmpty { effectivePool }
        selectedMonsters.add(pool.random())
    }

    return selectedMonsters.mapIndexed { i, monster ->
        val distMeters = Random.nextDouble(60.0, 480.0)
        val angle = Random.nextDouble(0.0, Math.PI * 2.0)
        val dLat = (distMeters * cos(angle)) / 111320.0
        val dLon = (distMeters * sin(angle)) / (111320.0 * cos(Math.toRadians(center.latitude)))
        FieldSpawn(
            spawnId = "${System.currentTimeMillis()}_$i",
            monster = monster,
            location = LatLng(center.latitude + dLat, center.longitude + dLon)
        )
    }
}

fun spawnNearbyCoins(center: LatLng, maxSlots: Int = 2, spawnChancePercent: Int = 15): List<CoinDrop> {
    val drops = mutableListOf<CoinDrop>()
    for (i in 0 until maxSlots) {
        if (Random.nextInt(100) >= spawnChancePercent) continue

        val distMeters = Random.nextDouble(50.0, 460.0)
        val angle = Random.nextDouble(0.0, Math.PI * 2.0)
        val dLat = (distMeters * cos(angle)) / 111320.0
        val dLon = (distMeters * sin(angle)) / (111320.0 * cos(Math.toRadians(center.latitude)))

        val amount = listOf(15, 20, 25, 30).random()

        drops.add(
            CoinDrop(
                dropId = "coin_${System.currentTimeMillis()}_$i",
                amount = amount,
                location = LatLng(center.latitude + dLat, center.longitude + dLon)
            )
        )
    }
    return drops
}

fun simulateQuickBattle(playerStats: PlayerStats, monster: BrainrotMonster): Boolean {
    var pHp = playerStats.maxHp
    var mHp = monster.maxHp
    while (pHp > 0 && mHp > 0) {
        val dmg = (playerStats.totalAtk * Random.nextDouble(0.85, 1.15)).roundToInt().coerceAtLeast(1)
        mHp -= dmg
        if (mHp <= 0) return true
        val eDmg = (monster.atk * Random.nextDouble(0.8, 1.2)).roundToInt().coerceAtLeast(1)
        pHp -= eDmg
        if (pHp > 0 && playerStats.companionRegen > 0) {
            pHp = (pHp + playerStats.companionRegen).coerceAtMost(playerStats.maxHp)
        }
    }
    return false
}
