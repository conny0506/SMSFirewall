package com.example.smsfirewall

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telephony.SmsManager
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.smsfirewall.databinding.ActivityConversationDetailBinding

class ConversationDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConversationDetailBinding
    private lateinit var chatAdapter: ChatAdapter
    private var senderNumber: String? = null

    // Canlı izleme
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

        // GÖNDER BUTONU
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
        // ChatAdapter oluşturulurken boş liste veriyoruz
        chatAdapter = ChatAdapter(emptyList())
        val layoutManager = LinearLayoutManager(this)
        binding.recyclerChat.layoutManager = layoutManager
        binding.recyclerChat.adapter = chatAdapter
    }

    private fun sendMessage(destination: String, text: String) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "SMS gönderme izni yok!", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val smsManager = SmsManager.getDefault()
            smsManager.sendTextMessage(destination, null, text, null, null)

            Log.d("SMS_FIREWALL", "Mesaj gönderildi: $text")

            // Manuel olarak veritabanına kaydet
            val values = ContentValues().apply {
                put("address", destination)
                put("body", text)
                put("date", System.currentTimeMillis())
                put("type", 2) // Giden mesaj
                put("read", 1)
            }
            contentResolver.insert(Uri.parse("content://sms/sent"), values)

            binding.etMessage.setText("")

        } catch (e: Exception) {
            Log.e("SMS_FIREWALL", "Hata: ${e.message}")
            Toast.makeText(this, "Gönderim Hatası!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadMessagesForNumber(phoneNumber: String) {
        val smsList = ArrayList<SmsModel>()
        val uri = Uri.parse("content://sms")

        // SORGUNUN GÜNCELLENMİŞ HALİ: "_id" EKLENDİ
        val cursor = contentResolver.query(
            uri,
            arrayOf("_id", "address", "body", "date", "type"),
            "address = ?",
            arrayOf(phoneNumber),
            "date ASC"
        )

        if (cursor != null && cursor.moveToFirst()) {
            val idxId = cursor.getColumnIndex("_id") // ID indexini al
            val idxBody = cursor.getColumnIndex("body")
            val idxDate = cursor.getColumnIndex("date")
            val idxType = cursor.getColumnIndex("type")

            do {
                // ID değerini oku
                val id = cursor.getLong(idxId)
                val body = cursor.getString(idxBody)
                val date = cursor.getLong(idxDate)
                val type = cursor.getInt(idxType)

                // MODELİ YENİ YAPIYA GÖRE OLUŞTUR (İlk parametre ID)
                smsList.add(SmsModel(id, phoneNumber, body, date, type))

            } while (cursor.moveToNext())
            cursor.close()
        }

        chatAdapter.updateList(smsList)

        if (smsList.isNotEmpty()) {
            binding.recyclerChat.scrollToPosition(smsList.size - 1)
        }
    }
}