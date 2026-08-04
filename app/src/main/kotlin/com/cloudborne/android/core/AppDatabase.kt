package com.cloudborne.android.core

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase

@Entity(tableName = "subscriptions")
data class SubscriptionEntity(
    @androidx.room.PrimaryKey val id: String,
    val name: String,
    val url: String,
)

@Entity(
    tableName = "proxy_nodes",
    primaryKeys = ["id"],
)
data class ProxyNodeEntity(
    val id: String,
    val subscriptionId: String,
    val name: String,
    val scheme: String,
    val server: String,
    val port: Int,
    val userInfo: String,
    val query: String,
)

@Dao
interface SubscriptionDao {
    @Query("SELECT * FROM subscriptions ORDER BY name")
    fun subscriptions(): List<SubscriptionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(subscription: SubscriptionEntity)

    @Query("DELETE FROM subscriptions WHERE id = :id")
    fun delete(id: String)
}

@Dao
interface ProxyNodeDao {
    @Query("SELECT * FROM proxy_nodes WHERE subscriptionId = :subscriptionId ORDER BY name")
    fun nodes(subscriptionId: String): List<ProxyNodeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertAll(nodes: List<ProxyNodeEntity>)

    @Query("DELETE FROM proxy_nodes WHERE subscriptionId = :subscriptionId")
    fun deleteForSubscription(subscriptionId: String)
}

@Database(
    entities = [SubscriptionEntity::class, ProxyNodeEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun subscriptions(): SubscriptionDao
    abstract fun nodes(): ProxyNodeDao
}
