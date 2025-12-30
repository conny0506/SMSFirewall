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
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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

    private val trashDao by lazy { AppDatabase.getDatabase(this).trashMessageDao() }

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

        binding.btnCloseSelection.setOnClickListener { chatAdapter.clearSelection() }
        binding.btnDeleteSelected.setOnClickListener {
            val selected = chatAdapter.getSelectedItems()
            if (selected.isNotEmpty()) showDeleteDialog(selected)
        }
    }

    private fun setupRecyclerView() {
        chatAdapter = ChatAdapter(emptyList()) { count ->
            if (count > 0) {
                binding.selectionHeader.visibility = View.VISIBLE
                binding.headerTitle.visibility = View.GONE
                binding.txtSelectionCount.text = "$count Seçildi"
            } else {
                binding.selectionHeader.visibility = View.GONE
                binding.headerTitle.visibility = View.VISIBLE
            }
        }
        binding.recyclerChat.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true // Mesajları alttan başlat (WhatsApp gibi)
        }
        binding.recyclerChat.adapter = chatAdapter
    }

    // Geri tuşu seçimi iptal etsin
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (chatAdapter.isSelectionMode) {
            chatAdapter.clearSelection()
        } else {
            super.onBackPressed()
        }
    }

    private fun loadMessagesForNumber(phoneNumber: String) {
        val smsList = ArrayList<SmsModel>()
        val uri = Uri.parse("content://sms")
        // Adres sorgusunu 'address = ?' olarak yapıyoruz.
        val cursor = contentResolver.query(uri,
            arrayOf("_id", "address", "body", "date", "type"),
            "address = ?",
            arrayOf(phoneNumber),
            "date ASC"
        )

        if (cursor != null && cursor.moveToFirst()) {
            val idxId = cursor.getColumnIndex("_id")
            val idxBody = cursor.getColumnIndex("body")
            val idxDate = cursor.getColumnIndex("date")
            val idxType = cursor.getColumnIndex("type")
            do {
                smsList.add(SmsModel(
                    id = cursor.getLong(idxId),
                    sender = phoneNumber,
                    messageBody = cursor.getString(idxBody),
                    date = cursor.getLong(idxDate),
                    type = cursor.getInt(idxType)
                ))
            } while (cursor.moveToNext())
            cursor.close()
        }

        // HATA AYIKLAMA: Eğer liste boşsa kullanıcıya haber ver (Numara formatı sorunu olabilir)
        if (smsList.isEmpty()) {
            // Toast.makeText(this, "Mesaj bulunamadı: $phoneNumber", Toast.LENGTH_SHORT).show()
        }

        chatAdapter.updateList(smsList)
        if (smsList.isNotEmpty() && !chatAdapter.isSelectionMode) {
            binding.recyclerChat.scrollToPosition(smsList.size - 1)
        }
    }

    private fun showDeleteDialog(selectedItems: List<SmsModel>) {
        AlertDialog.Builder(this)
            .setTitle("Sil")
            .setMessage("${selectedItems.size} mesaj silinsin mi?")
            .setPositiveButton("Sil") { _, _ -> deleteSelectedMessages(selectedItems) }
            .setNegativeButton("İptal", null)
            .show()
    }

    private fun deleteSelectedMessages(messages: List<SmsModel>) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val trashMessages = messages.map { TrashMessage(sender = it.sender, body = it.messageBody, date = it.date, type = it.type) }
                trashDao.insertAll(trashMessages)
                for (msg in messages) {
                    contentResolver.delete(Uri.parse("content://sms"), "_id = ?", arrayOf(msg.id.toString()))
                }
                withContext(Dispatchers.Main) {
                    chatAdapter.clearSelection()
                    Toast.makeText(this@ConversationDetailActivity, "Silindi", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) { e.printStackTrace() }
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