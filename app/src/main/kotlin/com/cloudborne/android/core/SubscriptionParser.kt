package com.cloudborne.android.core

import java.net.URI
import java.net.URLDecoder
import java.util.Base64
import java.util.UUID

/** Pure subscription decoder/parser; no Android Context or network side effects. */
object SubscriptionParser {
    private val supportedSchemes = setOf("vless", "trojan", "ss", "shadowsocks")

    fun parseEncoded(body: String): List<ProxyNode> = parse(decode(body))

    fun parse(body: String): List<ProxyNode> = body
        .lineSequence()
        .mapNotNull(::parseLine)
        .toList()

    private fun decode(body: String): String {
        val normalized = body.trim()
        if (normalized.contains("://") && normalized.lineSequence().any { it.contains("://") }) {
            return normalized
        }
        val compact = normalized.replace("\\s".toRegex(), "")
        return runCatching {
            val padded = compact + "=".repeat((4 - compact.length % 4) % 4)
            String(Base64.getDecoder().decode(padded), Charsets.UTF_8)
        }.getOrDefault(normalized)
    }

    private fun parseLine(raw: String): ProxyNode? {
        val line = raw.trim()
        if (line.isEmpty() || line.startsWith("#")) return null
        val uri = runCatching { URI(line) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme !in supportedSchemes) return null
        val host = uri.host ?: return null
        val port = uri.port.takeIf { it > 0 } ?: return null
        val query = uri.rawQuery.orEmpty().split('&').mapNotNull { item ->
            val pair = item.split('=', limit = 2)
            if (pair.size == 2) pair[0] to URLDecoder.decode(pair[1], "UTF-8") else null
        }.toMap()
        val userInfo = URLDecoder.decode(uri.userInfo.orEmpty(), "UTF-8")
        val label = uri.fragment?.let { URLDecoder.decode(it, "UTF-8") }
            ?.takeIf { it.isNotBlank() } ?: "$scheme://$host:$port"
        return ProxyNode(
            id = UUID.nameUUIDFromBytes(line.toByteArray()).toString(),
            name = label,
            scheme = scheme,
            server = host,
            port = port,
            userInfo = userInfo,
            query = query,
        )
    }
}
