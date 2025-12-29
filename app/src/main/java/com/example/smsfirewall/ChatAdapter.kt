package com.example.smsfirewall

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ChatAdapter(private var messageList: List<SmsModel>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    // Mesaj türlerini tanımlıyoruz
    private val TYPE_RECEIVED = 1
    private val TYPE_SENT = 2

    // Hangi tasarımın yükleneceğine karar veren fonksiyon
    override fun getItemViewType(position: Int): Int {
        // Eğer veritabanındaki type 1 ise Gelen, 2 ise Giden kabul edelim
        // (Android SMS veritabanında 1=Inbox, 2=Sent)
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
        if (holder is SentMessageHolder) {
            holder.bind(message)
        } else if (holder is ReceivedMessageHolder) {
            holder.bind(message)
        }
    }

    override fun getItemCount(): Int = messageList.size

    fun updateList(newList: List<SmsModel>) {
        messageList = newList
        notifyDataSetChanged()
    }

    // --- ViewHolder Sınıfları ---

    class SentMessageHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(message: SmsModel) {
            itemView.findViewById<TextView>(R.id.txtMessage).text = message.messageBody
        }
    }

    class ReceivedMessageHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(message: SmsModel) {
            itemView.findViewById<TextView>(R.id.txtMessage).text = message.messageBody
        }
    }
}