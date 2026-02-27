package com.example.smsfirewall

import android.os.Bundle
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.example.smsfirewall.ui.components.TopGradientBar
import com.example.smsfirewall.ui.theme.AppSpacing
import com.example.smsfirewall.ui.theme.SMSFirewallTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SpamBoxActivity : FragmentActivity() {

    private val spamList = mutableStateListOf<SmsModel>()
    private var isLoading by mutableStateOf(true)
    private var blockedWords by mutableStateOf<List<String>>(emptyList())
    private var trustedNumbers by mutableStateOf<Set<String>>(emptySet())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            SMSFirewallTheme {
                SpamBoxScreen(
                    spamList = spamList,
                    isLoading = isLoading,
                    blockedWords = blockedWords,
                    trustedNumbers = trustedNumbers,
                    onBackClick = { finish() },
                    onTrustNumber = { number -> addTrustedNumber(number) }
                )
            }
        }

        loadSpamMessages()
    }

    private fun loadSpamMessages() {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(applicationContext)
            val spamMessages = db.spamMessageDao().getAllSpam()
            val words = db.blockedWordDao().getWordListRaw()
            val trusted = db.trustedNumberDao().getAll()

            withContext(Dispatchers.Main) {
                spamList.clear()
                spamList.addAll(
                    spamMessages.map { spam ->
                        SmsModel(
                            id = spam.id.toString(),
                            address = spam.sender,
                            body = spam.body,
                            date = spam.date,
                            type = 1,
                            threadId = "spam_${spam.id}"
                        )
                    }
                )
                blockedWords = words
                trustedNumbers = trusted.toSet()
                isLoading = false
            }
        }
    }

    private fun addTrustedNumber(number: String) {
        if (trustedNumbers.contains(number)) {
            Toast.makeText(this, getString(R.string.toast_trusted_number_exists), Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            AppDatabase.getDatabase(applicationContext).trustedNumberDao().insert(TrustedNumber(number))
            withContext(Dispatchers.Main) {
                trustedNumbers = trustedNumbers + number
                Toast.makeText(
                    this@SpamBoxActivity,
                    getString(R.string.toast_trusted_number_added, number),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}

@Composable
private fun SpamBoxScreen(
    spamList: List<SmsModel>,
    isLoading: Boolean,
    blockedWords: List<String>,
    trustedNumbers: Set<String>,
    onBackClick: () -> Unit,
    onTrustNumber: (String) -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SpamTopBar(count = spamList.size, onBackClick = onBackClick)
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = AppSpacing.large)
        ) {
            Spacer(modifier = Modifier.height(10.dp))
            SpamHeroCard(count = spamList.size)
            Spacer(modifier = Modifier.height(10.dp))
            SpamSummaryCard(spamCount = spamList.size)
            Spacer(modifier = Modifier.height(10.dp))

            when {
                isLoading -> {
                    StatusStateCard(
                        modifier = Modifier.weight(1f),
                        title = stringResource(R.string.label_loading),
                        subtitle = stringResource(R.string.label_spam_loading_desc)
                    )
                }

                spamList.isEmpty() -> {
                    StatusStateCard(
                        modifier = Modifier.weight(1f),
                        title = stringResource(R.string.label_no_spam),
                        subtitle = stringResource(R.string.label_spam_empty_desc)
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
                        items(spamList, key = { it.threadId }) { sms ->
                            SpamMessageInsightCard(
                                item = sms,
                                blockedWords = blockedWords,
                                isTrusted = trustedNumbers.contains(sms.address),
                                selected = false,
                                selectionMode = false,
                                onLongClick = {},
                                onDelete = {},
                                onTrustNumber = onTrustNumber
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpamHeroCard(count: Int) {
    val brush = Brush.linearGradient(
        listOf(
            MaterialTheme.colorScheme.tertiary,
            MaterialTheme.colorScheme.primary
        )
    )
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Column(
            modifier = Modifier
                .background(brush)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.label_spam_hero_title),
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = stringResource(R.string.label_spam_hero_desc),
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color.White.copy(alpha = 0.22f)
                ) {
                    Text(
                        text = count.toString(),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun SpamSummaryCard(spamCount: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = stringResource(R.string.label_spam_summary_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.label_spam_summary_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Text(
                    text = spamCount.toString(),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun StatusStateCard(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String
) {
    Box(
        modifier = modifier
            .fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
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
                        Text("!", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SpamTopBar(count: Int, onBackClick: () -> Unit) {
    TopGradientBar(
        title = stringResource(R.string.activity_spam_box_label),
        onBackClick = onBackClick,
        startColor = MaterialTheme.colorScheme.tertiary,
        endColor = MaterialTheme.colorScheme.primary,
        badgeText = count.toString()
    )
}
