package com.example.smsfirewall

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.smsfirewall.databinding.ActivitySpamBoxBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class SpamBoxActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySpamBoxBinding
    private lateinit var adapter: SmsAdapter
    private val spamDao by lazy { AppDatabase.getDatabase(this).spamMessageDao() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySpamBoxBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        loadSpamMessages()
    }

    private fun setupRecyclerView() {
        // HATA DÜZELTİLDİ: onLongClick kaldırıldı.
        adapter = SmsAdapter(emptyList(),
            onClick = { sender ->
                Toast.makeText(this, "$sender'dan gelen spam", Toast.LENGTH_SHORT).show()
            }
            // onSelectionChanged parametresi opsiyonel olduğu için vermemize gerek yok
        )

        binding.recyclerSpam.layoutManager = LinearLayoutManager(this)
        binding.recyclerSpam.adapter = adapter

        // --- KAYDIRARAK SİLME (SWIPE TO DELETE) ---
        val itemTouchHelperCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val spamToDelete = adapter.getItem(position)
                deleteSpam(spamToDelete)
                Snackbar.make(binding.root, "Spam silindi", Snackbar.LENGTH_LONG).show()
            }
        }
        ItemTouchHelper(itemTouchHelperCallback).attachToRecyclerView(binding.recyclerSpam)
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

    private fun deleteSpam(sms: SmsModel) {
        lifecycleScope.launch {
            // Veritabanından ID'ye göre sil
            val spamMsg = SpamMessage(id = sms.id.toInt(), sender = sms.sender, body = sms.messageBody, date = sms.date)
            spamDao.delete(spamMsg)
            loadSpamMessages() // Listeyi yenile
        }
    }
}