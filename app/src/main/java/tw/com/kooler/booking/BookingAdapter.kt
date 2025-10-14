package tw.com.kooler.booking

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class BookingAdapter(private val bookings: List<Booking>) :
    RecyclerView.Adapter<BookingAdapter.BookingViewHolder>() {

    class BookingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textBookingId: TextView = itemView.findViewById(R.id.textBookingId)
        val textGuestName: TextView = itemView.findViewById(R.id.textGuestName)
        val textDate: TextView = itemView.findViewById(R.id.textDate)
        val textTotalPrice: TextView = itemView.findViewById(R.id.textTotalPrice)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookingViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_booking, parent, false)
        return BookingViewHolder(view)
    }

    override fun onBindViewHolder(holder: BookingViewHolder, position: Int) {
        val booking = bookings[position]
        holder.textBookingId.text = "#${booking.booking_id}"
        holder.textGuestName.text = booking.guest_name
        holder.textDate.text = "${booking.check_in} → ${booking.check_out}"
        holder.textTotalPrice.text = "NT$${booking.total_price}"

        holder.itemView.setOnClickListener {
            val context = holder.itemView.context
            val intent = Intent(context, BookingDetailActivity::class.java)
            intent.putExtra("booking", booking)
            context.startActivity(intent)
        }
    }

    override fun getItemCount(): Int = bookings.size
}
