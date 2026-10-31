package com.rachitratnammudaliar.renewly.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import java.io.File
import java.util.UUID

/** Custom subscription icons, kept in the app's private storage. */
object IconStore {

    private const val SIZE = 192

    private fun dir(context: Context): File = File(context.filesDir, "icons").apply { mkdirs() }

    fun file(context: Context, name: String): File? =
        if (name.isBlank()) null else File(dir(context), name).takeIf { it.exists() }

    /** Copies a picked image into private storage, scaled down. Returns the new file name. */
    fun save(context: Context, uri: Uri): String {
        val source: Bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                val longest = maxOf(info.size.width, info.size.height).coerceAtLeast(1)
                if (longest > SIZE * 2) {
                    val scale = (SIZE * 2).toFloat() / longest
                    decoder.setTargetSize(
                        (info.size.width * scale).toInt().coerceAtLeast(1),
                        (info.size.height * scale).toInt().coerceAtLeast(1),
                    )
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            context.contentResolver.openInputStream(uri).use { stream ->
                BitmapFactory.decodeStream(stream) ?: error("This image couldn't be read.")
            }
        }
        // Centre-crop to a square, then scale to SIZE.
        val side = minOf(source.width, source.height)
        val square = Bitmap.createBitmap(source, (source.width - side) / 2, (source.height - side) / 2, side, side)
        val scaled = Bitmap.createScaledBitmap(square, SIZE, SIZE, true)
        val name = "${UUID.randomUUID()}.png"
        File(dir(context), name).outputStream().use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return name
    }

    fun delete(context: Context, name: String) {
        if (name.isNotBlank()) File(dir(context), name).delete()
    }

    fun load(context: Context, name: String): Bitmap? =
        file(context, name)?.let { runCatching { BitmapFactory.decodeFile(it.absolutePath) }.getOrNull() }
}

/** How the "per month" total on the home screen is calculated. */
enum class TotalMode(val label: String, val description: String) {
    MONTHLY_ONLY("Monthly plans", "Only subscriptions billed every month"),
    ALL_AVERAGED("All, per month", "Every subscription, converted to a monthly average"),
    THIS_MONTH("Due this month", "What's actually charged in this calendar month"),
}

enum class SortMode(val label: String) {
    NEXT("Next payment"),
    PRICE("Price"),
    NAME("Name"),
}

/** Small on-device settings. */
class Prefs(context: Context) {
    private val prefs = context.getSharedPreferences("renewly", Context.MODE_PRIVATE)

    var totalMode: TotalMode
        get() = TotalMode.entries.firstOrNull { it.name == prefs.getString("total_mode", null) } ?: TotalMode.MONTHLY_ONLY
        set(value) = prefs.edit().putString("total_mode", value.name).apply()

    var sortMode: SortMode
        get() = SortMode.entries.firstOrNull { it.name == prefs.getString("sort_mode", null) } ?: SortMode.NEXT
        set(value) = prefs.edit().putString("sort_mode", value.name).apply()
}
