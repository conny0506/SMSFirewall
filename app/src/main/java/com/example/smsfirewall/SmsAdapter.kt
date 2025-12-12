package com.example.smsfirewall

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.smsfirewall.databinding.ItemSmsBinding

// Adapter sınıfımız, SmsModel listesini alır
class SmsAdapter(private val smsList: List<SmsModel>) : RecyclerView.Adapter<SmsAdapter.SmsViewHolder>() {

    // Tasarımdaki görünümleri tutan yardımcı sınıf (ViewHolder)
    class SmsViewHolder(val binding: ItemSmsBinding) : RecyclerView.ViewHolder(binding.root)

    // 1. ADIM: Her satır için tasarım dosyasını (item_sms.xml) bağlar
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SmsViewHolder {
        val binding = ItemSmsBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SmsViewHolder(binding)
    }

    // 2. ADIM: Tasarım ile veriyi eşleştirir
    override fun onBindViewHolder(holder: SmsViewHolder, position: Int) {
        val currentSms = smsList[position]

        // Tasarımdaki txtSender ve txtMessageBody alanlarına veriyi yazıyoruz
        holder.binding.txtSender.text = currentSms.sender
        holder.binding.txtMessageBody.text = currentSms.messageBody
    }

    // 3. ADIM: Listenin kaç elemanlı olduğunu söyler
    override fun getItemCount(): Int {
        return smsList.size
    }
}