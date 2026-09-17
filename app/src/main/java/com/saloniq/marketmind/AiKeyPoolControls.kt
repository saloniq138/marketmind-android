package com.saloniq.marketmind

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun AiKeyPoolControls(ai: AiProviderSettings, provider: AiProvider, onStatus: (String) -> Unit) {
    var newKey by remember(provider) { mutableStateOf("") }
    val keys = ai.getKeys()

    Text("API key pool", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
    Text("Add several keys. MarketMind tries them in order and automatically moves to the next key when a key hits a rate limit or temporary API error.", style = MaterialTheme.typography.bodySmall)

    OutlinedTextField(
        value = newKey,
        onValueChange = { newKey = it },
        label = { Text("New ${provider.label} API key") },
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
    )
    Button(onClick = {
        val result = ai.addKey(newKey)
        if (result.isSuccess) {
            newKey = ""
            onStatus("API key added. ${ai.getKeys().size} key(s) available for automatic failover.")
        } else onStatus("Could not add API key: ${result.exceptionOrNull()?.message ?: "unknown error"}")
    }, enabled = newKey.trim().isNotEmpty(), modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Text("Add API key")
    }

    if (keys.isEmpty()) {
        Text("No API keys saved for ${provider.label}.", modifier = Modifier.padding(vertical = 8.dp))
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
            keys.forEachIndexed { index, _ ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(10.dp), Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text("Key #${index + 1}", style = MaterialTheme.typography.titleSmall)
                            Text(if (index == 0) "Primary key — used first" else "Backup key — used automatically", style = MaterialTheme.typography.bodySmall)
                        }
                        OutlinedButton(onClick = {
                            if (ai.removeKey(index)) onStatus("Key #${index + 1} removed. ${ai.getKeys().size} key(s) remain.")
                            else onStatus("Could not remove API key.")
                        }) { Text("Remove") }
                    }
                }
            }
        }
    }
}
