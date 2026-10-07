package com.app.ralaunch.feature.gog.domain

import com.app.ralaunch.feature.gog.domain.ModLoaderConfigManager.ModLoaderVersion
import com.app.ralaunch.core.common.util.HttpUrlValidator
import com.app.ralaunch.core.logging.AppLog
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * ModLoader 版本获取器
 * 从 GitHub Releases API 动态获取最新版本
 */
object ModLoaderVersionFetcher {

    private const val TAG = "ModLoaderVersionFetcher"
    private const val GITHUB_API = "https://api.github.com/repos"
    private const val MAX_STABLE_VERSIONS = 5  // 返回的稳定版本数
    private const val FETCH_COUNT = 30  // 从 API 获取的版本数（用于筛选稳定版）
    private const val FULL_LIST_PER_PAGE = 100  // 拉取全量版本列表时的每页数量
    private const val FULL_LIST_MAX_PAGES = 3   // 全量版本列表最多拉取的页数

    /** GitHub 加速镜像前缀（gh-proxy） */
    private const val GITHUB_PROXY_PREFIX = "https://gh-proxy.com/"

    /**
     * GitHub 加速镜像列表（按实测速度排序，ghfast.top 支持 Range 分段请求，
     * 供多线程下载器择优；gh-proxy 不支持 Range 仅作单线程兜底）
     */
    val GITHUB_MIRROR_PREFIXES = listOf(
        "https://ghfast.top/",
        "https://gh-proxy.com/",
        "https://ghproxy.net/"
    )

    /**
     * 生成下载候选地址列表（供多线程分段下载器逐个择优）。
     * @param preferMirror true 时镜像优先，false 时直连优先
     */
    fun getDownloadCandidateUrls(assetUrl: String, preferMirror: Boolean): List<String> {
        val mirrored = GITHUB_MIRROR_PREFIXES.map { it + assetUrl }
        return if (preferMirror) mirrored + assetUrl else listOf(assetUrl) + mirrored
    }

    /**
     * 按用户选择决定是否走 gh-proxy 加速站下载 GitHub 资源
     * 仅代理 GitHub 文件域名（api.github.com 的列表接口不走代理）
     */
    fun applyGitHubProxy(url: String, useProxy: Boolean): String {
        if (!useProxy) return url
        val proxied = url.startsWith("https://github.com/") ||
            url.startsWith("https://raw.githubusercontent.com/") ||
            url.startsWith("https://objects.githubusercontent.com/")
        return if (proxied) GITHUB_PROXY_PREFIX + url else url
    }

    /**
     * GitHub Release 信息
     */
    data class GitHubRelease(
        val tagName: String,
        val name: String,
        val prerelease: Boolean,
        val draft: Boolean,
        val publishedAt: String,
        val assets: List<GitHubAsset>
    )

    data class GitHubAsset(
        val name: String,
        val downloadUrl: String,
        val size: Long
    )

    /**
     * 全量版本列表条目（含预发布版），供在线下载选择
     */
    data class ModLoaderReleaseVersion(
        val version: String,
        val displayName: String,
        val url: String,
        val fileName: String,
        val sizeBytes: Long,
        val prerelease: Boolean,
        val publishedAt: String
    )

    /**
     * ModLoader 仓库配置
     */
    enum class ModLoaderRepo(
        val owner: String,
        val repo: String,
        val assetPattern: Regex,
        val fileNameTemplate: (String) -> String,
        /** 为 true 时保留资产原始文件名（含扩展名），用于资产名不固定的游戏本体仓库 */
        val preserveAssetName: Boolean = false
    ) {
        TMODLOADER(
            owner = "tModLoader",
            repo = "tModLoader",
            assetPattern = Regex("tModLoader\\.zip", RegexOption.IGNORE_CASE),
            fileNameTemplate = { version -> "tModLoader-$version.zip" }
        ),
        SMAPI(
            owner = "Pathoschild",
            repo = "SMAPI",
            assetPattern = Regex("SMAPI-[\\d.]+-installer\\.zip", RegexOption.IGNORE_CASE),
            fileNameTemplate = { version -> "SMAPI-$version-installer.zip" }
        ),
        EVEREST(
            owner = "EverestAPI",
            repo = "Everest",
            assetPattern = Regex("main\\.zip", RegexOption.IGNORE_CASE),
            fileNameTemplate = { version -> "Everest-$version.zip" }
        ),
        // 原版游戏本体（启动器社区仓库）：每个 Release 放一个游戏文件包（.sh 安装包或 .zip），
        // 版本列表完全由 GitHub API 驱动 —— 发布新 Release 即自动出现，兼容后续游戏版本。
        // 资产名需含 "terraria"（不区分大小写），保证安装器按文件名识别为 Terraria。
        TERRARIA_GAME_FILES(
            owner = "wwr-12",
            repo = "RotatingartLauncher",
            assetPattern = Regex(".*\\.(?:sh|zip)", RegexOption.IGNORE_CASE),
            fileNameTemplate = { version -> "Terraria-$version" },
            preserveAssetName = true
        );

        companion object {
            fun fromModLoaderName(name: String): ModLoaderRepo? {
                return when (name.lowercase()) {
                    "tmodloader" -> TMODLOADER
                    "smapi" -> SMAPI
                    "everest" -> EVEREST
                    "terraria-game" -> TERRARIA_GAME_FILES
                    else -> null
                }
            }
        }
    }

