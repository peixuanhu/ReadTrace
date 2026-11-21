package com.dmer.neoreaderrecords

import org.json.JSONObject

internal data class ReadingQuestion(val text: String, val excerpt: String) {
    companion object {
        fun fromPage(page: JSONObject): List<ReadingQuestion> {
            val reviews = page.getJSONArray("reviews")
            return buildList {
                for (i in 0 until reviews.length()) {
                    val review = reviews.optJSONObject(i)?.optJSONObject("review") ?: continue
                    val text = review.optString("content").trim()
                    if (text.endsWith('？') || text.endsWith('?')) {
                        val excerpt = if (review.isNull("abstract")) "" else review.optString("abstract").trim()
                        add(ReadingQuestion(text, excerpt))
                    }
                }
            }
        }

        fun <T> forDay(items: List<T>, day: Int): T? =
            if (items.isEmpty()) null else items[Math.floorMod(day, items.size)]
    }
}
