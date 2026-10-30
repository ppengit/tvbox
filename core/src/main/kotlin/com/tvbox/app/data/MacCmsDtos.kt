package com.tvbox.app.data

import com.tvbox.app.domain.ApiLine
import com.tvbox.app.domain.Category
import com.tvbox.app.domain.Movie
import com.tvbox.app.domain.PagedMovies
import com.tvbox.app.domain.PlaySource
import com.tvbox.app.domain.cleanHtml
import com.tvbox.app.domain.isBlockedContent
import com.tvbox.app.domain.parsePlaySources
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI

@Serializable
data class MacCmsResponse(
    val code: Int = 0,
    val msg: String = "",
    val page: JsonElement? = null,
    val pagecount: JsonElement? = null,
    val total: JsonElement? = null,
    @SerialName("class")
    val categories: List<CategoryDto> = emptyList(),
    val list: List<VodDto> = emptyList(),
) {
    fun toPagedMovies(apiLine: ApiLine): PagedMovies {
        val blockedCategoryIds = mutableSetOf<Int>()
        var addedBlockedCategory: Boolean
        do {
            addedBlockedCategory = false
            categories.forEach { category ->
                if (
                    category.typeId > 0 &&
                    (isBlockedContent(cleanHtml(category.typeName)) || category.typePid in blockedCategoryIds)
                ) {
                    addedBlockedCategory = blockedCategoryIds.add(category.typeId) || addedBlockedCategory
                }
            }
        } while (addedBlockedCategory)

        return PagedMovies(
            page = page.asInt(defaultValue = 1).coerceAtLeast(1),
            pageCount = pagecount.asInt(defaultValue = 1).coerceAtLeast(1),
            total = total.asInt(defaultValue = 0).coerceAtLeast(0),
            apiLine = apiLine,
            categories = categories.filterNot { it.typeId in blockedCategoryIds }
                .mapNotNull { it.toDomainOrNull() }
                .distinctBy { it.id },
            movies = list.filterNot { it.typeId in blockedCategoryIds }
                .mapNotNull { it.toDomainOrNull(apiLine) }
                .distinctBy { it.id },
        )
    }
}

@Serializable
data class CategoryDto(
    @SerialName("type_id")
    val typeId: Int = 0,
    @SerialName("type_pid")
    val typePid: Int = 0,
    @SerialName("type_name")
    val typeName: String = "",
) {
    fun toDomainOrNull(): Category? {
        if (typeId <= 0 || typeName.isBlank()) return null
        if (typeId in hiddenCategoryIds) return null
        val name = cleanHtml(typeName)
        if (isBlockedContent(name)) return null
        return Category(id = typeId, parentId = typePid.coerceAtLeast(0), name = name)
    }
}

