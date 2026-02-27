package com.example.smsfirewall

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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

class TrustedListActivity : FragmentActivity() {

    private val trustedNumbers = mutableStateListOf<String>()
    private var isLoading by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            SMSFirewallTheme {
                TrustedListScreen(
                    trustedNumbers = trustedNumbers,
                    isLoading = isLoading,
                    onBackClick = { finish() },
                    onAddNumber = { addTrustedNumber(it) },
                    onRemoveNumber = { removeTrustedNumber(it) }
                )
            }
        }

        loadTrustedNumbers()
    }

    private fun loadTrustedNumbers() {
        lifecycleScope.launch(Dispatchers.IO) {
            val numbers = AppDatabase.getDatabase(applicationContext).trustedNumberDao().getAll()
            withContext(Dispatchers.Main) {
                trustedNumbers.clear()
                trustedNumbers.addAll(numbers)
                isLoading = false
            }
        }
    }

    private fun addTrustedNumber(number: String) {
        val sanitized = number.trim()
        if (sanitized.isBlank()) return

        lifecycleScope.launch(Dispatchers.IO) {
            AppDatabase.getDatabase(applicationContext).trustedNumberDao().insert(TrustedNumber(sanitized))
            withContext(Dispatchers.Main) {
                if (!trustedNumbers.contains(sanitized)) {
                    trustedNumbers.add(sanitized)
                }
                Toast.makeText(
                    this@TrustedListActivity,
                    getString(R.string.toast_trusted_number_added, sanitized),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun removeTrustedNumber(number: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            AppDatabase.getDatabase(applicationContext).trustedNumberDao().delete(number)
            withContext(Dispatchers.Main) {
                trustedNumbers.removeAll { it == number }
                Toast.makeText(
                    this@TrustedListActivity,
                    getString(R.string.toast_trusted_number_removed, number),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}

@Composable
private fun TrustedListScreen(
    trustedNumbers: List<String>,
    isLoading: Boolean,
    onBackClick: () -> Unit,
    onAddNumber: (String) -> Unit,
    onRemoveNumber: (String) -> Unit
) {
    var newNumber by remember { mutableStateOf("") }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopGradientBar(
                title = stringResource(R.string.activity_trusted_list_label),
                onBackClick = onBackClick,
                startColor = MaterialTheme.colorScheme.primary,
                endColor = MaterialTheme.colorScheme.secondary,
                badgeText = trustedNumbers.size.toString()
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = AppSpacing.large),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.height(6.dp))
            TrustedListSummaryCard(count = trustedNumbers.size)
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.label_trusted_add),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    OutlinedTextField(
                        value = newNumber,
                        onValueChange = { newNumber = it },
                        placeholder = { Text(stringResource(R.string.label_trusted_input_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            val value = newNumber.trim()
                            if (value.isNotEmpty()) {
                                onAddNumber(value)
                                newNumber = ""
                            }
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(stringResource(R.string.action_add))
                    }
                }
            }

            when {
                isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = stringResource(R.string.label_loading))
                    }
                }

                trustedNumbers.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = stringResource(R.string.label_trusted_empty))
                    }
                }

                else -> {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        itemsIndexed(trustedNumbers, key = { _, item -> item }) { index, item ->
                            AnimatedVisibility(
                                visible = true,
                                enter = fadeIn(animationSpec = tween(220, delayMillis = index * 35)) +
                                    slideInVertically(
                                        initialOffsetY = { it / 3 },
                                        animationSpec = tween(260, delayMillis = index * 35)
                                    )
                            ) {
                                TrustedNumberCard(
                                    number = item,
                                    onRemove = { onRemoveNumber(item) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrustedListSummaryCard(count: Int) {
    Card(
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
                    text = stringResource(R.string.label_trusted_summary_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.label_trusted_summary_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Text(
                    text = count.toString(),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun TrustedNumberCard(
    number: String,
    onRemove: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = number, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    text = stringResource(R.string.label_trusted_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onRemove) {
                Text(stringResource(R.string.action_remove))
            }
        }
    }
}
