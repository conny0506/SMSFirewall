package com.example.smsfirewall

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerSms: RecyclerView
    private lateinit var smsAdapter: SmsAdapter
    private val smsList = mutableListOf<SmsModel>()

    private lateinit var cardDefault: MaterialCardView
    private lateinit var btnSpamBox: MaterialCardView

    companion object {
        private const val PERMISSION_REQUEST_READ_SMS = 100
        private const val ROLE_REQUEST_CODE = 101
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. UI Elemanlarını Bağla
        recyclerSms = findViewById(R.id.recyclerSms)
        cardDefault = findViewById(R.id.cardDefault)
        btnSpamBox = findViewById(R.id.btnSpamBox)

        // 2. RecyclerView Ayarları
        recyclerSms.layoutManager = LinearLayoutManager(this)
        smsAdapter = SmsAdapter(smsList) { sms ->
            val intent = Intent(this, ConversationDetailActivity::class.java)
            intent.putExtra("thread_id", sms.threadId)
            intent.putExtra("address", sms.address)
            startActivity(intent)
        }
        recyclerSms.adapter = smsAdapter

        // 3. Varsayılan Yap Kartına Tıklama Olayı (GÜÇLENDİRİLMİŞ VERSİYON)
        cardDefault.setOnClickListener {
            // Tıklandığını anlamak için mesaj göster
            Toast.makeText(this, "İşlem başlatılıyor...", Toast.LENGTH_SHORT).show()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10 (Q) ve üzeri için Modern Yöntem
                val roleManager = getSystemService(RoleManager::class.java)
                if (roleManager.isRoleAvailable(RoleManager.ROLE_SMS)) {
                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
                    startActivityForResult(intent, ROLE_REQUEST_CODE)
                } else {
                    Toast.makeText(this, "Cihaz SMS rolünü desteklemiyor", Toast.LENGTH_SHORT).show()
                }
            } else {
                // Eski Android sürümleri için Klasik Yöntem
                val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
                startActivity(intent)
            }
        }

        // 4. Spam Kutusu
        btnSpamBox.setOnClickListener {
            val intent = Intent(this, SpamBoxActivity::class.java)
            startActivity(intent)
        }

        // 5. İzin Kontrolü
        checkPermissions()
    }

    override fun onResume() {
        super.onResume()
        // Ekran her açıldığında kontrol et
        updateDefaultAppUI()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) {
            loadSms()
        }
    }

    private fun updateDefaultAppUI() {
        val myPackageName = packageName
        val defaultSmsPackage = Telephony.Sms.getDefaultSmsPackage(this)

        if (defaultSmsPackage == myPackageName) {
            // Biz varsayılanız -> Kartı gizle
            cardDefault.visibility = View.GONE
        } else {
            // Değiliz -> Kartı göster
            cardDefault.visibility = View.VISIBLE
        }
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_SMS), PERMISSION_REQUEST_READ_SMS)
        } else {
            loadSms()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_READ_SMS) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadSms()
            } else {
                Toast.makeText(this, "SMS okuma izni gerekli!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadSms() {
        smsList.clear()

        val cursor = contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.THREAD_ID, Telephony.Sms.TYPE),
            null,
            null,
            Telephony.Sms.DATE + " DESC"
        )

        cursor?.use {
            val indexId = it.getColumnIndex(Telephony.Sms._ID)
            val indexAddress = it.getColumnIndex(Telephony.Sms.ADDRESS)
            val indexBody = it.getColumnIndex(Telephony.Sms.BODY)
            val indexDate = it.getColumnIndex(Telephony.Sms.DATE)
            val indexThreadId = it.getColumnIndex(Telephony.Sms.THREAD_ID)
            val indexType = it.getColumnIndex(Telephony.Sms.TYPE)

            val seenThreads = HashSet<String>()

            while (it.moveToNext()) {
                val id = it.getString(indexId)
                val address = it.getString(indexAddress)
                val body = it.getString(indexBody)
                val date = it.getLong(indexDate)
                val threadId = it.getString(indexThreadId)
                val type = it.getInt(indexType)

                if (threadId != null && !seenThreads.contains(threadId)) {
                    smsList.add(SmsModel(id, address, body, date, type, threadId))
                    seenThreads.add(threadId)
                }
            }
        }
        smsAdapter.notifyDataSetChanged()
    }
}