package com.cloudborne.android.core

import java.net.URI
import java.net.URLDecoder
import java.util.Base64
import java.util.UUID
import org.yaml.snakeyaml.Yaml

/** Pure subscription decoder/parser; no Android Context or network side effects. */
object SubscriptionParser {
    private val supportedSchemes = setOf("vless", "trojan", "ss", "shadowsocks")

    fun parseEncoded(body: String): List<ProxyNode> = parse(decode(body))

    fun parse(body: String): List<ProxyNode> {
        val trimmed = body.trim()
        if (trimmed.contains("proxies:")) return parseClash(trimmed)
        return trimmed.lineSequence().mapNotNull(::parseLine).toList()
    }

    private fun decode(body: String): String {
        val normalized = body.trim()
        if (normalized.contains("proxies:")) return normalized
        if (normalized.contains("://") && normalized.lineSequence().any { it.contains("://") }) {
            return normalized
        }
        val compact = normalized.replace("\\s".toRegex(), "")
        return runCatching {
            val padded = compact + "=".repeat((4 - compact.length % 4) % 4)
            String(Base64.getDecoder().decode(padded), Charsets.UTF_8)
        }.getOrDefault(normalized)
    }

    // ---------- Clash / mihomo YAML ----------

    private fun parseClash(body: String): List<ProxyNode> = runCatching {
        val yaml = Yaml().load<Any>(body)
        val proxies = (yaml as? Map<*, *>)?.get("proxies") as? List<*>
        proxies.orEmpty().withIndex().mapNotNull { (index, item) -> toClashNode(item, index) }
    }.getOrDefault(emptyList())

    private fun toClashNode(item: Any?, index: Int): ProxyNode? {
        if (item !is Map<*, *>) return null
        val type = (item["type"] as? String)?.lowercase() ?: return null
        if (type !in supportedSchemes) return null
        val server = (item["server"] as? String)?.takeIf { it.isNotBlank() } ?: return null
        val port = (item["port"] as? Number)?.toInt()?.takeIf { it > 0 } ?: return null
        val name = (item["name"] as? String)?.takeIf { it.isNotBlank() }
            ?: "$type://$server:$port"

        val query = linkedMapOf<String, String>()
        val userInfo = when (type) {
            "ss", "shadowsocks" -> {
                val cipher = (item["cipher"] as? String) ?: "aes-128-gcm"
                val password = item["password"]?.toString() ?: ""
                cipher + ":" + password
            }
            "trojan" -> item["password"]?.toString() ?: ""
            else -> item["uuid"]?.toString() ?: "" // vless
        }

        // ws/grpc 传输
        (item["network"] as? String)?.let { query["type"] = it }
        (item["ws-opts"] as? Map<*, *>)?.let { ws ->
            (ws["path"] as? String)?.let { query["path"] = it }
            val headers = ws["headers"] as? Map<*, *>
            (headers?.get("Host") as? String)?.let { query["host"] = it }
        }
        // TLS：trojan 协议默认即 TLS；其余按显式 tls 字段
        if (item["tls"] == true || type == "trojan") query["security"] = "tls"
        (item["servername"] as? String)?.let { query["sni"] = it }
        (item["sni"] as? String)?.let { query["sni"] = it }
        if (item["skip-cert-verify"] == true) query["insecure"] = "true"
        // SIP003 插件（如 simple-obfs），sing-box 核心不支持，保留字段供未来核心使用
        (item["plugin"] as? String)?.let { query["plugin"] = it }
        (item["plugin-opts"] as? Map<*, *>)?.let { opts ->
            (opts["mode"] as? String)?.let { query["plugin_mode"] = it }
            (opts["host"] as? String)?.let { query["plugin_host"] = it }
            (opts["path"] as? String)?.let { query["plugin_path"] = it }
        }

        return ProxyNode(
            id = UUID.nameUUIDFromBytes("yaml:$index:$name:$server:$port".toByteArray()).toString(),
            name = name,
            scheme = type,
            server = server,
            port = port,
            userInfo = userInfo,
            query = query,
        )
    }

    // ---------- share-link ----------

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
