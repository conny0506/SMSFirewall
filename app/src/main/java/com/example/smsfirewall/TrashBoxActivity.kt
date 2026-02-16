package com.example.smsfirewall

import android.content.ContentValues
import android.os.Bundle
import android.provider.Telephony
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.example.smsfirewall.ui.components.TopGradientBar
import com.example.smsfirewall.ui.theme.AppSpacing
import com.example.smsfirewall.ui.theme.SMSFirewallTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class TrashConversationItem(
    val threadId: String,
    val displayName: String,
    val messageCount: Int,
    val lastDeletedAt: Long
)

class TrashBoxActivity : FragmentActivity() {

    private val trashConversations = mutableStateListOf<TrashConversationItem>()
    private var isLoading by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            SMSFirewallTheme {
                TrashBoxScreen(
                    conversations = trashConversations,
                    isLoading = isLoading,
                    onBackClick = { finish() },
                    onRestoreConversationClick = { restoreConversation(it) },
                    onDeleteConversationPermanentlyClick = { deleteConversationPermanently(it) },
                    onBulkDeleteClick = { bulkDeleteTrash() }
                )
            }
        }

        loadTrashConversations()
    }

    private fun loadTrashConversations() {
        CoroutineScope(Dispatchers.IO).launch {
            val dao = AppDatabase.getDatabase(applicationContext).trashMessageDao()
            val allTrash = dao.getAllTrash()
            val grouped = allTrash
                .groupBy { it.threadId }
                .values
                .map { messages ->
                    val latest = messages.maxByOrNull { it.deletedAt } ?: messages.first()
                    val displayName = messages.firstOrNull { it.sender.isNotBlank() }?.sender ?: "Bilinmeyen"
                    TrashConversationItem(
                        threadId = latest.threadId,
                        displayName = displayName,
                        messageCount = messages.size,
                        lastDeletedAt = latest.deletedAt
                    )
                }
                .sortedByDescending { it.lastDeletedAt }

            withContext(Dispatchers.Main) {
                trashConversations.clear()
                trashConversations.addAll(grouped)
                isLoading = false
            }
        }
    }

    private fun restoreConversation(item: TrashConversationItem) {
        if (!SmsRoleUtils.isAppDefaultSmsHandler(this)) {
            Toast.makeText(this, "Geri yuklemek icin varsayilan SMS uygulamasi olmalisin.", Toast.LENGTH_SHORT).show()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = AppDatabase.getDatabase(applicationContext).trashMessageDao()
                val messages = dao.getByThreadId(item.threadId)
                var restoredCount = 0

                for (message in messages) {
                    val values = ContentValues().apply {
                        put(Telephony.Sms.ADDRESS, message.sender)
                        put(Telephony.Sms.BODY, message.body)
                        put(Telephony.Sms.DATE, message.date)
                        put(Telephony.Sms.TYPE, message.type)
                        put(Telephony.Sms.READ, if (message.type == Telephony.Sms.MESSAGE_TYPE_SENT) 1 else 0)
                    }

                    val targetUri = if (message.type == Telephony.Sms.MESSAGE_TYPE_SENT) {
                        Telephony.Sms.Sent.CONTENT_URI
                    } else {
                        Telephony.Sms.Inbox.CONTENT_URI
                    }

                    val insertedUri = contentResolver.insert(targetUri, values)
                    if (insertedUri != null) restoredCount++
                }

                if (restoredCount > 0) {
                    dao.deleteByThreadId(item.threadId)
                }

                withContext(Dispatchers.Main) {
                    if (restoredCount > 0) {
                        trashConversations.removeAll { it.threadId == item.threadId }
                        Toast.makeText(
                            this@TrashBoxActivity,
                            "Sohbet geri yuklendi ($restoredCount mesaj)",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(this@TrashBoxActivity, "Geri yukleme basarisiz", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@TrashBoxActivity, "Hata: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun deleteConversationPermanently(item: TrashConversationItem) {
        CoroutineScope(Dispatchers.IO).launch {
            AppDatabase.getDatabase(applicationContext).trashMessageDao().deleteByThreadId(item.threadId)
            withContext(Dispatchers.Main) {
                trashConversations.removeAll { it.threadId == item.threadId }
                Toast.makeText(this@TrashBoxActivity, "Sohbet cop kutusundan silindi", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun bulkDeleteTrash() {
        CoroutineScope(Dispatchers.IO).launch {
            AppDatabase.getDatabase(applicationContext).trashMessageDao().deleteAll()
            withContext(Dispatchers.Main) {
                val deletedCount = trashConversations.size
                trashConversations.clear()
                Toast.makeText(this@TrashBoxActivity, "$deletedCount sohbet kalici silindi", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@Composable
private fun TrashBoxScreen(
    conversations: List<TrashConversationItem>,
    isLoading: Boolean,
    onBackClick: () -> Unit,
    onRestoreConversationClick: (TrashConversationItem) -> Unit,
    onDeleteConversationPermanentlyClick: (TrashConversationItem) -> Unit,
    onBulkDeleteClick: () -> Unit
) {
    var pendingPermanentDelete by remember { mutableStateOf<TrashConversationItem?>(null) }
    var showBulkDeleteDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopGradientBar(
                title = "Cop Kutusu",
                onBackClick = onBackClick,
                startColor = MaterialTheme.colorScheme.primary,
                endColor = MaterialTheme.colorScheme.secondary,
                badgeText = conversations.size.toString(),
                actionLabel = "Toplu Sil",
                onActionClick = { showBulkDeleteDialog = true }
            )
        }
    ) { padding ->
        when {
            isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Yukleniyor...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            conversations.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Cop kutusu bos", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = AppSpacing.large),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(conversations, key = { it.threadId }) { item ->
                        TrashConversationCard(
                            item = item,
                            onRestoreClick = { onRestoreConversationClick(item) },
                            onDeletePermanentClick = { pendingPermanentDelete = item }
                        )
                    }
                }
            }
        }
    }

    pendingPermanentDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingPermanentDelete = null },
            title = { Text("Emin misin?") },
            text = { Text("Bu sohbet cop kutusundan kalici olarak silinecek.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteConversationPermanentlyClick(item)
                        pendingPermanentDelete = null
                    }
                ) {
                    Text("Kalici Sil")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingPermanentDelete = null }) {
                    Text("Iptal")
                }
            }
        )
    }

    if (showBulkDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showBulkDeleteDialog = false },
            title = { Text("Emin misin?") },
            text = { Text("Cop kutusundaki tum sohbetler kalici olarak silinecek.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onBulkDeleteClick()
                        showBulkDeleteDialog = false
                    }
                ) {
                    Text("Toplu Sil")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDeleteDialog = false }) {
                    Text("Iptal")
                }
            }
        )
    }
}

@Composable
private fun TrashConversationCard(
    item: TrashConversationItem,
    onRestoreClick: () -> Unit,
    onDeletePermanentClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = item.displayName,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "${item.messageCount} mesaj",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = item.lastDeletedAt.toTrashDate(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRestoreClick) {
                    Text("Geri Yukle")
                }
                OutlinedButton(onClick = onDeletePermanentClick) {
                    Text("Kalici Sil")
                }
            }
        }
    }
}

private fun Long.toTrashDate(): String {
    val format = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault())
    return format.format(Date(this))
}
