package com.tvbox.app.data

import com.tvbox.app.domain.ApiLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MacCmsDtosTest {

    private val line = ApiLine(
        id = "test",
        name = "测试线路",
        baseUrls = listOf("https://api.test/api.php/provide/vod/"),
    )

    @Test
    fun relativePosterAndEpisodeUrlsAreResolvedAgainstApiBase() {
        val movie = VodDto(
            vodId = 1,
            vodName = "测试剧",
            vodPic = "/upload/poster.jpg",
            vodPlayFrom = "m3u8",
            vodPlayUrl = "第01集$/play/1.m3u8#第02集$//cdn.test/2.m3u8",
        ).toDomainOrNull(line)!!

        assertEquals("https://api.test/upload/poster.jpg", movie.posterUrl)
        assertEquals(
            listOf("https://api.test/play/1.m3u8", "https://cdn.test/2.m3u8"),
            movie.playSources.single().episodes.map { it.url },
        )
    }

    @Test
    fun relativeUrlsAreResolvedWhenApiBaseHasNoTrailingSlash() {
        val custom = ApiLine(
            id = "custom",
            name = "自定义",
            baseUrls = listOf("https://api.test/api.php/provide/vod"),
        )
        val movie = VodDto(
            vodId = 1,
            vodName = "测试剧",
            vodPic = "upload/poster.jpg",
            vodPlayFrom = "m3u8",
            vodPlayUrl = "第01集${'$'}play/1.m3u8",
        ).toDomainOrNull(custom)!!

        assertEquals("https://api.test/api.php/provide/vod/upload/poster.jpg", movie.posterUrl)
        assertEquals(
            "https://api.test/api.php/provide/vod/play/1.m3u8",
            movie.playSources.single().episodes.single().url,
        )
    }

    @Test
    fun absoluteUrlsAndCustomSchemesAreKept() {
        val movie = VodDto(
            vodId = 1,
            vodName = "测试剧",
            vodPic = "https://img.test/poster.jpg",
            vodPlayFrom = "magnet",
            vodPlayUrl = "正片\$magnet:?xt=urn:btih:abc",
        ).toDomainOrNull(line)!!

        assertEquals("https://img.test/poster.jpg", movie.posterUrl)
        assertEquals("magnet:?xt=urn:btih:abc", movie.playSources.single().episodes.single().url)
    }

    @Test
    fun doubleBracketPosterUsesFirstUnquotedCandidate() {
        val movie = VodDto(
            vodId = 1,
            vodName = "测试剧",
            vodPic = "[\"https://img.test/a.jpg\", \"https://img.test/b.jpg\"]",
        ).toDomainOrNull(line)!!

        assertEquals("https://img.test/a.jpg", movie.posterUrl)
    }

    @Test
    fun duplicateIdsAndBlankNamesAreDropped() {
        val response = MacCmsResponse(
            list = listOf(
                VodDto(vodId = 1, vodName = "影片一"),
                VodDto(vodId = 1, vodName = "影片一（重复）"),
                VodDto(vodId = 2, vodName = "<b></b>"),
            ),
        )

        val movies = response.toPagedMovies(line).movies
        assertEquals(1, movies.size)
        assertEquals("影片一", movies.single().name)
    }

    @Test
    fun childrenOfBlockedCategoriesAreFilteredToo() {
        val response = MacCmsResponse(
            categories = listOf(
                CategoryDto(typeId = 10, typePid = 0, typeName = "电影解说"),
                CategoryDto(typeId = 11, typePid = 10, typeName = "子分类"),
                CategoryDto(typeId = 12, typePid = 0, typeName = "正常分类"),
            ),
            list = listOf(
                VodDto(vodId = 1, typeId = 10, vodName = "来自屏蔽分类"),
                VodDto(vodId = 2, typeId = 11, vodName = "来自屏蔽子分类"),
                VodDto(vodId = 3, typeId = 12, vodName = "正常影片"),
            ),
        )

        val page = response.toPagedMovies(line)
        assertEquals(listOf(12), page.categories.map { it.id })
        assertEquals(listOf(3), page.movies.map { it.id })
    }

    @Test
    fun invalidMovieIdOrBlankRawNameIsRejected() {
        assertNull(VodDto(vodId = 0, vodName = "影片").toDomainOrNull(line))
        assertNull(VodDto(vodId = 1, vodName = "   ").toDomainOrNull(line))
    }
}
