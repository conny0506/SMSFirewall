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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
                    title = address ?: "Bilinmeyen",
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
            Toast.makeText(this, "Hata: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteMessage(message: SmsModel) {
        if (!SmsRoleUtils.isAppDefaultSmsHandler(this)) {
            Toast.makeText(this, "Mesaj silmek icin varsayilan SMS uygulamasi olmalisin.", Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(this@ConversationDetailActivity, "Mesaj silinemedi", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun deleteCurrentConversation() {
        if (!SmsRoleUtils.isAppDefaultSmsHandler(this)) {
            Toast.makeText(this, "Sohbet silmek icin varsayilan SMS uygulamasi olmalisin.", Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(this@ConversationDetailActivity, "Silinecek mesaj bulunamadi", Toast.LENGTH_SHORT).show()
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
                        sender = it.getString(addressIdx) ?: "Bilinmeyen",
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

        if (visibleMessages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(chatBackgroundBrush)
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("Mesaj bulunamadi", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .background(chatBackgroundBrush)
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(visibleMessages, key = { it.id + it.date }) { message ->
                    MessageBubble(
                        message = message,
                        onLongPress = { pendingDeleteMessage = message }
                    )
                }
            }
        }
    }

    pendingDeleteMessage?.let { selected ->
        AlertDialog(
            onDismissRequest = { pendingDeleteMessage = null },
            title = { Text("Mesaj silinsin mi?") },
            text = { Text(selected.body.ifBlank { "(Bos mesaj)" }) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDeleteMessage = null
                        pendingDeleteMessageIds = pendingDeleteMessageIds + selected.id

                        coroutineScope.launch {
                            val result = showSnackbarForThreeSeconds(
                                snackbarHostState = snackbarHostState,
                                message = "Mesaj silindi",
                                actionLabel = "Geri al"
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
                    Text("Sil")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteMessage = null }) {
                    Text("Iptal")
                }
            }
        )
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Sohbet Arkaplani") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeOptionButton(
                        text = "Sade",
                        selected = chatBackgroundKey == AppSettings.CHAT_BG_CLASSIC,
                        onClick = {
                            onBackgroundThemeChange(AppSettings.CHAT_BG_CLASSIC)
                            showThemeDialog = false
                        }
                    )
                    ThemeOptionButton(
                        text = "Mavi",
                        selected = chatBackgroundKey == AppSettings.CHAT_BG_OCEAN,
                        onClick = {
                            onBackgroundThemeChange(AppSettings.CHAT_BG_OCEAN)
                            showThemeDialog = false
                        }
                    )
                    ThemeOptionButton(
                        text = "Mint",
                        selected = chatBackgroundKey == AppSettings.CHAT_BG_MINT,
                        onClick = {
                            onBackgroundThemeChange(AppSettings.CHAT_BG_MINT)
                            showThemeDialog = false
                        }
                    )
                    ThemeOptionButton(
                        text = "Gunbatimi",
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
                    Text("Kapat")
                }
            }
        )
    }

    if (showDeleteConversationDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConversationDialog = false },
            title = { Text("Sohbet silinsin mi?") },
            text = { Text("Bu kisiyle tum mesajlar silinecek.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConversationDialog = false
                        coroutineScope.launch {
                            val result = showSnackbarForThreeSeconds(
                                snackbarHostState = snackbarHostState,
                                message = "Sohbet silinecek",
                                actionLabel = "Geri al"
                            )
                            if (result != SnackbarResult.ActionPerformed) {
                                onDeleteConversation()
                            }
                        }
                    }
                ) {
                    Text("Sil")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConversationDialog = false }) {
                    Text("Iptal")
                }
            }
        )
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
        actionLabel = "Tema",
        onActionClick = onThemeClick,
        secondActionLabel = "Sil",
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
        Text(if (selected) "$text (Secili)" else text)
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
                placeholder = { Text("Mesaj yaz...") },
                shape = RoundedCornerShape(24.dp),
                maxLines = 4
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onSendClick,
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Text("Gonder", fontWeight = FontWeight.Bold)
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

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = rowAlignment) {
        Surface(
            shape = bubbleShape,
            color = bubbleColor,
            tonalElevation = if (isSent) 0.dp else 1.dp,
            shadowElevation = if (isSent) 0.dp else 1.dp,
            modifier = Modifier.combinedClickable(
                onClick = {},
                onLongClick = onLongPress
            )
        ) {
            Text(
                text = message.body,
                color = textColor,
                modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
