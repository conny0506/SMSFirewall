package com.example.smsfirewall

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import android.telephony.SmsManager
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.smsfirewall.ui.theme.AppSpacing
import com.example.smsfirewall.ui.theme.ChatReceivedBubble
import com.example.smsfirewall.ui.theme.ChatSentBubble
import com.example.smsfirewall.ui.theme.SMSFirewallTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ConversationDetailActivity : FragmentActivity() {

    private val messageList = mutableStateListOf<SmsModel>()
    private var inputText by mutableStateOf("")

    private var threadId: String? = null
    private var address: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        threadId = intent.getStringExtra("thread_id")
        address = intent.getStringExtra("address")

        setContent {
            SMSFirewallTheme {
                ConversationDetailScreen(
                    title = address ?: "Bilinmeyen",
                    messages = messageList,
                    inputText = inputText,
                    onInputChanged = { inputText = it },
                    onBackClick = { finish() },
                    onSendClick = { sendMessage() }
                )
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) {
            loadMessages()
        }
    }

    private fun loadMessages() {
        val localThreadId = threadId ?: return

        CoroutineScope(Dispatchers.IO).launch {
            val loadedMessages = mutableListOf<SmsModel>()

            val selection = "${Telephony.Sms.THREAD_ID} = ?"
            val selectionArgs = arrayOf(localThreadId)

            val cursor = contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(
                    Telephony.Sms._ID,
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY,
                    Telephony.Sms.DATE,
                    Telephony.Sms.TYPE
                ),
                selection,
                selectionArgs,
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
            contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)

            messageList.add(
                SmsModel(
                    id = now.toString(),
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
}

@Composable
private fun ConversationDetailScreen(
    title: String,
    messages: List<SmsModel>,
    inputText: String,
    onInputChanged: (String) -> Unit,
    onBackClick: () -> Unit,
    onSendClick: () -> Unit
) {
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(shadowElevation = 2.dp, color = MaterialTheme.colorScheme.surface) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppSpacing.small, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onBackClick) {
                        Text("Geri")
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        },
        bottomBar = {
            Surface(
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.imePadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(AppSpacing.medium),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = onInputChanged,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Mesaj yazin...") },
                        shape = RoundedCornerShape(20.dp),
                        maxLines = 4
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = onSendClick) {
                        Text("Gonder")
                    }
                }
            }
        }
    ) { padding ->
        if (messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
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
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = AppSpacing.large),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { it.id + it.date }) { message ->
                    MessageBubble(message = message)
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: SmsModel) {
    val isSent = message.type == Telephony.Sms.MESSAGE_TYPE_SENT
    val rowAlignment = if (isSent) Arrangement.End else Arrangement.Start
    val bubbleColor = if (isSent) ChatSentBubble else ChatReceivedBubble
    val textColor = if (isSent) Color.White else MaterialTheme.colorScheme.onSurface

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = rowAlignment) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = bubbleColor
        ) {
            Text(
                text = message.body,
                color = textColor,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
