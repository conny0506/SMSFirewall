package com.example.smsfirewall

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SmsAdapter(
    private val smsList: List<SmsModel>,
    private val onItemClick: (SmsModel) -> Unit
) : RecyclerView.Adapter<SmsAdapter.SmsViewHolder>() {

    class SmsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val txtSender: TextView = itemView.findViewById(R.id.txtSender)
        val txtMessageBody: TextView = itemView.findViewById(R.id.txtMessageBody)
        // Tarih alanı layout'ta varsa eklenebilir, şimdilik sadece temel alanlar:
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SmsViewHolder {
        // Yeni tasarımımız olan 'item_sms' layoutunu kullanıyoruz
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_sms, parent, false)
        return SmsViewHolder(view)
    }

    override fun onBindViewHolder(holder: SmsViewHolder, position: Int) {
        val sms = smsList[position]

        // Gönderen (Numara veya Rehber ismi)
        holder.txtSender.text = sms.address

        // Mesaj İçeriği (Çok uzunsa kısaltılabilir veya layout halleder)
        holder.txtMessageBody.text = sms.body

        // Tıklama Olayı
        holder.itemView.setOnClickListener {
            onItemClick(sms)
        }
    }

    override fun getItemCount(): Int {
        return smsList.size
    }
}