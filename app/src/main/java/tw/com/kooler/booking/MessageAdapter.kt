package tw.com.kooler.booking

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class MessageAdapter(
    private val messages: MutableList<Message>,
    private val onRoomSelected: (String) -> Unit,
    private val onShowLoading: () -> Unit,
    private val onHideLoading: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == 1) {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_room_card, parent, false)
            RoomCardViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_message, parent, false)
            MessageViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]

        if (holder is MessageViewHolder) {
            if (message.isUser == 1) {
                // 顯示右側（使用者訊息）
                holder.textRight.visibility = View.VISIBLE
                holder.timeRight.visibility = View.VISIBLE
                holder.imageLeft.visibility = View.GONE
                holder.textLeft.visibility = View.GONE
                holder.timeLeft.visibility = View.GONE
                holder.imageGifLoadingLeft.visibility = View.GONE

                holder.textRight.text = message.text
                holder.timeRight.text = message.time
            } else {
                // 顯示左側（AI或系統）
                holder.imageLeft.visibility = View.VISIBLE
                if (message.isUser == 2) {
                    holder.imageLeft.setImageResource(R.drawable.ic_booking_256)
                } else {
                    holder.imageLeft.setImageResource(R.drawable.ic_custom_service_girl)
                }
                holder.textRight.visibility = View.GONE
                holder.timeRight.visibility = View.GONE

                if (message.isLoading) {
                    // 顯示 GIF 動畫
                    holder.imageGifLoadingLeft.visibility = View.VISIBLE
                    holder.textLeft.visibility = View.VISIBLE
                    holder.timeLeft.visibility = View.VISIBLE
                    holder.textLeft.text = message.text
                    holder.timeLeft.text = message.time

                    Glide.with(holder.itemView.context)
                        .asGif()
                        .load(R.drawable.g1)
                        .into(holder.imageGifLoadingLeft)
                } else {
                    holder.imageGifLoadingLeft.visibility = View.GONE
                    holder.textLeft.visibility = View.VISIBLE
                    holder.timeLeft.visibility = View.VISIBLE
                    holder.textLeft.text = message.text
                    holder.timeLeft.text = message.time
                }
            }
        } else if (holder is RoomCardViewHolder) {
            val room = message.roomInfo ?: return
            holder.textRoomType.text = room.type
            holder.textPrice.text = "NT$${room.price}"
            holder.textFeatures.text = room.features
            Glide.with(holder.itemView.context)
                .load(room.pictures.firstOrNull())
                .placeholder(R.drawable.ic_image_placeholder)
                .into(holder.imageRoom)

            holder.imageRoom.setOnClickListener {
                val context = holder.itemView.context
                if (context is MainActivity) {
                    val intent = Intent(context, RoomDetailActivity::class.java)
                    intent.putExtra("type", room.type)
                    intent.putExtra("price", room.price)
                    intent.putExtra("features", room.features)
                    intent.putStringArrayListExtra("pictures", ArrayList(room.pictures))
                    context.roomDetailLauncher.launch(intent)
                }
            }

            holder.buttonBook.setOnClickListener {
                onShowLoading() // 顯示 GIF
                onRoomSelected(room.type) // 通知 MainActivity
            }
        }
    }

    override fun getItemCount(): Int = messages.size

    override fun getItemViewType(position: Int): Int {
        val message = messages[position]
        return when {
            message.isRoomCard -> 1
            else -> 0
        }
    }

    class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageLeft: ImageView = itemView.findViewById(R.id.imageAvatarLeft)
        val textLeft: TextView = itemView.findViewById(R.id.textMessageLeft)
        val timeLeft: TextView = itemView.findViewById(R.id.textTimeLeft)

        // 改為 GIF ImageView
        val imageGifLoadingLeft: ImageView = itemView.findViewById(R.id.imageGifLoadingLeft)

        val textRight: TextView = itemView.findViewById(R.id.textMessageRight)
        val timeRight: TextView = itemView.findViewById(R.id.textTimeRight)
    }

    class RoomCardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageRoom: ImageView = itemView.findViewById(R.id.imageRoom)
        val textRoomType: TextView = itemView.findViewById(R.id.textRoomType)
        val textPrice: TextView = itemView.findViewById(R.id.textPrice)
        val textFeatures: TextView = itemView.findViewById(R.id.textFeatures)
        val buttonBook: Button = itemView.findViewById(R.id.buttonBook)
    }
}
