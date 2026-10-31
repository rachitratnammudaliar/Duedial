package com.rachitratnammudaliar.renewly.backup

import com.rachitratnammudaliar.renewly.data.Subscription
import com.rachitratnammudaliar.renewly.domain.BillingCycle
import org.json.JSONArray
import org.json.JSONObject

/** Plain JSON backups, so data can be moved to a new phone without any cloud. */
object Backup {

    private const val FORMAT = "renewly-backup"
    private const val VERSION = 1

    fun encode(subscriptions: List<Subscription>): String {
        val items = JSONArray()
        subscriptions.forEach { s ->
            items.put(
                JSONObject()
                    .put("name", s.name)
                    .put("nickname", s.nickname)
                    .put("amountMinor", s.amountMinor)
                    .put("currency", s.currency)
                    .put("cycle", s.cycle)
                    .put("firstPaymentEpochDay", s.firstPaymentEpochDay)
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
        return (0 until items.length()).map { i ->
            val o = items.getJSONObject(i)
            val last4 = o.optString("cardLast4").filter { it.isDigit() }.take(4)
            Subscription(
                name = o.getString("name"),
                nickname = o.optString("nickname"),
                amountMinor = o.getLong("amountMinor"),
                currency = o.optString("currency", "INR"),
                cycle = BillingCycle.fromName(o.optString("cycle")).name,
                firstPaymentEpochDay = o.getLong("firstPaymentEpochDay"),
                cardNickname = o.optString("cardNickname"),
                cardLast4 = if (last4.length == 4) last4 else "",
                remindDaysBefore = o.optInt("remindDaysBefore", 1).coerceIn(0, 30),
                notes = o.optString("notes"),
                active = o.optBoolean("active", true),
                createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            )
        }
    }
}
