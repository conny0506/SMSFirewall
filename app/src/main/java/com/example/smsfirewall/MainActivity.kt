package com.example.smsfirewall

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.smsfirewall.databinding.ActivityMainBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var smsAdapter: SmsAdapter

    // Çöp Kutusu veritabanı erişimi
    private val trashDao by lazy { AppDatabase.getDatabase(this).trashMessageDao() }

    private val smsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            if (hasSmsReadPermission()) loadInboxMessages()
        }
    }

    private val roleRequestLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) loadInboxMessages()
    }

    private val notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()

        // Buton Dinleyicileri
        binding.btnSpamBox.setOnClickListener { startActivity(Intent(this, SpamBoxActivity::class.java)) }
        binding.btnTrashBox.setOnClickListener { startActivity(Intent(this, TrashBoxActivity::class.java)) }
        binding.btnSetDefault.setOnClickListener { askDefaultSmsHandlerPermission() }

        askNotificationPermission()
    }

    override fun onResume() {
        super.onResume()
        checkDefaultSmsApp()
        if (hasSmsReadPermission()) {
            loadInboxMessages()
            contentResolver.registerContentObserver(Uri.parse("content://sms"), true, smsObserver)
        }
    }

    override fun onPause() {
        super.onPause()
        contentResolver.unregisterContentObserver(smsObserver)
    }

    private fun setupRecyclerView() {
        smsAdapter = SmsAdapter(emptyList(),
            onClick = { clickedNumber ->
                val intent = Intent(this, ConversationDetailActivity::class.java)
                intent.putExtra("SENDER_NUMBER", clickedNumber)
                startActivity(intent)
            }
        )
        binding.recyclerSms.layoutManager = LinearLayoutManager(this)
        binding.recyclerSms.adapter = smsAdapter

        // --- KAYDIRMA İLE SİLME (SWIPE TO DELETE) ---
        val itemTouchHelperCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                // Adaptörden silinecek kişiyi al
                val smsToDelete = smsAdapter.getItem(position)

                // Mesajı Çöp Kutusuna taşı ve sil
                deleteThread(smsToDelete.sender)

                // Kullanıcıya bilgi ver
                Snackbar.make(binding.root, "Sohbet Çöp Kutusuna taşındı", Snackbar.LENGTH_LONG).show()
            }
        }
        ItemTouchHelper(itemTouchHelperCallback).attachToRecyclerView(binding.recyclerSms)
    }

    private fun deleteThread(sender: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 1. Önce silinecek mesajları telefondan oku
                val messagesToTrash = ArrayList<TrashMessage>()
                val cursor = contentResolver.query(
                    Uri.parse("content://sms"),
                    arrayOf("address", "body", "date", "type"),
                    "address = ?",
                    arrayOf(sender),
                    null
                )

                if (cursor != null && cursor.moveToFirst()) {
                    val idxBody = cursor.getColumnIndex("body")
                    val idxDate = cursor.getColumnIndex("date")
                    val idxType = cursor.getColumnIndex("type")
                    do {
                        messagesToTrash.add(TrashMessage(
                            sender = sender,
                            body = cursor.getString(idxBody),
                            date = cursor.getLong(idxDate),
                            type = cursor.getInt(idxType)
                        ))
                    } while (cursor.moveToNext())
                    cursor.close()
                }

                // 2. Çöp Kutusuna (Veritabanına) Kaydet
                trashDao.insertAll(messagesToTrash)

                // 3. Telefondan Sil
                contentResolver.delete(Uri.parse("content://sms"), "address = ?", arrayOf(sender))

            } catch (e: Exception) {
                e.printStackTrace()
                // Hata olursa listeyi yenile ki silinmiş gibi görünen eleman geri gelsin
                withContext(Dispatchers.Main) {
                    loadInboxMessages()
                    Toast.makeText(this@MainActivity, "Silme hatası!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadInboxMessages() {
        val distinctMessages = HashMap<String, SmsModel>()
        val uri = Uri.parse("content://sms/inbox")
        val cursor = contentResolver.query(uri, arrayOf("_id", "address", "body", "date", "type"), null, null, "date DESC")

        if (cursor != null && cursor.moveToFirst()) {
            val idxId = cursor.getColumnIndex("_id")
            val idxAddr = cursor.getColumnIndex("address")
            val idxBody = cursor.getColumnIndex("body")
            val idxDate = cursor.getColumnIndex("date")
            val idxType = cursor.getColumnIndex("type")

            do {
                if (idxAddr >= 0 && idxId >= 0) {
                    val id = cursor.getLong(idxId)
                    val address = cursor.getString(idxAddr)
                    val body = cursor.getString(idxBody)
                    val date = cursor.getLong(idxDate)
                    val type = cursor.getInt(idxType)
                    if (!distinctMessages.containsKey(address)) {
                        distinctMessages[address] = SmsModel(id, address, body, date, type)
                    }
                }
            } while (cursor.moveToNext())
            cursor.close()
        }
        smsAdapter.updateList(distinctMessages.values.toList())
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
    private fun askDefaultSmsHandlerPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(Context.ROLE_SERVICE) as RoleManager
            if (roleManager.isRoleAvailable(RoleManager.ROLE_SMS)) roleRequestLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS))
        } else {
            val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
            intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
            startActivity(intent)
        }
    }
    private fun checkDefaultSmsApp() {
        val isDefault = Telephony.Sms.getDefaultSmsPackage(this) == packageName
        binding.btnSetDefault.isEnabled = !isDefault
        binding.btnSetDefault.text = if (isDefault) "Varsayılan Uygulama" else "Varsayılan Yap"
    }
    private fun hasSmsReadPermission(): Boolean = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED || Telephony.Sms.getDefaultSmsPackage(this) == packageName
}