    /**
     * 获取 ModLoader 的全量版本列表（含预发布版，按发布时间从新到旧）
     * 版本列表完全来自 GitHub API，仓库更新后自动包含新版本
     *
     * @param useMirror 是否优先走 gh-proxy 加速站；请求失败会自动切换到另一条路径重试
     */
    fun fetchAllVersions(
        modLoaderName: String,
        useMirror: Boolean = false,
        maxCount: Int = FULL_LIST_PER_PAGE * FULL_LIST_MAX_PAGES
    ): List<ModLoaderReleaseVersion> {
        val repo = ModLoaderRepo.fromModLoaderName(modLoaderName) ?: run {
            AppLog.w(TAG, "未知的 ModLoader: $modLoaderName")
            return emptyList()
        }

        // 直连与镜像互为兜底：用户选的路径优先，失败自动换另一条
        val mirrorOrder = if (useMirror) listOf(true, false) else listOf(false, true)
        var lastError: Exception? = null

        for (useProxy in mirrorOrder) {
            try {
                val versions = fetchAllVersionsVia(repo, useProxy, maxCount)
                if (versions.isNotEmpty()) return versions
            } catch (e: Exception) {
                AppLog.w(TAG, "获取 $modLoaderName 版本列表失败${if (useProxy) "(镜像)" else "(直连)"}: ${e.message}")
                lastError = e
            }
        }

        lastError?.let { throw it }
        return emptyList()
    }

    private fun fetchAllVersionsVia(
        repo: ModLoaderRepo,
        useProxy: Boolean,
        maxCount: Int
    ): List<ModLoaderReleaseVersion> {
        val releases = mutableListOf<GitHubRelease>()
        val seenTags = mutableSetOf<String>()
        var pageError: Exception? = null

        for (page in 1..FULL_LIST_MAX_PAGES) {
            try {
                val pageReleases = fetchGitHubReleases(
                    owner = repo.owner,
                    repo = repo.repo,
                    perPage = FULL_LIST_PER_PAGE,
                    page = page,
                    useProxy = useProxy
                )
                val fresh = pageReleases.filter { seenTags.add(it.tagName) }
                releases.addAll(fresh)
                if (pageReleases.size < FULL_LIST_PER_PAGE) break
            } catch (e: Exception) {
                // 保留已成功获取的页，后续页失败不再继续
                pageError = e
                break
            }
        }

        if (releases.isEmpty()) {
            pageError?.let { throw it }
            return emptyList()
        }

        return releases
            .filter { !it.draft }
            .take(maxCount)
            .mapNotNull { release ->
                // 全量列表只保留带 tModLoader.zip 资产的版本：
                // 1.3 时代的旧版只有分平台资产（如 tModLoader.Linux.v*.zip），
                // 目录结构与现代版不同，本启动器无法安装，不提供给用户选择
                val asset = release.assets.find { repo.assetPattern.matches(it.name) }
                    ?: return@mapNotNull null

                // 游戏本体仓库保留资产原始名；若资产名不含 "terraria"（安装器靠文件名识别），
                // 归一化为 Terraria-<tag>.<ext>
                val localFileName = if (repo.preserveAssetName) {
                    if (asset.name.contains("terraria", ignoreCase = true)) {
                        asset.name
                    } else {
                        val ext = asset.name.substringAfterLast('.', "")
                        if (ext.isEmpty()) "Terraria-${release.tagName}"
                        else "Terraria-${release.tagName}.$ext"
                    }
                } else {
                    repo.fileNameTemplate(release.tagName)
                }

                ModLoaderReleaseVersion(
                    version = release.tagName,
                    displayName = release.name.ifBlank { release.tagName },
                    url = asset.downloadUrl,
                    fileName = localFileName,
                    sizeBytes = asset.size,
                    prerelease = release.prerelease,
                    publishedAt = release.publishedAt
                )
            }
    }

