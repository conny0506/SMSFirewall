package com.example.smsfirewall

import android.Manifest
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telephony.SmsManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.smsfirewall.databinding.ActivityConversationDetailBinding

class ConversationDetailActivity : AppCompatActivity() {

    // Artık yeni layout binding dosyasını kullanıyoruz
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
        // Binding sınıfı değişti dikkat!
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
        chatAdapter = ChatAdapter(emptyList())

        // GÜNCELLEME: "stackFromEnd = true" kaldırıldı.
        // Artık mesajlar varsayılan olarak yukarıdan aşağıya dizilecek.
        val layoutManager = LinearLayoutManager(this)
        // layoutManager.stackFromEnd = false // Varsayılanı zaten false'tur.

        binding.recyclerChat.layoutManager = layoutManager
        binding.recyclerChat.adapter = chatAdapter
    }

    private fun sendMessage(destination: String, text: String) {
        // İzin kontrolü
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "SMS gönderme izni yok!", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val smsManager = SmsManager.getDefault()
            smsManager.sendTextMessage(destination, null, text, null, null)

            // Mesaj kutusunu temizle
            binding.etMessage.setText("")
            Toast.makeText(this, "Gönderildi", Toast.LENGTH_SHORT).show()

            // Not: Gönderilen mesaj otomatik olarak content://sms/sent içine Android tarafından
            // eklenecektir (Varsayılan app olduğumuz için). Observer bunu yakalayıp listeyi güncelleyecektir.

        } catch (e: Exception) {
            Toast.makeText(this, "Hata: ${e.message}", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    private fun loadMessagesForNumber(phoneNumber: String) {
        val smsList = ArrayList<SmsModel>()
        val uri = Uri.parse("content://sms")

        val cursor = contentResolver.query(
            uri,
            arrayOf("address", "body", "date", "type"),
            "address = ?",
            arrayOf(phoneNumber),
            "date ASC"
        )

        if (cursor != null && cursor.moveToFirst()) {
            val idxBody = cursor.getColumnIndex("body")
            val idxDate = cursor.getColumnIndex("date")
            val idxType = cursor.getColumnIndex("type")

            do {
                val body = cursor.getString(idxBody)
                val date = cursor.getLong(idxDate)
                val type = cursor.getInt(idxType)
                smsList.add(SmsModel(phoneNumber, body, date, type))
            } while (cursor.moveToNext())
            cursor.close()
        }

        chatAdapter.updateList(smsList)

        // Yeni mesaj gelince veya gönderince yine de en alta kaydıralım mı?
        // Kullanıcı "üstten başlasın" dedi ama sohbet akışı için en son mesajı görmek isteyebilir.
        // Eğer istemezsen aşağıdaki if bloğunu silebilirsin.
        if (smsList.isNotEmpty()) {
            binding.recyclerChat.scrollToPosition(smsList.size - 1)
        }
    }
}