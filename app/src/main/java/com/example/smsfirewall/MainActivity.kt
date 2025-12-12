package com.example.smsfirewall

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.smsfirewall.databinding.ActivityMainBinding
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding // Tasarıma erişim aracı

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ViewBinding kurulumu
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupButtons()
    }

    private fun setupRecyclerView() {
        // Test için sahte veri listesi oluşturalım
        val dummySmsList = listOf(
            SmsModel("+905551112233", "Merhaba, nasılsın?", System.currentTimeMillis()),
            SmsModel("REKLAM", "BEDAVA BAHIS FIRSATI! HEMEN TIKLA!", System.currentTimeMillis()),
            SmsModel("Kargo", "Kargonuz yola çıkmıştır.", System.currentTimeMillis())
        )

        // Adapter'ı oluştur ve listeyi ver
        val adapter = SmsAdapter(dummySmsList)

        // RecyclerView ayarları
        binding.recyclerSms.layoutManager = LinearLayoutManager(this) // Alt alta liste
        binding.recyclerSms.adapter = adapter
    }

    private fun setupButtons() {
        // Varsayılan Yap butonu
        binding.btnSetDefault.setOnClickListener {
            Toast.makeText(this, "Henüz kodlanmadı!", Toast.LENGTH_SHORT).show()
        }

        // Yasaklı Kelime Ekle butonu
        binding.fabAddBlockWord.setOnClickListener {
            Toast.makeText(this, "Kelime ekleme ekranı açılacak", Toast.LENGTH_SHORT).show()
        }
    }
}