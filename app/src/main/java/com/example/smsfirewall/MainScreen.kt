package com.example.smsfirewall

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.smsfirewall.ui.theme.AppSpacing
import com.example.smsfirewall.ui.theme.AvatarBlueEnd
import com.example.smsfirewall.ui.theme.AvatarBlueStart
import com.example.smsfirewall.ui.theme.WarningContainer
import com.example.smsfirewall.ui.theme.WarningContainerStrong
import com.example.smsfirewall.ui.theme.WarningOnContainer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MainScreen(
    smsList: List<SmsModel>,
    isDefaultSmsApp: Boolean,
    onSetDefaultClick: () -> Unit,
    onSpamClick: () -> Unit,
    onTrashClick: () -> Unit,
    onConversationClick: (SmsModel) -> Unit,
    onDeleteConversationClick: (SmsModel) -> Unit
) {
    var pendingDelete by remember { mutableStateOf<SmsModel?>(null) }
    var pendingDeleteThreadIds by remember { mutableStateOf(setOf<String>()) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val visibleSmsList = smsList.filterNot { it.threadId in pendingDeleteThreadIds }
    val unreadTotal = visibleSmsList.sumOf { it.unreadCount }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = AppSpacing.xLarge)
        ) {
            Spacer(modifier = Modifier.height(14.dp))

            MainHeroHeader(
                threadCount = visibleSmsList.size,
                unreadCount = unreadTotal,
                onSpamClick = onSpamClick,
                onTrashClick = onTrashClick
            )

            Spacer(modifier = Modifier.height(AppSpacing.large))

            if (!isDefaultSmsApp) {
                DefaultAppWarningCard(onClick = onSetDefaultClick)
                Spacer(modifier = Modifier.height(AppSpacing.large))
            }

            if (visibleSmsList.isEmpty()) {
                EmptyState(modifier = Modifier.weight(1f))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 26.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(visibleSmsList, key = { it.threadId }) { sms ->
                        SmsConversationCard(
                            sms = sms,
                            onClick = { onConversationClick(sms) },
                            onDeleteClick = { pendingDelete = sms }
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { sms ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Sohbet silinsin mi?") },
            text = { Text("${sms.address} ile tum mesajlar silinecek.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        pendingDeleteThreadIds = pendingDeleteThreadIds + sms.threadId

                        coroutineScope.launch {
                            val result = showSnackbarForThreeSeconds(
                                snackbarHostState = snackbarHostState,
                                message = "Sohbet silindi",
                                actionLabel = "Geri al"
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                pendingDeleteThreadIds = pendingDeleteThreadIds - sms.threadId
                            } else {
                                onDeleteConversationClick(sms)
                                pendingDeleteThreadIds = pendingDeleteThreadIds - sms.threadId
                            }
                        }
                    }
                ) {
                    Text("Sil")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Iptal")
                }
            }
        )
    }
}

@Composable
private fun MainHeroHeader(
    threadCount: Int,
    unreadCount: Int,
    onSpamClick: () -> Unit,
    onTrashClick: () -> Unit
) {
    val heroBrush = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.secondary
        )
    )

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .background(brush = heroBrush)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        text = "Mesajlar",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "Kutunu hizli yonet",
                        color = Color.White.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = if (unreadCount > 0) "$unreadCount yeni" else "Guncel",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricPill(label = "Sohbet", value = threadCount.toString())
                MetricPill(label = "Okunmamis", value = unreadCount.toString())
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onTrashClick) {
                    Text(text = "Cop Kutusu", fontWeight = FontWeight.Bold)
                }
                FilledTonalButton(onClick = onSpamClick) {
                    Text(text = "Spam", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun MetricPill(label: String, value: String) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(alpha = 0.18f)
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                text = label,
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.labelMedium
            )
            Text(
                text = value,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun DefaultAppWarningCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = WarningContainer),
        border = androidx.compose.foundation.BorderStroke(1.dp, WarningContainerStrong)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = CircleShape, color = WarningContainerStrong) {
                Text(
                    text = "!",
                    color = WarningOnContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(AppSpacing.medium))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Varsayilan SMS uygulamasi yap",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Engelleme ve gonderim ozellikleri icin gerekli",
                    style = MaterialTheme.typography.bodySmall,
                    color = WarningOnContainer
                )
            }
            TextButton(onClick = onClick) {
                Text(text = "Ayarla", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SmsConversationCard(
    sms: SmsModel,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val hasUnread = sms.unreadCount > 0
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .border(
                width = if (hasUnread) 1.4.dp else 1.dp,
                color = if (hasUnread) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(18.dp)
            ),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = if (hasUnread) 5.dp else 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val avatarBrush = Brush.linearGradient(listOf(AvatarBlueStart, AvatarBlueEnd))

            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(avatarBrush, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = sms.address.firstOrNull()?.uppercase() ?: "?",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(AppSpacing.medium))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = sms.address,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (hasUnread) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = sms.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (hasUnread) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (hasUnread) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(AppSpacing.small))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = sms.date.toDayTime(),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (hasUnread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (hasUnread) FontWeight.Bold else FontWeight.Normal
                )
                if (hasUnread) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Text(
                            text = if (sms.unreadCount > 99) "99+" else sms.unreadCount.toString(),
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
                TextButton(onClick = onDeleteClick, contentPadding = PaddingValues(0.dp)) {
                    Text(text = "Sil", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("SMS", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Mesaj bulunamadi",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "SMS iznini kontrol ederek tekrar deneyin.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun Long.toDayTime(): String {
    val format = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault())
    return format.format(Date(this))
}

private suspend fun showSnackbarForThreeSeconds(
    snackbarHostState: SnackbarHostState,
    message: String,
    actionLabel: String
): SnackbarResult = coroutineScope {
    val result = async {
        snackbarHostState.showSnackbar(
            message = message,
            actionLabel = actionLabel,
            duration = SnackbarDuration.Indefinite
        )
    }
    delay(3000)
    snackbarHostState.currentSnackbarData?.dismiss()
    result.await()
}
