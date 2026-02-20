package com.example.smsfirewall

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.telephony.SmsManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.smsfirewall.ui.theme.AppSpacing
import com.example.smsfirewall.ui.theme.ChatReceivedBubble
import com.example.smsfirewall.ui.theme.ChatSentBubble
import com.example.smsfirewall.ui.theme.SMSFirewallTheme
import com.example.smsfirewall.ui.components.TopGradientBar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ConversationDetailActivity : FragmentActivity() {

    private val messageList = mutableStateListOf<SmsModel>()
    private var inputText by mutableStateOf("")
    private var chatBackgroundKey by mutableStateOf(AppSettings.CHAT_BG_CLASSIC)

    private var threadId: String? = null
    private var address: String? = null
    private var smsContentObserver: ContentObserver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        threadId = intent.getStringExtra("thread_id")
        address = intent.getStringExtra("address")
        chatBackgroundKey = loadSavedChatBackgroundKey()

        setContent {
            SMSFirewallTheme {
                ConversationDetailScreen(
                    title = address ?: getString(R.string.label_unknown_sender),
                    messages = messageList,
                    inputText = inputText,
                    chatBackgroundKey = chatBackgroundKey,
                    onInputChanged = { inputText = it },
                    onBackgroundThemeChange = { key ->
                        chatBackgroundKey = key
                        saveChatBackgroundKey(key)
                    },
                    onBackClick = { finish() },
                    onSendClick = { sendMessage() },
                    onDeleteMessage = { message -> deleteMessage(message) },
                    onDeleteConversation = { deleteCurrentConversation() }
                )
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) {
            loadMessages()
        }
    }

    override fun onStart() {
        super.onStart()
        registerSmsObserver()
    }

    override fun onStop() {
        super.onStop()
        unregisterSmsObserver()
    }

    private fun registerSmsObserver() {
        if (smsContentObserver != null) return

        smsContentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                if (ContextCompat.checkSelfPermission(this@ConversationDetailActivity, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) {
                    loadMessages()
                }
            }
        }

        contentResolver.registerContentObserver(
            Telephony.Sms.CONTENT_URI,
            true,
            smsContentObserver as ContentObserver
        )
    }

    private fun unregisterSmsObserver() {
        smsContentObserver?.let {
            contentResolver.unregisterContentObserver(it)
            smsContentObserver = null
        }
    }

    private fun loadMessages() {
        val localThreadId = threadId ?: return

        CoroutineScope(Dispatchers.IO).launch {
            markThreadMessagesAsRead(localThreadId)
            val loadedMessages = mutableListOf<SmsModel>()

            val cursor = contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(
                    Telephony.Sms._ID,
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY,
                    Telephony.Sms.DATE,
                    Telephony.Sms.TYPE
                ),
                "${Telephony.Sms.THREAD_ID} = ?",
                arrayOf(localThreadId),
                Telephony.Sms.DATE + " ASC"
            )

            cursor?.use {
                val indexId = it.getColumnIndex(Telephony.Sms._ID)
                val indexAddress = it.getColumnIndex(Telephony.Sms.ADDRESS)
                val indexBody = it.getColumnIndex(Telephony.Sms.BODY)
                val indexDate = it.getColumnIndex(Telephony.Sms.DATE)
                val indexType = it.getColumnIndex(Telephony.Sms.TYPE)

                while (it.moveToNext()) {
                    loadedMessages.add(
                        SmsModel(
                            id = it.getString(indexId),
                            address = it.getString(indexAddress) ?: "",
                            body = it.getString(indexBody) ?: "",
                            date = it.getLong(indexDate),
                            type = it.getInt(indexType),
                            threadId = localThreadId
                        )
                    )
                }
            }

            withContext(Dispatchers.Main) {
                messageList.clear()
                messageList.addAll(loadedMessages)
            }
        }
    }

    private fun markThreadMessagesAsRead(localThreadId: String) {
        val values = ContentValues().apply {
            put(Telephony.Sms.READ, 1)
        }
        contentResolver.update(
            Telephony.Sms.CONTENT_URI,
            values,
            "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.TYPE} = ? AND ${Telephony.Sms.READ} = ?",
            arrayOf(localThreadId, Telephony.Sms.MESSAGE_TYPE_INBOX.toString(), "0")
        )
    }

    private fun sendMessage() {
        val messageBody = inputText.trim()
        val targetAddress = address

        if (messageBody.isEmpty() || targetAddress.isNullOrBlank()) return

        try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            smsManager.sendTextMessage(targetAddress, null, messageBody, null, null)

            val now = System.currentTimeMillis()
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, targetAddress)
                put(Telephony.Sms.BODY, messageBody)
                put(Telephony.Sms.DATE, now)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
                put(Telephony.Sms.READ, 1)
            }
            val insertedUri = contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)
            val insertedId = insertedUri?.lastPathSegment ?: now.toString()

            messageList.add(
                SmsModel(
                    id = insertedId,
                    address = targetAddress,
                    body = messageBody,
                    date = now,
                    type = Telephony.Sms.MESSAGE_TYPE_SENT,
                    threadId = threadId ?: "0"
                )
            )

            inputText = ""
        } catch (e: Exception) {
            Toast.makeText(
                this,
                getString(R.string.toast_error_with_message, e.message ?: ""),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun deleteMessage(message: SmsModel) {
        if (!SmsRoleUtils.isAppDefaultSmsHandler(this)) {
            Toast.makeText(this, getString(R.string.toast_delete_requires_default_sms), Toast.LENGTH_SHORT).show()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            val trashItem = TrashMessage(
                originalSmsId = message.id,
                sender = message.address,
                body = message.body,
                date = message.date,
                type = message.type,
                threadId = message.threadId,
                deletedAt = System.currentTimeMillis()
            )

            val messageId = message.id.toLongOrNull()
            val deletedRows = if (messageId != null) {
                val uri = ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, messageId)
                contentResolver.delete(uri, null, null)
            } else {
                contentResolver.delete(
                    Telephony.Sms.CONTENT_URI,
                    "${Telephony.Sms._ID} = ?",
                    arrayOf(message.id)
                )
            }
            if (deletedRows > 0) {
                AppDatabase.getDatabase(applicationContext).trashMessageDao().insert(trashItem)
            }

            withContext(Dispatchers.Main) {
                if (deletedRows > 0) {
                    messageList.removeAll { it.id == message.id }
                } else {
                    Toast.makeText(this@ConversationDetailActivity, getString(R.string.toast_message_delete_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun deleteCurrentConversation() {
        if (!SmsRoleUtils.isAppDefaultSmsHandler(this)) {
            Toast.makeText(this, getString(R.string.toast_conversation_delete_requires_default_sms), Toast.LENGTH_SHORT).show()
            return
        }

        val localThreadId = threadId ?: return

        CoroutineScope(Dispatchers.IO).launch {
            val trashItems = queryThreadMessagesForTrash(localThreadId)
            val deletedRows = contentResolver.delete(
                Telephony.Sms.CONTENT_URI,
                "${Telephony.Sms.THREAD_ID} = ?",
                arrayOf(localThreadId)
            )
            if (deletedRows > 0 && trashItems.isNotEmpty()) {
                AppDatabase.getDatabase(applicationContext).trashMessageDao().insertAll(trashItems)
            }

            withContext(Dispatchers.Main) {
                if (deletedRows > 0) {
                    finish()
                } else {
                    Toast.makeText(this@ConversationDetailActivity, getString(R.string.toast_no_message_to_delete), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun queryThreadMessagesForTrash(localThreadId: String): List<TrashMessage> {
        val archived = mutableListOf<TrashMessage>()
        val cursor = contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.TYPE,
                Telephony.Sms.THREAD_ID
            ),
            "${Telephony.Sms.THREAD_ID} = ?",
            arrayOf(localThreadId),
            null
        )

        cursor?.use {
            val idIdx = it.getColumnIndex(Telephony.Sms._ID)
            val addressIdx = it.getColumnIndex(Telephony.Sms.ADDRESS)
            val bodyIdx = it.getColumnIndex(Telephony.Sms.BODY)
            val dateIdx = it.getColumnIndex(Telephony.Sms.DATE)
            val typeIdx = it.getColumnIndex(Telephony.Sms.TYPE)
            val threadIdx = it.getColumnIndex(Telephony.Sms.THREAD_ID)

            while (it.moveToNext()) {
                archived.add(
                    TrashMessage(
                        originalSmsId = it.getString(idIdx) ?: "",
                        sender = it.getString(addressIdx) ?: getString(R.string.label_unknown_sender),
                        body = it.getString(bodyIdx) ?: "",
                        date = it.getLong(dateIdx),
                        type = it.getInt(typeIdx),
                        threadId = it.getString(threadIdx) ?: localThreadId,
                        deletedAt = System.currentTimeMillis()
                    )
                )
            }
        }

        return archived
    }

    private fun loadSavedChatBackgroundKey(): String {
        return AppSettings.getChatBackgroundKey(this)
    }

    private fun saveChatBackgroundKey(key: String) {
        AppSettings.setChatBackgroundKey(this, key)
    }

}

@Composable
private fun ConversationDetailScreen(
    title: String,
    messages: List<SmsModel>,
    inputText: String,
    chatBackgroundKey: String,
    onInputChanged: (String) -> Unit,
    onBackgroundThemeChange: (String) -> Unit,
    onBackClick: () -> Unit,
    onSendClick: () -> Unit,
    onDeleteMessage: (SmsModel) -> Unit,
    onDeleteConversation: () -> Unit
) {
    val listState = rememberLazyListState()
    var pendingDeleteMessage by remember { mutableStateOf<SmsModel?>(null) }
    var showDeleteConversationDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var pendingDeleteMessageIds by remember { mutableStateOf(setOf<String>()) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val visibleMessages = messages.filterNot { it.id in pendingDeleteMessageIds }
    val chatRows = remember(visibleMessages) { buildChatRows(visibleMessages) }
    val undoLabel = stringResource(R.string.action_undo)
    val messageDeletedLabel = stringResource(R.string.snackbar_message_deleted)
    val conversationWillDeleteLabel = stringResource(R.string.snackbar_conversation_will_delete)

    LaunchedEffect(visibleMessages.size) {
        if (visibleMessages.isNotEmpty()) {
            listState.animateScrollToItem(visibleMessages.lastIndex)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            ConversationTopBar(
                title = title,
                onBackClick = onBackClick,
                onThemeClick = { showThemeDialog = true },
                onDeleteConversationClick = { showDeleteConversationDialog = true }
            )
        },
        bottomBar = {
            ComposerBar(
                inputText = inputText,
                onInputChanged = onInputChanged,
                onSendClick = onSendClick
            )
        }
    ) { padding ->
        val chatBackgroundBrush = when (chatBackgroundKey) {
            AppSettings.CHAT_BG_OCEAN -> Brush.verticalGradient(listOf(Color(0xFFEAF4FF), Color(0xFFDDEBFF)))
            AppSettings.CHAT_BG_MINT -> Brush.verticalGradient(listOf(Color(0xFFEAFBF4), Color(0xFFDFF5EA)))
            AppSettings.CHAT_BG_SUNSET -> Brush.verticalGradient(listOf(Color(0xFFFFF4EB), Color(0xFFFFE8DA)))
            else -> Brush.verticalGradient(
                listOf(
                    MaterialTheme.colorScheme.background,
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                )
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .background(chatBackgroundBrush)
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                ConversationSummaryStrip(
                    messageCount = visibleMessages.size,
                    themeKey = chatBackgroundKey
                )
            }

            if (visibleMessages.isEmpty()) {
                item {
                    ConversationEmptyCard()
                }
            } else {
                items(chatRows, key = { it.key }) { row ->
                    when (row) {
                        is ChatRow.DayHeader -> DayHeaderChip(text = row.label)
                        is ChatRow.MessageRow -> {
                            MessageBubble(
                                message = row.message,
                                onLongPress = { pendingDeleteMessage = row.message }
                            )
                        }
                    }
                }
            }
        }
    }

    pendingDeleteMessage?.let { selected ->
        AlertDialog(
            onDismissRequest = { pendingDeleteMessage = null },
            title = { Text(stringResource(R.string.label_delete_message_prompt)) },
            text = { Text(selected.body.ifBlank { stringResource(R.string.label_empty_message) }) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDeleteMessage = null
                        pendingDeleteMessageIds = pendingDeleteMessageIds + selected.id

                        coroutineScope.launch {
                            val result = showSnackbarForThreeSeconds(
                                snackbarHostState = snackbarHostState,
                                message = messageDeletedLabel,
                                actionLabel = undoLabel
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                pendingDeleteMessageIds = pendingDeleteMessageIds - selected.id
                            } else {
                                onDeleteMessage(selected)
                                pendingDeleteMessageIds = pendingDeleteMessageIds - selected.id
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteMessage = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text(stringResource(R.string.label_theme)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeOptionButton(
                        text = stringResource(R.string.label_theme_classic),
                        selected = chatBackgroundKey == AppSettings.CHAT_BG_CLASSIC,
                        onClick = {
                            onBackgroundThemeChange(AppSettings.CHAT_BG_CLASSIC)
                            showThemeDialog = false
                        }
                    )
                    ThemeOptionButton(
                        text = stringResource(R.string.label_theme_ocean),
                        selected = chatBackgroundKey == AppSettings.CHAT_BG_OCEAN,
                        onClick = {
                            onBackgroundThemeChange(AppSettings.CHAT_BG_OCEAN)
                            showThemeDialog = false
                        }
                    )
                    ThemeOptionButton(
                        text = stringResource(R.string.label_theme_mint),
                        selected = chatBackgroundKey == AppSettings.CHAT_BG_MINT,
                        onClick = {
                            onBackgroundThemeChange(AppSettings.CHAT_BG_MINT)
                            showThemeDialog = false
                        }
                    )
                    ThemeOptionButton(
                        text = stringResource(R.string.label_theme_sunset),
                        selected = chatBackgroundKey == AppSettings.CHAT_BG_SUNSET,
                        onClick = {
                            onBackgroundThemeChange(AppSettings.CHAT_BG_SUNSET)
                            showThemeDialog = false
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text(stringResource(R.string.action_close))
                }
            }
        )
    }

    if (showDeleteConversationDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConversationDialog = false },
            title = { Text(stringResource(R.string.label_delete_conversation_prompt)) },
            text = { Text(stringResource(R.string.label_conversation_delete_desc)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConversationDialog = false
                        coroutineScope.launch {
                            val result = showSnackbarForThreeSeconds(
                                snackbarHostState = snackbarHostState,
                                message = conversationWillDeleteLabel,
                                actionLabel = undoLabel
                            )
                            if (result != SnackbarResult.ActionPerformed) {
                                onDeleteConversation()
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConversationDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun DayHeaderChip(text: String) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
            shadowElevation = 1.dp
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun ConversationSummaryStrip(
    messageCount: Int,
    themeKey: String
) {
    val selectedTheme = when (themeKey) {
        AppSettings.CHAT_BG_OCEAN -> stringResource(R.string.label_theme_ocean)
        AppSettings.CHAT_BG_MINT -> stringResource(R.string.label_theme_mint)
        AppSettings.CHAT_BG_SUNSET -> stringResource(R.string.label_theme_sunset)
        else -> stringResource(R.string.label_theme_classic)
    }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Text(
                    text = stringResource(R.string.label_chat_messages_count, messageCount),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Text(
                    text = stringResource(R.string.label_chat_theme_pill, selectedTheme),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun ConversationEmptyCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 36.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            shadowElevation = 2.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.label_sms), fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.label_no_messages),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.label_conversation_empty_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ConversationTopBar(
    title: String,
    onBackClick: () -> Unit,
    onThemeClick: () -> Unit,
    onDeleteConversationClick: () -> Unit
) {
    TopGradientBar(
        title = title,
        onBackClick = onBackClick,
        startColor = MaterialTheme.colorScheme.primary,
        endColor = MaterialTheme.colorScheme.secondary,
        actionLabel = stringResource(R.string.label_theme_action),
        onActionClick = onThemeClick,
        secondActionLabel = stringResource(R.string.action_delete),
        onSecondActionClick = onDeleteConversationClick
    )
}

@Composable
private fun ThemeOptionButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(if (selected) stringResource(R.string.label_theme_selected_suffix, text) else text)
    }
}

@Composable
private fun ComposerBar(
    inputText: String,
    onInputChanged: (String) -> Unit,
    onSendClick: () -> Unit
) {
    Surface(
        shadowElevation = 10.dp,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.imePadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.medium, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = onInputChanged,
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.label_message_placeholder)) },
                shape = RoundedCornerShape(24.dp),
                maxLines = 4
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onSendClick,
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Text(stringResource(R.string.action_send), fontWeight = FontWeight.Bold)
            }
        }
    }
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

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun MessageBubble(
    message: SmsModel,
    onLongPress: () -> Unit
) {
    val isSent = message.type == Telephony.Sms.MESSAGE_TYPE_SENT
    val rowAlignment = if (isSent) Arrangement.End else Arrangement.Start
    val bubbleColor = if (isSent) ChatSentBubble else ChatReceivedBubble
    val textColor = if (isSent) Color.White else MaterialTheme.colorScheme.onSurface
    val bubbleShape = if (isSent) {
        RoundedCornerShape(topStart = 18.dp, topEnd = 6.dp, bottomStart = 18.dp, bottomEnd = 18.dp)
    } else {
        RoundedCornerShape(topStart = 6.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp)
    }
    var appeared by remember(message.id, message.date) { mutableStateOf(false) }
    LaunchedEffect(message.id, message.date) { appeared = true }
    val alpha by animateFloatAsState(targetValue = if (appeared) 1f else 0f, animationSpec = tween(240), label = "bubble-alpha")
    val shiftY by animateFloatAsState(targetValue = if (appeared) 0f else 18f, animationSpec = tween(260), label = "bubble-shift")
    val popScale by animateFloatAsState(
        targetValue = if (appeared) 1f else if (isSent) 1.08f else 0.97f,
        animationSpec = tween(260),
        label = "bubble-pop-scale"
    )

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = rowAlignment) {
        Surface(
            shape = bubbleShape,
            color = bubbleColor,
            tonalElevation = if (isSent) 0.dp else 1.dp,
            shadowElevation = if (isSent) 0.dp else 1.dp,
            modifier = Modifier.combinedClickable(
                onClick = {},
                onLongClick = onLongPress
            ).graphicsLayer(
                alpha = alpha,
                translationY = shiftY,
                scaleX = popScale,
                scaleY = popScale
            )
        ) {
            Column(modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp)) {
                Text(
                    text = message.body,
                    color = textColor,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = message.date.toChatTime(),
                    color = if (isSent) Color.White.copy(alpha = 0.78f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

private sealed interface ChatRow {
    val key: String

    data class DayHeader(val label: String) : ChatRow {
        override val key: String = "header_$label"
    }

    data class MessageRow(val message: SmsModel) : ChatRow {
        override val key: String = "msg_${message.id}_${message.date}"
    }
}

private fun buildChatRows(messages: List<SmsModel>): List<ChatRow> {
    val rows = mutableListOf<ChatRow>()
    var previousDayLabel: String? = null
    for (message in messages) {
        val dayLabel = message.date.toChatDayLabel()
        if (dayLabel != previousDayLabel) {
            rows.add(ChatRow.DayHeader(dayLabel))
            previousDayLabel = dayLabel
        }
        rows.add(ChatRow.MessageRow(message))
    }
    return rows
}

private fun Long.toChatDayLabel(): String {
    return SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(Date(this))
}

private fun Long.toChatTime(): String {
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(this))
}
