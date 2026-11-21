package com.dmer.neoreaderrecords

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class BookplateTest {
    @Test fun extractsOnlyPersonalSentencesWithTheirChapter() {
        val response = JSONObject("""{"chapters":[{"chapterUid":8,"title":"起点"}],"updated":[
            {"chapterUid":8,"markText":"先停下。再想想！","type":1},
            {"markText":"不显示","type":0},{"markText":"  "}]}""")
        val lines = Bookplate.highlights(response)
        assertEquals(listOf("先停下。", "再想想！"), lines.map { it.text })
        assertTrue(lines.all { it.chapter == "起点" })
        assertEquals("再想想！", Bookplate.pick(lines, "先停下。")?.text)
        assertEquals(lines.first(), Bookplate.pick(lines.take(1), lines.first().text))
        assertNull(Bookplate.pick(emptyList(), null))
    }
}
