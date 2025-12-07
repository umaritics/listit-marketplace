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
    private val onMessageLongClick: (Message, Int) -> Unit // Callback for Edit/Delete
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_SENT = 1
        private const val TYPE_RECEIVED = 2
    }

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].senderId == currentUserId) {
            TYPE_SENT
        } else {
            TYPE_RECEIVED
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_SENT) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_message_sent, parent, false)
            SentMessageHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_message_received, parent, false)
            ReceivedMessageHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]

        // Set Long Click Listener for Delete/Edit
        holder.itemView.setOnLongClickListener {
            onMessageLongClick(message, position)
            true
        }

        if (holder is SentMessageHolder) {
            holder.bind(message)
        } else if (holder is ReceivedMessageHolder) {
            holder.bind(message)
        }
    }

    override fun getItemCount(): Int = messages.size

    inner class SentMessageHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val body: TextView = itemView.findViewById(R.id.text_message_body)
        val time: TextView = itemView.findViewById(R.id.text_message_time)
        fun bind(m: Message) {
            body.text = m.messageText
            time.text = m.timestamp.takeLast(8) // Just show time
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
}