package com.example.smsfirewall

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.smsfirewall.databinding.ItemSmsBinding

class SmsAdapter(
    private var smsList: List<SmsModel>,
    private val onClick: (String) -> Unit = {} // Tıklama özelliği
) : RecyclerView.Adapter<SmsAdapter.SmsViewHolder>() {

    class SmsViewHolder(val binding: ItemSmsBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SmsViewHolder {
        val binding = ItemSmsBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SmsViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SmsViewHolder, position: Int) {
        val currentSms = smsList[position]
        holder.binding.txtSender.text = currentSms.sender
        holder.binding.txtMessageBody.text = currentSms.messageBody

        // İkon veya Avatar varsa (önceki tasarım adımında eklediysek) burada bind edilebilir
        // Örneğin: holder.binding.txtSender harf ikonunu ayarlayabiliriz.

        holder.itemView.setOnClickListener {
            onClick(currentSms.sender)
        }
    }

    override fun getItemCount(): Int = smsList.size

    // GÜNCELLENEN KISIM: DiffUtil ile Akıllı Güncelleme
    fun updateList(newList: List<SmsModel>) {
        val diffCallback = SmsDiffCallback(smsList, newList)
        val diffResult = DiffUtil.calculateDiff(diffCallback)

        smsList = newList
        // notifyDataSetChanged() yerine bunu kullanıyoruz:
        diffResult.dispatchUpdatesTo(this)
    }

    // İki liste arasındaki farkı hesaplayan yardımcı sınıf
    class SmsDiffCallback(
        private val oldList: List<SmsModel>,
        private val newList: List<SmsModel>
    ) : DiffUtil.Callback() {
        override fun getOldListSize(): Int = oldList.size
        override fun getNewListSize(): Int = newList.size

        // Aynı öğe mi? (Benzersiz ID kontrolü gibi - Tarih ve Gönderen yeterli)
        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldItem = oldList[oldItemPosition]
            val newItem = newList[newItemPosition]
            return oldItem.date == newItem.date && oldItem.sender == newItem.sender
        }

        // İçerik aynı mı? (Görsel değişiklik var mı?)
        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition] == newList[newItemPosition]
        }
    }
}