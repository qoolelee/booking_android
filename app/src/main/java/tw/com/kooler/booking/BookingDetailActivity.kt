package tw.com.kooler.booking

import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class BookingDetailActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_booking_detail)

        val buttonBack = findViewById<ImageButton>(R.id.buttonBack)
        val textBookingId = findViewById<TextView>(R.id.textBookingId)
        val textGuestName = findViewById<TextView>(R.id.textGuestName)
        val textGuestPhone = findViewById<TextView>(R.id.textGuestPhone)
        val textCheckIn = findViewById<TextView>(R.id.textCheckIn)
        val textCheckOut = findViewById<TextView>(R.id.textCheckOut)
        val textRoomId = findViewById<TextView>(R.id.textRoomId)
        val textExtraOrder = findViewById<TextView>(R.id.textExtraOrder)
        val textTotalPrice = findViewById<TextView>(R.id.textTotalPrice)
        val textSpecial = findViewById<TextView>(R.id.textSpecial)
        val textNote = findViewById<TextView>(R.id.textNote)

        buttonBack.setOnClickListener { finish() }

        val booking = intent.getSerializableExtra("booking") as? Booking ?: return

        textBookingId.text = getString(R.string.label_booking_id, booking.booking_id)
        textGuestName.text = getString(R.string.label_guest_name, booking.guest_name)
        textGuestPhone.text = getString(R.string.label_guest_phone, booking.guest_phone)
        textCheckIn.text = getString(R.string.label_check_in, booking.check_in)
        textCheckOut.text = getString(R.string.label_check_out, booking.check_out)
        textRoomId.text = getString(R.string.label_room_id, booking.room_id)
        textExtraOrder.text = getString(R.string.label_extra_order, booking.extra_order.ifEmpty { getString(R.string.label_none) })
        textTotalPrice.text = getString(R.string.label_total_price, booking.total_price)
        textSpecial.text = getString(R.string.label_special, booking.special_requirement.ifEmpty { getString(R.string.label_none) })
        textNote.text = getString(R.string.label_note, booking.note.ifEmpty { getString(R.string.label_none) })
    }
}
