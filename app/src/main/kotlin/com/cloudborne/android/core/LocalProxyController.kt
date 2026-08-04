package com.cloudborne.android.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.nekohasekai.libbox.CommandServer
import io.nekohasekai.libbox.CommandServerHandler
import io.nekohasekai.libbox.ConnectionOwner
import io.nekohasekai.libbox.InterfaceUpdateListener
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.LocalDNSTransport
import io.nekohasekai.libbox.NetworkInterfaceIterator
import io.nekohasekai.libbox.Notification as LibboxNotification
import io.nekohasekai.libbox.OverrideOptions
import io.nekohasekai.libbox.PlatformInterface
import io.nekohasekai.libbox.StringBox
import io.nekohasekai.libbox.StringIterator
import io.nekohasekai.libbox.SystemProxyStatus
import io.nekohasekai.libbox.TunOptions
import io.nekohasekai.libbox.WIFIState
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

object LocalProxyController {
    private const val ACTION_START = "com.cloudborne.android.START_PROXY"
    private const val ACTION_STOP = "com.cloudborne.android.STOP_PROXY"
    const val SOCKS_PORT = 2080

    suspend fun start(context: Context, node: ProxyNode): Result<Unit> {
        return runCatching {
            val intent = Intent(context, LocalProxyService::class.java).apply {
                action = ACTION_START
                putExtra("config", buildConfig(node))
            }
            ContextCompat.startForegroundService(context, intent)
            awaitPort()
        }
    }

    private suspend fun awaitPort() = withContext(Dispatchers.IO) {
        repeat(40) {
            if (runCatching {
                    Socket().use { socket ->
                        socket.connect(InetSocketAddress("127.0.0.1", SOCKS_PORT), 200)
                    }
                    true
                }.getOrDefault(false)
            ) return@withContext
            delay(250)
        }
        error("本地 SOCKS 服务未在 ${SOCKS_PORT} 端口监听")
    }

    fun stop(context: Context) {
        context.startService(Intent(context, LocalProxyService::class.java).setAction(ACTION_STOP))
    }

    private fun buildConfig(node: ProxyNode): String = """
        {
          "log":{"level":"info"},
          "inbounds":[{"type":"mixed","tag":"mixed-in","listen":"127.0.0.1","listen_port":$SOCKS_PORT}],
          "outbounds":[${node.toSingBoxOutbound()},{"type":"direct","tag":"direct"}],
          "route":{"final":"proxy"}
        }
    """.trimIndent()

    internal fun isStart(action: String?) = action == ACTION_START
    internal fun isStop(action: String?) = action == ACTION_STOP
}

class LocalProxyService : Service() {
    private var commandServer: CommandServer? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(1, notification("代理服务准备中"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when {
            LocalProxyController.isStop(intent?.action) -> stopProxy()
            LocalProxyController.isStart(intent?.action) -> startProxy(intent?.getStringExtra("config"))
        }
        return START_STICKY
    }

    private fun startProxy(config: String?) {
        if (config == null) return
        Thread {
            runCatching {
                setupLibbox()
                commandServer?.close()
                commandServer = CommandServer(ServiceHandler(), PlatformStub())
                commandServer!!.start()
                commandServer!!.startOrReloadService(config, OverrideOptions())
                updateNotification("本地 SOCKS 127.0.0.1:${LocalProxyController.SOCKS_PORT}")
            }.onFailure { error ->
                Log.e("LocalProxyService", "Failed to start libbox service", error)
                updateNotification("代理启动失败：${error.message ?: "unknown"}")
                stopProxy()
            }
        }.start()
    }

    private fun setupLibbox() {
        if (initialized) return
        synchronized(LocalProxyService::class.java) {
            if (initialized) return
            val base = File(filesDir, "libbox").apply { mkdirs() }
            val options = io.nekohasekai.libbox.SetupOptions().apply {
                basePath = base.absolutePath
                workingPath = base.absolutePath
                tempPath = cacheDir.absolutePath
                fixAndroidStack = true
                logMaxLines = 300
            }
            Libbox.setup(options)
            Libbox.setLocale("zh-CN")
            initialized = true
        }
    }

    private fun stopProxy() {
        runCatching { commandServer?.closeService() }
        runCatching { commandServer?.close() }
        commandServer = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("proxy", "代理服务", NotificationManager.IMPORTANCE_LOW))
    }

    private fun notification(text: String): Notification = NotificationCompat.Builder(this, "proxy")
        .setSmallIcon(android.R.drawable.stat_sys_warning)
        .setContentTitle("Cloudborne")
        .setContentText(text)
        .setOngoing(true)
        .build()

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(1, notification(text))
    }

    override fun onDestroy() {
        stopProxy()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        @Volatile private var initialized = false
    }
}

private class ServiceHandler : CommandServerHandler {
    override fun getSystemProxyStatus() = SystemProxyStatus().apply { available = false; enabled = false }
    override fun serviceReload() = Unit
    override fun serviceStop() = Unit
    override fun setSystemProxyEnabled(enabled: Boolean) = Unit
    override fun writeDebugMessage(message: String) = Unit
}

private class PlatformStub : PlatformInterface {
    override fun autoDetectInterfaceControl(fd: Int) = Unit
    override fun clearDNSCache() = Unit
    override fun closeDefaultInterfaceMonitor(listener: InterfaceUpdateListener) = Unit
    override fun findConnectionOwner(network: Int, sourceAddress: String, sourcePort: Int, destinationAddress: String, destinationPort: Int): ConnectionOwner? = null
    override fun getInterfaces(): NetworkInterfaceIterator? = null
    override fun includeAllNetworks() = false
    override fun localDNSTransport(): LocalDNSTransport? = null
    override fun openTun(options: TunOptions): Int = throw UnsupportedOperationException("VPN is an M2 feature")
    override fun readWIFIState(): WIFIState? = null
    override fun sendNotification(notification: LibboxNotification) = Unit
    override fun startDefaultInterfaceMonitor(listener: InterfaceUpdateListener) = Unit
    override fun systemCertificates(): StringIterator? = null
    override fun underNetworkExtension() = false
    override fun usePlatformAutoDetectInterfaceControl() = false
    override fun useProcFS() = true
}
