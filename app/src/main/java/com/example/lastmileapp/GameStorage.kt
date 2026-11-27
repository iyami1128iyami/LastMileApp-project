package com.example.lastmileapp

import android.content.Context
import com.google.android.gms.maps.model.LatLng
import kotlin.math.roundToInt

class GameStorage(context: Context) {
    private val prefs = context.getSharedPreferences("brainwalk_rpg_prefs", Context.MODE_PRIVATE)

    fun loadStats(): PlayerStats {
        val ownedWeapons = prefs.getStringSet("owned_weapons", setOf("w_hand")) ?: setOf("w_hand")
        val caughtMonsters = prefs.getStringSet("caught_monsters", emptySet()) ?: emptySet()
        val upgrades = mutableMapOf<String, Int>()
        MasterData.weapons.forEach { w ->
            val bonus = prefs.getInt("upgrade_${w.id}", 0)
            if (bonus > 0) upgrades[w.id] = bonus
        }
        return PlayerStats(
            launchCount = prefs.getInt("launch_count", 0),
            totalDistanceMeters = prefs.getFloat("total_distance", 0f),
            totalSteps = prefs.getInt("total_steps", 0),
            xp = prefs.getInt("xp", 0),
            coins = prefs.getInt("coins", 50),
            equippedWeaponId = prefs.getString("equipped_weapon", "w_hand") ?: "w_hand",
            ownedWeaponIds = ownedWeapons,
            caughtMonsterIds = caughtMonsters,
            nextUncaughtRadar = prefs.getBoolean("next_uncaught_radar", false),
            nextRareIncense = prefs.getBoolean("next_rare_incense", false),
            nextHabitatFilter = prefs.getString("next_habitat_filter", null),
            potionCount = prefs.getInt("potion_count", 0),
            tabascoCount = prefs.getInt("tabasco_count", 0),
            stunCount = prefs.getInt("stun_count", 0),
            weaponUpgrades = upgrades,
            xpBoostBattlesLeft = prefs.getInt("xp_boost_battles_left", 0),
            skipCaughtBattle = prefs.getBoolean("skip_caught_battle", false),
            companionMonsterId = prefs.getString("companion_monster_id", null)
        )
    }

    fun recordLaunch(): PlayerStats {
        val current = loadStats()
        val updated = current.copy(
            launchCount = current.launchCount + 1,
            coins = current.coins + 10
        )
        saveStats(updated)
        return updated
    }

    fun addDistance(meters: Float): PlayerStats {
        val current = loadStats()
        val gainedXp = (meters / 5f).toInt().coerceAtLeast(1)
        val gainedCoins = (meters / 10f).toInt()
        val updated = current.copy(
            totalDistanceMeters = current.totalDistanceMeters + meters,
            xp = current.xp + gainedXp,
            coins = current.coins + gainedCoins
        )
        saveStats(updated)
        return updated
    }

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
        val oldUnits = current.totalSteps / 10
        val newUnits = newTotalSteps / 10
        val gained = (newUnits - oldUnits).coerceAtLeast(0)

