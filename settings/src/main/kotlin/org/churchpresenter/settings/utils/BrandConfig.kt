package org.churchpresenter.settings.utils

/**
 * Centralized branding, website, and distribution configuration for Apresenter.
 *
 * Leave [WEBSITE_URL], [CUSTOM_UPDATE_MANIFEST_URL], [PING_URL], and [CONTACT_API_URL] blank
 * until the official website is live; optional web telemetry and custom update endpoints skip
 * remote calls cleanly when empty.
 */
object BrandConfig {
    const val APP_NAME = "Apresenter"
    const val ASSISTANT_NAME = "Timóteo"
    const val AUTHOR_NAME = "Mizael G Nascimento"

    const val HOME_FOLDER = ".apresenter"
    const val APP_FOLDER = "Apresenter"
    const val SINGLE_INSTANCE_PORT = 47642

    const val GITHUB_OWNER = "viebmiza"
    const val GITHUB_REPO = "Apresenter"
    const val GITHUB_URL = "https://github.com/$GITHUB_OWNER/$GITHUB_REPO"
    const val RELEASES_API_URL =
        "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases?per_page=50"
    const val RELEASES_URL = "$GITHUB_URL/releases/latest"
    const val PULLS_URL = "$GITHUB_URL/pull"
    const val BUG_REPORT_URL = "$GITHUB_URL/issues/new?template=bug_report.md"
    const val FEATURE_REQUEST_URL = "$GITHUB_URL/issues/new?template=feature_request.md"
    const val WICK_PACK_URL =
        "https://raw.githubusercontent.com/$GITHUB_OWNER/$GITHUB_REPO/main/wick-pack/pack.json"

    // Plug in the user's future domain/manifest when ready (e.g. "https://apresenter.org" /
    // "https://apresenter.org/updates/latest.json"). Empty keeps external site calls disabled.
    const val WEBSITE_URL = ""
    const val DOCS_URL = "$GITHUB_URL#readme"
    const val TUTORIALS_URL = "$GITHUB_URL#readme"
    const val BLOG_URL = GITHUB_URL
    const val CONTACT_WEB_URL = "$GITHUB_URL/issues"
    const val CONTACT_API_URL = ""
    const val PING_URL = ""
    const val DOWNLOAD_BEACON_URL = ""
    const val CUSTOM_UPDATE_MANIFEST_URL = ""

    const val SSL_KEY_ALIAS = "apresenter"
    const val SSL_KEYSTORE_PASSWORD = "apresenter_ssl"
    const val CA_ALIAS = "apresenter-ca"

    /** Resolves [path] against [WEBSITE_URL] when configured, or returns [fallback]. */
    fun websitePageUrl(path: String, fallback: String): String {
        val base = WEBSITE_URL.trim().trimEnd('/')
        if (base.isBlank()) return fallback
        val normalizedPath = path.trim().trimStart('/')
        return if (normalizedPath.isEmpty()) base else "$base/$normalizedPath"
    }
}
