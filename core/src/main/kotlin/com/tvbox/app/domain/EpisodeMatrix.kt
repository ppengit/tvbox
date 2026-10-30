package com.tvbox.app.domain

/**
 * 多源集数对齐结果。
 *
 * 不同资源站的集数与命名并不一致：有的写「第01集」，有的写「01」，还有的把正片、
 * 花絮混在一起。对齐规则：
 * 1. 从标题里抽取集号（优先「第N集/期/话」，其次独立的 1-4 位数字）。
 * 2. 以抽取到的集号建索引；抽不到集号的按顺序补齐。
 * 3. 以集数最多的源作为基准，生成统一的集数列表。
 *
 * 播放时若当前源缺少某一集，直接跳到下一个可用源，而不是报错。
 */
data class EpisodeMatrix(
    val episodes: List<AlignedEpisode>,
    val sources: List<SourceEpisodes>,
    val baselineSourceIndex: Int,
) {
    fun episodeAt(index: Int): AlignedEpisode? = episodes.getOrNull(index)

    /** 该源在统一集数列表下的可用情况。 */
    fun availability(sourceIndex: Int): BooleanArray =
        sources.getOrNull(sourceIndex)?.available ?: BooleanArray(episodes.size)

    fun firstAvailableIndex(sourceIndex: Int, from: Int = 0): Int? {
        val array = availability(sourceIndex)
        return (from.coerceAtLeast(0) until array.size).firstOrNull { array[it] }
    }

    /**
     * 解析一次选集播放。集数是 matrix 下标，绝不因为当前线路缺集而换成另一集；
     * 当前线路缺失时只允许换到拥有同一集的线路。
     */
    fun resolvePlayable(sourceIndex: Int, episodeIndex: Int): Pair<Int, Int>? {
        if (episodeIndex !in episodes.indices) return null
        if (availability(sourceIndex).getOrElse(episodeIndex) { false }) {
            return sourceIndex to episodeIndex
        }
        sources.forEachIndexed { index, candidate ->
            if (index != sourceIndex && candidate.available.getOrElse(episodeIndex) { false }) {
                return index to episodeIndex
            }
        }
        return null
    }

    /**
     * 用户主动切到 [sourceIndex] 时保留当前集；目标线路缺集才选择距离最近的可播集。
     * 距离相同时优先较小的 matrix 下标，保证结果稳定。
     */
    fun resolveSourceSwitch(sourceIndex: Int, episodeIndex: Int): Pair<Int, Int>? {
        val available = sources.getOrNull(sourceIndex)?.available ?: return null
        if (available.getOrElse(episodeIndex) { false }) return sourceIndex to episodeIndex
        val nearest = available.indices
            .asSequence()
            .filter { available[it] }
            .minWithOrNull(compareBy<Int>({ kotlin.math.abs(it - episodeIndex) }, { it }))
            ?: return null
        return sourceIndex to nearest
    }
}

data class AlignedEpisode(
    val number: Int,
    val title: String,
)

data class SourceEpisodes(
    /** 该源在统一集数列表上每一集是否有资源。 */
    val available: BooleanArray,
    /** 统一集数下标 → 该源真实的剧集。 */
    val byIndex: Map<Int, PlayEpisode>,
) {
    val playableCount: Int get() = available.count { it }
}

fun buildEpisodeMatrix(sources: List<PlaySource>): EpisodeMatrix {
    if (sources.isEmpty()) {
        return EpisodeMatrix(episodes = emptyList(), sources = emptyList(), baselineSourceIndex = 0)
    }
    val parsed = sources.map { source ->
        source.episodes.mapIndexed { index, episode ->
            index to (episodeNumber(episode.title) ?: (index + 1))
        }
    }

    // 以集数最多的源为基准；数量相同取集号更连续的源。
    val baselineSourceIndex = sources.indices.maxByOrNull { index ->
        sources[index].episodes.size * 1000 + parsed[index].count { it.second > 0 }
    } ?: 0

    val unionNumbers = parsed
        .flatMap { it.map { entry -> entry.second } }
        .filter { it > 0 }
        .distinct()
        .sorted()

    // 编号连续时直接用编号；编号混乱（重复/缺号严重）时退回按位置对齐。
    val numbers = if (unionNumbers.isNotEmpty() && unionNumbers.size >= sources.maxOf { it.episodes.size }) {
        unionNumbers
    } else {
        (1..sources.maxOf { it.episodes.size }).toList()
    }

    val titles = mutableMapOf<Int, String>()
    val alignedSources = sources.mapIndexed { sourceIndex, source ->
        val byNumber = parsed[sourceIndex].associate { it.second to it.first }
        val byIndex = mutableMapOf<Int, PlayEpisode>()
        val available = BooleanArray(numbers.size)
        numbers.forEachIndexed { position, number ->
            val episodePosition = byNumber[number] ?: if (unionNumbers.isEmpty()) position else null
            val episode = episodePosition?.let { source.episodes.getOrNull(it) }
            if (episode != null) {
                byIndex[position] = episode
                available[position] = true
                titles.putIfAbsent(position, episode.title)
            }
        }
        SourceEpisodes(available = available, byIndex = byIndex)
    }

    val episodes = numbers.mapIndexed { position, number ->
        AlignedEpisode(
            number = number,
            title = titles[position] ?: sources[baselineSourceIndex].episodes.getOrNull(position)?.title
                ?: "第${number}集",
        )
    }

    return EpisodeMatrix(
        episodes = episodes,
        sources = alignedSources,
        baselineSourceIndex = baselineSourceIndex,
    )
}

private val CHINESE_EPISODE = Regex("""第\s*(\d{1,4})\s*[集期话回]""")
private val NUMBER_WITH_BRACKET = Regex("""[\[\(（【]\s*(\d{1,4})\s*[\]\)）】]""")
private val LEADING_NUMBER = Regex("""(?:^|[^\d])(\d{1,4})(?:[^\d]|$)""")
private val YEAR_LIKE = Regex("""(19|20)\d{2}""")

/**
 * 从剧集标题里取出集号。识别不出来返回 null，交给调用方按顺序补齐。
 */
fun episodeNumber(title: String): Int? {
    val text = title.trim()
    if (text.isBlank()) return null
    CHINESE_EPISODE.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it }
    NUMBER_WITH_BRACKET.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it }
    // 纯数字标题（"01" / "1"）
    text.toIntOrNull()?.let { return it }
    if (YEAR_LIKE.containsMatchIn(text)) return null
    return LEADING_NUMBER.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()?.takeIf { it in 1..2000 }
}
