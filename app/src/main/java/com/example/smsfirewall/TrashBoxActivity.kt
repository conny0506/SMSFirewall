package com.example.smsfirewall

import android.app.AlertDialog
import android.content.ContentValues
import android.os.Bundle
import android.provider.Telephony
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.smsfirewall.databinding.ActivityTrashBoxBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TrashBoxActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTrashBoxBinding
    private lateinit var adapter: SmsAdapter
    private val trashDao by lazy { AppDatabase.getDatabase(this).trashMessageDao() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTrashBoxBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        loadTrashMessages()

        binding.btnEmptyTrash.setOnClickListener {
            showEmptyTrashDialog()
        }
    }

    private fun setupRecyclerView() {
        // HATA DÜZELTİLDİ: onLongClick yerine onClick içinde seçenek sunuyoruz
        adapter = SmsAdapter(emptyList(),
            onClick = { sender ->
                showActionDialog(sender)
            }
        )
        binding.recyclerTrash.layoutManager = LinearLayoutManager(this)
        binding.recyclerTrash.adapter = adapter
    }

    private fun loadTrashMessages() {
        lifecycleScope.launch {
            val trashList = trashDao.getAllTrash()
            val distinctTrash = HashMap<String, SmsModel>()
            for (msg in trashList) {
                if (!distinctTrash.containsKey(msg.sender)) {
                    distinctTrash[msg.sender] = SmsModel(msg.id, msg.sender, msg.body, msg.date, msg.type)
                }
            }
            adapter.updateList(distinctTrash.values.toList())
        }
    }

    // Tıklayınca Seçenek Sun: Geri Yükle veya Kalıcı Sil
    private fun showActionDialog(sender: String) {
        val options = arrayOf("Geri Yükle", "Kalıcı Olarak Sil")
        AlertDialog.Builder(this)
            .setTitle(sender)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showRestoreDialog(sender) // Geri Yükle
                    1 -> showDeleteForeverDialog(sender) // Kalıcı Sil
                }
            }
            .show()
    }

    private fun showEmptyTrashDialog() {
        AlertDialog.Builder(this)
            .setTitle("Çöpü Boşalt")
            .setMessage("Tüm mesajlar kalıcı olarak silinecek.")
            .setPositiveButton("Sil") { _, _ ->
                lifecycleScope.launch {
                    trashDao.deleteAll()
                    loadTrashMessages()
                    Toast.makeText(this@TrashBoxActivity, "Temizlendi", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("İptal", null)
            .show()
    }

    private fun showRestoreDialog(sender: String) {
        lifecycleScope.launch {
            restoreThread(sender)
            loadTrashMessages()
            Toast.makeText(this@TrashBoxActivity, "Geri Yüklendi", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showDeleteForeverDialog(sender: String) {
        lifecycleScope.launch {
            trashDao.deleteBySender(sender)
            loadTrashMessages()
            Toast.makeText(this@TrashBoxActivity, "Silindi", Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun restoreThread(sender: String) {
        val allTrash = trashDao.getAllTrash().filter { it.sender == sender }
        withContext(Dispatchers.IO) {
            for (msg in allTrash) {
                try {
                    val values = ContentValues().apply {
                        put(Telephony.Sms.ADDRESS, msg.sender)
                        put(Telephony.Sms.BODY, msg.body)
                        put(Telephony.Sms.DATE, msg.date)
                        put(Telephony.Sms.READ, 1)
                        put(Telephony.Sms.TYPE, msg.type)
                    }
                    val uri = if (msg.type == 2) Telephony.Sms.Sent.CONTENT_URI else Telephony.Sms.Inbox.CONTENT_URI
                    contentResolver.insert(uri, values)
                } catch (e: Exception) { e.printStackTrace() }
            }
            trashDao.deleteBySender(sender)
        }
    }
}