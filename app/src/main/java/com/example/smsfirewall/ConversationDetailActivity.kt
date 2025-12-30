package com.example.smsfirewall

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telephony.PhoneNumberUtils
import android.telephony.SmsManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.smsfirewall.databinding.ActivityConversationDetailBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ConversationDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConversationDetailBinding
    private lateinit var chatAdapter: ChatAdapter
    private var senderNumber: String? = null

    private val smsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            if (senderNumber != null) loadMessagesForNumber(senderNumber!!)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConversationDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        senderNumber = intent.getStringExtra("SENDER_NUMBER")
        binding.headerTitle.text = senderNumber ?: "Sohbet"

        setupRecyclerView()

        binding.btnSend.setOnClickListener {
            val messageText = binding.etMessage.text.toString().trim()
            if (messageText.isNotEmpty() && senderNumber != null) {
                sendMessage(senderNumber!!, messageText)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (senderNumber != null) {
            loadMessagesForNumber(senderNumber!!)
            contentResolver.registerContentObserver(Uri.parse("content://sms"), true, smsObserver)
        }
    }

    override fun onPause() {
        super.onPause()
        contentResolver.unregisterContentObserver(smsObserver)
    }

    private fun setupRecyclerView() {
        chatAdapter = ChatAdapter(emptyList())
        binding.recyclerChat.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        binding.recyclerChat.adapter = chatAdapter
    }

    private fun loadMessagesForNumber(targetNumber: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val smsList = ArrayList<SmsModel>()
            val uri = Uri.parse("content://sms")

            val cursor = contentResolver.query(
                uri,
                arrayOf("_id", "address", "body", "date", "type"),
                null,
                null,
                "date ASC"
            )

            if (cursor != null && cursor.moveToFirst()) {
                val idxId = cursor.getColumnIndex("_id")
                val idxAddr = cursor.getColumnIndex("address")
                val idxBody = cursor.getColumnIndex("body")
                val idxDate = cursor.getColumnIndex("date")
                val idxType = cursor.getColumnIndex("type")

                do {
                    val addressFromDb = cursor.getString(idxAddr)
                    if (addressFromDb != null && PhoneNumberUtils.compare(this@ConversationDetailActivity, targetNumber, addressFromDb)) {
                        smsList.add(SmsModel(
                            id = cursor.getLong(idxId),
                            sender = addressFromDb,
                            messageBody = cursor.getString(idxBody),
                            date = cursor.getLong(idxDate),
                            type = cursor.getInt(idxType)
                        ))
                    }
                } while (cursor.moveToNext())
                cursor.close()
            }

            withContext(Dispatchers.Main) {
                chatAdapter.updateList(smsList)
                if (smsList.isNotEmpty()) {
                    binding.recyclerChat.scrollToPosition(smsList.size - 1)
                }
            }
        }
    }

    private fun sendMessage(destination: String, text: String) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) return
        try {
            SmsManager.getDefault().sendTextMessage(destination, null, text, null, null)
            val values = ContentValues().apply {
                put("address", destination)
                put("body", text)
                put("date", System.currentTimeMillis())
                put("type", 2)
                put("read", 1)
            }
            contentResolver.insert(Uri.parse("content://sms/sent"), values)
            binding.etMessage.setText("")
        } catch (e: Exception) { }
    }
}