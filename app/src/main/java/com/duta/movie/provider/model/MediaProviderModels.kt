package com.duta.movie.provider.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ProviderMediaType {
    @SerialName("MOVIE")
    MOVIE,
    @SerialName("SERIES")
    SERIES,
    @SerialName("ANIME")
    ANIME,
    @SerialName("MULTI")
    MULTI
}

@Serializable
enum class ProviderEngineType {
    @SerialName("TEMPLATE")
    TEMPLATE,
    @SerialName("DECLARATIVE")
    DECLARATIVE,
    @SerialName("DEX")
    DEX
}

@Serializable
enum class TemplateType {
    @SerialName("WORDPRESS_MUVIPRO")
    WORDPRESS_MUVIPRO,
    @SerialName("PENCURI")
    PENCURI,
    @SerialName("DUTAFILM")
    DUTAFILM,
    @SerialName("GENERIC_HTML")
    GENERIC_HTML
}

@Serializable
enum class ProviderStatus {
    @SerialName("ACTIVE")
    ACTIVE,
    @SerialName("MAINTENANCE")
    MAINTENANCE,
    @SerialName("DEPRECATED")
    DEPRECATED
}

@Serializable
data class RemoteRepository(
    val id: String,
    val name: String,
    val description: String = "",
    val author: String = "DreamStream Community",
    @SerialName("icon_url")
    val iconUrl: String = "",
    val url: String = "",
    @SerialName("is_official")
    val isOfficial: Boolean = false,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null
)

@Serializable
data class RemoteProviderManifest(
    val id: String,
    @SerialName("repo_id")
    val repoId: String = "dreamstream-official",
    val name: String,
    @SerialName("display_name")
    val displayName: String = name,
    val description: String = "",
    val author: String = "",
    val version: Int = 1,
    @SerialName("version_name")
    val versionName: String = "1.0.0",
    @SerialName("icon_url")
    val iconUrl: String = "",
    @SerialName("media_type")
    val mediaType: ProviderMediaType = ProviderMediaType.MULTI,
    @SerialName("engine_type")
    val engineType: ProviderEngineType = ProviderEngineType.TEMPLATE,
    @SerialName("template_type")
    val templateType: TemplateType = TemplateType.WORDPRESS_MUVIPRO,
    @SerialName("base_urls")
    val baseUrls: List<String> = emptyList(),
    val config: kotlinx.serialization.json.JsonObject = kotlinx.serialization.json.buildJsonObject {},
    @SerialName("plugin_url")
    val pluginUrl: String = "",
    val status: ProviderStatus = ProviderStatus.ACTIVE,
    @SerialName("is_enabled_default")
    val isEnabledDefault: Boolean = true,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null
)

/**
 * Standard CloudStream repo.json manifest support
 * Users can paste CloudStream repo URLs into DreamStream!
 */
@Serializable
data class CloudStreamRepoManifest(
    val name: String = "",
    val description: String = "",
    val manifestVersion: Int = 1,
    @SerialName("iconUrl")
    val iconUrl: String = "",
    val pluginLists: List<String> = emptyList()
)

/**
 * CloudStream plugin entry from plugins.json
 */
@Serializable
data class CloudStreamPlugin(
    val name: String,
    val internalName: String? = null,
    val description: String? = null,
    val version: Int = 1,
    val url: String = "",
    val iconUrl: String? = null,
    val authors: List<String>? = null,
    val tvTypes: List<String>? = null,
    val language: String? = null,
    val status: Int? = null,
    val repositoryUrl: String? = null
)

