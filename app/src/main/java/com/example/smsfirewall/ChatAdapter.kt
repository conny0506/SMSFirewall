package com.example.smsfirewall

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import android.provider.Telephony

class ChatAdapter(
    private val messageList: List<SmsModel>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    // Mesaj tiplerini belirlemek için sabitler
    private val TYPE_RECEIVED = 1
    private val TYPE_SENT = 2

    // Gelen Mesaj ViewHolder
    class ReceivedMessageHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val txtMessage: TextView = itemView.findViewById(R.id.txtMessage)
    }

    // Giden Mesaj ViewHolder
    class SentMessageHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val txtMessage: TextView = itemView.findViewById(R.id.txtMessage)
    }

    // Hangi layout'un kullanılacağını belirler (Gelen mi giden mi?)
    override fun getItemViewType(position: Int): Int {
        val message = messageList[position]
        // Telephony.Sms.MESSAGE_TYPE_INBOX = 1, MESSAGE_TYPE_SENT = 2
        return if (message.type == Telephony.Sms.MESSAGE_TYPE_SENT) {
            TYPE_SENT
        } else {
            TYPE_RECEIVED
        }
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
            holder.txtMessage.text = message.body // messageBody yerine body kullanıyoruz
        } else if (holder is ReceivedMessageHolder) {
            holder.txtMessage.text = message.body
        }
    }

    override fun getItemCount(): Int {
        return messageList.size
    }
}