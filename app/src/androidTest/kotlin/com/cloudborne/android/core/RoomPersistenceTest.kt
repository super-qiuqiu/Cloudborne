package com.cloudborne.android.core

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomPersistenceTest {
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun replacingSubscriptionNodesIsIdempotent() {
        val subscription = SubscriptionEntity("sub-1", "Test", "https://example.test/sub")
        val node = ProxyNodeEntity("node-1", "sub-1", "Node", "vless", "example.test", 443, "uuid", "{}")
        val subscriptions = database.subscriptions()
        val nodes = database.nodes()

        subscriptions.upsert(subscription)
        nodes.upsertAll(listOf(node))
        nodes.deleteForSubscription(subscription.id)
        nodes.upsertAll(listOf(node))

        assertEquals(1, subscriptions.subscriptions().size)
        assertEquals(1, nodes.nodes(subscription.id).size)
    }
}
