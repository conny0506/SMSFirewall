package com.example.smsfirewall

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.smsfirewall.ui.theme.SMSFirewallTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : FragmentActivity() {

    private val smsList = mutableStateListOf<SmsModel>()
    private var isDefaultSmsApp by mutableStateOf(false)
    private var showUnreadIndicators by mutableStateOf(true)
    private var smsContentObserver: ContentObserver? = null

    private val requestDefaultSmsRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            isDefaultSmsApp = true
        }
        updateDefaultAppUI()
    }

    companion object {
        private const val PERMISSION_REQUEST_READ_SMS = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        updateDefaultAppUI()
        showUnreadIndicators = AppSettings.isUnreadBadgesEnabled(this)

        setContent {
            SMSFirewallTheme {
                MainScreen(
                    smsList = smsList,
                    isDefaultSmsApp = isDefaultSmsApp,
                    showUnreadIndicators = showUnreadIndicators,
                    onSetDefaultClick = { requestDefaultSmsRole() },
                    onSpamClick = {
                        startActivity(Intent(this, SpamBoxActivity::class.java))
                    },
                    onTrashClick = {
                        startActivity(Intent(this, TrashBoxActivity::class.java))
                    },
                    onSettingsClick = {
                        startActivity(Intent(this, SettingsActivity::class.java))
                    },
                    onConversationClick = { sms ->
                        val intent = Intent(this, ConversationDetailActivity::class.java)
                        intent.putExtra("thread_id", sms.threadId)
                        intent.putExtra("address", sms.address)
                        startActivity(intent)
                    },
                    onDeleteConversationClick = { sms ->
                        deleteConversation(sms)
                    }
                )
            }
        }

        checkPermissions()
    }

    override fun onStart() {
        super.onStart()
        registerSmsObserver()
    }

    override fun onStop() {
        super.onStop()
        unregisterSmsObserver()
    }

    override fun onResume() {
        super.onResume()
        updateDefaultAppUI()
        showUnreadIndicators = AppSettings.isUnreadBadgesEnabled(this)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) {
            loadSms()
        }
    }

    private fun registerSmsObserver() {
        if (smsContentObserver != null) return

        smsContentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) {
                    loadSms()
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

    private fun updateDefaultAppUI() {
        isDefaultSmsApp = SmsRoleUtils.isAppDefaultSmsHandler(this)
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_SMS), PERMISSION_REQUEST_READ_SMS)
        } else {
            loadSms()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_READ_SMS) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadSms()
            } else {
                Toast.makeText(this, "SMS okuma izni gerekli!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun requestDefaultSmsRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager.isRoleAvailable(RoleManager.ROLE_SMS)) {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
                requestDefaultSmsRoleLauncher.launch(intent)
            } else {
                Toast.makeText(this, "Cihaz SMS rolunu desteklemiyor", Toast.LENGTH_SHORT).show()
            }
        } else {
            val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
            intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
            startActivity(intent)
        }
    }

    private fun deleteConversation(sms: SmsModel) {
        if (!isDefaultSmsApp) {
            Toast.makeText(this, "Mesaj silmek icin varsayilan SMS uygulamasi olmalisin.", Toast.LENGTH_SHORT).show()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            val trashItems = queryThreadMessagesForTrash(sms.threadId)
            val deletedRows = contentResolver.delete(
                Telephony.Sms.CONTENT_URI,
                "${Telephony.Sms.THREAD_ID} = ?",
                arrayOf(sms.threadId)
            )
            if (deletedRows > 0 && trashItems.isNotEmpty()) {
                AppDatabase.getDatabase(applicationContext).trashMessageDao().insertAll(trashItems)
            }

            withContext(Dispatchers.Main) {
                if (deletedRows > 0) {
                    smsList.removeAll { it.threadId == sms.threadId }
                    Toast.makeText(this@MainActivity, "Sohbet silindi", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, "Silinecek mesaj bulunamadi", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun queryThreadMessagesForTrash(threadId: String): List<TrashMessage> {
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
            arrayOf(threadId),
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
                        threadId = it.getString(threadIdx) ?: threadId,
                        deletedAt = System.currentTimeMillis()
                    )
                )
            }
        }

        return archived
    }

    private fun loadSms() {
        CoroutineScope(Dispatchers.IO).launch {
            val loaded = mutableListOf<SmsModel>()
            val unreadCountMap = loadUnreadCountsByThread()
            val cursor = contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(
                    Telephony.Sms._ID,
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY,
                    Telephony.Sms.DATE,
                    Telephony.Sms.THREAD_ID,
                    Telephony.Sms.TYPE
                ),
                null,
                null,
                Telephony.Sms.DATE + " DESC"
            )

            cursor?.use {
                val indexId = it.getColumnIndex(Telephony.Sms._ID)
                val indexAddress = it.getColumnIndex(Telephony.Sms.ADDRESS)
                val indexBody = it.getColumnIndex(Telephony.Sms.BODY)
                val indexDate = it.getColumnIndex(Telephony.Sms.DATE)
                val indexThreadId = it.getColumnIndex(Telephony.Sms.THREAD_ID)
                val indexType = it.getColumnIndex(Telephony.Sms.TYPE)

                val seenThreads = HashSet<String>()

                while (it.moveToNext()) {
                    val id = it.getString(indexId)
                    val address = it.getString(indexAddress) ?: "Bilinmeyen"
                    val body = it.getString(indexBody) ?: ""
                    val date = it.getLong(indexDate)
                    val threadId = it.getString(indexThreadId)
                    val type = it.getInt(indexType)

                    if (threadId != null && !seenThreads.contains(threadId)) {
                        loaded.add(
                            SmsModel(
                                id = id,
                                address = address,
                                body = body,
                                date = date,
                                type = type,
                                threadId = threadId,
                                unreadCount = unreadCountMap[threadId] ?: 0
                            )
                        )
                        seenThreads.add(threadId)
                    }
                }
            }

            withContext(Dispatchers.Main) {
                smsList.clear()
                smsList.addAll(loaded)
            }
        }
    }

    private fun loadUnreadCountsByThread(): Map<String, Int> {
        val unreadMap = HashMap<String, Int>()
        val cursor = contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms.THREAD_ID),
            "${Telephony.Sms.READ} = ? AND ${Telephony.Sms.TYPE} = ?",
            arrayOf("0", Telephony.Sms.MESSAGE_TYPE_INBOX.toString()),
            null
        )

        cursor?.use {
            val threadIdIndex = it.getColumnIndex(Telephony.Sms.THREAD_ID)
            while (it.moveToNext()) {
                val threadId = it.getString(threadIdIndex) ?: continue
                unreadMap[threadId] = (unreadMap[threadId] ?: 0) + 1
            }
        }

        return unreadMap
    }
}
