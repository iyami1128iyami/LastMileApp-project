package com.example.lastmileapp

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.random.Random

@Composable
fun ItemShopDialog(
    stats: PlayerStats,
    onBuyItem: (ShopItem) -> String?,
    onDismiss: () -> Unit
) {
    var selectedCategory by remember { mutableStateOf("すべて") }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }

    val filteredItems = remember(selectedCategory) {
        if (selectedCategory == "すべて") MasterData.shopItems else MasterData.shopItems.filter { it.category == selectedCategory }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🛒 アイテムショップ", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("🪙 ${stats.coins}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD97706))
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("すべて", "探索", "バトル", "育成").forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat, fontSize = 11.sp) }
                        )
                    }
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                feedbackMessage?.let { msg ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7ED)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(msg, fontSize = 11.sp, color = Color(0xFFB45309), modifier = Modifier.padding(8.dp))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                LazyColumn(
                    modifier = Modifier.heightIn(max = 340.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredItems) { item ->
                        val isAlreadyActive = when (item.id) {
                            "item_radar" -> stats.nextUncaughtRadar
                            "item_incense" -> stats.nextRareIncense
                            "item_city" -> stats.nextHabitatFilter == "街"
                            "item_jungle" -> stats.nextHabitatFilter == "ジャングル"
                            else -> false
                        }
                        val statusBadge = when (item.id) {
                            "item_radar" -> if (stats.nextUncaughtRadar) "セット済" else null
                            "item_incense" -> if (stats.nextRareIncense) "セット済" else null
                            "item_city" -> if (stats.nextHabitatFilter == "街") "セット済" else null
                            "item_jungle" -> if (stats.nextHabitatFilter == "ジャングル") "セット済" else null
                            "item_potion" -> "所持: ${stats.potionCount}"
                            "item_tabasco" -> "所持: ${stats.tabascoCount}"
                            "item_stun" -> "所持: ${stats.stunCount}"
                            "item_stone" -> "現在 +${stats.equippedWeaponUpgrade}"
                            "item_oil" -> if (stats.xpBoostBattlesLeft > 0) "残${stats.xpBoostBattlesLeft}戦" else null
                            else -> null
                        }
                        val canBuy = stats.coins >= item.price && !isAlreadyActive

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color.LightGray, RoundedCornerShape(10.dp))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(item.emoji, fontSize = 26.sp, modifier = Modifier.padding(end = 8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(item.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    if (statusBadge != null) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            statusBadge,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                Text(item.desc, fontSize = 10.sp, color = Color.Gray)
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Button(
                                onClick = { feedbackMessage = onBuyItem(item) },
                                enabled = canBuy,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("🪙${item.price}", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("閉じる") }
        }
    )
}

@Composable
fun WeaponGachaDialog(
    stats: PlayerStats,
    onPullGacha: () -> Weapon?,
    onEquipWeapon: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var lastPulled by remember { mutableStateOf<Weapon?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("⚔️ ブレインロット武器ガチャ", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("🪙 ${stats.coins}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD97706))
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { lastPulled = onPullGacha() },
                    enabled = stats.coins >= GACHA_COST,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("ガチャを引く (🪙 $GACHA_COST)")
                }

                lastPulled?.let { w ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7ED)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.5.dp, rarityColorCompose(w.rarity), RoundedCornerShape(12.dp))
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                "🎉 ガチャ結果！ [${w.rarity}]",
                                fontSize = 12.sp,
                                color = rarityColorCompose(w.rarity),
                                fontWeight = FontWeight.Bold
                            )
                            Text("${w.emoji} ${w.name} (ATK +${w.atkBonus})", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(w.desc, fontSize = 11.sp, color = Color(0xFF4B5563))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text("所持している武器（タップで装備）", fontSize = 12.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(6.dp))

                val ownedList = MasterData.weapons.filter { stats.ownedWeaponIds.contains(it.id) }
                    .sortedByDescending { it.atkBonus + (stats.weaponUpgrades[it.id] ?: 0) }

                LazyColumn(
                    modifier = Modifier.heightIn(max = 220.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(ownedList) { w ->
                        val isEquipped = stats.equippedWeaponId == w.id
                        val upg = stats.weaponUpgrades[w.id] ?: 0
                        val totalWAtk = w.atkBonus + upg
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    width = if (isEquipped) 2.dp else 1.dp,
                                    color = if (isEquipped) MaterialTheme.colorScheme.primary else Color.LightGray,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { onEquipWeapon(w.id) }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(w.emoji, fontSize = 22.sp, modifier = Modifier.padding(end = 8.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "[${w.rarity}] ",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = rarityColorCompose(w.rarity)
                                        )
                                        Text(
                                            if (upg > 0) "${w.name} (+$upg)" else w.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    }
                                    Text("ATK +$totalWAtk｜${w.desc}", fontSize = 10.sp, color = Color.Gray)
                                }
                            }
                            if (isEquipped) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("装備中", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("閉じる") }
        }
    )
}

@Composable
fun BrainrotDexDialog(
    caughtIds: Set<String>,
    companionId: String?,
    onSetCompanion: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedFilter by remember { mutableStateOf("すべて") }
    var selectedMonster by remember { mutableStateOf<BrainrotMonster?>(null) }

    val filteredList = remember(selectedFilter) {
        when (selectedFilter) {
            "街" -> MasterData.brainrots.filter { it.habitat == "街" }
            "ジャングル" -> MasterData.brainrots.filter { it.habitat == "ジャングル" }
            else -> MasterData.brainrots
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    "📖 ブレインロット図鑑 (${caughtIds.size}/${MasterData.brainrots.size})",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("すべて", "街", "ジャングル").forEach { tab ->
                        FilterChip(
                            selected = selectedFilter == tab,
                            onClick = { selectedFilter = tab },
                            label = { Text(tab, fontSize = 11.sp) }
                        )
                    }
                }
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredList) { m ->
                    val isCaught = caughtIds.contains(m.id)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.dp,
                                color = if (isCaught) rarityColorCompose(m.rarity) else Color.LightGray,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable(enabled = isCaught) { selectedMonster = m }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isCaught) {
                            MonsterAvatar(monster = m, size = 48.dp, borderWidth = 2.dp, emojiFontSize = 24.sp)
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFE5E7EB))
                                    .border(2.dp, Color.LightGray, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "?",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Gray
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                val isComp = companionId == m.id
                                Text(
                                    text = if (isCaught) "${if (isComp) "⭐" else ""}No.${m.no} ${m.name}" else "No.${m.no} ？？？",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "[${m.rarity}]",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = rarityColorCompose(m.rarity)
                                )
                            }
                            if (isCaught) {
                                Text(
                                    text = "生息: ${m.habitat} / モチーフ: ${m.motif}",
                                    fontSize = 11.sp,
                                    color = Color(0xFF4B5563)
                                )
                                Text(
                                    text = "スキル: ${m.skillName}（${companionSkillDesc(m)}）",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Text(
                                    text = "生息: ${m.habitat}（${m.spawnPlace}）",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("閉じる") }
        }
    )

    selectedMonster?.let { m ->
        AlertDialog(
            onDismissRequest = { selectedMonster = null },
            title = {
                Text("No.${m.no} ${m.name}", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    MonsterAvatar(monster = m, size = 88.dp, borderWidth = 3.dp, emojiFontSize = 44.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(m.englishName, fontSize = 12.sp, color = Color.Gray, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(10.dp))

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("レア度: ${m.rarity}（HP:${m.maxHp} / ATK:${m.atk}）", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("生息エリア: ${m.habitat}タイプ", fontSize = 12.sp)
                            Text("モチーフ: ${m.motif}", fontSize = 12.sp)
                            Text("出現場所: ${m.spawnPlace}", fontSize = 12.sp)
                            Text("お供スキル: ${m.skillName}（${companionSkillDesc(m)}）", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB45309))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(m.description, fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                val isCurrentComp = companionId == m.id
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            onSetCompanion(if (isCurrentComp) null else m.id)
                            selectedMonster = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isCurrentComp) Color.Gray else Color(0xFFD97706)
                        )
                    ) {
                        Text(if (isCurrentComp) "お供を外す" else "⭐ お供にする")
                    }
                    TextButton(onClick = { selectedMonster = null }) { Text("戻る") }
                }
            }
        )
    }
}

@Composable
fun BattleDialog(
    spawn: FieldSpawn,
    playerStats: PlayerStats,
    onConsumeItem: (String) -> Unit,
    onWin: () -> Unit,
    onEscapeOrLose: () -> Unit
) {
    val monster = spawn.monster
    var playerHp by remember { mutableStateOf(playerStats.maxHp) }
    var monsterHp by remember { mutableStateOf(monster.maxHp) }
    var isAtkBoosted by remember { mutableStateOf(false) }
    var isEnemyStunned by remember { mutableStateOf(false) }
    var battleLog by remember { mutableStateOf("野生の ${monster.name} があらわれた！") }
    var battleState by remember { mutableStateOf("FIGHTING") }
    var isAutoBattle by remember { mutableStateOf(false) }
    val isAlreadyCaught = playerStats.caughtMonsterIds.contains(monster.id)

    fun performTurn(isSpecial: Boolean) {
        if (battleState != "FIGHTING") return

        val hit = if (isSpecial) Random.nextInt(100) < 70 else true
        val dmg = if (!hit) {
            0
        } else {
            val base = playerStats.totalAtk * (if (isAtkBoosted) 1.5 else 1.0)
            val mult = if (isSpecial) 1.7 else 1.0
            ((base * mult) * Random.nextDouble(0.85, 1.15)).roundToInt().coerceAtLeast(1)
        }

        val nextMonsterHp = (monsterHp - dmg).coerceAtLeast(0)
        monsterHp = nextMonsterHp

        if (nextMonsterHp <= 0) {
            val coinPreview = (monster.rewardCoins * (1.0 + playerStats.companionCoinPercent / 100.0)).roundToInt()
            val xpBase = (monster.rewardXp * (1.0 + playerStats.companionXpPercent / 100.0)).roundToInt()
            val xpPreview = if (playerStats.xpBoostBattlesLeft > 0) "${xpBase * 2}XP (2倍!)" else "${xpBase}XP"
            battleLog = "${playerStats.equippedWeapon.name} で ${dmg} ダメージ！\n勝利！ ${monster.name} をゲットした！\n(+${coinPreview}🪙 / +$xpPreview)"
            battleState = "WIN"
            return
        }

        val attackText = if (hit) "${playerStats.equippedWeapon.name} で ${dmg} ダメージ！" else "ひっさつわざが外れた！"

        if (isEnemyStunned) {
            isEnemyStunned = false
            battleLog = "$attackText\n⚡ ${monster.name} はしびれて動けない！"
            return
        }

        val enemyDmg = (monster.atk * Random.nextDouble(0.8, 1.2)).roundToInt().coerceAtLeast(1)
        val nextPlayerHp = (playerHp - enemyDmg).coerceAtLeast(0)
        playerHp = nextPlayerHp

        if (nextPlayerHp <= 0) {
            battleLog = "$attackText\n${monster.name} の反撃で ${enemyDmg} ダメージ…\n負けてしまった！武器を強くして再挑戦しよう。"
            battleState = "LOSE"
        } else if (playerStats.companionRegen > 0 && nextPlayerHp < playerStats.maxHp) {
            val healedHp = (nextPlayerHp + playerStats.companionRegen).coerceAtMost(playerStats.maxHp)
            playerHp = healedHp
            val compEmoji = playerStats.companionMonster?.emoji ?: "⭐"
            battleLog = "$attackText\n${monster.name} の反撃！ ${enemyDmg} ダメージ！\n$compEmoji お供のスキルで HP +${playerStats.companionRegen} 回復！"
        } else {
            battleLog = "$attackText\n${monster.name} の反撃！ ${enemyDmg} ダメージを受けた！"
        }
    }

    LaunchedEffect(isAutoBattle, battleState) {
        while (isAutoBattle && battleState == "FIGHTING") {
            delay(450L)
            if (battleState == "FIGHTING") {
                performTurn(isSpecial = false)
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (battleState != "WIN") onEscapeOrLose() else onWin() },
        title = {
            Text("⚔️ [${monster.rarity}] ${monster.name}", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                MonsterAvatar(monster = monster, size = 84.dp, borderWidth = 3.dp, emojiFontSize = 42.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text("${monster.habitat}タイプ / ${monster.motif}", fontSize = 11.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        if (isEnemyStunned) "${monster.name} ⚡" else monster.name,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text("HP: $monsterHp / ${monster.maxHp}", fontSize = 12.sp)
                }
                LinearProgressIndicator(
                    progress = { monsterHp.toFloat() / monster.maxHp.toFloat() },
                    color = Color(0xFFEF4444),
                    modifier = Modifier.fillMaxWidth().height(8.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    val buffTag = if (isAtkBoosted) " 🌶️1.5x" else ""
                    Text("自分 (Lv.${playerStats.level} ${playerStats.equippedWeapon.emoji}$buffTag)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("HP: $playerHp / ${playerStats.maxHp}", fontSize = 12.sp)
                }
                LinearProgressIndicator(
                    progress = { playerHp.toFloat() / playerStats.maxHp.toFloat() },
                    color = Color(0xFF22C55E),
                    modifier = Modifier.fillMaxWidth().height(8.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = battleLog,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (battleState == "FIGHTING") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = { performTurn(isSpecial = false) },
                            enabled = !isAutoBattle,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                        ) {
                            Text("こうげき", fontSize = 12.sp)
                        }
                        Button(
                            onClick = { performTurn(isSpecial = true) },
                            enabled = !isAutoBattle,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                        ) {
                            Text("ひっさつ", fontSize = 12.sp)
                        }
                        Button(
                            onClick = { isAutoBattle = !isAutoBattle },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isAutoBattle) Color(0xFF16A34A) else Color(0xFF4B5563)
                            ),
                            modifier = Modifier.weight(0.9f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                        ) {
                            Text(if (isAutoBattle) "🤖ON" else "🤖オート", fontSize = 11.sp)
                        }
                    }

                    if (isAlreadyCaught) {
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedButton(
                            onClick = {
                                isAutoBattle = false
                                while (battleState == "FIGHTING") {
                                    performTurn(isSpecial = false)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            Text("⚡ 取得済みのため戦闘をスキップ", fontSize = 11.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                onConsumeItem("potion")
                                playerHp = playerStats.maxHp
                                battleLog = "☕ カプチーノ回復薬を飲んだ！HPが全回復！"
                            },
                            enabled = playerStats.potionCount > 0 && playerHp < playerStats.maxHp,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
                        ) {
                            Text("☕回復(${playerStats.potionCount})", fontSize = 10.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                onConsumeItem("tabasco")
                                isAtkBoosted = true
                                battleLog = "🌶️ 激辛タバスコを使用！この戦闘中ずっと攻撃力1.5倍！"
                            },
                            enabled = playerStats.tabascoCount > 0 && !isAtkBoosted,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
                        ) {
                            Text("🌶️強化(${playerStats.tabascoCount})", fontSize = 10.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                onConsumeItem("stun")
                                isEnemyStunned = true
                                battleLog = "⚡ スタン・ホットスポット発動！次の敵の反撃を封じる！"
                            },
                            enabled = playerStats.stunCount > 0 && !isEnemyStunned,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
                        ) {
                            Text("⚡スタン(${playerStats.stunCount})", fontSize = 10.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            when (battleState) {
                "WIN" -> Button(onClick = onWin) { Text("ゲットして図鑑へ！") }
                "LOSE" -> Button(onClick = onEscapeOrLose) { Text("撤退する") }
                else -> TextButton(onClick = onEscapeOrLose) { Text("にげる") }
            }
        }
    )
}
