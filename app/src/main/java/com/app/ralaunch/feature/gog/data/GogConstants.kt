package com.app.ralaunch.feature.gog.data

/**
 * GOG 全局常量
 * 借鉴 lgogdownloader 的 globalconstants.h 设计
 */
object GogConstants {
    
    // ==================== API URLs ====================
    const val AUTH_URL = "https://auth.gog.com/token"
    const val AUTH_FORM_URL = "https://auth.gog.com/auth"
    const val LOGIN_CHECK_URL = "https://login.gog.com/login_check"
    const val TWO_STEP_URL = "https://login.gog.com/login/two_step"
    const val TOTP_URL = "https://login.gog.com/login/two_factor/totp"
    
    const val USER_DATA_URL = "https://embed.gog.com/userData.json"
    const val GAMES_URL = "https://embed.gog.com/account/getFilteredProducts"
    const val PRODUCT_API_URL = "https://api.gog.com/products"
    
    const val CONTENT_SYSTEM_URL = "https://content-system.gog.com"
    const val CDN_URL = "https://cdn.gog.com"
    const val DEPENDENCIES_URL = "https://content-system.gog.com/dependencies/repository"
    
    // ==================== OAuth ====================
    const val CLIENT_ID = "46899977096215655"
    const val CLIENT_SECRET = "9d85c43b1482497dbbce61f6e4aa173a433796eeae2ca8c5f6129f2dc4de46d9"
    const val REDIRECT_URI = "https://embed.gog.com/on_login_success?origin=client"
    
    // ==================== 网络配置 ====================
    const val MAX_RETRIES = 3
    const val RETRY_DELAY_MS = 1000
    const val MAX_REDIRECTS = 10
    const val CONNECT_TIMEOUT_MS = 15000
    const val READ_TIMEOUT_MS = 15000
    const val DOWNLOAD_TIMEOUT_MS = 20000
    
    // ==================== SharedPreferences ====================
    const val PREF_NAME = "gog_auth"
    const val PREF_ACCESS_TOKEN = "access_token"
    const val PREF_REFRESH_TOKEN = "refresh_token"
    const val PREF_TOKEN_EXPIRY = "token_expiry"
    
    // ==================== 平台常量 ====================
    object Platform {
        const val WINDOWS = 1 shl 0  // 1
        const val MAC = 1 shl 1      // 2
        const val LINUX = 1 shl 2    // 4
        
        val ALL = listOf(
            PlatformInfo(WINDOWS, "win", "Windows"),
            PlatformInfo(MAC, "mac", "Mac"),
            PlatformInfo(LINUX, "linux", "Linux")
        )
        
        fun fromCode(code: String): Int = when (code.lowercase()) {
            "win", "windows" -> WINDOWS
            "mac", "osx" -> MAC
            "lin", "linux" -> LINUX
            else -> 0
        }
        
        fun toCode(platform: Int): String = when (platform) {
            WINDOWS -> "windows"
            MAC -> "mac"
            LINUX -> "linux"
            else -> "unknown"
        }
    }
    
    data class PlatformInfo(val id: Int, val code: String, val name: String)
    
    // ==================== 架构常量 ====================
    object Arch {
        const val X86 = 1 shl 0  // 32-bit
        const val X64 = 1 shl 1  // 64-bit
        
        val ALL = listOf(
            ArchInfo(X86, "32", "32-bit"),
            ArchInfo(X64, "64", "64-bit")
        )
        
        fun fromCode(code: String): Int = when (code) {
            "32", "x86", "32bit", "32-bit" -> X86
            "64", "x64", "64bit", "64-bit" -> X64
            else -> X64 // 默认 64 位
        }
    }
    
    data class ArchInfo(val id: Int, val code: String, val name: String)
    
