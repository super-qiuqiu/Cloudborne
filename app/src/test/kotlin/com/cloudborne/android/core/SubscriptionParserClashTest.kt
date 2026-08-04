package com.cloudborne.android.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionParserClashTest {

    private val clashFixture = """
        mixed-port: 7890
        allow-lan: false
        mode: rule
        log-level: info
        proxies:
          - name: "SS-Obfs"
            type: ss
            server: edge-1.example.com
            port: 2377
            udp: true
            cipher: chacha20-ietf-poly1305
            password: "secret-ss"
            plugin: obfs
            plugin-opts:
              mode: tls
              host: edge-1.default.example.net
          - name: Trojan-TLS
            type: trojan
            server: trojan.example.com
            port: 443
            password: "secret-trojan"
            udp: true
            sni: trojan.example.com
          - name: Vless-WS
            type: vless
            server: vless.example.com
            port: 443
            uuid: "22222222-2222-2222-2222-222222222222"
            udp: true
            tls: true
            network: ws
            servername: vless.example.com
            ws-opts:
              path: /ws
              headers:
                Host: vless.example.com
          - name: Hysteria2
            type: hysteria2
            server: h2.example.com
            port: 443
            password: "h2-password"
        proxy-groups:
          - name: "Proxy"
            type: select
            proxies: [ "SS-Obfs", "Trojan-TLS", "Vless-WS" ]
        rules:
          - MATCH,Proxy
    """.trimIndent()

    @Test
    fun parsesClashProxiesAndSkipsUnsupportedTypes() {
        val nodes = SubscriptionParser.parse(clashFixture)

        assertEquals(3, nodes.size)
        assertTrue(nodes.none { it.scheme == "hysteria2" })
    }

    @Test
    fun mapsShadowsocksFieldsIncludingObfsPlugin() {
        val node = SubscriptionParser.parse(clashFixture).first { it.name == "SS-Obfs" }

        assertEquals("ss", node.scheme)
        assertEquals("edge-1.example.com", node.server)
        assertEquals(2377, node.port)
        assertEquals("chacha20-ietf-poly1305:secret-ss", node.userInfo)
        assertEquals("obfs", node.query["plugin"])
        assertEquals("tls", node.query["plugin_mode"])
        assertEquals("edge-1.default.example.net", node.query["plugin_host"])
    }

    @Test
    fun mapsTrojanAndVlessTransportFields() {
        val nodes = SubscriptionParser.parse(clashFixture)

        val trojan = nodes.first { it.name == "Trojan-TLS" }
        assertEquals("secret-trojan", trojan.userInfo)
        assertEquals("tls", trojan.query["security"])
        assertEquals("trojan.example.com", trojan.query["sni"])

        val vless = nodes.first { it.name == "Vless-WS" }
        assertEquals("22222222-2222-2222-2222-222222222222", vless.userInfo)
        assertEquals("ws", vless.query["type"])
        assertEquals("/ws", vless.query["path"])
        assertEquals("vless.example.com", vless.query["host"])
        assertEquals("tls", vless.query["security"])
    }

    @Test
    fun parseEncodedPassesClashYamlThroughUnmodified() {
        val nodes = SubscriptionParser.parseEncoded(clashFixture)

        assertEquals(3, nodes.size)
    }
}
