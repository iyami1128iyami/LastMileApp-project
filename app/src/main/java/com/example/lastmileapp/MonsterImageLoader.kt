package com.example.lastmileapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * assets/images/ に置かれたブレインロット画像を自動で読み込み、
 * 未配置のキャラは絵文字へ自動フォールバックするローダー。
 *
 * 例: app/src/main/assets/images/br_01.png (jpg / jpeg / webp も対応)
 */
object MonsterImageLoader {
    private val cache = mutableMapOf<String, Bitmap?>()

    fun getBitmap(context: Context, monster: BrainrotMonster): Bitmap? {
        val key = "${monster.id}|${monster.image}"
        if (cache.containsKey(key)) return cache[key]

        val candidates = mutableListOf<String>()
        if (monster.image.isNotBlank()) {
            candidates.add("images/${monster.image}")
            candidates.add(monster.image)
        }
        candidates.add("images/${monster.id}.png")
        candidates.add("images/${monster.id}.jpg")
        candidates.add("images/${monster.id}.jpeg")
        candidates.add("images/${monster.id}.webp")

        var decoded: Bitmap? = null
        for (path in candidates) {
            try {
                context.assets.open(path).use { stream ->
                    decoded = BitmapFactory.decodeStream(stream)
                }
                if (decoded != null) break
            } catch (_: Exception) {
                // 画像が存在しない場合は次の拡張子候補へ
            }
        }
        cache[key] = decoded
        return decoded
    }
}

@Composable
fun MonsterAvatar(
    monster: BrainrotMonster,
    size: Dp,
    borderWidth: Dp = 2.dp,
    emojiFontSize: TextUnit = (size.value * 0.5f).sp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bitmap = remember(monster.id, monster.image) {
        MonsterImageLoader.getBitmap(context, monster)
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(0xFFF9FAFB))
            .border(borderWidth, rarityColorCompose(monster.rarity), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = monster.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
            )
        } else {
            Text(
                text = monster.emoji,
                fontSize = emojiFontSize
            )
        }
    }
}