        val updated = current.copy(
            totalSteps = newTotalSteps,
            xp = current.xp + gained * 3,
            coins = current.coins + gained
        )
        saveStats(updated)
        return updated
    }

    fun addCoins(amount: Int): PlayerStats {
        val current = loadStats()
        val updated = current.copy(coins = current.coins + amount)
        saveStats(updated)
        return updated
    }

    fun addActionBonus(): PlayerStats = addCoins(15)

    fun pullGacha(): Pair<PlayerStats, Weapon>? {
        val current = loadStats()
        if (current.coins < GACHA_COST) return null
        val weapon = rollWeaponGacha()
        val newOwned = current.ownedWeaponIds + weapon.id
        val currentWeaponTotal = current.equippedWeapon.atkBonus + current.equippedWeaponUpgrade
        val newWeaponTotal = weapon.atkBonus + (current.weaponUpgrades[weapon.id] ?: 0)
        val newEquipped = if (newWeaponTotal > currentWeaponTotal) {
            weapon.id
        } else {
            current.equippedWeaponId
        }
        val updated = current.copy(
            coins = current.coins - GACHA_COST,
            ownedWeaponIds = newOwned,
            equippedWeaponId = newEquipped
        )
        saveStats(updated)
        return Pair(updated, weapon)
    }

    fun equipWeapon(weaponId: String): PlayerStats {
        val current = loadStats()
        if (!current.ownedWeaponIds.contains(weaponId)) return current
        val updated = current.copy(equippedWeaponId = weaponId)
        saveStats(updated)
        return updated
    }

    fun toggleSkipCaughtBattle(): PlayerStats {
        val current = loadStats()
        val updated = current.copy(skipCaughtBattle = !current.skipCaughtBattle)
        saveStats(updated)
        return updated
    }

    fun setCompanion(monsterId: String?): PlayerStats {
        val current = loadStats()
        if (monsterId != null && !current.caughtMonsterIds.contains(monsterId)) return current
        val updated = current.copy(companionMonsterId = monsterId)
        saveStats(updated)
        return updated
    }

    fun buyShopItem(item: ShopItem): Pair<PlayerStats, String>? {
        val current = loadStats()
        if (current.coins < item.price) return null

        val updatedAndMsg: Pair<PlayerStats, String> = when (item.id) {
            "item_radar" -> {
                if (current.nextUncaughtRadar) return null
                current.copy(coins = current.coins - item.price, nextUncaughtRadar = true) to
                    "🧭 未取得レーダーをセット！次の「🔄 探索」で未登録キャラが確定出現します"
            }
            "item_incense" -> {
                if (current.nextRareIncense) return null
                current.copy(coins = current.coins - item.price, nextRareIncense = true) to
                    "✨ レア呼び寄せおこうをセット！次の「🔄 探索」でSR/SSRが確定出現します"
            }
            "item_city" -> {
                current.copy(coins = current.coins - item.price, nextHabitatFilter = "街") to
                    "🏙️ 街の招待状をセット！次の「🔄 探索」は街タイプのみ出現します"
            }
            "item_jungle" -> {
                current.copy(coins = current.coins - item.price, nextHabitatFilter = "ジャングル") to
                    "🌴 ジャングル笛をセット！次の「🔄 探索」はジャングルタイプのみ出現します"
            }
            "item_potion" -> {
                current.copy(coins = current.coins - item.price, potionCount = current.potionCount + 1) to
                    "☕ カプチーノ回復薬を購入！（所持: ${current.potionCount + 1}個）"
            }
            "item_tabasco" -> {
                current.copy(coins = current.coins - item.price, tabascoCount = current.tabascoCount + 1) to
                    "🌶️ 激辛タバスコを購入！（所持: ${current.tabascoCount + 1}個）"
            }
            "item_stun" -> {
                current.copy(coins = current.coins - item.price, stunCount = current.stunCount + 1) to
                    "⚡ スタン・ホットスポットを購入！（所持: ${current.stunCount + 1}個）"
            }
            "item_stone" -> {
                val wId = current.equippedWeaponId
                val newMap = current.weaponUpgrades.toMutableMap()
                val newBonus = (newMap[wId] ?: 0) + 5
                newMap[wId] = newBonus
                current.copy(coins = current.coins - item.price, weaponUpgrades = newMap) to
                    "🔨 ${current.equippedWeapon.name} を強化！（強化ボーナス +$newBonus）"
            }
            "item_oil" -> {
                val nextCount = current.xpBoostBattlesLeft + 3
                current.copy(coins = current.coins - item.price, xpBoostBattlesLeft = nextCount) to
                    "👟 爆速スニーカーオイルを使用！あと${nextCount}回のバトルで獲得XPが2倍！"
            }
            else -> return null
        }

        saveStats(updatedAndMsg.first)
        return updatedAndMsg
    }

    fun consumeExplorationBuffs(): PlayerStats {
        val current = loadStats()
        if (!current.nextUncaughtRadar && !current.nextRareIncense && current.nextHabitatFilter == null) {
            return current
        }
        val updated = current.copy(
            nextUncaughtRadar = false,
            nextRareIncense = false,
            nextHabitatFilter = null
        )
        saveStats(updated)
        return updated
    }

    fun consumeBattleItem(itemType: String): PlayerStats {
        val current = loadStats()
        val updated = when (itemType) {
            "potion" -> if (current.potionCount > 0) current.copy(potionCount = current.potionCount - 1) else current
            "tabasco" -> if (current.tabascoCount > 0) current.copy(tabascoCount = current.tabascoCount - 1) else current
            "stun" -> if (current.stunCount > 0) current.copy(stunCount = current.stunCount - 1) else current
            else -> current
        }
        saveStats(updated)
        return updated
    }

    fun recordMonsterCatch(monster: BrainrotMonster): Triple<PlayerStats, Int, Int> {
        val current = loadStats()
        val isBoosted = current.xpBoostBattlesLeft > 0
        val coinMult = 1.0 + (current.companionCoinPercent / 100.0)
        val xpMult = (1.0 + (current.companionXpPercent / 100.0)) * (if (isBoosted) 2.0 else 1.0)
        val gainedCoins = (monster.rewardCoins * coinMult).roundToInt()
        val gainedXp = (monster.rewardXp * xpMult).roundToInt()
        val nextBoostLeft = if (isBoosted) current.xpBoostBattlesLeft - 1 else 0

        val updated = current.copy(
            coins = current.coins + gainedCoins,
            xp = current.xp + gainedXp,
            caughtMonsterIds = current.caughtMonsterIds + monster.id,
            xpBoostBattlesLeft = nextBoostLeft
        )
        saveStats(updated)
        return Triple(updated, gainedCoins, gainedXp)
    }

    fun getLastSavedLocation(): LatLng? {
        if (!prefs.contains("last_lat")) return null
        val lat = prefs.getFloat("last_lat", 0f).toDouble()
        val lon = prefs.getFloat("last_lon", 0f).toDouble()
        return LatLng(lat, lon)
    }

    fun saveLastLocation(point: LatLng) {
        prefs.edit()
            .putFloat("last_lat", point.latitude.toFloat())
            .putFloat("last_lon", point.longitude.toFloat())
            .apply()
    }

    private fun saveStats(stats: PlayerStats) {
        val editor = prefs.edit()
            .putInt("launch_count", stats.launchCount)
            .putFloat("total_distance", stats.totalDistanceMeters)
            .putInt("total_steps", stats.totalSteps)
            .putInt("xp", stats.xp)
            .putInt("coins", stats.coins)
            .putString("equipped_weapon", stats.equippedWeaponId)
            .putStringSet("owned_weapons", stats.ownedWeaponIds)
            .putStringSet("caught_monsters", stats.caughtMonsterIds)
            .putBoolean("next_uncaught_radar", stats.nextUncaughtRadar)
            .putBoolean("next_rare_incense", stats.nextRareIncense)
            .putString("next_habitat_filter", stats.nextHabitatFilter)
            .putInt("potion_count", stats.potionCount)
            .putInt("tabasco_count", stats.tabascoCount)
            .putInt("stun_count", stats.stunCount)
            .putInt("xp_boost_battles_left", stats.xpBoostBattlesLeft)
            .putBoolean("skip_caught_battle", stats.skipCaughtBattle)
            .putString("companion_monster_id", stats.companionMonsterId)

        stats.weaponUpgrades.forEach { (wId, bonus) ->
            editor.putInt("upgrade_$wId", bonus)
        }
        editor.apply()
    }
}
