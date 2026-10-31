package com.rachitratnammudaliar.renewly.backup

import com.rachitratnammudaliar.renewly.data.Subscription
import com.rachitratnammudaliar.renewly.domain.BillingPeriod
import com.rachitratnammudaliar.renewly.domain.Category
import org.json.JSONArray
import org.json.JSONObject

/**
 * Plain JSON backups, so data can be moved to a new phone without any cloud.
 * Custom icons are not included; they stay on the old phone.
 */
object Backup {

    private const val FORMAT = "renewly-backup"
    private const val VERSION = 2

    fun encode(subscriptions: List<Subscription>): String {
        val items = JSONArray()
        subscriptions.forEach { s ->
            items.put(
                JSONObject()
                    .put("name", s.name)
                    .put("nickname", s.nickname)
                    .put("amountMinor", s.amountMinor)
                    .put("currency", s.currency)
                    .put("periodCount", s.periodCount)
                    .put("periodUnit", s.periodUnit)
                    .put("firstPaymentEpochDay", s.firstPaymentEpochDay)
                    .put("category", s.category)
                    .put("cardNickname", s.cardNickname)
                    .put("cardLast4", s.cardLast4)
                    .put("remindDaysBefore", s.remindDaysBefore)
                    .put("notes", s.notes)
                    .put("active", s.active)
                    .put("createdAt", s.createdAt),
            )
        }
        return JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("subscriptions", items)
            .toString(2)
    }

    /** Throws IllegalArgumentException with a readable message if the file isn't a Renewly backup. */
    fun decode(text: String): List<Subscription> {
        val root = try {
            JSONObject(text)
        } catch (e: Exception) {
            throw IllegalArgumentException("This file isn't a Renewly backup.")
        }
        if (root.optString("format") != FORMAT) {
            throw IllegalArgumentException("This file isn't a Renewly backup.")
        }
        val items = root.optJSONArray("subscriptions") ?: JSONArray()
        return (0 until items.length()).mapNotNull { i ->
            val o = items.optJSONObject(i) ?: return@mapNotNull null
            val name = o.optString("name").trim()
            val amount = o.optLong("amountMinor", 0L)
            if (name.isEmpty() || amount <= 0L || !o.has("firstPaymentEpochDay")) return@mapNotNull null

            val period = if (o.has("periodUnit")) {
                BillingPeriod.of(o.optInt("periodCount", 1), o.optString("periodUnit"))
            } else {
                BillingPeriod.fromLegacyCycle(o.optString("cycle"))
            }
            val last4 = o.optString("cardLast4").filter { it.isDigit() }.take(4)
            Subscription(
                name = name.take(60),
                nickname = o.optString("nickname").take(60),
                amountMinor = amount,
                currency = o.optString("currency", "INR").ifBlank { "INR" },
                periodCount = period.count,
                periodUnit = period.unit.name,
                firstPaymentEpochDay = o.getLong("firstPaymentEpochDay"),
                category = Category.fromName(o.optString("category")).name,
                cardNickname = o.optString("cardNickname").take(30),
                cardLast4 = if (last4.length == 4) last4 else "",
                remindDaysBefore = o.optInt("remindDaysBefore", 1).coerceIn(-1, 30),
                notes = o.optString("notes").take(500),
                active = o.optBoolean("active", true),
                createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            )
        }
    }
}
