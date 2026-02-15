package com.example.smsfirewall

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SpamBoxActivity : AppCompatActivity() {

    private lateinit var recyclerSpam: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var btnBack: ImageView

    private lateinit var adapter: SmsAdapter
    private val spamListAsSmsModel = mutableListOf<SmsModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_spam_box)

        // 1. View Bağlamaları (Artık XML'de bu ID'ler var)
        recyclerSpam = findViewById(R.id.recyclerSpam)
        tvEmpty = findViewById(R.id.tvEmpty)
        btnBack = findViewById(R.id.btnBack)

        // 2. Geri Butonu İşlevi
        btnBack.setOnClickListener { finish() }

        // 3. RecyclerView Ayarları
        recyclerSpam.layoutManager = LinearLayoutManager(this)

        // Adapter Kurulumu
        adapter = SmsAdapter(spamListAsSmsModel) { sms ->
            // Spam mesajına tıklandığında içeriği göster
            Toast.makeText(this, "Spam İçeriği: ${sms.body}", Toast.LENGTH_LONG).show()
        }
        recyclerSpam.adapter = adapter

        // 4. Verileri Yükle
        loadSpamMessages()
    }

    private fun loadSpamMessages() {
        CoroutineScope(Dispatchers.IO).launch {
            // Veritabanı örneği al
            val db = AppDatabase.getDatabase(applicationContext)

            // DİKKAT: Dao dosyasındaki isme göre 'getAllSpam()' çağırıyoruz
            val spamMessages = db.spamMessageDao().getAllSpam()

            withContext(Dispatchers.Main) {
                spamListAsSmsModel.clear()

                if (spamMessages.isEmpty()) {
                    // Liste boşsa uyarıyı göster, listeyi gizle
                    tvEmpty.visibility = View.VISIBLE
                    recyclerSpam.visibility = View.GONE
                } else {
                    // Liste doluysa uyarıyı gizle, listeyi göster
                    tvEmpty.visibility = View.GONE
                    recyclerSpam.visibility = View.VISIBLE

                    // Veritabanı verisini (SpamMessage) -> Arayüz Modeline (SmsModel) çevir
                    for (spam in spamMessages) {
                        spamListAsSmsModel.add(
                            SmsModel(
                                id = spam.id.toString(),
                                address = spam.sender,  // Veritabanındaki 'sender'
                                body = spam.body,       // Veritabanındaki 'body'
                                date = spam.date,       // Veritabanındaki 'date'
                                type = 1,               // Gelen kutusu gibi göster
                                threadId = "0"          // Spamlerin gruplaması olmayabilir
                            )
                        )
                    }
                    adapter.notifyDataSetChanged()
                }
            }
        }
    }
}