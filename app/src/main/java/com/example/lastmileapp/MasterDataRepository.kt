package com.example.lastmileapp

import android.content.Context
import org.json.JSONArray

/**
 * assets/ 配下の JSON リソースからマスターデータを読み込むリポジトリ。
 * ブレインロットや武器を追加する場合は assets/brainrots.json 等を編集するだけで
 * Kotlin ソースコードの修正なしでアプリに反映されます。
 */
object MasterData {
    val defaultWeapon = Weapon("w_hand", "すで", "N", 0, "✊", "初期装備")

    var brainrots: List<BrainrotMonster> = emptyList()
        private set

    var weapons: List<Weapon> = listOf(defaultWeapon)
        private set

    var shopItems: List<ShopItem> = emptyList()
        private set

    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        brainrots = loadBrainrots(context)
        weapons = loadWeapons(context).ifEmpty { listOf(defaultWeapon) }
        shopItems = loadShopItems(context)
        initialized = true
    }

    private fun readAssetText(context: Context, fileName: String): String {
        return context.assets.open(fileName).bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private fun loadBrainrots(context: Context): List<BrainrotMonster> {
        return try {
            val arr = JSONArray(readAssetText(context, "brainrots.json"))
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                BrainrotMonster(
                    id = o.getString("id"),
                    no = o.getInt("no"),
                    name = o.getString("name"),
                    englishName = o.optString("englishName", ""),
                    habitat = o.optString("habitat", "街"),
                    motif = o.optString("motif", ""),
                    spawnPlace = o.optString("spawnPlace", ""),
                    description = o.optString("description", ""),
                    emoji = o.optString("emoji", "👾"),
                    rarity = o.optString("rarity", "N"),
                    maxHp = o.optInt("maxHp", 50),
                    atk = o.optInt("atk", 10),
                    rewardCoins = o.optInt("rewardCoins", 25),
                    rewardXp = o.optInt("rewardXp", 25),
                    skillType = o.optString("skillType", "ATK"),
                    skillName = o.optString("skillName", "ブレインパワー"),
                    image = o.optString("image", "")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun loadWeapons(context: Context): List<Weapon> {
        return try {
            val arr = JSONArray(readAssetText(context, "weapons.json"))
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                Weapon(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    rarity = o.optString("rarity", "N"),
                    atkBonus = o.optInt("atkBonus", 0),
                    emoji = o.optString("emoji", "⚔️"),
                    desc = o.optString("desc", "")
                )
            }
        } catch (e: Exception) {
            listOf(defaultWeapon)
        }
    }

    private fun loadShopItems(context: Context): List<ShopItem> {
        return try {
            val arr = JSONArray(readAssetText(context, "shop_items.json"))
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                ShopItem(
                    id = o.getString("id"),
                    emoji = o.optString("emoji", "🎁"),
                    name = o.getString("name"),
                    price = o.optInt("price", 50),
                    category = o.optString("category", "探索"),
                    desc = o.optString("desc", "")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
