package com.example.smsfirewall

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.smsfirewall.ui.theme.SMSFirewallTheme

class MainActivity : FragmentActivity() {

    private val smsList = mutableStateListOf<SmsModel>()
    private var isDefaultSmsApp by mutableStateOf(false)

    companion object {
        private const val PERMISSION_REQUEST_READ_SMS = 100
        private const val ROLE_REQUEST_CODE = 101
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            SMSFirewallTheme {
                MainScreen(
                    smsList = smsList,
                    isDefaultSmsApp = isDefaultSmsApp,
                    onSetDefaultClick = { requestDefaultSmsRole() },
                    onSpamClick = {
                        startActivity(Intent(this, SpamBoxActivity::class.java))
                    },
                    onConversationClick = { sms ->
                        val intent = Intent(this, ConversationDetailActivity::class.java)
                        intent.putExtra("thread_id", sms.threadId)
                        intent.putExtra("address", sms.address)
                        startActivity(intent)
                    }
                )
            }
        }

        checkPermissions()
    }

    override fun onResume() {
        super.onResume()
        updateDefaultAppUI()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) {
            loadSms()
        }
    }

    private fun updateDefaultAppUI() {
        isDefaultSmsApp = Telephony.Sms.getDefaultSmsPackage(this) == packageName
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
        Toast.makeText(this, "Islem baslatiliyor...", Toast.LENGTH_SHORT).show()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager.isRoleAvailable(RoleManager.ROLE_SMS)) {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
                startActivityForResult(intent, ROLE_REQUEST_CODE)
            } else {
                Toast.makeText(this, "Cihaz SMS rolunu desteklemiyor", Toast.LENGTH_SHORT).show()
            }
        } else {
            val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
            intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
            startActivity(intent)
        }
    }

    private fun loadSms() {
        smsList.clear()

        val cursor = contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.THREAD_ID, Telephony.Sms.TYPE),
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
                    smsList.add(SmsModel(id, address, body, date, type, threadId))
                    seenThreads.add(threadId)
                }
            }
        }
    }
}
