package com.cloudborne.android.core

import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyNodeTest {
    @Test
    fun singBoxOutboundQuotesStringFields() {
        val node = ProxyNode(
            id = "node-1",
            name = "TLS",
            scheme = "vless",
            server = "example.com",
            port = 443,
            userInfo = "11111111-1111-1111-1111-111111111111",
            query = mapOf("security" to "tls"),
        )

        val json = node.toSingBoxOutbound()

        assertTrue(json.contains("\"type\":\"vless\""))
        assertTrue(json.contains("\"tag\":\"proxy\""))
        assertTrue(json.contains("\"server\":\"example.com\""))
        assertTrue(!json.contains("\"type\":vless"))
    }

    @Test
    fun shadowsocksObfsEmitsSingBoxSip003PluginFields() {
        val node = ProxyNode(
            id = "node-ss-obfs",
            name = "SS-Obfs",
            scheme = "ss",
            server = "edge-1.example.com",
            port = 2377,
            userInfo = "chacha20-ietf-poly1305:secret",
            query = mapOf(
                "plugin" to "obfs",
                "plugin_mode" to "tls",
                "plugin_host" to "edge-1.default.example.net",
            ),
        )

        val json = node.toSingBoxOutbound()

        assertTrue(json.contains("\"type\":\"shadowsocks\""))
        assertTrue(json.contains("\"method\":\"chacha20-ietf-poly1305\""))
        assertTrue(json.contains("\"password\":\"secret\""))
        assertTrue(json.contains("\"plugin\":\"obfs-local\""))
        assertTrue(json.contains("\"plugin_opts\":\"obfs=tls;obfs-host=edge-1.default.example.net;\""))
    }
}
