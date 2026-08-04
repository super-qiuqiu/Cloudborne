package com.cloudborne.android.core

data class ProxyNode(
    val id: String,
    val name: String,
    val scheme: String,
    val server: String,
    val port: Int,
    val userInfo: String,
    val query: Map<String, String> = emptyMap(),
) {
    fun toSingBoxOutbound(): String {
        val type = when (scheme.lowercase()) {
            "vless" -> "vless"
            "trojan" -> "trojan"
            "ss", "shadowsocks" -> "shadowsocks"
            else -> "direct"
        }
        if (type == "direct") return "{\"type\":\"direct\",\"tag\":\"proxy\"}"
        val fields = linkedMapOf<String, String>()
        fields["type"] = jsonString(type)
        fields["tag"] = jsonString("proxy")
        fields["server"] = jsonString(server)
        fields["server_port"] = port.toString()
        when (type) {
            "vless" -> {
                fields["uuid"] = jsonString(userInfo)
                fields["packet_encoding"] = "\"xudp\""
            }
            "trojan" -> fields["password"] = jsonString(userInfo)
            "shadowsocks" -> {
                val parts = userInfo.split(":", limit = 2)
                fields["method"] = jsonString(parts.getOrElse(0) { "aes-128-gcm" })
                fields["password"] = jsonString(parts.getOrElse(1) { "" })
                // SIP003 插件：sing-box 原生支持 obfs-local / v2ray-plugin
                if (query["plugin"] == "obfs") {
                    val mode = query["plugin_mode"] ?: "tls"
                    val host = query["plugin_host"]
                    if (host != null) {
                        fields["plugin"] = jsonString("obfs-local")
                        fields["plugin_opts"] = jsonString("obfs=$mode;obfs-host=$host;")
                    }
                }
            }
        }
        val security = query["security"] ?: query["tls"]
        if (security == "tls" || query["type"] == "ws" || query["sni"] != null) {
            val serverName = query["sni"] ?: query["host"] ?: server
            fields["tls"] = "{\"enabled\":true,\"server_name\":${jsonString(serverName)}}"
        }
        if (query["type"] == "ws") {
            val path = query["path"] ?: "/"
            val host = query["host"] ?: server
            fields["transport"] = "{\"type\":\"ws\",\"path\":${jsonString(path)},\"headers\":{\"Host\":${jsonString(host)}}}"
        }
        return fields.entries.joinToString(prefix = "{", postfix = "}") { "\"${it.key}\":${it.value}" }
    }

    companion object {
        private fun jsonString(value: String): String = buildString {
            append('"')
            value.forEach { char ->
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    else -> append(char)
                }
            }
            append('"')
        }
    }
}

data class Subscription(
    val id: String,
    val name: String,
    val url: String,
    val nodes: List<ProxyNode> = emptyList(),
)
