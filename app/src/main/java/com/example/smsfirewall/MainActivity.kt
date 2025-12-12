package com.example.smsfirewall

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.smsfirewall.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val blockedWordDao by lazy { (application as SmsApp).database.blockedWordDao() }

    // 1. Varsayılan SMS olma sonucu
    private val roleRequestLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Toast.makeText(this, "Varsayılan uygulama olduk!", Toast.LENGTH_SHORT).show()
            checkDefaultSmsApp()
        } else {
            Toast.makeText(this, "Varsayılan olma reddedildi.", Toast.LENGTH_SHORT).show()
        }
    }

    // 2. Bildirim izni sonucu (Android 13+)
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(this, "Bildirim izni verildi ✅", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Bildirim izni reddedildi ❌", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupButtons()

        // Uygulama açılır açılmaz izinleri kontrol et
        askNotificationPermission()
    }

    override fun onResume() {
        super.onResume()
        checkDefaultSmsApp()
    }

    private fun setupRecyclerView() {
        val dummySmsList = listOf(
            SmsModel("+90555...", "Bu listede gerçek mesajları görmek için", System.currentTimeMillis()),
            SmsModel("Bilgi", "Bir sonraki adımda Inbox'ı okuyacağız.", System.currentTimeMillis())
        )
        val adapter = SmsAdapter(dummySmsList)
        binding.recyclerSms.layoutManager = LinearLayoutManager(this)
        binding.recyclerSms.adapter = adapter
    }

    private fun setupButtons() {
        binding.btnSetDefault.setOnClickListener {
            askDefaultSmsHandlerPermission()
        }

        binding.fabAddBlockWord.setOnClickListener {
            showAddWordDialog()
        }
    }

    // Bildirim İzni İsteme Fonksiyonu
    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13+
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun showAddWordDialog() {
        val input = EditText(this)
        input.hint = "Örn: bahis, casino"
        AlertDialog.Builder(this)
            .setTitle("Yasaklı Kelime Ekle")
            .setView(input)
            .setPositiveButton("Ekle") { _, _ ->
                val word = input.text.toString().trim()
                if (word.isNotEmpty()) {
                    saveBlockedWord(word)
                }
            }
            .setNegativeButton("İptal", null)
            .show()
    }

    private fun saveBlockedWord(word: String) {
        lifecycleScope.launch {
            blockedWordDao.insert(BlockedWord(word = word))
            Toast.makeText(this@MainActivity, "'$word' eklendi!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun askDefaultSmsHandlerPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(Context.ROLE_SERVICE) as RoleManager
            if (roleManager.isRoleAvailable(RoleManager.ROLE_SMS)) {
                roleRequestLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS))
            }
        } else {
            val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
            intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
            startActivity(intent)
        }
    }

    private fun checkDefaultSmsApp() {
        val defaultSmsPackage = Telephony.Sms.getDefaultSmsPackage(this)
        if (defaultSmsPackage == packageName) {
            binding.btnSetDefault.isEnabled = false
            binding.btnSetDefault.text = "Zaten Varsayılan Uygulama"
        } else {
            binding.btnSetDefault.isEnabled = true
            binding.btnSetDefault.text = "Varsayılan SMS Uygulaması Yap"
        }
    }
}