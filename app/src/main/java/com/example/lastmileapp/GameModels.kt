package com.example.lastmileapp

import androidx.compose.ui.graphics.Color
import com.google.android.gms.maps.model.LatLng

const val GACHA_COST = 50

// --- 交通データモデル ---
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

// --- RPGゲームデータモデル ---
data class Weapon(
    val id: String,
    val name: String,
    val rarity: String, // N, R, SR, SSR
    val atkBonus: Int,
    val emoji: String,
    val desc: String
)

data class BrainrotMonster(
    val id: String,
    val no: Int,
    val name: String,
    val englishName: String,
    val habitat: String,       // "街" または "ジャングル"
    val motif: String,         // モチーフ
    val spawnPlace: String,    // 出現場所
    val description: String,   // 特徴・解説
    val emoji: String,         // マップ・図鑑用アイコン絵文字
    val rarity: String,        // N, R, SR, SSR
    val maxHp: Int,
    val atk: Int,
    val rewardCoins: Int,
    val rewardXp: Int,
    val skillType: String,     // "ATK", "HP", "COIN", "XP", "REGEN"
    val skillName: String,
    val image: String = ""     // 任意: assets/images/ 内のファイル名（空なら <id>.png 等を自動検索）
)

data class FieldSpawn(
    val spawnId: String,
    val monster: BrainrotMonster,
    val location: LatLng
)

data class CoinDrop(
    val dropId: String,
    val amount: Int,
    val location: LatLng
)

data class ShopItem(
    val id: String,
    val emoji: String,
    val name: String,
    val price: Int,
    val category: String, // "探索", "バトル", "育成"
    val desc: String
)

data class PlayerStats(
    val launchCount: Int,
    val totalDistanceMeters: Float,
    val totalSteps: Int,
    val xp: Int,
    val coins: Int,
    val equippedWeaponId: String,
    val ownedWeaponIds: Set<String>,
    val caughtMonsterIds: Set<String>,
    val nextUncaughtRadar: Boolean,
    val nextRareIncense: Boolean,
    val nextHabitatFilter: String?,
    val potionCount: Int,
    val tabascoCount: Int,
    val stunCount: Int,
    val weaponUpgrades: Map<String, Int>,
    val xpBoostBattlesLeft: Int,
    val skipCaughtBattle: Boolean,
    val companionMonsterId: String?
) {
    val level: Int get() = (xp / 100) + 1
    val currentLevelXp: Int get() = xp % 100
    val companionMonster: BrainrotMonster? get() = MasterData.brainrots.find { it.id == companionMonsterId }
    val companionHpBonus: Int get() = companionMonster?.let { if (it.skillType == "HP") companionSkillValue(it.rarity, "HP") else 0 } ?: 0
    val companionAtkBonus: Int get() = companionMonster?.let { if (it.skillType == "ATK") companionSkillValue(it.rarity, "ATK") else 0 } ?: 0
    val companionRegen: Int get() = companionMonster?.let { if (it.skillType == "REGEN") companionSkillValue(it.rarity, "REGEN") else 0 } ?: 0
    val companionCoinPercent: Int get() = companionMonster?.let { if (it.skillType == "COIN") companionSkillValue(it.rarity, "COIN") else 0 } ?: 0
    val companionXpPercent: Int get() = companionMonster?.let { if (it.skillType == "XP") companionSkillValue(it.rarity, "XP") else 0 } ?: 0
    val maxHp: Int get() = 80 + (level - 1) * 15 + companionHpBonus
    val baseAtk: Int get() = 12 + (level - 1) * 4
    val equippedWeapon: Weapon get() = MasterData.weapons.find { it.id == equippedWeaponId } ?: MasterData.defaultWeapon
    val equippedWeaponUpgrade: Int get() = weaponUpgrades[equippedWeaponId] ?: 0
    val totalAtk: Int get() = baseAtk + equippedWeapon.atkBonus + equippedWeaponUpgrade + companionAtkBonus
}

// --- ユーティリティ ---
fun companionSkillValue(rarity: String, skillType: String): Int = when (skillType) {
    "ATK" -> when (rarity) {
        "SSR" -> 45
        "SR"  -> 25
        "R"   -> 12
        else  -> 5
    }
    "HP" -> when (rarity) {
        "SSR" -> 120
        "SR"  -> 70
        "R"   -> 35
        else  -> 15
    }
    "COIN", "XP" -> when (rarity) {
        "SSR" -> 80
        "SR"  -> 50
        "R"   -> 30
        else  -> 15
    }
    "REGEN" -> when (rarity) {
        "SSR" -> 42
        "SR"  -> 24
        "R"   -> 12
        else  -> 5
    }
    else -> 0
}

fun companionSkillDesc(m: BrainrotMonster): String {
    val v = companionSkillValue(m.rarity, m.skillType)
    return when (m.skillType) {
        "ATK"   -> "攻撃力 +$v"
        "HP"    -> "最大HP +$v"
        "COIN"  -> "バトル報酬コイン +${v}%"
        "XP"    -> "バトル報酬XP +${v}%"
        "REGEN" -> "毎ターンHP +$v 回復"
        else    -> ""
    }
}

fun rarityColorInt(rarity: String): Int = when (rarity) {
    "SSR" -> android.graphics.Color.parseColor("#F59E0B")
    "SR"  -> android.graphics.Color.parseColor("#A855F7")
    "R"   -> android.graphics.Color.parseColor("#3B82F6")
    else  -> android.graphics.Color.parseColor("#10B981")
}

fun rarityColorCompose(rarity: String): Color = when (rarity) {
    "SSR" -> Color(0xFFD97706)
    "SR"  -> Color(0xFF9333EA)
    "R"   -> Color(0xFF2563EB)
    else  -> Color(0xFF059669)
}
