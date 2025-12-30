package com.example.smsfirewall

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ChatAdapter(
    private var messageList: List<SmsModel>,
    private val onSelectionChanged: (Int) -> Unit = {}
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val TYPE_RECEIVED = 1
    private val TYPE_SENT = 2

    var isSelectionMode = false

    override fun getItemViewType(position: Int): Int {
        // Tip 2 ise Giden, diğer her şey Gelen kabul edilir
        return if (messageList[position].type == 2) TYPE_SENT else TYPE_RECEIVED
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_SENT) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat_sent, parent, false)
            SentMessageHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat_received, parent, false)
            ReceivedMessageHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messageList[position]

        val txtMessage: TextView = if (holder is SentMessageHolder) holder.txtMessage else (holder as ReceivedMessageHolder).txtMessage
        txtMessage.text = message.messageBody

        // Seçim Renkleri
        if (message.isSelected) {
            holder.itemView.setBackgroundColor(Color.parseColor("#B0BEC5")) // Seçiliyse Gri Arkaplan
        } else {
            holder.itemView.setBackgroundColor(Color.TRANSPARENT) // Değilse Şeffaf
        }

        // Tıklama Olayı (itemView üzerine)
        holder.itemView.setOnClickListener {
            if (isSelectionMode) {
                toggleSelection(position)
            }
        }

        // Uzun Basma Olayı (itemView üzerine)
        holder.itemView.setOnLongClickListener {
            if (!isSelectionMode) {
                isSelectionMode = true
                toggleSelection(position)
            }
            true
        }
    }

    private fun toggleSelection(position: Int) {
        messageList[position].isSelected = !messageList[position].isSelected
        notifyItemChanged(position) // Sadece o satırı güncelle

        val count = messageList.count { it.isSelected }
        onSelectionChanged(count)

        if (count == 0) {
            isSelectionMode = false
        }
    }

    fun clearSelection() {
        isSelectionMode = false
        messageList.forEach { it.isSelected = false }
        notifyDataSetChanged()
        onSelectionChanged(0)
    }

    fun getSelectedItems(): List<SmsModel> {
        return messageList.filter { it.isSelected }
    }

    override fun getItemCount(): Int = messageList.size

    fun updateList(newList: List<SmsModel>) {
        messageList = newList
        notifyDataSetChanged()
    }

    class SentMessageHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val txtMessage: TextView = itemView.findViewById(R.id.txtMessage)
    }

    class ReceivedMessageHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val txtMessage: TextView = itemView.findViewById(R.id.txtMessage)
    }
}