@Serializable
data class VodDto(
    @SerialName("vod_id")
    val vodId: Int = 0,
    @SerialName("type_id")
    val typeId: Int = 0,
    @SerialName("type_name")
    val typeName: String = "",
    @SerialName("vod_name")
    val vodName: String = "",
    @SerialName("vod_sub")
    val vodSub: String = "",
    @SerialName("vod_class")
    val vodClass: String = "",
    @SerialName("vod_tag")
    val vodTag: String = "",
    @SerialName("vod_pic")
    val vodPic: String = "",
    @SerialName("vod_actor")
    val vodActor: String = "",
    @SerialName("vod_director")
    val vodDirector: String = "",
    @SerialName("vod_content")
    val vodContent: String = "",
    @SerialName("vod_blurb")
    val vodBlurb: String = "",
    @SerialName("vod_remarks")
    val vodRemarks: String = "",
    @SerialName("vod_area")
    val vodArea: String = "",
    @SerialName("vod_lang")
    val vodLang: String = "",
    @SerialName("vod_year")
    val vodYear: String = "",
    @SerialName("vod_duration")
    val vodDuration: String = "",
    @SerialName("vod_play_from")
    val vodPlayFrom: String = "",
    @SerialName("vod_play_url")
    val vodPlayUrl: String = "",
) {
    fun toDomainOrNull(apiLine: ApiLine): Movie? {
        if (vodId <= 0 || vodName.isBlank()) return null
        if (
            isBlockedContent(
                vodName,
                vodSub,
                typeName,
                vodClass,
                vodTag,
                vodRemarks,
                vodContent,
                vodBlurb,
            )
        ) {
            return null
        }
        // 有的站点只返回 HTML 标签或空白，清洗后为空时不应生成一张空卡片。
        val name = cleanHtml(vodName)
        if (name.isBlank()) return null
        val baseUrl = apiLine.baseUrls.firstOrNull()
        return Movie(
            id = vodId,
            apiLineId = apiLine.id,
            apiLineName = apiLine.name,
            name = name,
            typeId = typeId,
            typeName = cleanHtml(typeName),
            posterUrl = normalizePosterUrl(vodPic, baseUrl),
            remarks = cleanHtml(vodRemarks),
            year = cleanField(vodYear),
            area = cleanField(vodArea),
            language = cleanField(vodLang),
            actor = cleanHtml(vodActor),
            director = cleanHtml(vodDirector),
            duration = cleanHtml(vodDuration),
            description = cleanHtml(vodContent.ifBlank { vodBlurb }),
            playSources = resolvePlaySources(vodPlayFrom, vodPlayUrl, baseUrl),
        )
    }
}

/** 站点常把「地区：中国大陆」整段塞进字段里，展示前把开头的分隔符去掉。 */
private fun cleanField(raw: String?): String =
    cleanHtml(raw).trim().trimStart('：', ':', '，', ',', '、', ' ').trim()

/**
 * MacCMS 的 vod_pic 有时是 `["https://..."]` 这样的字符串化数组。
 * 取第一个元素并去掉引号，再按接口地址补全相对路径。
 */
private fun normalizePosterUrl(raw: String, baseUrl: String?): String {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return ""
    val candidate = if (trimmed.startsWith("[")) {
        trimmed.removePrefix("[")
            .substringBefore("]")
            .split(',')
            .firstOrNull()
            ?.trim()
            ?.trim('"', '\'', ' ')
            .orEmpty()
            .ifBlank { trimmed }
    } else {
        trimmed
    }
    return resolveUrl(candidate, baseUrl)
}

private fun resolvePlaySources(
    playFrom: String,
    playUrl: String,
    baseUrl: String?,
): List<PlaySource> = parsePlaySources(playFrom, playUrl).map { source ->
    source.copy(
        episodes = source.episodes.map { episode ->
            episode.copy(url = resolveUrl(episode.url, baseUrl))
        },
    )
}

private val URL_SCHEME = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:")

/**
 * 把相对的剧集/海报地址按接口地址补全。播放地址里常见的 `magnet:`、`ed2k:` 等
 * 自定义协议原样保留；协议相对地址统一升级为 https，避免桌面端明文请求被拦截。
 */
internal fun resolveUrl(raw: String, baseUrl: String?): String {
    val value = raw.trim()
    if (value.isBlank()) return ""
    if (value.startsWith("//")) return "https:$value"
    if (URL_SCHEME.containsMatchIn(value)) return value
    // 自定义接口常只填到 .../api.php/provide/vod（没有结尾斜杠），
    // 直接 URI.resolve 会把最后一段路径吃掉，补斜杠后才能拼出正确的资源地址。
    val base = baseUrl?.trim().orEmpty()
        .let { if (it.isNotEmpty() && !it.endsWith("/")) "$it/" else it }
    if (base.isBlank()) return value
    return runCatching { URI(base).resolve(value).toString() }.getOrDefault(value)
}

private fun JsonElement?.asInt(defaultValue: Int): Int {
    val primitive = this?.jsonPrimitive ?: return defaultValue
    return primitive.intOrNull ?: primitive.content.toIntOrNull() ?: defaultValue
}

private val hiddenCategoryIds = setOf(41, 42, 43, 44)
