package com.cloudborne.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.ViewModelProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.moriafly.salt.ui.SaltTheme
import com.cloudborne.android.core.ConnectionState
import com.cloudborne.android.core.MainViewModel
import com.cloudborne.android.core.SyncState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SaltTheme {
                Surface(Modifier.fillMaxSize()) {
                    MainScreen()
                }
            }
        }
    }
}

@Composable
private fun MainScreen() {
    val activity = LocalContext.current as ComponentActivity
    val model = ViewModelProvider(activity)[MainViewModel::class.java]
    val state by model.state.collectAsState()
    var name by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Cloudborne", style = MaterialTheme.typography.headlineSmall)
        Text("M1：订阅、节点与本地 SOCKS", style = MaterialTheme.typography.bodyMedium)

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("添加订阅", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("订阅 URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        model.addSubscription(name, url)
                        name = ""
                        url = ""
                    },
                    enabled = name.isNotBlank() && url.startsWith("http"),
                ) { Text("添加并更新") }
            }
        }

        when (val sync = state.syncState) {
            SyncState.Loading -> Text("正在更新订阅…", modifier = Modifier.testTag("subscription-loading"))
            is SyncState.Error -> Text(
                "更新失败：${sync.message}",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("subscription-error"),
            )
            SyncState.Idle -> Unit
        }

        ConnectionCard(state.connectionState, state.connectionError, state.selectedNode?.name, model)

        Text("节点 (${state.subscriptions.sumOf { it.nodes.size }})", style = MaterialTheme.typography.titleMedium)
        if (state.subscriptions.isEmpty()) {
            Text("暂无订阅，请先添加订阅", modifier = Modifier.testTag("subscriptions-empty"))
        } else if (state.subscriptions.all { it.nodes.isEmpty() }) {
            Text("暂无节点，请更新订阅", modifier = Modifier.testTag("nodes-empty"))
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.subscriptions.forEach { subscription ->
                item(key = "subscription-${subscription.id}") {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(subscription.name, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                        TextButton(onClick = { model.refresh(subscription.id) }) { Text("更新") }
                    }
                }
                items(subscription.nodes, key = { it.id }) { node ->
                    NodeRow(node.name, node.scheme, node.server, node.port, node.id == state.selectedNode?.id) {
                        model.selectNode(node)
                    }
                }
            }
        }
    }
}

@Composable
private fun NodeRow(name: String, scheme: String, server: String, port: Int, selected: Boolean, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().testTag("node-row-$name")) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("$scheme · $server:$port", style = MaterialTheme.typography.bodySmall)
            }
            if (selected) Text("已选", color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun ConnectionCard(connectionState: ConnectionState, error: String?, nodeName: String?, model: MainViewModel) {
    val label = when (connectionState) {
        ConnectionState.Disconnected -> "未连接"
        ConnectionState.Connecting -> "正在连接…"
        ConnectionState.Connected -> "已连接 · SOCKS 127.0.0.1:2080"
        ConnectionState.Failed -> "连接失败"
    }
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).testTag("connection-state")) {
                Text("连接状态", style = MaterialTheme.typography.labelLarge)
                Text(label)
                if (nodeName != null) Text(nodeName, style = MaterialTheme.typography.bodySmall)
                if (connectionState == ConnectionState.Failed && error != null) {
                    Text(error, color = MaterialTheme.colorScheme.error)
                }
            }
            if (connectionState == ConnectionState.Connected || connectionState == ConnectionState.Connecting) {
                TextButton(onClick = model::disconnect) { Text("断开") }
            } else {
                Button(onClick = model::connect, enabled = nodeName != null) { Text("连接") }
            }
        }
    }
}
