package com.dmer.neoreaderrecords

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ReadingQuestionTest {
    @Test fun onlyUsesMyQuestionAndKeepsLinkedHighlight() {
        val page = JSONObject("""{"reviews":[
          {"review":{"content":"作者为什么这样想？","abstract":"章节原句"}},
          {"review":{"content":"  Can this generalize?  "}},
          {"review":{"content":"值得试试。","abstract":"别人的疑问？"}},
          {"review":{"abstract":"仅有划线？"}}]}""")
        val items = ReadingQuestion.fromPage(page)
        assertEquals(listOf("作者为什么这样想？", "Can this generalize?"), items.map { it.text })
        assertEquals("章节原句", items.first().excerpt)
        assertEquals("", items.last().excerpt)
    }

    @Test fun daySelectsRepeatablyAndHandlesEmpty() {
        val options = listOf("甲","乙","丙")
        assertEquals(ReadingQuestion.forDay(options,20261122), ReadingQuestion.forDay(options,20261122))
        assertNotEquals(ReadingQuestion.forDay(options,20261122), ReadingQuestion.forDay(options,20261123))
        assertNull(ReadingQuestion.forDay(emptyList<String>(),20261122))
    }
}
