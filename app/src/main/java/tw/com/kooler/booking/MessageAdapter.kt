package tw.com.kooler.booking

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class MessageAdapter(private val messages: MutableList<Message>) :
    RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {

    class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageLeft: ImageView = itemView.findViewById(R.id.imageAvatarLeft)
        val textLeft: TextView = itemView.findViewById(R.id.textMessageLeft)
        val timeLeft: TextView = itemView.findViewById(R.id.textTimeLeft)
        val progressLeft: ProgressBar = itemView.findViewById(R.id.progressLoadingLeft) // ← 新增

        val textRight: TextView = itemView.findViewById(R.id.textMessageRight)
        val timeRight: TextView = itemView.findViewById(R.id.textTimeRight)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_message, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]

        if (message.isUser) {
            // 顯示右側
            holder.textRight.visibility = View.VISIBLE
            holder.timeRight.visibility = View.VISIBLE

            holder.imageLeft.visibility = View.GONE
            holder.textLeft.visibility = View.GONE
            holder.timeLeft.visibility = View.GONE
            holder.progressLeft.visibility = View.GONE

            holder.textRight.text = message.text
            holder.timeRight.text = message.time
        } else {
            // 顯示左側
            holder.imageLeft.visibility = View.VISIBLE

            holder.textRight.visibility = View.GONE
            holder.timeRight.visibility = View.GONE

            if (message.isLoading) {
                holder.progressLeft.visibility = View.VISIBLE
                holder.textLeft.visibility = View.GONE
                holder.timeLeft.visibility = View.GONE
            } else {
                holder.progressLeft.visibility = View.GONE
                holder.textLeft.visibility = View.VISIBLE
                holder.timeLeft.visibility = View.VISIBLE
                holder.textLeft.text = message.text
                holder.timeLeft.text = message.time
            }
        }
    }

    override fun getItemCount(): Int = messages.size
}
