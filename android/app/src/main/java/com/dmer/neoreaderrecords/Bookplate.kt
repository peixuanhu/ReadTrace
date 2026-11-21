package com.dmer.neoreaderrecords

import org.json.JSONObject

internal object Bookplate {
    data class Line(val text: String, val chapter: String)
    private val sentence = Regex("""[^。！？!?\n]+(?:[。！？!?]+[”」』）]*)?""")

    fun highlights(json: JSONObject): List<Line> {
        val chapters = mutableMapOf<Int, String>()
        val directory = json.optJSONArray("chapters")
        for (i in 0 until (directory?.length() ?: 0)) {
            val chapter = directory?.optJSONObject(i) ?: continue
            chapters[chapter.optInt("chapterUid")] = chapter.optString("title")
        }
        val lines = mutableListOf<Line>()
        val marks = json.getJSONArray("updated")
        for (i in 0 until marks.length()) {
            val mark = marks.optJSONObject(i) ?: continue
            if (mark.optInt("type", 1) != 1 || mark.isNull("markText")) continue
            val chapter = chapters[mark.optInt("chapterUid")].orEmpty()
            for (match in sentence.findAll(mark.optString("markText"))) {
                val text = match.value.trim()
                if (text.isNotEmpty()) lines += Line(text, chapter)
            }
        }
        return lines.distinctBy { it.text }
    }

    fun pick(lines: List<Line>, previous: String?): Line? =
        lines.filter { it.text != previous }.randomOrNull() ?: lines.firstOrNull()
}
