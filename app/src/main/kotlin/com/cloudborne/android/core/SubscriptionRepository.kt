package com.cloudborne.android.core

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class SubscriptionRepository(context: Context) {
    private val database = Room.databaseBuilder(
        context.applicationContext,
        AppDatabase::class.java,
        "cloudborne.db",
    ).build()

    suspend fun load(): List<Subscription> = withContext(Dispatchers.IO) {
        database.subscriptions().subscriptions().map { subscription ->
            Subscription(
                id = subscription.id,
                name = subscription.name,
                url = subscription.url,
                nodes = database.nodes().nodes(subscription.id).map(::toDomain),
            )
        }
    }

    suspend fun save(subscription: Subscription) = withContext(Dispatchers.IO) {
        database.runInTransaction {
            database.subscriptions().upsert(
                SubscriptionEntity(subscription.id, subscription.name, subscription.url)
            )
            database.nodes().deleteForSubscription(subscription.id)
            database.nodes().upsertAll(subscription.nodes.map { it.toEntity(subscription.id) })
        }
    }

    suspend fun refresh(subscription: Subscription): Subscription = withContext(Dispatchers.IO) {
        val connection = (URL(subscription.url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", "Cloudborne/0.1")
        }
        try {
            if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val refreshed = subscription.copy(nodes = SubscriptionParser.parseEncoded(body))
            save(refreshed)
            refreshed
        } finally {
            connection.disconnect()
        }
    }

    private fun toDomain(entity: ProxyNodeEntity): ProxyNode = ProxyNode(
        id = entity.id,
        name = entity.name,
        scheme = entity.scheme,
        server = entity.server,
        port = entity.port,
        userInfo = entity.userInfo,
        query = JSONObject(entity.query).let { json ->
            json.keys().asSequence().associateWith { key -> json.getString(key) }
        },
    )

    private fun ProxyNode.toEntity(subscriptionId: String) = ProxyNodeEntity(
        id = id,
        subscriptionId = subscriptionId,
        name = name,
        scheme = scheme,
        server = server,
        port = port,
        userInfo = userInfo,
        query = JSONObject(query).toString(),
    )
}
