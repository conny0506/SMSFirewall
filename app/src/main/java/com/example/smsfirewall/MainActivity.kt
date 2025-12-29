package com.example.smsfirewall

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.smsfirewall.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var smsAdapter: SmsAdapter

    private val smsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            if (hasSmsReadPermission()) loadInboxMessages()
        }
    }

    private val roleRequestLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            checkDefaultSmsApp()
            loadInboxMessages()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()

        // Buton dinleyicisi (Sadece varsayılan yap butonu kaldı)
        binding.btnSetDefault.setOnClickListener { askDefaultSmsHandlerPermission() }

        askNotificationPermission()
    }

    override fun onResume() {
        super.onResume()
        checkDefaultSmsApp()
        if (hasSmsReadPermission()) {
            loadInboxMessages()
            contentResolver.registerContentObserver(Uri.parse("content://sms"), true, smsObserver)
        }
    }

    override fun onPause() {
        super.onPause()
        contentResolver.unregisterContentObserver(smsObserver)
    }

    private fun setupRecyclerView() {
        smsAdapter = SmsAdapter(emptyList()) { clickedNumber ->
            val intent = Intent(this, ConversationDetailActivity::class.java)
            intent.putExtra("SENDER_NUMBER", clickedNumber)
            startActivity(intent)
        }
        binding.recyclerSms.layoutManager = LinearLayoutManager(this)
        binding.recyclerSms.adapter = smsAdapter
    }

    private fun loadInboxMessages() {
        val distinctMessages = HashMap<String, SmsModel>()
        val cursor = contentResolver.query(
            Uri.parse("content://sms/inbox"),
            arrayOf("address", "body", "date", "type"),
            null, null, "date DESC"
        )

        if (cursor != null && cursor.moveToFirst()) {
            val idxAddr = cursor.getColumnIndex("address")
            val idxBody = cursor.getColumnIndex("body")
            val idxDate = cursor.getColumnIndex("date")
            val idxType = cursor.getColumnIndex("type") // Type okuyoruz

            do {
                if (idxAddr >= 0) {
                    val address = cursor.getString(idxAddr)
                    val body = cursor.getString(idxBody)
                    val date = cursor.getLong(idxDate)
                    val type = cursor.getInt(idxType)

                    if (!distinctMessages.containsKey(address)) {
                        distinctMessages[address] = SmsModel(address, body, date, type)
                    }
                }
            } while (cursor.moveToNext())
            cursor.close()
        }
        smsAdapter.updateList(distinctMessages.values.toList())
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun askDefaultSmsHandlerPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(Context.ROLE_SERVICE) as RoleManager
            if (roleManager.isRoleAvailable(RoleManager.ROLE_SMS)) {
                roleRequestLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS))
            }
        } else {
            val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
            intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
            startActivity(intent)
        }
    }

    private fun checkDefaultSmsApp() {
        val defaultSmsPackage = Telephony.Sms.getDefaultSmsPackage(this)
        if (defaultSmsPackage == packageName) {
            binding.btnSetDefault.isEnabled = false
            binding.btnSetDefault.text = "Zaten Varsayılan Uygulama"
        } else {
            binding.btnSetDefault.isEnabled = true
            binding.btnSetDefault.text = "Varsayılan SMS Uygulaması Yap"
        }
    }

    private fun hasSmsReadPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED ||
                Telephony.Sms.getDefaultSmsPackage(this) == packageName
    }
}