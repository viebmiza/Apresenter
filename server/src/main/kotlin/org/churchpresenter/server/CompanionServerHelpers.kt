package org.churchpresenter.server

import io.ktor.http.ContentType
import java.net.Inet4Address
import java.net.NetworkInterface

private const val FIRST_PRINTABLE_CHAR = 0x20
private const val IFACE_RANK_ETHERNET = 0
private const val IFACE_RANK_EN_SECONDARY = 1
private const val IFACE_RANK_WIFI = 2
private const val IFACE_RANK_OTHER = 10
private const val MAX_PTP_PREFIX_LENGTH = 31

private val VIRTUAL_OR_VPN_MARKERS = listOf(
    "warp", "cloudflare", "vpn", "tunnel", "tap", "tun", "tailscale",
    "zerotier", "wireguard", "docker", "veth", "hyper-v", "wsl",
    "virtualbox", "vmware", "pseudo", "loopback", "wan miniport",
)

/**
 * Small pure helpers used by `CompanionServer` and its route groups. None of them read server
 * state, so they live here rather than as members.
 */
internal fun jsonEscape(s: String): String = buildString {
    for (c in s) {
        when {
            c == '\\' -> append("\\\\")
            c == '"' -> append("\\\"")
            c == '\n' -> append("\\n")
            c == '\r' -> append("\\r")
            c == '\t' -> append("\\t")
            c.code < FIRST_PRINTABLE_CHAR -> append("\\u%04x".format(c.code))
            else -> append(c)
        }
    }
}

internal fun contentTypeForExtension(ext: String): ContentType = when (ext.lowercase()) {
    "jpg", "jpeg" -> ContentType.Image.JPEG
    "png"         -> ContentType.Image.PNG
    "gif"         -> ContentType.Image.GIF
    "webp"        -> ContentType.parse("image/webp")
    "bmp"         -> ContentType.parse("image/bmp")
    "heic", "heif" -> ContentType.parse("image/heic")
    else          -> ContentType.Image.JPEG
}

private fun isVpnOrVirtual(iface: NetworkInterface): Boolean {
    val description = (iface.name + " " + iface.displayName).lowercase()
    return VIRTUAL_OR_VPN_MARKERS.any { description.contains(it) }
}

private fun ifaceRank(iface: NetworkInterface): Int {
    val name = iface.name.lowercase()
    val desc = iface.displayName.lowercase()
    return when {
        name.startsWith("eth") || name == "en0" || desc.contains("ethernet") ||
            desc.contains("gigabit") || desc.contains("lan") -> IFACE_RANK_ETHERNET
        name.startsWith("en") -> IFACE_RANK_EN_SECONDARY
        name.startsWith("wlan") || name.startsWith("wifi") || name.startsWith("wireless") ||
            desc.contains("wi-fi") || desc.contains("wifi") || desc.contains("wireless") ||
            desc.contains("802.11") -> IFACE_RANK_WIFI
        else -> IFACE_RANK_OTHER
    }
}

/**
 * Returns the best local IPv4 address for display in the Server URL.
 *
 * Preference order (most-stable first):
 *   1. Wired Ethernet  — eth*, en0 (macOS primary), Gigabit LAN
 *   2. Other en* interfaces (macOS secondary)
 *   3. wlan* / wifi* / wireless* (Linux & Windows Wi-Fi)
 *   4. Everything else (non-loopback, non-virtual, non-VPN, prefix < 32)
 *
 * Virtual, VPN and tunnel interfaces (Cloudflare WARP, Tailscale, Docker, TAP, etc.)
 * are filtered out so that remote devices on the local Wi-Fi can always reach this PC.
 */
internal fun localIpAddress(): String {
    return try {
        val all = NetworkInterface.getNetworkInterfaces().asSequence().toList()

        // Phase 1: Physical LAN interfaces (filtering out VPNs and point-to-point tunnels)
        val bestLan = all.asSequence()
            .filter { iface -> iface.isUp && !iface.isLoopback && !iface.isVirtual && !isVpnOrVirtual(iface) }
            .sortedWith(compareBy({ ifaceRank(it) }, { it.name }))
            .flatMap { it.interfaceAddresses.asSequence() }
            .filter { ia ->
                val addr = ia.address
                addr is Inet4Address && !addr.isLoopbackAddress && !addr.isLinkLocalAddress &&
                    ia.networkPrefixLength <= MAX_PTP_PREFIX_LENGTH
            }
            .map { it.address.hostAddress }
            .firstOrNull()

        if (bestLan != null) return bestLan

        // Phase 2: Fallback to any non-loopback IPv4 if no physical LAN interface was found
        all.asSequence()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.asSequence() }
            .firstOrNull { addr -> addr is Inet4Address && !addr.isLoopbackAddress }
            ?.hostAddress ?: "localhost"
    } catch (_: Exception) {
        "localhost"
    }
}
