package com.cloudborne.android.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionParserTest {
    @Test
    fun parsesSupportedNodesAndSkipsUnsupportedLines() {
        val input = """
            # comment
            vless://11111111-1111-1111-1111-111111111111@example.com:443?security=tls#TLS%20Node
            trojan://password@example.org:443?sni=example.org#Trojan
            ss://YWVzLTI1Ni1nY206cGFzc3dvcmQ=@example.net:8388#SS
            http://unsupported.example:80#skip
        """.trimIndent()

        val nodes = SubscriptionParser.parse(input)

        assertEquals(3, nodes.size)
        assertEquals("TLS Node", nodes[0].name)
        assertEquals("vless", nodes[0].scheme)
        assertEquals("trojan", nodes[1].scheme)
        assertEquals("ss", nodes[2].scheme)
    }

    @Test
    fun decodesBase64SubscriptionWithPadding() {
        val plain = "vless://11111111-1111-1111-1111-111111111111@example.com:443#node"
        val encoded = java.util.Base64.getEncoder().encodeToString(plain.toByteArray())

        val nodes = SubscriptionParser.parseEncoded(encoded)

        assertTrue(nodes.single().name == "node")
    }
}
