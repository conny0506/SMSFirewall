package com.example.smsfirewall

import android.content.ContentValues
import android.provider.Telephony
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min

private enum class InboxFilter {
    ALL,
    UNREAD
}

private enum class HomeTab {
    INBOX,
    SPAM,
    TRASH,
    SETTINGS
}

private data class TrashConversationSummary(
    val threadId: String,
    val displayName: String,
    val messageCount: Int,
    val lastDeletedAt: Long
)

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun MainScreen(
    smsList: List<SmsModel>,
    isDefaultSmsApp: Boolean,
    showUnreadIndicators: Boolean,
    onStartChatClick: (String) -> Unit,
    onSetDefaultClick: () -> Unit,
    onConversationClick: (SmsModel) -> Unit,
    onBulkDeleteConversationsClick: (List<SmsModel>) -> Unit,
    onDeleteConversationClick: (SmsModel) -> Unit
) {
    val context = LocalContext.current
    var pendingDelete by remember { mutableStateOf<SmsModel?>(null) }
    var pendingBulkDelete by remember { mutableStateOf<List<SmsModel>>(emptyList()) }
    var pendingDeleteThreadIds by remember { mutableStateOf(setOf<String>()) }
    var selectedThreadIds by remember { mutableStateOf(setOf<String>()) }
    var pinnedThreadIds by remember(context) { mutableStateOf(AppSettings.getPinnedThreadIds(context)) }
    var mutedThreadIds by remember(context) { mutableStateOf(AppSettings.getMutedThreadIds(context)) }
    var searchQuery by remember { mutableStateOf("") }
    var inboxFilter by remember { mutableStateOf(InboxFilter.ALL) }
    var selectedTab by remember { mutableStateOf(HomeTab.INBOX) }
    var showNewChatDialog by remember { mutableStateOf(false) }
    var newChatAddress by remember { mutableStateOf("") }
    var localShowUnreadIndicators by remember { mutableStateOf(showUnreadIndicators) }
    var notificationContentVisible by remember(context) { mutableStateOf(AppSettings.isNotificationContentVisible(context)) }
    var chatBackgroundKey by remember(context) { mutableStateOf(AppSettings.getChatBackgroundKey(context)) }
    val spamList = remember { mutableStateListOf<SmsModel>() }
    val trashConversations = remember { mutableStateListOf<TrashConversationSummary>() }
    var isSpamLoading by remember { mutableStateOf(false) }
    var isTrashLoading by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val visibleSmsList = smsList.filterNot { it.threadId in pendingDeleteThreadIds }
    val orderedSmsList = visibleSmsList.sortedWith(
        compareByDescending<SmsModel> { it.threadId in pinnedThreadIds }
            .thenByDescending { it.date }
    )
    val filteredList = orderedSmsList.filter { sms ->
        val matchesSearch = searchQuery.isBlank() ||
            sms.address.contains(searchQuery, ignoreCase = true) ||
            sms.body.contains(searchQuery, ignoreCase = true)

        val matchesFilter = when (inboxFilter) {
            InboxFilter.ALL -> true
            InboxFilter.UNREAD -> localShowUnreadIndicators && sms.unreadCount > 0 && sms.threadId !in mutedThreadIds
        }
        matchesSearch && matchesFilter
    }

    val unreadTotal = if (localShowUnreadIndicators) {
        filteredList.filterNot { it.threadId in mutedThreadIds }.sumOf { it.unreadCount }
    } else {
        0
    }
    val snackbarConversationDeleted = stringResource(R.string.snackbar_conversation_deleted)
    val undoLabel = stringResource(R.string.action_undo)

    LaunchedEffect(selectedTab) {
        if (selectedTab == HomeTab.SPAM) {
            isSpamLoading = true
            val spamMessages = withContext(Dispatchers.IO) {
                AppDatabase.getDatabase(context.applicationContext).spamMessageDao().getAllSpam()
            }
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
            isSpamLoading = false
        }
        if (selectedTab == HomeTab.TRASH) {
            isTrashLoading = true
            val grouped = withContext(Dispatchers.IO) {
                AppDatabase.getDatabase(context.applicationContext).trashMessageDao().getAllTrash()
                    .groupBy { it.threadId }
                    .values
                    .map { messages ->
                        val latest = messages.maxByOrNull { it.deletedAt } ?: messages.first()
                        val displayName = messages.firstOrNull { it.sender.isNotBlank() }?.sender
                            ?: context.getString(R.string.label_unknown_sender)
                        TrashConversationSummary(
                            threadId = latest.threadId,
                            displayName = displayName,
                            messageCount = messages.size,
                            lastDeletedAt = latest.deletedAt
                        )
                    }
                    .sortedByDescending { it.lastDeletedAt }
            }
            trashConversations.clear()
            trashConversations.addAll(grouped)
            isTrashLoading = false
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            AnimatedVisibility(
                visible = selectedTab == HomeTab.INBOX,
                enter = fadeIn(animationSpec = tween(220)) + slideInVertically(initialOffsetY = { it / 2 }, animationSpec = tween(260)),
                exit = fadeOut(animationSpec = tween(180)) + slideOutVertically(targetOffsetY = { it / 2 }, animationSpec = tween(220))
            ) {
                FloatingActionButton(onClick = { showNewChatDialog = true }) {
                    Text(text = stringResource(R.string.label_new_message), fontWeight = FontWeight.Bold)
                }
            }
        },
        bottomBar = {
            HomeBottomBar(
                selectedTab = selectedTab,
                onSelect = { tab ->
                    selectedTab = tab
                }
            )
        }
    ) { padding ->
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                (fadeIn(animationSpec = tween(260)) + slideInVertically(initialOffsetY = { it / 8 }, animationSpec = tween(260)))
                    .togetherWith(fadeOut(animationSpec = tween(200)))
            },
            label = "home-tab-content"
        ) { currentTab ->
        if (currentTab == HomeTab.INBOX) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = AppSpacing.xLarge)
            ) {
                Spacer(modifier = Modifier.height(14.dp))

                MainHeroHeader(
                    threadCount = filteredList.size,
                    unreadCount = unreadTotal
                )

                Spacer(modifier = Modifier.height(10.dp))

                SearchAndFilterBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    selectedFilter = inboxFilter,
                    onFilterChange = { inboxFilter = it }
                )

                Spacer(modifier = Modifier.height(AppSpacing.medium))

                AnimatedVisibility(
                    visible = !isDefaultSmsApp,
                    enter = fadeIn(animationSpec = tween(220)),
                    exit = fadeOut(animationSpec = tween(160))
                ) {
                    DefaultAppWarningCard(onClick = onSetDefaultClick)
                }
                Spacer(modifier = Modifier.height(AppSpacing.large))

                AnimatedVisibility(
                    visible = selectedThreadIds.isNotEmpty(),
                    enter = fadeIn(animationSpec = tween(180)) + slideInVertically(initialOffsetY = { -it / 2 }, animationSpec = tween(220)),
                    exit = fadeOut(animationSpec = tween(120))
                ) {
                    SelectionActionBar(
                        count = selectedThreadIds.size,
                        onClear = { selectedThreadIds = emptySet() },
                        onDelete = {
                            pendingBulkDelete = filteredList.filter { it.threadId in selectedThreadIds }
                        }
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))

                if (filteredList.isEmpty()) {
                    EmptyState(modifier = Modifier.weight(1f))
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 88.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredList, key = { it.threadId }) { sms ->
                            Box {
                                SwipeDeleteConversationItem(
                                    onDelete = { pendingDelete = sms }
                                ) {
                                    SmsConversationCard(
                                        sms = sms,
                                        showUnreadIndicators = localShowUnreadIndicators,
                                        selected = sms.threadId in selectedThreadIds,
                                        selectionMode = selectedThreadIds.isNotEmpty(),
                                        pinned = sms.threadId in pinnedThreadIds,
                                        muted = sms.threadId in mutedThreadIds,
                                        onClick = {
                                            if (selectedThreadIds.isNotEmpty()) {
                                                selectedThreadIds = toggleId(selectedThreadIds, sms.threadId)
                                            } else {
                                                onConversationClick(sms)
                                            }
                                        },
                                        onLongClick = {
                                            selectedThreadIds = toggleId(selectedThreadIds, sms.threadId)
                                        },
                                        onDeleteClick = { pendingDelete = sms },
                                        onTogglePin = {
                                            pinnedThreadIds = toggleId(pinnedThreadIds, sms.threadId)
                                            AppSettings.setPinnedThreadIds(context, pinnedThreadIds)
                                        },
                                        onToggleMute = {
                                            mutedThreadIds = toggleId(mutedThreadIds, sms.threadId)
                                            AppSettings.setMutedThreadIds(context, mutedThreadIds)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            when (currentTab) {
                HomeTab.SPAM -> {
                    SpamTabContent(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        spamList = spamList,
                        isLoading = isSpamLoading
                    )
                }
                HomeTab.TRASH -> {
                    TrashTabContent(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        conversations = trashConversations,
                        isLoading = isTrashLoading,
                        onRestoreConversation = { item ->
                            if (!SmsRoleUtils.isAppDefaultSmsHandler(context)) {
                                Toast.makeText(context, context.getString(R.string.toast_restore_requires_default_sms), Toast.LENGTH_SHORT).show()
                            } else {
                                coroutineScope.launch {
                                    val restoredCount = withContext(Dispatchers.IO) {
                                        val dao = AppDatabase.getDatabase(context.applicationContext).trashMessageDao()
                                        val messages = dao.getByThreadId(item.threadId)
                                        var restored = 0
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
                                            if (context.contentResolver.insert(targetUri, values) != null) restored++
                                        }
                                        if (restored > 0) dao.deleteByThreadId(item.threadId)
                                        restored
                                    }
                                    if (restoredCount > 0) {
                                        trashConversations.removeAll { it.threadId == item.threadId }
                                        Toast.makeText(
                                            context,
                                            context.resources.getQuantityString(
                                                R.plurals.toast_conversation_restored_count,
                                                restoredCount,
                                                restoredCount
                                            ),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        Toast.makeText(context, context.getString(R.string.toast_restore_failed), Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        onDeletePermanent = { item ->
                            coroutineScope.launch {
                                withContext(Dispatchers.IO) {
                                    AppDatabase.getDatabase(context.applicationContext).trashMessageDao().deleteByThreadId(item.threadId)
                                }
                                trashConversations.removeAll { it.threadId == item.threadId }
                            }
                        }
                    )
                }
                HomeTab.SETTINGS -> {
                    SettingsTabContent(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        unreadBadgesEnabled = localShowUnreadIndicators,
                        notificationContentVisible = notificationContentVisible,
                        chatBackgroundKey = chatBackgroundKey,
                        onUnreadBadgeChange = {
                            localShowUnreadIndicators = it
                            AppSettings.setUnreadBadgesEnabled(context, it)
                        },
                        onNotificationContentChange = {
                            notificationContentVisible = it
                            AppSettings.setNotificationContentVisible(context, it)
                        },
                        onChatBackgroundChange = {
                            chatBackgroundKey = it
                            AppSettings.setChatBackgroundKey(context, it)
                        },
                        onClearTrash = {
                            coroutineScope.launch {
                                withContext(Dispatchers.IO) {
                                    AppDatabase.getDatabase(context.applicationContext).trashMessageDao().deleteAll()
                                }
                                trashConversations.clear()
                                Toast.makeText(context, context.getString(R.string.toast_trash_cleared), Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
                HomeTab.INBOX -> Unit
            }
        }
        }
    }

    pendingDelete?.let { sms ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.label_delete_conversation_prompt)) },
            text = { Text(stringResource(R.string.label_delete_conversation_desc, sms.address)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        pendingDeleteThreadIds = pendingDeleteThreadIds + sms.threadId

                        coroutineScope.launch {
                            val result = showSnackbarForThreeSeconds(
                                snackbarHostState = snackbarHostState,
                                message = snackbarConversationDeleted,
                                actionLabel = undoLabel
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                pendingDeleteThreadIds = pendingDeleteThreadIds - sms.threadId
                            } else {
                                onDeleteConversationClick(sms)
                                pendingDeleteThreadIds = pendingDeleteThreadIds - sms.threadId
                                selectedThreadIds = selectedThreadIds - sms.threadId
                                pinnedThreadIds = pinnedThreadIds - sms.threadId
                                mutedThreadIds = mutedThreadIds - sms.threadId
                                AppSettings.setPinnedThreadIds(context, pinnedThreadIds)
                                AppSettings.setMutedThreadIds(context, mutedThreadIds)
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (pendingBulkDelete.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { pendingBulkDelete = emptyList() },
            title = { Text(stringResource(R.string.label_delete_selected_prompt)) },
            text = { Text(stringResource(R.string.label_delete_selected_desc, pendingBulkDelete.size)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onBulkDeleteConversationsClick(pendingBulkDelete)
                        val deletedIds = pendingBulkDelete.map { it.threadId }.toSet()
                        pinnedThreadIds = pinnedThreadIds - deletedIds
                        mutedThreadIds = mutedThreadIds - deletedIds
                        AppSettings.setPinnedThreadIds(context, pinnedThreadIds)
                        AppSettings.setMutedThreadIds(context, mutedThreadIds)
                        selectedThreadIds = emptySet()
                        pendingBulkDelete = emptyList()
                    }
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingBulkDelete = emptyList() }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showNewChatDialog) {
        AlertDialog(
            onDismissRequest = { showNewChatDialog = false },
            title = { Text(stringResource(R.string.label_start_new_chat)) },
            text = {
                OutlinedTextField(
                    value = newChatAddress,
                    onValueChange = { newChatAddress = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.label_new_chat_placeholder)) }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = newChatAddress.trim()
                        if (trimmed.isNotEmpty()) {
                            onStartChatClick(trimmed)
                            newChatAddress = ""
                            showNewChatDialog = false
                        }
                    }
                ) {
                    Text(stringResource(R.string.action_start_chat))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showNewChatDialog = false
                        newChatAddress = ""
                    }
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

private fun toggleId(ids: Set<String>, target: String): Set<String> {
    return if (target in ids) ids - target else ids + target
}

@Composable
private fun HomeBottomBar(
    selectedTab: HomeTab,
    onSelect: (HomeTab) -> Unit
) {
    val selectedIndex = when (selectedTab) {
        HomeTab.INBOX -> 0
        HomeTab.SPAM -> 1
        HomeTab.TRASH -> 2
        HomeTab.SETTINGS -> 3
    }
    val barElevation by animateFloatAsState(
        targetValue = if (selectedTab == HomeTab.SPAM) 14f else 10f,
        animationSpec = tween(280),
        label = "bottom-bar-elevation"
    )
    Surface(
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 6.dp,
        shadowElevation = barElevation.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 11.dp)
        ) {
            val segmentWidth = maxWidth / 4
            val targetOffset = segmentWidth * selectedIndex
            val animatedOffset by animateDpAsState(
                targetValue = targetOffset,
                animationSpec = tween(280),
                label = "tab-indicator-offset"
            )
            Box(
                modifier = Modifier
                    .offset(x = animatedOffset)
                    .width(segmentWidth)
                    .padding(horizontal = 3.dp)
                    .height(40.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                        shape = RoundedCornerShape(14.dp)
                    )
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ModernTabChip(
                    modifier = Modifier.weight(1f),
                    selected = selectedTab == HomeTab.INBOX,
                    label = stringResource(R.string.label_tab_inbox),
                    onClick = { onSelect(HomeTab.INBOX) }
                )
                ModernTabChip(
                    modifier = Modifier.weight(1f),
                    selected = selectedTab == HomeTab.SPAM,
                    label = stringResource(R.string.label_tab_spam),
                    onClick = { onSelect(HomeTab.SPAM) }
                )
                ModernTabChip(
                    modifier = Modifier.weight(1f),
                    selected = selectedTab == HomeTab.TRASH,
                    label = stringResource(R.string.label_tab_trash),
                    onClick = { onSelect(HomeTab.TRASH) }
                )
                ModernTabChip(
                    modifier = Modifier.weight(1f),
                    selected = selectedTab == HomeTab.SETTINGS,
                    label = stringResource(R.string.label_tab_settings),
                    onClick = { onSelect(HomeTab.SETTINGS) }
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun ModernTabChip(
    modifier: Modifier = Modifier,
    selected: Boolean,
    label: String,
    onClick: () -> Unit
) {
    val targetContainer = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent
    val targetBorder = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant
    val targetText = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val scale by animateFloatAsState(targetValue = if (selected) 1.03f else 1f, animationSpec = tween(220), label = "tab-scale")

    Surface(
        modifier = modifier
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = targetContainer,
        border = BorderStroke(
            width = if (selected) 1.2.dp else 1.dp,
            color = targetBorder
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 11.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = targetText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun SpamTabContent(
    modifier: Modifier = Modifier,
    spamList: List<SmsModel>,
    isLoading: Boolean
) {
    val spamBrush = Brush.verticalGradient(
        listOf(
            Color(0xFFFFF2EE),
            Color(0xFFFFF8F3),
            MaterialTheme.colorScheme.background
        )
    )
    Column(
        modifier = modifier
            .background(spamBrush)
            .padding(horizontal = AppSpacing.large, vertical = 12.dp)
    ) {
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(animationSpec = tween(260)) + slideInVertically(initialOffsetY = { -it / 3 }, animationSpec = tween(320))
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.22f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.label_tab_spam),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.label_spam_summary_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f)) {
                        Text(
                            text = spamList.size.toString(),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.label_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (spamList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = stringResource(R.string.label_no_spam), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(spamList, key = { _, item -> item.threadId }) { index, item ->
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(animationSpec = tween(220, delayMillis = min(index * 36, 220))) +
                            slideInVertically(
                                initialOffsetY = { it / 3 },
                                animationSpec = tween(260, delayMillis = min(index * 36, 220))
                            )
                    ) {
                        Box {
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(item.address, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(item.body, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(item.date.toDayTime(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrashTabContent(
    modifier: Modifier = Modifier,
    conversations: List<TrashConversationSummary>,
    isLoading: Boolean,
    onRestoreConversation: (TrashConversationSummary) -> Unit,
    onDeletePermanent: (TrashConversationSummary) -> Unit
) {
    Column(modifier = modifier.padding(horizontal = AppSpacing.large, vertical = 12.dp)) {
        Text(
            text = stringResource(R.string.label_tab_trash),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(10.dp))
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = stringResource(R.string.label_loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else if (conversations.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = stringResource(R.string.label_trash_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(conversations, key = { _, item -> item.threadId }) { index, item ->
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(animationSpec = tween(220, delayMillis = min(index * 30, 220))) +
                            slideInVertically(
                                initialOffsetY = { it / 4 },
                                animationSpec = tween(260, delayMillis = min(index * 30, 220))
                            )
                    ) {
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(item.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.label_delete_selected_desc, item.messageCount),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilledTonalButton(onClick = { onRestoreConversation(item) }) {
                                        Text(stringResource(R.string.action_restore))
                                    }
                                    TextButton(onClick = { onDeletePermanent(item) }) {
                                        Text(stringResource(R.string.label_delete_permanent))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsTabContent(
    modifier: Modifier = Modifier,
    unreadBadgesEnabled: Boolean,
    notificationContentVisible: Boolean,
    chatBackgroundKey: String,
    onUnreadBadgeChange: (Boolean) -> Unit,
    onNotificationContentChange: (Boolean) -> Unit,
    onChatBackgroundChange: (String) -> Unit,
    onClearTrash: () -> Unit
) {
    Column(
        modifier = modifier.padding(horizontal = AppSpacing.large, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = stringResource(R.string.label_tab_settings),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        SettingSwitchRow(
            title = stringResource(R.string.label_show_unread_badges),
            subtitle = stringResource(R.string.label_unread_badges_desc),
            checked = unreadBadgesEnabled,
            onCheckedChange = onUnreadBadgeChange
        )
        SettingSwitchRow(
            title = stringResource(R.string.label_show_notification_content),
            subtitle = stringResource(R.string.label_show_notification_content_desc),
            checked = notificationContentVisible,
            onCheckedChange = onNotificationContentChange
        )
        Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = stringResource(R.string.label_default_chat_background), style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = chatBackgroundKey == AppSettings.CHAT_BG_CLASSIC,
                        onClick = { onChatBackgroundChange(AppSettings.CHAT_BG_CLASSIC) },
                        label = { Text(stringResource(R.string.label_theme_classic)) }
                    )
                    FilterChip(
                        selected = chatBackgroundKey == AppSettings.CHAT_BG_OCEAN,
                        onClick = { onChatBackgroundChange(AppSettings.CHAT_BG_OCEAN) },
                        label = { Text(stringResource(R.string.label_theme_ocean)) }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = chatBackgroundKey == AppSettings.CHAT_BG_MINT,
                        onClick = { onChatBackgroundChange(AppSettings.CHAT_BG_MINT) },
                        label = { Text(stringResource(R.string.label_theme_mint)) }
                    )
                    FilterChip(
                        selected = chatBackgroundKey == AppSettings.CHAT_BG_SUNSET,
                        onClick = { onChatBackgroundChange(AppSettings.CHAT_BG_SUNSET) },
                        label = { Text(stringResource(R.string.label_theme_sunset)) }
                    )
                }
            }
        }
        FilledTonalButton(onClick = onClearTrash) {
            Text(stringResource(R.string.action_clean), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            androidx.compose.material3.Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun SearchAndFilterBar(
    query: String,
    onQueryChange: (String) -> Unit,
    selectedFilter: InboxFilter,
    onFilterChange: (InboxFilter) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.label_search_conversations)) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MorphFilterChip(
                selected = selectedFilter == InboxFilter.ALL,
                onClick = { onFilterChange(InboxFilter.ALL) },
                label = stringResource(R.string.label_filter_all)
            )
            MorphFilterChip(
                selected = selectedFilter == InboxFilter.UNREAD,
                onClick = { onFilterChange(InboxFilter.UNREAD) },
                label = stringResource(R.string.label_filter_unread)
            )
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun MorphFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String
) {
    val radius by animateDpAsState(targetValue = if (selected) 16.dp else 12.dp, animationSpec = tween(240), label = "filter-radius")
    val padX by animateDpAsState(targetValue = if (selected) 12.dp else 10.dp, animationSpec = tween(220), label = "filter-pad-x")
    val padY by animateDpAsState(targetValue = if (selected) 8.dp else 7.dp, animationSpec = tween(220), label = "filter-pad-y")
    val scale by animateFloatAsState(targetValue = if (selected) 1.03f else 1f, animationSpec = tween(220), label = "filter-scale")
    Surface(
        modifier = Modifier
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .clip(RoundedCornerShape(radius))
            .combinedClickable(onClick = onClick),
        shape = RoundedCornerShape(radius),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (selected) 1.2.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = padX, vertical = padY),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun SelectionActionBar(
    count: Int,
    onClear: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.label_selected_count, count),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onClear) {
                    Text(stringResource(R.string.action_clear_selection))
                }
                FilledTonalButton(onClick = onDelete) {
                    Text(stringResource(R.string.action_delete_selected), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeDeleteConversationItem(
    onDelete: () -> Unit,
    content: @Composable () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                false
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Text(
                    text = stringResource(R.string.action_delete),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        content = { content() }
    )
}

@Composable
private fun MainHeroHeader(
    threadCount: Int,
    unreadCount: Int
) {
    val resources = LocalContext.current.resources
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
                        text = stringResource(R.string.label_messages),
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = stringResource(R.string.label_inbox_tagline),
                        color = Color.White.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White.copy(alpha = 0.2f)
                ) {
                    val statusText = if (unreadCount > 0) {
                        resources.getQuantityString(R.plurals.label_new_count, unreadCount, unreadCount)
                    } else {
                        stringResource(R.string.label_recent)
                    }
                    Text(
                        text = statusText,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricPill(label = stringResource(R.string.label_threads), value = threadCount.toString())
                MetricPill(label = stringResource(R.string.label_unread), value = unreadCount.toString())
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
@OptIn(ExperimentalFoundationApi::class)
private fun DefaultAppWarningCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = WarningContainer),
        border = BorderStroke(1.dp, WarningContainerStrong)
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
                    text = stringResource(R.string.label_default_sms_app),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.label_default_sms_app_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = WarningOnContainer
                )
            }
            TextButton(onClick = onClick) {
                Text(text = stringResource(R.string.action_set), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun SmsConversationCard(
    sms: SmsModel,
    showUnreadIndicators: Boolean,
    selected: Boolean,
    selectionMode: Boolean,
    pinned: Boolean,
    muted: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleMute: () -> Unit
) {
    val hasUnread = showUnreadIndicators && sms.unreadCount > 0 && !muted
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val cardScale by animateFloatAsState(targetValue = if (pressed) 0.985f else 1f, animationSpec = tween(120), label = "card-press-scale")
    val extraElevation by animateDpAsState(targetValue = if (pressed) 8.dp else 0.dp, animationSpec = tween(140), label = "card-press-elevation")
    Card(
        modifier = Modifier
            .graphicsLayer(scaleX = cardScale, scaleY = cardScale)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .border(
                width = if (selected) 2.dp else if (hasUnread) 1.4.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else if (hasUnread) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                shape = RoundedCornerShape(18.dp)
            ),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = if (hasUnread || selected) 5.dp + extraElevation else 2.dp + extraElevation)
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
                    if (pinned) {
                        Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                            Text(
                                text = stringResource(R.string.label_pinned_short),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (muted) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                            Text(
                                text = stringResource(R.string.label_muted_short),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
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

                if (!selectionMode) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = onTogglePin, contentPadding = PaddingValues(0.dp)) {
                            Text(
                                text = if (pinned) stringResource(R.string.action_unpin) else stringResource(R.string.action_pin),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                        TextButton(onClick = onToggleMute, contentPadding = PaddingValues(0.dp)) {
                            Text(
                                text = if (muted) stringResource(R.string.action_unmute) else stringResource(R.string.action_mute),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                        TextButton(onClick = onDeleteClick, contentPadding = PaddingValues(0.dp)) {
                            Text(text = stringResource(R.string.action_delete), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(AppSpacing.small))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = sms.date.toDayTime(),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (hasUnread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (hasUnread) FontWeight.Bold else FontWeight.Normal
                )
                if (selectionMode) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = if (selected) stringResource(R.string.label_selected) else stringResource(R.string.label_select),
                            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                } else if (hasUnread) {
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
                    Text(stringResource(R.string.label_sms), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.label_no_messages),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.label_check_sms_permission),
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


