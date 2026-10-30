package com.tvbox.app.data

import com.tvbox.app.domain.AppUpdate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 更新清单的解析与版本比较。纯文本逻辑，不依赖任何平台网络或安装能力；
 * 各端拿到清单后自行决定如何下载与安装。
 */
fun parseAppUpdateManifest(
    raw: String,
    currentVersionCode: Long,
    json: Json = Json { ignoreUnknownKeys = true },
): AppUpdate? {
    val dto = json.decodeFromString<AppUpdateManifestDto>(raw.trimStart('\uFEFF'))
    if (dto.versionCode <= currentVersionCode) return null
    return AppUpdate(
        versionCode = dto.versionCode,
        versionName = dto.versionName.trim(),
        apkUrl = dto.apkUrl.trim(),
        apkSha256 = dto.apkSha256.trim(),
        apkSize = dto.apkSize.coerceAtLeast(0L),
        force = dto.force,
        changelog = dto.changelog.map { it.trim() }.filter { it.isNotBlank() },
    )
}

@Serializable
data class AppUpdateManifestDto(
    @SerialName("versionCode") val versionCode: Long,
    @SerialName("versionName") val versionName: String,
    @SerialName("apkUrl") val apkUrl: String,
    @SerialName("apkSha256") val apkSha256: String = "",
    @SerialName("apkSize") val apkSize: Long = 0,
    @SerialName("force") val force: Boolean = false,
    @SerialName("changelog") val changelog: List<String> = emptyList(),
)
