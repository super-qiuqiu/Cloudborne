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
}
