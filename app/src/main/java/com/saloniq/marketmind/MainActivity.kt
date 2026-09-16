package com.saloniq.marketmind

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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private data class Asset(
    val symbol: String,
    val name: String,
    val type: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MarketMindApp() }
    }
}

@Composable
private fun MarketMindApp() {
    val watchlist = remember {
        mutableStateListOf(
            Asset("BTC", "Bitcoin", "Crypto"),
            Asset("TSLA", "Tesla", "Stock"),
            Asset("CDR", "CD Projekt", "Stock")
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "MarketMind",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    "AI market monitor",
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    "Stocks + crypto • technical analysis • NVIDIA NIM",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
            }

            items(watchlist) { asset ->
                AssetCard(asset)
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { /* TODO: add asset screen */ },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Add asset")
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { /* TODO: settings */ },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Settings")
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun AssetCard(asset: Asset) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(asset.symbol, style = MaterialTheme.typography.titleLarge)
                Text(asset.type, style = MaterialTheme.typography.labelMedium)
            }
            Text(asset.name, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            Text("Price: —", style = MaterialTheme.typography.bodyLarge)
            Text("24h: —", style = MaterialTheme.typography.bodyMedium)
            Text("RSI: —   MACD: —", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "Potential entry signal: waiting for market data",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
