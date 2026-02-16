package com.example.smsfirewall

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.compose.ui.platform.LocalContext
import com.example.smsfirewall.ui.components.TopGradientBar
import com.example.smsfirewall.ui.theme.AppSpacing
import com.example.smsfirewall.ui.theme.SMSFirewallTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            SMSFirewallTheme {
                SettingsScreen(
                    onBackClick = { finish() },
                    onShowToast = { message ->
                        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    onBackClick: () -> Unit,
    onShowToast: (String) -> Unit
) {
    val context = LocalContext.current
    var unreadBadgesEnabled by remember(context) { mutableStateOf(AppSettings.isUnreadBadgesEnabled(context)) }
    var chatBackgroundKey by remember(context) { mutableStateOf(AppSettings.getChatBackgroundKey(context)) }
    var showClearTrashDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopGradientBar(
                title = "Ayarlar",
                onBackClick = onBackClick,
                startColor = MaterialTheme.colorScheme.primary,
                endColor = MaterialTheme.colorScheme.secondary
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = AppSpacing.large),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                SettingToggleCard(
                    title = "Okunmamis rozetleri goster",
                    subtitle = "Ana ekranda sohbet rozetleri ve sayaclar",
                    checked = unreadBadgesEnabled,
                    onCheckedChange = {
                        unreadBadgesEnabled = it
                        AppSettings.setUnreadBadgesEnabled(context, it)
                    }
                )
            }

            item {
                BackgroundThemeCard(
                    selectedKey = chatBackgroundKey,
                    onSelect = { key ->
                        chatBackgroundKey = key
                        AppSettings.setChatBackgroundKey(context, key)
                    }
                )
            }

            item {
                ActionCard(
                    title = "Cop kutusunu temizle",
                    subtitle = "Tum cop kutusu kayitlari kalici olarak silinir",
                    buttonText = "Temizle",
                    onClick = { showClearTrashDialog = true }
                )
            }
        }
    }

    if (showClearTrashDialog) {
        AlertDialog(
            onDismissRequest = { showClearTrashDialog = false },
            title = { Text("Emin misin?") },
            text = { Text("Cop kutusundaki tum kayitlar kalici silinecek.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearTrashDialog = false
                        CoroutineScope(Dispatchers.IO).launch {
                            AppDatabase.getDatabase(context).trashMessageDao().deleteAll()
                            withContext(Dispatchers.Main) {
                                onShowToast("Cop kutusu temizlendi")
                            }
                        }
                    }
                ) {
                    Text("Temizle")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearTrashDialog = false }) {
                    Text("Iptal")
                }
            }
        )
    }
}

@Composable
private fun SettingToggleCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun BackgroundThemeCard(
    selectedKey: String,
    onSelect: (String) -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Varsayilan sohbet arkaplani", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeChip("Sade", AppSettings.CHAT_BG_CLASSIC, selectedKey, onSelect)
                ThemeChip("Mavi", AppSettings.CHAT_BG_OCEAN, selectedKey, onSelect)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeChip("Mint", AppSettings.CHAT_BG_MINT, selectedKey, onSelect)
                ThemeChip("Gunbatimi", AppSettings.CHAT_BG_SUNSET, selectedKey, onSelect)
            }
        }
    }
}

@Composable
private fun ThemeChip(
    label: String,
    key: String,
    selectedKey: String,
    onSelect: (String) -> Unit
) {
    Button(
        onClick = { onSelect(key) },
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(if (selectedKey == key) "$label *" else label)
    }
}

@Composable
private fun ActionCard(
    title: String,
    subtitle: String,
    buttonText: String,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = onClick) {
                Text(buttonText)
            }
        }
    }
}
