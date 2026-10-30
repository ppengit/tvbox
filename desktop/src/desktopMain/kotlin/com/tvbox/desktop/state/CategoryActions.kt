package com.tvbox.desktop.state

import com.tvbox.app.data.AppLog
import com.tvbox.app.domain.ApiLine
import com.tvbox.app.domain.Category
import com.tvbox.app.domain.Movie
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * 片库改动的筛选选项。站点大多按 MacCMS 的 year/area/lang 查询参数过滤，
 * 但并非所有站点都支持，所以返回结果还会做一次本地校验。
 */
object CategoryFilters {
    val years = listOf("2026", "2025", "2024", "2023", "2022", "2021", "2020", "2019", "2018", "2017", "2016", "2015")
    val areas = listOf("大陆", "香港", "台湾", "美国", "日本", "韩国", "英国", "法国", "德国", "泰国", "印度", "其他")
    val languages = listOf("国语", "粤语", "英语", "日语", "韩语", "法语", "德语", "泰语", "其他")
}

/**
 * 片库聚合：分类树取自首页数据源，内容则向所有已配置线路并发请求同一分类，
 * 按剧名去重后合并，避免同一部剧在几十个站点里重复出现。
 */
fun DesktopAppState.loadCategory(reset: Boolean = false) {
    val current = category.value
    if (reset) {
        categoryJob?.cancel()
        updateCategory { it.copy(loading = true, loadingMore = false, error = null) }
    } else {
        if (current.loading || current.loadingMore || current.page >= current.pageCount) return
        updateCategory { it.copy(loadingMore = true) }
    }

    categoryJob = scopeRef.launch {
        val snapshot = category.value
        val homeLineId = homeApiLineId
        val requestedPage = if (reset) 1 else snapshot.page + 1
        val lines = movieRepository.apiLines
        try {
            val categories = (
                if (reset || snapshot.categories.isEmpty() || snapshot.apiLineId != homeLineId) {
                    runCatching { movieRepository.getCategories(homeLineId) }.getOrDefault(emptyList())
                } else {
                    snapshot.categories
                }
                ).distinctBy { it.parentId to it.name.trim() }
            val typeNames = buildTypeNames(snapshot, categories)
            val results = coroutineScope {
                lines.map { line -> async { fetchLinePage(line, snapshot, typeNames, requestedPage) } }.awaitAll()
            }
            val answered = results.count { it.movies.isNotEmpty() }
            val seen = if (reset) mutableSetOf() else snapshot.movies.map { normalizeTitle(it.name) }.toMutableSet()
            val incoming = interleaveDedupe(results.map { it.movies }, seen)
            val pageCount = results.maxOfOrNull { it.pageCount }?.coerceAtLeast(1) ?: snapshot.pageCount
            updateCategory { state ->
                state.copy(
                    apiLineId = homeLineId,
                    categories = categories,
                    movies = if (reset) incoming else state.movies + incoming,
                    sourceCount = answered,
                    page = requestedPage,
                    pageCount = pageCount,
                    loading = false,
                    loadingMore = false,
                    error = null,
                    ready = true,
                )
            }
            AppLog.info(
                "Category",
                "片库聚合 ${incoming.size} 条（第 $requestedPage/$pageCount 页，$answered/${lines.size} 个源有结果）" +
                    " type=${typeNames.joinToString("/").ifBlank { "全部" }}" +
                    " year=${snapshot.year.ifBlank { "-" }} area=${snapshot.area.ifBlank { "-" }} lang=${snapshot.lang.ifBlank { "-" }}",
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            updateCategory {
                it.copy(
                    loading = false,
                    loadingMore = false,
                    ready = true,
                    error = if (it.movies.isEmpty()) error.userMessage() else null,
                )
            }
            AppLog.warn("Category", "片库加载失败：${error.userMessage()}")
            if (category.value.movies.isNotEmpty()) notify("加载更多失败：${error.userMessage()}")
        }
    }
}

private data class LinePage(val movies: List<Movie>, val pageCount: Int)

private fun buildTypeNames(state: CategoryState, categories: List<Category>): List<String> = when {
    state.selectedTypeId != null -> listOfNotNull(
        categories.firstOrNull { it.id == state.selectedTypeId }?.name,
    )

    state.selectedParentId != 0 -> listOfNotNull(
        categories.firstOrNull { it.id == state.selectedParentId && it.parentId == 0 }?.name,
    )

    else -> emptyList()
}

/** 各站点的分类 id 不通用，只能按分类名在本站分类树里找对应的 typeId。 */
private suspend fun DesktopAppState.fetchLinePage(
    line: ApiLine,
    state: CategoryState,
    typeNames: List<String>,
    page: Int,
): LinePage {
    val typeRequested = state.selectedParentId != 0 || state.selectedTypeId != null
    // 选了分类却解析不到分类名（分类树没拉到）时直接跳过该源，宁可少结果也不能把全站内容混进来。
    if (typeRequested && typeNames.isEmpty()) return LinePage(emptyList(), 1)
    val typeId = if (typeNames.isEmpty()) {
        null
    } else {
        val index = runCatching {
            categoryTypeIndex.getOrPut(line.id) {
                runCatching { movieRepository.getCategories(line.id) }
                    .getOrDefault(emptyList())
                    .associate { it.name to it.id }
            }
        }.getOrDefault(emptyMap())
        // 子类必须精确命中；只有父类时才允许按大类名查询，不能把子类退回父类。
        typeNames.firstNotNullOfOrNull { index[it] } ?: return LinePage(emptyList(), 1)
    }
    val paged = runCatching {
        movieRepository.getMovies(
            apiLineId = line.id,
            page = page,
            typeId = typeId,
            year = state.year.takeIf { it.isNotBlank() },
            area = state.area.takeIf { it.isNotBlank() },
            lang = state.lang.takeIf { it.isNotBlank() },
        )
    }.getOrNull() ?: return LinePage(emptyList(), 1)
    return LinePage(
        movies = paged.movies.filter { it.matchesCategoryFilters(state) },
        pageCount = paged.pageCount.coerceAtLeast(1),
    )
}

private fun normalizeTitle(name: String): String =
    name.lowercase().replace(Regex("[\\s\\p{Punct}，。、：；·—\\-（）()《》【】]"), "")

/** 轮流从每个站点取一条，保证首屏不会先被某一个源占满。 */
private fun interleaveDedupe(groups: List<List<Movie>>, seen: MutableSet<String>): List<Movie> {
    if (groups.isEmpty()) return emptyList()
    val out = ArrayList<Movie>()
    val iterators = groups.map { it.iterator() }
    var anyAdded = true
    while (anyAdded) {
        anyAdded = false
        for (iterator in iterators) {
            while (iterator.hasNext()) {
                val movie = iterator.next()
                if (seen.add(normalizeTitle(movie.name))) {
                    out.add(movie)
                    anyAdded = true
                    break
                }
            }
        }
    }
    return out
}

fun DesktopAppState.selectCategoryParent(parentId: Int) {
    val state = category.value
    if (state.selectedParentId == parentId) return
    updateCategory {
        it.copy(selectedParentId = parentId, selectedTypeId = null, movies = emptyList(), page = 1, pageCount = 1)
    }
    loadCategory(reset = true)
}

fun DesktopAppState.selectCategoryType(typeId: Int?) {
    if (category.value.selectedTypeId == typeId) return
    updateCategory { it.copy(selectedTypeId = typeId, movies = emptyList(), page = 1, pageCount = 1) }
    loadCategory(reset = true)
}

fun DesktopAppState.setCategoryFilter(year: String? = null, area: String? = null, lang: String? = null) {
    updateCategory {
        it.copy(
            year = year ?: it.year,
            area = area ?: it.area,
            lang = lang ?: it.lang,
            movies = emptyList(),
            page = 1,
            pageCount = 1,
        )
    }
    loadCategory(reset = true)
}

/** 点「全部」需要真的清空对应条件，所以年份/地区/语言各自有独立的清除入口。 */
fun DesktopAppState.setCategoryYear(value: String?) {
    if (category.value.year == value.orEmpty()) return
    updateCategory { it.copy(year = value.orEmpty(), movies = emptyList(), page = 1, pageCount = 1) }
    loadCategory(reset = true)
}

fun DesktopAppState.setCategoryArea(value: String?) {
    if (category.value.area == value.orEmpty()) return
    updateCategory { it.copy(area = value.orEmpty(), movies = emptyList(), page = 1, pageCount = 1) }
    loadCategory(reset = true)
}

fun DesktopAppState.setCategoryLang(value: String?) {
    if (category.value.lang == value.orEmpty()) return
    updateCategory { it.copy(lang = value.orEmpty(), movies = emptyList(), page = 1, pageCount = 1) }
    loadCategory(reset = true)
}

fun DesktopAppState.clearCategoryFilters() {
    if (category.value.activeFilters == 0) return
    updateCategory { it.copy(year = "", area = "", lang = "", movies = emptyList(), page = 1, pageCount = 1) }
    loadCategory(reset = true)
}

fun DesktopAppState.openCategoryMovie(movie: Movie) {
    openDetail(movie = movie, apiLineId = movie.apiLineId)
}

private fun Movie.matchesCategoryFilters(state: CategoryState): Boolean =
    CategoryMatcher.matches(
        movieYear = year,
        movieArea = area,
        movieLanguage = language,
        year = state.year,
        area = state.area,
        language = state.lang,
    )

/**
 * 各源站对地区/语言的写法不统一（内地/中国大陆、普通话/国语、英文/英语），
 * 统一成别名后再匹配；选「其他」时表示不属于任何已知分类。
 */
internal object CategoryMatcher {
    private val areaAliases = mapOf(
        "大陆" to listOf("大陆", "内地", "中国大陆"),
        "香港" to listOf("香港", "港剧", "港"),
        "台湾" to listOf("台湾", "台剧", "台"),
        "美国" to listOf("美国", "美剧", "美"),
        "日本" to listOf("日本", "日剧", "日"),
        "韩国" to listOf("韩国", "韩剧", "韩"),
        "英国" to listOf("英国", "英剧", "英"),
        "法国" to listOf("法国", "法剧", "法"),
        "德国" to listOf("德国", "德剧", "德"),
        "泰国" to listOf("泰国", "泰剧", "泰"),
        "印度" to listOf("印度", "印剧", "印"),
    )

    private val languageAliases = mapOf(
        "国语" to listOf("国语", "普通话", "汉语", "中文"),
        "粤语" to listOf("粤语", "粤"),
        "英语" to listOf("英语", "英文"),
        "日语" to listOf("日语", "日文"),
        "韩语" to listOf("韩语", "韩文"),
        "法语" to listOf("法语", "法文"),
        "德语" to listOf("德语", "德文"),
        "泰语" to listOf("泰语", "泰文"),
    )

    fun matches(
        movieYear: String,
        movieArea: String,
        movieLanguage: String,
        year: String,
        area: String,
        language: String,
    ): Boolean {
        if (year.isNotBlank() && !movieYear.contains(year)) return false
        if (area.isNotBlank() && !areaMatches(movieArea, area)) return false
        if (language.isNotBlank() && !languageMatches(movieLanguage, language)) return false
        return true
    }

    private fun areaMatches(raw: String, selected: String): Boolean {
        val value = raw.trim()
        if (selected == "其他") return value.isNotBlank() && areaAliases.keys.none { aliasMatches(value, it, areaAliases) }
        if (value.isBlank()) return false
        return aliasMatches(value, selected, areaAliases)
    }

    private fun languageMatches(raw: String, selected: String): Boolean {
        val value = raw.trim()
        if (selected == "其他") {
            return value.isNotBlank() && languageAliases.keys.none { aliasMatches(value, it, languageAliases) }
        }
        if (value.isBlank()) return false
        return aliasMatches(value, selected, languageAliases)
    }

    private fun aliasMatches(value: String, selected: String, aliases: Map<String, List<String>>): Boolean {
        val candidates = aliases[selected] ?: listOf(selected)
        return candidates.any { value.contains(it, ignoreCase = true) }
    }
}