    // ==================== 语言常量 ====================
    object Language {
        const val EN = 1 shl 0
        const val DE = 1 shl 1
        const val FR = 1 shl 2
        const val PL = 1 shl 3
        const val RU = 1 shl 4
        const val CN = 1 shl 5
        const val CZ = 1 shl 6
        const val ES = 1 shl 7
        const val HU = 1 shl 8
        const val IT = 1 shl 9
        const val JP = 1 shl 10
        const val TR = 1 shl 11
        const val PT = 1 shl 12
        const val KO = 1 shl 13
        const val NL = 1 shl 14
        const val SV = 1 shl 15
        const val NO = 1 shl 16
        const val DA = 1 shl 17
        const val FI = 1 shl 18
        const val PT_BR = 1 shl 19
        const val SK = 1 shl 20
        const val BG = 1 shl 21
        const val UK = 1 shl 22
        const val ES_MX = 1 shl 23
        const val AR = 1 shl 24
        const val RO = 1 shl 25
        const val HE = 1 shl 26
        const val TH = 1 shl 27
        
        val ALL = listOf(
            LanguageInfo(EN, "en", "English"),
            LanguageInfo(DE, "de", "German"),
            LanguageInfo(FR, "fr", "French"),
            LanguageInfo(PL, "pl", "Polish"),
            LanguageInfo(RU, "ru", "Russian"),
            LanguageInfo(CN, "cn", "Chinese"),
            LanguageInfo(CZ, "cz", "Czech"),
            LanguageInfo(ES, "es", "Spanish"),
            LanguageInfo(HU, "hu", "Hungarian"),
            LanguageInfo(IT, "it", "Italian"),
            LanguageInfo(JP, "jp", "Japanese"),
            LanguageInfo(TR, "tr", "Turkish"),
            LanguageInfo(PT, "pt", "Portuguese"),
            LanguageInfo(KO, "ko", "Korean"),
            LanguageInfo(NL, "nl", "Dutch"),
            LanguageInfo(SV, "sv", "Swedish"),
            LanguageInfo(NO, "no", "Norwegian"),
            LanguageInfo(DA, "da", "Danish"),
            LanguageInfo(FI, "fi", "Finnish"),
            LanguageInfo(PT_BR, "br", "Brazilian Portuguese"),
            LanguageInfo(SK, "sk", "Slovak"),
            LanguageInfo(BG, "bl", "Bulgarian"),
            LanguageInfo(UK, "uk", "Ukrainian"),
            LanguageInfo(ES_MX, "es_mx", "Spanish (Latin American)"),
            LanguageInfo(AR, "ar", "Arabic"),
            LanguageInfo(RO, "ro", "Romanian"),
            LanguageInfo(HE, "he", "Hebrew"),
            LanguageInfo(TH, "th", "Thai")
        )
        
        fun fromCode(code: String): Int {
            return ALL.find { it.code.equals(code, ignoreCase = true) }?.id ?: EN
        }
    }
    
    data class LanguageInfo(val id: Int, val code: String, val name: String)
    
    // ==================== 文件类型常量 ====================
    object FileType {
        const val BASE_INSTALLER = 1 shl 0
        const val BASE_EXTRA = 1 shl 1
        const val BASE_PATCH = 1 shl 2
        const val BASE_LANGPACK = 1 shl 3
        const val DLC_INSTALLER = 1 shl 4
        const val DLC_EXTRA = 1 shl 5
        const val DLC_PATCH = 1 shl 6
        const val DLC_LANGPACK = 1 shl 7
        
        const val DLC = DLC_INSTALLER or DLC_EXTRA or DLC_PATCH or DLC_LANGPACK
        const val BASE = BASE_INSTALLER or BASE_EXTRA or BASE_PATCH or BASE_LANGPACK
        const val INSTALLER = BASE_INSTALLER or DLC_INSTALLER
        const val EXTRA = BASE_EXTRA or DLC_EXTRA
        const val PATCH = BASE_PATCH or DLC_PATCH
        const val LANGPACK = BASE_LANGPACK or DLC_LANGPACK
    }
}
