package tw.com.kooler.booking

data class Booking(
    val booking_id: Int,
    val room_id: Int,
    val check_in: String,
    val check_out: String,
    val guest_name: String,
    val guest_phone: String,
    val extra_order: String,
    val total_price: String,
    val special_requirement: String,
    val note: String
) : java.io.Serializable
