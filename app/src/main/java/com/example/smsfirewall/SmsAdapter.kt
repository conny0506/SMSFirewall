package com.example.smsfirewall

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.smsfirewall.databinding.ItemSmsBinding

class SmsAdapter(
    private var smsList: List<SmsModel>,
    private val onClick: (String) -> Unit = {},
    // Seçim değiştiğinde Activity'e haber verecek fonksiyon
    private val onSelectionChanged: (Int) -> Unit = {}
) : RecyclerView.Adapter<SmsAdapter.SmsViewHolder>() {

    var isSelectionMode = false

    class SmsViewHolder(val binding: ItemSmsBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SmsViewHolder {
        val binding = ItemSmsBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SmsViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SmsViewHolder, position: Int) {
        val currentSms = smsList[position]
        holder.binding.txtSender.text = currentSms.sender
        holder.binding.txtMessageBody.text = currentSms.messageBody

        // GÖRSELLİK: Seçiliyse arkaplanı gri yap
        if (currentSms.isSelected) {
            holder.itemView.setBackgroundColor(Color.parseColor("#CFD8DC"))
        } else {
            holder.itemView.setBackgroundColor(Color.WHITE)
        }

        // TIKLAMA
        holder.itemView.setOnClickListener {
            if (isSelectionMode) {
                toggleSelection(position)
            } else {
                onClick(currentSms.sender)
            }
        }

        // UZUN BASMA
        holder.itemView.setOnLongClickListener {
            if (!isSelectionMode) {
                isSelectionMode = true
                toggleSelection(position)
            }
            true
        }
    }

    private fun toggleSelection(position: Int) {
        smsList[position].isSelected = !smsList[position].isSelected
        notifyItemChanged(position)

        val count = smsList.count { it.isSelected }
        onSelectionChanged(count)

        if (count == 0) {
            isSelectionMode = false
        }
    }

    fun clearSelection() {
        isSelectionMode = false
        smsList.forEach { it.isSelected = false }
        notifyDataSetChanged()
        onSelectionChanged(0)
    }

    fun getSelectedItems(): List<SmsModel> {
        return smsList.filter { it.isSelected }
    }

    override fun getItemCount(): Int = smsList.size

    // Swipe için gerekli
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