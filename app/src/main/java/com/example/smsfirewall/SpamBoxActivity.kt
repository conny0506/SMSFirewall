package com.example.smsfirewall

import android.app.AlertDialog
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.smsfirewall.databinding.ActivitySpamBoxBinding
import kotlinx.coroutines.launch

class SpamBoxActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySpamBoxBinding
    private lateinit var adapter: SmsAdapter
    // Eğer spamMessageDao hatası alırsan Adım 4'ü tamamlayınca düzelir.
    private val spamDao by lazy { (application as SmsApp).database.spamMessageDao() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySpamBoxBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = SmsAdapter(emptyList(),
            onClick = { Toast.makeText(this, "Bu bir spam mesajdır", Toast.LENGTH_SHORT).show() },
            onLongClick = { sms -> showDeleteDialog(sms) }
        )

        binding.recyclerSpam.layoutManager = LinearLayoutManager(this)
        binding.recyclerSpam.adapter = adapter

        loadSpamMessages()
    }

    private fun loadSpamMessages() {
        lifecycleScope.launch {
            val spamList = spamDao.getAllSpam()
            val uiList = spamList.map {
                SmsModel(it.id.toLong(), it.sender, it.body, it.date, 1)
            }
            adapter.updateList(uiList)
        }
    }

    private fun showDeleteDialog(sms: SmsModel) {
        AlertDialog.Builder(this)
            .setTitle("Silinsin mi?")
            .setMessage("Bu spam mesajı kalıcı olarak silinecek.")
            .setPositiveButton("Evet") { _, _ ->
                lifecycleScope.launch {
                    val spamMsg = SpamMessage(id = sms.id.toInt(), sender = sms.sender, body = sms.messageBody, date = sms.date)
                    spamDao.delete(spamMsg)
                    loadSpamMessages()
                }
            }
            .setNegativeButton("Hayır", null)
            .show()
    }
}