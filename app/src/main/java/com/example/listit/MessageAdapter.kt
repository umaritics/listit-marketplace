package com.example.listit

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class Message(
    val id: Int,
    val messageText: String,
    val senderId: Int,
    val timestamp: String
)

class MessageAdapter(
    private val currentUserId: Int,
    private val messages: ArrayList<Message>,
    private val onMessageLongClick: (Message, Int) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_SENT = 1
        private const val TYPE_RECEIVED = 2
        private const val TYPE_SYSTEM = 3 // For Offers
    }

    override fun getItemViewType(position: Int): Int {
        val msg = messages[position]
        return if (msg.messageText.startsWith("OFFER:")) {
            TYPE_SYSTEM
        } else if (msg.senderId == currentUserId) {
            TYPE_SENT
        } else {
            TYPE_RECEIVED
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_SYSTEM -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_message_system, parent, false)
                SystemMessageHolder(view)
            }
            TYPE_SENT -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_message_sent, parent, false)
                SentMessageHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_message_received, parent, false)
                ReceivedMessageHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]

        // Only allow edit/delete on normal sent messages
        if (getItemViewType(position) == TYPE_SENT) {
            holder.itemView.setOnLongClickListener {
                onMessageLongClick(message, position)
                true
            }
        }

        when (holder) {
            is SentMessageHolder -> holder.bind(message)
            is ReceivedMessageHolder -> holder.bind(message)
            is SystemMessageHolder -> holder.bind(message)
        }
    }

    override fun getItemCount(): Int = messages.size

    inner class SentMessageHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val body: TextView = itemView.findViewById(R.id.text_message_body)
        val time: TextView = itemView.findViewById(R.id.text_message_time)
        fun bind(m: Message) {
            body.text = m.messageText
            time.text = m.timestamp.takeLast(8)
        }
    }

    inner class ReceivedMessageHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val body: TextView = itemView.findViewById(R.id.text_message_body)
        val time: TextView = itemView.findViewById(R.id.text_message_time)
        fun bind(m: Message) {
            body.text = m.messageText
            time.text = m.timestamp.takeLast(8)
        }
    }

    inner class SystemMessageHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val body: TextView = itemView.findViewById(R.id.text_message_body)
        fun bind(m: Message) {
            // Display neatly: "OFFER: 500" -> "Offer Made: Rs 500"
            val price = m.messageText.removePrefix("OFFER:")
            body.text = "Offer Made: Rs $price"
        }
    }
}