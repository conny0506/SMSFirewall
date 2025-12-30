package com.example.smsfirewall

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.smsfirewall.databinding.ItemSmsBinding

class SmsAdapter(
    private var smsList: List<SmsModel>,
    private val onClick: (String) -> Unit = {},
    private val onLongClick: (SmsModel) -> Unit = {}
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

        holder.itemView.setOnClickListener { onClick(currentSms.sender) }
        holder.itemView.setOnLongClickListener {
            onLongClick(currentSms)
            true
        }
    }

    override fun getItemCount(): Int = smsList.size

    // YENİ EKLENEN FONKSİYON: Pozisyona göre öğeyi getirir
    fun getItem(position: Int): SmsModel {
        return smsList[position]
    }

    fun updateList(newList: List<SmsModel>) {
        val diffCallback = SmsDiffCallback(smsList, newList)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        smsList = newList
        diffResult.dispatchUpdatesTo(this)
    }

    class SmsDiffCallback(private val oldList: List<SmsModel>, private val newList: List<SmsModel>) : DiffUtil.Callback() {
        override fun getOldListSize(): Int = oldList.size
        override fun getNewListSize(): Int = newList.size
        override fun areItemsTheSame(oldPos: Int, newPos: Int): Boolean = oldList[oldPos].id == newList[newPos].id
        override fun areContentsTheSame(oldPos: Int, newPos: Int): Boolean = oldList[oldPos] == newList[newPos]
    }
}