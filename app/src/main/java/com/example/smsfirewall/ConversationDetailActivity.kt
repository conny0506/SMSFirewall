package com.example.smsfirewall

import android.Manifest
import android.content.ContentValues // <-- Bu import veritabanı yazma işlemi için gerekli
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import android.telephony.SmsManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class ConversationDetailActivity : AppCompatActivity() {

    private lateinit var recyclerChat: RecyclerView
    private lateinit var chatAdapter: ChatAdapter
    private val messageList = mutableListOf<SmsModel>()

    private lateinit var etMessage: EditText
    private lateinit var btnSend: ImageButton
    private lateinit var headerTitle: TextView
    private lateinit var btnBack: ImageView

    private var threadId: String? = null
    private var address: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_conversation_detail)

        threadId = intent.getStringExtra("thread_id")
        address = intent.getStringExtra("address")

        // UI Elemanları
        recyclerChat = findViewById(R.id.recyclerChat)
        etMessage = findViewById(R.id.etMessage)
        btnSend = findViewById(R.id.btnSend)
        headerTitle = findViewById(R.id.headerTitle)
        btnBack = findViewById(R.id.btnBack)

        headerTitle.text = address ?: "Bilinmeyen"

        btnBack.setOnClickListener { finish() }

        // RecyclerView Ayarları
        val layoutManager = LinearLayoutManager(this)
        layoutManager.stackFromEnd = true
        recyclerChat.layoutManager = layoutManager

        chatAdapter = ChatAdapter(messageList)
        recyclerChat.adapter = chatAdapter

        btnSend.setOnClickListener {
            sendMessage()
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) {
            loadMessages()
        }
    }

    private fun loadMessages() {
        if (threadId == null) return

        messageList.clear()

        val selection = "${Telephony.Sms.THREAD_ID} = ?"
        val selectionArgs = arrayOf(threadId)

        val cursor = contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE),
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
                val id = it.getString(indexId)
                val msgAddress = it.getString(indexAddress) ?: ""
                val body = it.getString(indexBody)
                val date = it.getLong(indexDate)
                val type = it.getInt(indexType)

                val safeThreadId = threadId ?: "0"

                messageList.add(SmsModel(id, msgAddress, body, date, type, safeThreadId))
            }
        }
        chatAdapter.notifyDataSetChanged()

        if (messageList.isNotEmpty()) {
            recyclerChat.scrollToPosition(messageList.size - 1)
        }
    }

    private fun sendMessage() {
        val messageBody = etMessage.text.toString().trim()

        if (messageBody.isNotEmpty() && address != null) {
            try {
                // 1. SMS Gönder (Operatöre ilet)
                val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    this.getSystemService(SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }

                smsManager.sendTextMessage(address, null, messageBody, null, null)

                // 2. VERİTABANINA KAYDET (EKSİK OLAN KISIM BURASIYDI)
                // Varsayılan uygulama olduğumuz için bunu elle yapmalıyız.
                val values = ContentValues().apply {
                    put(Telephony.Sms.ADDRESS, address)
                    put(Telephony.Sms.BODY, messageBody)
                    put(Telephony.Sms.DATE, System.currentTimeMillis())
                    put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT) // Giden Mesaj Tipi
                    put(Telephony.Sms.READ, 1) // Okundu olarak işaretle
                }
                contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)

                // 3. EKRANA EKLE (Anlık Gösterim)
                val sentSms = SmsModel(
                    id = System.currentTimeMillis().toString(),
                    address = address!!,
                    body = messageBody,
                    date = System.currentTimeMillis(),
                    type = Telephony.Sms.MESSAGE_TYPE_SENT,
                    threadId = threadId ?: "0"
                )

                messageList.add(sentSms)
                chatAdapter.notifyItemInserted(messageList.size - 1)
                recyclerChat.scrollToPosition(messageList.size - 1)

                etMessage.text.clear()

            } catch (e: Exception) {
                Toast.makeText(this, "Hata: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}