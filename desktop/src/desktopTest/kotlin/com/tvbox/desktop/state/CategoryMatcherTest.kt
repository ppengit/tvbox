package com.tvbox.desktop.state

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CategoryMatcherTest {
    @Test
    fun `年份为空表示不限`() {
        assertTrue(matches(year = "2024", filterYear = ""))
    }

    @Test
    fun `年份精确包含`() {
        assertTrue(matches(year = "2024", filterYear = "2024"))
        assertFalse(matches(year = "2023", filterYear = "2024"))
        assertFalse(matches(year = "", filterYear = "2024"))
    }

    @Test
    fun `地区别名互相匹配`() {
        assertTrue(matches(area = "内地", filterArea = "大陆"))
        assertTrue(matches(area = "中国大陆", filterArea = "大陆"))
        assertTrue(matches(area = "港剧", filterArea = "香港"))
        assertTrue(matches(area = "美剧", filterArea = "美国"))
        assertTrue(matches(area = "日本", filterArea = "日本"))
    }

    @Test
    fun `地区为空时被排除`() {
        assertFalse(matches(area = "", filterArea = "大陆"))
    }

    @Test
    fun `地区其他只保留未知地区`() {
        assertFalse(matches(area = "美国", filterArea = "其他"))
        assertFalse(matches(area = "大陆", filterArea = "其他"))
        assertTrue(matches(area = "西班牙", filterArea = "其他"))
        assertFalse(matches(area = "", filterArea = "其他"))
    }

    @Test
    fun `语言别名互相匹配`() {
        assertTrue(matches(language = "普通话", filterLanguage = "国语"))
        assertTrue(matches(language = "中文", filterLanguage = "国语"))
        assertTrue(matches(language = "英文", filterLanguage = "英语"))
        assertTrue(matches(language = "粤语", filterLanguage = "粤语"))
    }

    @Test
    fun `语言其他只保留未知语言`() {
        assertFalse(matches(language = "英语", filterLanguage = "其他"))
        assertTrue(matches(language = "西班牙语", filterLanguage = "其他"))
    }

    @Test
    fun `多项条件同时生效`() {
        assertTrue(
            matches(
                year = "2024",
                area = "内地",
                language = "普通话",
                filterYear = "2024",
                filterArea = "大陆",
                filterLanguage = "国语",
            ),
        )
        assertFalse(
            matches(
                year = "2022",
                area = "内地",
                language = "普通话",
                filterYear = "2024",
                filterArea = "大陆",
                filterLanguage = "国语",
            ),
        )
    }

    private fun matches(
        year: String = "",
        area: String = "",
        language: String = "",
        filterYear: String = "",
        filterArea: String = "",
        filterLanguage: String = "",
    ): Boolean = CategoryMatcher.matches(
        movieYear = year,
        movieArea = area,
        movieLanguage = language,
        year = filterYear,
        area = filterArea,
        language = filterLanguage,
    )
}
