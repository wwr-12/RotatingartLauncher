package com.app.ralaunch.core.common.util

import java.net.Inet4Address
import java.net.InetAddress
import java.net.MalformedURLException
import java.net.URI
import java.net.URL

/**
 * 远程请求 URL 安全校验：
 * 仅允许 http/https 协议，且目标主机不得是 localhost、环回、私有或保留地址。
 */
object HttpUrlValidator {

    fun requirePublicHttpUrl(url: String): URL {
        val uri = try {
            URI(url)
        } catch (e: Exception) {
            throw MalformedURLException("Invalid URL: $url")
        }

        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") {
            throw MalformedURLException("Only http/https URLs are allowed: $url")
        }

        val host = uri.host?.lowercase()
            ?: throw MalformedURLException("URL has no host: $url")
        if (isForbiddenHostname(host)) {
            throw MalformedURLException("Forbidden host: $host")
        }

        // 解析所有地址逐一校验，防止域名解析到内网地址
        val addresses = try {
            InetAddress.getAllByName(host)
        } catch (e: Exception) {
            throw MalformedURLException("Cannot resolve host: $host")
        }
        if (addresses.isEmpty() || addresses.any { isPrivateOrReserved(it) }) {
            throw MalformedURLException("Forbidden address for host: $host")
        }

        return uri.toURL()
    }

    private fun isForbiddenHostname(host: String): Boolean {
        if (host == "localhost" || host.endsWith(".localhost") || host == "localhost.localdomain") {
            return true
        }
        // 纯数字或冒号形式视为 IP 字面量，直接按地址校验
        if (host.matches(Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")) || host.contains(':')) {
            return try {
                isPrivateOrReserved(InetAddress.getByName(host))
            } catch (e: Exception) {
                true
            }
        }
        return false
    }

    private fun isPrivateOrReserved(address: InetAddress): Boolean {
        if (address.isLoopbackAddress ||
            address.isAnyLocalAddress ||
            address.isLinkLocalAddress ||
            address.isMulticastAddress
        ) {
            return true
        }

        val bytes = address.address
        return when (address) {
            is Inet4Address -> isPrivateOrReservedIpv4(bytes)
            else -> isPrivateOrReservedIpv6(bytes)
        }
    }

    private fun isPrivateOrReservedIpv4(bytes: ByteArray): Boolean {
        val b0 = bytes[0].toInt() and 0xFF
        val b1 = bytes[1].toInt() and 0xFF
        val b2 = bytes[2].toInt() and 0xFF
        return b0 == 0 ||                                // 0.0.0.0/8
            b0 == 10 ||                                  // 10.0.0.0/8
            (b0 == 100 && b1 in 64..127) ||              // 100.64.0.0/10 (CGNAT)
            (b0 == 172 && b1 in 16..31) ||               // 172.16.0.0/12
            (b0 == 192 && b1 == 168) ||                  // 192.168.0.0/16
            (b0 == 192 && b1 == 0 && b2 == 0) ||         // 192.0.0.0/24
            (b0 == 198 && b1 in 18..19) ||               // 198.18.0.0/15
            b0 >= 240                                    // 240.0.0.0/4 保留段
    }

    private fun isPrivateOrReservedIpv6(bytes: ByteArray): Boolean {
        // IPv4-mapped 地址（::ffff:a.b.c.d）按 IPv4 规则校验
        val isV4Mapped = bytes[10] == 0xFF.toByte() && bytes[11] == 0xFF.toByte() &&
            bytes.copyOfRange(0, 10).all { it == 0.toByte() }
        if (isV4Mapped) {
            return isPrivateOrReservedIpv4(bytes.copyOfRange(12, 16))
        }
        // fc00::/7 唯一本地地址（ULA）
        return (bytes[0].toInt() and 0xFE) == 0xFC
    }
}
