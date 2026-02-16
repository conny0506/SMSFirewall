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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.example.smsfirewall.ui.theme.AppSpacing
import com.example.smsfirewall.ui.theme.SMSFirewallTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TrashBoxActivity : FragmentActivity() {

    private val trashList = mutableStateListOf<TrashMessage>()
    private var isLoading by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            SMSFirewallTheme {
                TrashBoxScreen(
                    trashList = trashList,
                    isLoading = isLoading,
                    onBackClick = { finish() },
                    onRestoreClick = { restoreMessage(it) },
                    onDeletePermanentClick = { deletePermanently(it) }
                )
            }
        }

        loadTrashMessages()
    }

    private fun loadTrashMessages() {
        CoroutineScope(Dispatchers.IO).launch {
            val dao = AppDatabase.getDatabase(applicationContext).trashMessageDao()
            val allTrash = dao.getAllTrash()
            withContext(Dispatchers.Main) {
                trashList.clear()
                trashList.addAll(allTrash)
                isLoading = false
            }
        }
    }

    private fun restoreMessage(item: TrashMessage) {
        if (Telephony.Sms.getDefaultSmsPackage(this) != packageName) {
            Toast.makeText(this, "Geri yuklemek icin varsayilan SMS uygulamasi olmalisin.", Toast.LENGTH_SHORT).show()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val values = ContentValues().apply {
                    put(Telephony.Sms.ADDRESS, item.sender)
                    put(Telephony.Sms.BODY, item.body)
                    put(Telephony.Sms.DATE, item.date)
                    put(Telephony.Sms.TYPE, item.type)
                    put(Telephony.Sms.READ, if (item.type == Telephony.Sms.MESSAGE_TYPE_SENT) 1 else 0)
                }
                val targetUri = if (item.type == Telephony.Sms.MESSAGE_TYPE_SENT) {
                    Telephony.Sms.Sent.CONTENT_URI
                } else {
                    Telephony.Sms.Inbox.CONTENT_URI
                }

                val insertedUri = contentResolver.insert(targetUri, values)
                if (insertedUri != null) {
                    AppDatabase.getDatabase(applicationContext).trashMessageDao().deleteById(item.id)
                    withContext(Dispatchers.Main) {
                        trashList.removeAll { it.id == item.id }
                        Toast.makeText(this@TrashBoxActivity, "Mesaj geri yuklendi", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    withContext(Dispatchers.Main) {
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

    private fun deletePermanently(item: TrashMessage) {
        CoroutineScope(Dispatchers.IO).launch {
            AppDatabase.getDatabase(applicationContext).trashMessageDao().deleteById(item.id)
            withContext(Dispatchers.Main) {
                trashList.removeAll { it.id == item.id }
                Toast.makeText(this@TrashBoxActivity, "Cop kutusundan silindi", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@Composable
private fun TrashBoxScreen(
    trashList: List<TrashMessage>,
    isLoading: Boolean,
    onBackClick: () -> Unit,
    onRestoreClick: (TrashMessage) -> Unit,
    onDeletePermanentClick: (TrashMessage) -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TrashTopBar(count = trashList.size, onBackClick = onBackClick)
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

            trashList.isEmpty() -> {
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
                    items(trashList, key = { it.id }) { item ->
                        TrashMessageCard(
                            item = item,
                            onRestoreClick = { onRestoreClick(item) },
                            onDeletePermanentClick = { onDeletePermanentClick(item) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrashTopBar(count: Int, onBackClick: () -> Unit) {
    val topBrush = Brush.linearGradient(
        listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
    )

    Surface(color = Color.Transparent) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(topBrush)
                .padding(horizontal = AppSpacing.medium, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBackClick) {
                Text("Geri", color = Color.White, fontWeight = FontWeight.Bold)
            }
            Text(
                text = "Cop Kutusu",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = Color.White.copy(alpha = 0.2f)
            ) {
                Text(
                    text = "$count",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }
    }
}

@Composable
private fun TrashMessageCard(
    item: TrashMessage,
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
                text = item.sender,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = item.body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = item.date.toTrashDate(),
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
