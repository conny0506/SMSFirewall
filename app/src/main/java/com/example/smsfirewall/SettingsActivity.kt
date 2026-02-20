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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
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
    var notificationContentVisible by remember(context) { mutableStateOf(AppSettings.isNotificationContentVisible(context)) }
    var showClearTrashDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopGradientBar(
                title = stringResource(R.string.activity_settings_label),
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
                SettingsOverviewCard(
                    unreadBadgesEnabled = unreadBadgesEnabled,
                    notificationContentVisible = notificationContentVisible,
                    selectedTheme = chatBackgroundKey
                )
            }

            item {
                SectionLabel(text = stringResource(R.string.label_settings_section_notifications))
                SettingToggleCard(
                    title = stringResource(R.string.label_show_unread_badges),
                    subtitle = stringResource(R.string.label_unread_badges_desc),
                    checked = unreadBadgesEnabled,
                    onCheckedChange = {
                        unreadBadgesEnabled = it
                        AppSettings.setUnreadBadgesEnabled(context, it)
                    }
                )
            }

            item {
                SettingToggleCard(
                    title = stringResource(R.string.label_show_notification_content),
                    subtitle = stringResource(R.string.label_show_notification_content_desc),
                    checked = notificationContentVisible,
                    onCheckedChange = {
                        notificationContentVisible = it
                        AppSettings.setNotificationContentVisible(context, it)
                    }
                )
            }

            item {
                SectionLabel(text = stringResource(R.string.label_settings_section_chat))
                BackgroundThemeCard(
                    selectedKey = chatBackgroundKey,
                    onSelect = { key ->
                        chatBackgroundKey = key
                        AppSettings.setChatBackgroundKey(context, key)
                    }
                )
            }

            item {
                SectionLabel(text = stringResource(R.string.label_settings_section_cleanup))
                ActionCard(
                    title = stringResource(R.string.label_trash_clear),
                    subtitle = stringResource(R.string.label_trash_clear_desc),
                    buttonText = stringResource(R.string.action_clean),
                    onClick = { showClearTrashDialog = true }
                )
                Spacer(modifier = Modifier.height(14.dp))
            }
        }
    }

    if (showClearTrashDialog) {
        AlertDialog(
            onDismissRequest = { showClearTrashDialog = false },
            title = { Text(stringResource(R.string.label_are_you_sure)) },
            text = { Text(stringResource(R.string.label_trash_clear_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearTrashDialog = false
                        CoroutineScope(Dispatchers.IO).launch {
                            AppDatabase.getDatabase(context).trashMessageDao().deleteAll()
                            withContext(Dispatchers.Main) {
                                onShowToast(context.getString(R.string.toast_trash_cleared))
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.action_clean))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearTrashDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun SettingsOverviewCard(
    unreadBadgesEnabled: Boolean,
    notificationContentVisible: Boolean,
    selectedTheme: String
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.label_settings_overview_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill(
                    label = if (unreadBadgesEnabled) {
                        stringResource(R.string.label_settings_unread_on)
                    } else {
                        stringResource(R.string.label_settings_unread_off)
                    }
                )
                StatusPill(
                    label = if (notificationContentVisible) {
                        stringResource(R.string.label_settings_preview_on)
                    } else {
                        stringResource(R.string.label_settings_preview_off)
                    }
                )
            }
            StatusPill(
                label = stringResource(
                    R.string.label_settings_theme_selected,
                    when (selectedTheme) {
                        AppSettings.CHAT_BG_OCEAN -> stringResource(R.string.label_theme_ocean)
                        AppSettings.CHAT_BG_MINT -> stringResource(R.string.label_theme_mint)
                        AppSettings.CHAT_BG_SUNSET -> stringResource(R.string.label_theme_sunset)
                        else -> stringResource(R.string.label_theme_classic)
                    }
                )
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 2.dp, top = 2.dp)
    )
}

@Composable
private fun StatusPill(label: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium
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
            Text(stringResource(R.string.label_default_chat_background), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeChip(stringResource(R.string.label_theme_classic), AppSettings.CHAT_BG_CLASSIC, selectedKey, onSelect)
                ThemeChip(stringResource(R.string.label_theme_ocean), AppSettings.CHAT_BG_OCEAN, selectedKey, onSelect)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeChip(stringResource(R.string.label_theme_mint), AppSettings.CHAT_BG_MINT, selectedKey, onSelect)
                ThemeChip(stringResource(R.string.label_theme_sunset), AppSettings.CHAT_BG_SUNSET, selectedKey, onSelect)
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
        Text(if (selectedKey == key) stringResource(R.string.label_theme_selected_format, label) else label)
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
