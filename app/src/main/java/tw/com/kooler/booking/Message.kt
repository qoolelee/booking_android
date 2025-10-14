package tw.com.kooler.booking

data class Message(
    val text: String,
    val isUser: Int,
    val time: String,
    val isLoading: Boolean = false,
    val isRoomCard: Boolean = false,
    val roomInfo: RoomInfo? = null
)

data class RoomInfo(
    val type: String,
    val price: String,
    val features: String,
    val pictures: List<String>
)