    /**
     * 获取 ModLoader 的最新稳定版本列表
     * @param modLoaderName ModLoader 名称 (tModLoader, SMAPI, Everest)
     * @param maxCount 最大返回数量
     * @param includePrerelease 是否包含预发布版本（已弃用，始终只返回稳定版）
     */
    fun fetchVersions(
        modLoaderName: String,
        maxCount: Int = MAX_STABLE_VERSIONS,
        @Suppress("UNUSED_PARAMETER") includePrerelease: Boolean = false
    ): List<ModLoaderVersion> {
        val repo = ModLoaderRepo.fromModLoaderName(modLoaderName) ?: run {
            AppLog.w(TAG, "未知的 ModLoader: $modLoaderName")
            return emptyList()
        }

        return try {
            val releases = fetchGitHubReleases(repo.owner, repo.repo)
            
            // 只获取稳定版本（非 draft、非 prerelease）
            releases
                .filter { !it.draft && !it.prerelease }
                .take(maxCount)
                .mapNotNull { release ->
                    val asset = release.assets.find { repo.assetPattern.matches(it.name) }
                    
                    if (asset != null) {
                        ModLoaderVersion(
                            version = release.tagName,
                            url = asset.downloadUrl,
                            fileName = repo.fileNameTemplate(release.tagName),
                            stable = true
                        )
                    } else {
                        val downloadUrl = buildFallbackDownloadUrl(repo, release.tagName)
                        ModLoaderVersion(
                            version = release.tagName,
                            url = downloadUrl,
                            fileName = repo.fileNameTemplate(release.tagName),
                            stable = true
                        )
                    }
                }
        } catch (e: Exception) {
            AppLog.e(TAG, "获取 $modLoaderName 版本失败", e)
            emptyList()
        }
    }

    /**
     * 从 GitHub API 获取 Releases
     * @param useProxy 是否经 gh-proxy 加速站代理 API 请求
     */
    private fun fetchGitHubReleases(
        owner: String,
        repo: String,
        perPage: Int = FETCH_COUNT,
        page: Int = 1,
        useProxy: Boolean = false
    ): List<GitHubRelease> {
        val url = "$GITHUB_API/$owner/$repo/releases?per_page=$perPage&page=$page"
        val requestUrl = if (useProxy) GITHUB_PROXY_PREFIX + url else url
        HttpUrlValidator.requirePublicHttpUrl(requestUrl)

        val conn = URL(requestUrl).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
            conn.setRequestProperty("User-Agent", "RotatingartLauncher")
            conn.connectTimeout = 15000
            conn.readTimeout = 20000

            if (conn.responseCode != 200) {
                throw java.io.IOException("HTTP ${conn.responseCode}")
            }

            val response = BufferedReader(InputStreamReader(conn.inputStream, StandardCharsets.UTF_8))
                .use { it.readText() }

            return parseReleasesJson(response)
        } finally {
            conn.disconnect()
        }
    }

    /**
     * 解析 GitHub Releases JSON
     */
    private fun parseReleasesJson(json: String): List<GitHubRelease> {
        val releases = mutableListOf<GitHubRelease>()
        val array = JSONArray(json)

        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            
            val assets = mutableListOf<GitHubAsset>()
            val assetsArray = obj.optJSONArray("assets")
            if (assetsArray != null) {
                for (j in 0 until assetsArray.length()) {
                    val assetObj = assetsArray.getJSONObject(j)
                    assets.add(GitHubAsset(
                        name = assetObj.optString("name", ""),
                        downloadUrl = assetObj.optString("browser_download_url", ""),
                        size = assetObj.optLong("size", 0)
                    ))
                }
            }

            releases.add(GitHubRelease(
                tagName = obj.optString("tag_name", ""),
                name = obj.optString("name", ""),
                prerelease = obj.optBoolean("prerelease", false),
                draft = obj.optBoolean("draft", false),
                publishedAt = obj.optString("published_at", ""),
                assets = assets
            ))
        }

        return releases
    }

    /**
     * 构建备用下载链接（某些项目使用固定格式）
     */
    private fun buildFallbackDownloadUrl(repo: ModLoaderRepo, version: String): String {
        return when (repo) {
            ModLoaderRepo.TMODLOADER -> 
                "https://github.com/tModLoader/tModLoader/releases/download/$version/tModLoader.zip"
            ModLoaderRepo.SMAPI -> 
                "https://github.com/Pathoschild/SMAPI/releases/download/$version/SMAPI-${version.removePrefix("v")}-installer.zip"
            ModLoaderRepo.EVEREST ->
                "https://github.com/EverestAPI/Everest/releases/download/$version/main.zip"
            // 游戏本体无固定资产名可回退（fetchVersions 的兜底路径），对话框走的是
            // fetchAllVersions（必须有匹配资产），此分支仅为 when 穷尽性而存在
            ModLoaderRepo.TERRARIA_GAME_FILES -> ""
        }
    }
}
