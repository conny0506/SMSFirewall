package com.example.smsfirewall

import android.content.ContentValues
import android.os.Bundle
import android.provider.Telephony
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.example.smsfirewall.ui.components.TopGradientBar
import com.example.smsfirewall.ui.theme.AppSpacing
import com.example.smsfirewall.ui.theme.SMSFirewallTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
                    val displayName = messages.firstOrNull { it.sender.isNotBlank() }?.sender ?: getString(R.string.label_unknown_sender)
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
            Toast.makeText(this, getString(R.string.toast_restore_requires_default_sms), Toast.LENGTH_SHORT).show()
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
                            resources.getQuantityString(
                                R.plurals.toast_conversation_restored_count,
                                restoredCount,
                                restoredCount
                            ),
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(this@TrashBoxActivity, getString(R.string.toast_restore_failed), Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@TrashBoxActivity,
                        getString(R.string.toast_error_with_message, e.message ?: ""),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun deleteConversationPermanently(item: TrashConversationItem) {
        CoroutineScope(Dispatchers.IO).launch {
            AppDatabase.getDatabase(applicationContext).trashMessageDao().deleteByThreadId(item.threadId)
            withContext(Dispatchers.Main) {
                trashConversations.removeAll { it.threadId == item.threadId }
                Toast.makeText(this@TrashBoxActivity, getString(R.string.toast_trash_conversation_deleted), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun bulkDeleteTrash() {
        CoroutineScope(Dispatchers.IO).launch {
            AppDatabase.getDatabase(applicationContext).trashMessageDao().deleteAll()
            withContext(Dispatchers.Main) {
                val deletedCount = trashConversations.size
                trashConversations.clear()
                Toast.makeText(
                    this@TrashBoxActivity,
                    resources.getQuantityString(
                        R.plurals.toast_bulk_deleted_count,
                        deletedCount,
                        deletedCount
                    ),
                    Toast.LENGTH_SHORT
                ).show()
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
    val totalMessages = conversations.sumOf { it.messageCount }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopGradientBar(
                title = stringResource(R.string.activity_trash_box_label),
                onBackClick = onBackClick,
                startColor = MaterialTheme.colorScheme.primary,
                endColor = MaterialTheme.colorScheme.secondary,
                badgeText = conversations.size.toString(),
                actionLabel = stringResource(R.string.label_bulk_delete),
                onActionClick = { showBulkDeleteDialog = true }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = AppSpacing.large)
        ) {
            Spacer(modifier = Modifier.height(10.dp))
            TrashSummaryCard(
                conversationCount = conversations.size,
                messageCount = totalMessages
            )
            Spacer(modifier = Modifier.height(10.dp))

            when {
                isLoading -> {
                    TrashStatusCard(
                        modifier = Modifier.weight(1f),
                        title = stringResource(R.string.label_loading),
                        subtitle = stringResource(R.string.label_trash_loading_desc)
                    )
                }

                conversations.isEmpty() -> {
                    TrashStatusCard(
                        modifier = Modifier.weight(1f),
                        title = stringResource(R.string.label_trash_empty),
                        subtitle = stringResource(R.string.label_trash_empty_desc)
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(bottom = 16.dp),
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
    }

    pendingPermanentDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingPermanentDelete = null },
            title = { Text(stringResource(R.string.label_are_you_sure)) },
            text = { Text(stringResource(R.string.label_trash_delete_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteConversationPermanentlyClick(item)
                        pendingPermanentDelete = null
                    }
                ) {
                    Text(stringResource(R.string.label_delete_permanent))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingPermanentDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showBulkDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showBulkDeleteDialog = false },
            title = { Text(stringResource(R.string.label_are_you_sure)) },
            text = { Text(stringResource(R.string.label_trash_bulk_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onBulkDeleteClick()
                        showBulkDeleteDialog = false
                    }
                ) {
                    Text(stringResource(R.string.label_bulk_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDeleteDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun TrashSummaryCard(
    conversationCount: Int,
    messageCount: Int
) {
    val resources = LocalContext.current.resources
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                text = stringResource(R.string.label_trash_summary_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Text(
                        text = resources.getQuantityString(
                            R.plurals.label_conversation_count,
                            conversationCount,
                            conversationCount
                        ),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Text(
                        text = resources.getQuantityString(R.plurals.label_messages_count, messageCount, messageCount),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun TrashStatusCard(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String
) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("T", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun TrashConversationCard(
    item: TrashConversationItem,
    onRestoreClick: () -> Unit,
    onDeletePermanentClick: () -> Unit
) {
    val resources = LocalContext.current.resources
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Text(
                        text = resources.getQuantityString(
                            R.plurals.label_messages_count,
                            item.messageCount,
                            item.messageCount
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Text(
                        text = item.lastDeletedAt.toTrashDate(),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRestoreClick) {
                    Text(stringResource(R.string.action_restore))
                }
                OutlinedButton(onClick = onDeletePermanentClick) {
                    Text(stringResource(R.string.label_delete_permanent))
                }
            }
        }
    }
}

private fun Long.toTrashDate(): String {
    val format = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault())
    return format.format(Date(this))
}
