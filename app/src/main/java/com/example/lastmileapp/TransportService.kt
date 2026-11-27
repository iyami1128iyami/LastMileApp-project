package com.example.lastmileapp

import android.content.Context
import android.content.Intent
import android.location.Location
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import com.google.android.gms.maps.model.LatLng
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.ceil
import kotlin.math.roundToInt

fun distanceBetween(a: LatLng, b: LatLng): Float {
    val result = FloatArray(1)
    Location.distanceBetween(a.latitude, a.longitude, b.latitude, b.longitude, result)
    return result[0]
}

fun buildTransportOptions(
    start: LatLng,
    end: LatLng,
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
