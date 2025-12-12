package com.example.smsfirewall

import android.app.Activity
import android.app.AlertDialog
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.smsfirewall.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding // Tasarıma erişim aracı

    // 1. Veritabanı bağlantımızı alıyoruz (SmsApp üzerinden)
    private val blockedWordDao by lazy { (application as SmsApp).database.blockedWordDao() }

    // 2. İzin isteği sonucunu dinleyen özel başlatıcı
    private val roleRequestLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Toast.makeText(this, "Başarılı! Artık varsayılan SMS uygulamasıyız.", Toast.LENGTH_SHORT).show()
            checkDefaultSmsApp() // Butonu güncellemek için kontrol et
        } else {
            Toast.makeText(this, "İşlem iptal edildi veya başarısız oldu.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ViewBinding kurulumu
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupButtons()
    }

    // Uygulama arka plandan öne her geldiğinde durumu kontrol et
    override fun onResume() {
        super.onResume()
        checkDefaultSmsApp()
    }

    private fun setupRecyclerView() {
        // Test için sahte veri listesi
        val dummySmsList = listOf(
            SmsModel("+905551112233", "Merhaba, nasılsın?", System.currentTimeMillis()),
            SmsModel("REKLAM", "BEDAVA BAHIS FIRSATI! HEMEN TIKLA!", System.currentTimeMillis()),
            SmsModel("Kargo", "Kargonuz yola çıkmıştır.", System.currentTimeMillis())
        )

        val adapter = SmsAdapter(dummySmsList)
        binding.recyclerSms.layoutManager = LinearLayoutManager(this)
        binding.recyclerSms.adapter = adapter
    }

    private fun setupButtons() {
        // Varsayılan Yap butonu tıklandığında izin iste
        binding.btnSetDefault.setOnClickListener {
            askDefaultSmsHandlerPermission()
        }

        // Yasaklı Kelime Ekle butonu (ARTIK ÇALIŞIYOR)
        binding.fabAddBlockWord.setOnClickListener {
            showAddWordDialog()
        }
    }

    // YENİ: Kelime ekleme penceresi açan fonksiyon
    private fun showAddWordDialog() {
        val input = EditText(this)
        input.hint = "Örn: bahis, casino, kampanya"

        AlertDialog.Builder(this)
            .setTitle("Yasaklı Kelime Ekle")
            .setView(input)
            .setPositiveButton("Ekle") { _, _ ->
                val word = input.text.toString().trim()
                if (word.isNotEmpty()) {
                    saveBlockedWord(word)
                } else {
                    Toast.makeText(this, "Boş kelime eklenemez!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("İptal", null)
            .show()
    }

    // YENİ: Veritabanına kelime kaydeden fonksiyon
    private fun saveBlockedWord(word: String) {
        // Veritabanı işlemi ana thread'i dondurmasın diye coroutine kullanıyoruz
        lifecycleScope.launch {
            blockedWordDao.insert(BlockedWord(word = word))
            Toast.makeText(this@MainActivity, "'$word' engellenenlere eklendi!", Toast.LENGTH_SHORT).show()
        }
    }

    // Varsayılan uygulama olma iznini isteyen fonksiyon
    private fun askDefaultSmsHandlerPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10 ve üzeri için RoleManager kullanılır
            val roleManager = getSystemService(Context.ROLE_SERVICE) as RoleManager
            if (roleManager.isRoleAvailable(RoleManager.ROLE_SMS)) {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
                roleRequestLauncher.launch(intent)
            } else {
                Toast.makeText(this, "SMS Rolü bu cihazda desteklenmiyor.", Toast.LENGTH_SHORT).show()
            }
        } else {
            // Android 9 ve altı için eski yöntem
            val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
            intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
            startActivity(intent)
        }
    }

    // Şu an varsayılan mıyız diye kontrol eden fonksiyon
    private fun checkDefaultSmsApp() {
        val defaultSmsPackage = Telephony.Sms.getDefaultSmsPackage(this)

        // Eğer varsayılan paket biz isek butonu pasif yap ve metni değiştir
        if (defaultSmsPackage == packageName) {
            binding.btnSetDefault.isEnabled = false
            binding.btnSetDefault.text = "Zaten Varsayılan Uygulama"
        } else {
            binding.btnSetDefault.isEnabled = true
            binding.btnSetDefault.text = "Varsayılan SMS Uygulaması Yap"
        }
    }
}