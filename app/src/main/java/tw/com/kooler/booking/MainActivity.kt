package tw.com.kooler.booking

import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.widget.EditText
import android.widget.ImageButton
import com.google.firebase.Firebase
import com.google.firebase.ai.Chat
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import android.view.KeyEvent
import androidx.annotation.RequiresApi
import androidx.room.Room
import com.google.firebase.ai.type.FunctionDeclaration
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.Tool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import java.io.IOException
import java.text.ParseException
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: MessageAdapter
    private val messageList = mutableListOf<Message>()
    private lateinit var model: GenerativeModel
    private val client = OkHttpClient()
    private lateinit var chat: Chat
    private lateinit var db: AppDatabase

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 初始化 Room 資料庫
        db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "app_database"
        ).build()

        recyclerView = findViewById(R.id.recyclerView)
        val editText = findViewById<EditText>(R.id.editTextMessage)
        val buttonSend = findViewById<ImageButton>(R.id.buttonSend)

        adapter = MessageAdapter(messageList)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // 載入儲存的訊息
        lifecycleScope.launch {
            // loadMessagesFromRoom()
            // 在載入訊息後初始化 AI
            initAI()
        }

        // 設置按鈕點擊事件
        buttonSend.setOnClickListener {
            sendMessage(editText)
        }

        // 設置鍵盤 Enter 鍵監聽
        editText.setOnEditorActionListener { _, actionId, event ->
            if (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN) {
                sendMessage(editText)
                true // 表示事件已處理
            } else {
                false // 讓其他事件正常處理
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun sendMessage(editText: EditText) {
        val text = editText.text.toString()
        if (text.isNotEmpty()) {
            // 本地訊息
            addMessage(text, true)
            editText.text.clear()

            // 加入左側 Loading 訊息
            val loadingMessage = Message("", false, getTime(), isLoading = true)
            lifecycleScope.launch {
                withContext(Dispatchers.Main) {
                    messageList.add(loadingMessage)
                    adapter.notifyItemInserted(messageList.size - 1)
                    recyclerView.scrollToPosition(messageList.size - 1)
                }

                // 送出至 chat
                var response = chat.sendMessage(text)

                // 處理 functionCalls
                val functionCalls = response.functionCalls
                val getExactDateFunction = functionCalls.find { it.name == "getExactDate" }
                if (getExactDateFunction != null) {
                    val approximateDate = getExactDateFunction?.let {
                        it.args["approximateDate"].toString()
                    }
                    val functionResponse = getExactDate(approximateDate ?: "")
                    val newText = "$text is $functionResponse."
                    response = chat.sendMessage(newText)

                    val getRoomTypesFunction = response.functionCalls.find { it.name == "getRoomTypes" }
                    if (getRoomTypesFunction != null) {
                        val id = getRoomTypesFunction.args["id"].toString()
                        val checkinDate = getRoomTypesFunction.args["checkinDate"].toString()
                        val checkoutDate = getRoomTypesFunction.args["checkoutDate"].toString()

                        val roomTypes = getRoomTypes(id, checkinDate, checkoutDate).toString()
                        val roomText = "during the check in and check out date, there are room types $roomTypes available, be sure to answer the user according to those room types, do list them all, no more, no less."
                        response = chat.sendMessage(roomText)
                    }
                }

                val getRoomTypesFunction = functionCalls.find { it.name == "getRoomTypes" }
                if (getRoomTypesFunction != null) {
                    val id = getRoomTypesFunction.args["id"].toString()
                    val checkinDate = getRoomTypesFunction.args["checkinDate"].toString()
                    val checkoutDate = getRoomTypesFunction.args["checkoutDate"].toString()

                    val roomTypes = getRoomTypes(id, checkinDate, checkoutDate).toString()
                    val roomText = "during the check in and check out date, there are room types $roomTypes available, be sure to answer the user according to those room types, do list them all, no more, no less."
                    response = chat.sendMessage(roomText)
                }

                val bookingRoomFunction = functionCalls.find { it.name == "bookingRoom" }
                if (bookingRoomFunction != null) {
                    val hotel_id = bookingRoomFunction.args["hotel_id"].toString()
                    val room_type_id = bookingRoomFunction.args["room_type_id"].toString()
                    val checkin_date = bookingRoomFunction.args["checkin_date"].toString()
                    val checkout_date = bookingRoomFunction.args["checkout_date"].toString()
                    val total_price = bookingRoomFunction.args["total_price"].toString()
                    val guest_name = bookingRoomFunction.args["guest_name"].toString()
                    val guest_phone = bookingRoomFunction.args["guest_phone"].toString()
                    val extra_order = bookingRoomFunction.args["extra_order"].toString()

                    val specialRequirement: List<String> = when (val requirement = bookingRoomFunction.args["special_requirement"]) {
                        is JSONArray -> {
                            (0 until requirement.length()).map { requirement.getString(it) }
                        }
                        is List<*> -> {
                            requirement.filterIsInstance<String>()
                        }
                        else -> emptyList()
                    }

                    val note = bookingRoomFunction.args["note"].toString()

                    val result = bookingRoom(hotel_id, room_type_id, checkin_date, checkout_date, total_price, guest_name, guest_phone, extra_order, specialRequirement, note)
                    val booking_id = result.booking_id
                    val bookingText = if (booking_id != null) {
                        "已成功預訂房間，訂單編號為 $booking_id"
                    } else {
                        "訂房完成，但未返回訂單編號：${result.message}。錯誤：${result.error}"
                    }
                    response = chat.sendMessage(bookingText)
                }

                // 移除 loading 訊息並添加回應
                withContext(Dispatchers.Main) {
                    val lastIndex = messageList.indexOfLast { it.isLoading }
                    if (lastIndex != -1) {
                        messageList.removeAt(lastIndex)
                        adapter.notifyItemRemoved(lastIndex)
                    }
                    addMessage(response.text, false)
                }
            }
        }
    }

    private fun getTime(): String {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        return sdf.format(Date())
    }

    private suspend fun loadMessagesFromRoom() {
        val messages = withContext(Dispatchers.IO) {
            db.messageDao().getAllMessages()
        }
        messageList.clear()
        messageList.addAll(messages.map { Message(it.text, it.isUser, it.time) })
        withContext(Dispatchers.Main) {
            adapter.notifyDataSetChanged()
            if (messageList.isNotEmpty()) {
                recyclerView.scrollToPosition(messageList.size - 1)
            }
        }
    }

    private fun initAI() {
        val hotelName = "Grand Hyatt Taipei"

        lifecycleScope.launch {
            try {
                val hotelInfo = getHotelInfo(hotelName)[0]
                Log.d("HotelInfo", "Hotel Info: $hotelInfo")

                val instructions = """
                {
                    'Role': 'You are Janet. You are a professional hotel reservation specialist for $hotelName',

                    'Hotel Information': $hotelInfo,

                    'Tasks': [
                      'Responsible for communicating with callers, inquiring about the hotel, and providing them with the necessary information to make a reservation.',
                      'Acquire the necessary information for guests as quickly as possible and assist them in completing their reservation.'
                    ]

                    'Information Required for Booking': ['Check-in Date', 'Check-out Date', 'Room Type Request', 'Special Requests', 'Guest Name', 'Guest Phone Number'],

                    'Attitude': ['Be polite and patient.', 'Speak user's language as much as possible.', 'Translate all the information to user's language as much as possible.'],

                    'Notes': [
                      'At the beginning of the reservation conversation, briefly explain the purpose of the conversation and the necessary customer information to complete the reservation.',
                      'If the conversation deviates from the reservation topic, return to it politely and quickly.',
                      'In each conversation, limit the number of questions in one or fewer.',
                      'Before starting the reservation, be sure to list all the information gathered from the user, and ask for confirmation.',
                      'Once the reservation is completed, politely inform the customer that the reservation is successful and provide them with the reservation details.',
                    ]
                }"""

                model = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
                    modelName = "gemini-2.5-flash",
                    systemInstruction = content(role = "system") { text(instructions) },
                    tools = listOf(Tool.functionDeclarations(listOf(getExactDateDeclaration, getRoomTypesDeclaration, bookingRoomDeclaration)))
                )

                val loc = getDeviceLocale()
                chat = model.startChat(
                    history = listOf(
                        content(role = "user") { text("I am a customer language $loc.") }
                    )
                )

                val response = chat.sendMessage("@string/hello")
                addMessage(response.text, false)

            } catch (e: IOException) {
                Log.e("HotelInfo", "Failed to fetch hotel info: ${e.message}")
            }
        }
    }

    private suspend fun getHotelInfo(hotelName: String): List<Map<String, Any>> {
        return withContext(Dispatchers.IO) {
            try {
                val url = "http://34.63.109.78/api/hotels/search?name=$hotelName"
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Unexpected code $response")
                    val jsonString = response.body?.string() ?: throw IOException("Empty response body")
                    val jsonArray = JSONArray(jsonString)
                    val hotelList = mutableListOf<Map<String, Any>>()

                    for (i in 0 until jsonArray.length()) {
                        val jsonObject = jsonArray.getJSONObject(i)
                        hotelList.add(
                            mapOf(
                                "id" to jsonObject.getString("id"),
                                "name" to jsonObject.getString("name"),
                                "address" to jsonObject.getString("address"),
                                "rating" to jsonObject.getString("rating"),
                                "info" to jsonObject.getString("info")
                            )
                        )
                    }
                    hotelList
                }
            } catch (e: Exception) {
                Log.e("HotelInfo", "無法取得飯店資訊", e)
                withContext(Dispatchers.Main) {
                    messageList.add(Message("錯誤：無法取得飯店資訊，請稍後再試。", false, getTime()))
                    adapter.notifyItemInserted(messageList.size - 1)
                    recyclerView.scrollToPosition(messageList.size - 1)
                }
                emptyList()
            }
        }
    }

    private fun addMessage(str: String?, isUser: Boolean) {
        val message = Message(str.toString(), isUser, getTime())
        lifecycleScope.launch {
            withContext(Dispatchers.Main) {
                messageList.add(message)
                adapter.notifyItemInserted(messageList.size - 1)
                recyclerView.scrollToPosition(messageList.size - 1)
            }
        }
    }

    private fun getDeviceLocale(): String {
        val locale = Locale.getDefault()
        return locale.toString()
    }

    // --------- AI called functions -----------

    private val getExactDateDeclaration = FunctionDeclaration(
        "getExactDate",
        "Converts approximate dates in a calculation, such as \"the day after tomorrow\", \"this weekend\", \"next Thursday\", etc., to precise dates, such as in the format: YYYY-MM-DD.",
        mapOf(
            "approximateDate" to Schema.string("approximate dates in a calculation, such as \"the day after tomorrow\", \"this weekend\", \"next Thursday\", etc.")
        )
    )

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun getExactDate(approximateDate: String): String {
        val today: LocalDate = LocalDate.now()
        val formatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
        val todayStr: String = today.format(formatter)

        val model = Firebase.ai(backend = GenerativeBackend.googleAI())
            .generativeModel("gemini-2.5-flash")

        val prompt = """
            Your task is to convert a colloquial, vague date description into a precise Gregorian calendar date based on today's date.

            Today's date is: $todayStr

            please analyze the description of the date：
            $approximateDate

            Your response must be a string in the format of YYYY-MM-DD.
            - The value of "year" must be a 4-digit year (e.g., "2025").
            - The value of "month" must be a 2-digit month from 01 to 12 (e.g., "08").
            - The value of "date" must be a 2-digit date from 01 to 31 (e.g., "03").

            If a reasonable date cannot be inferred from the description, your response must be a string starting with "error: " followed by a simple description of the error (e.g., "error: unrecognized date").

            Please output the string without any additional explanation or Markdown in the format of YYYY-MM-DD or "error: <description>".

            Example 1:
            Input: "The day after tomorrow"
            Assume today's date: 2025-07-31
            Output: 2025-08-02

            Example 2:
            Input: "Next weekend"
            Assume today's date: 2025-07-31 (Thursday)
            Output: 2025-08-03

            Example 3:
            Input: "The first of next month"
            Assume today's date: 2025-07-31
            Output: 2025-08-01

            Example 4:
            Input: "Invalid date"
            Output: "error: unrecognized date"
            """

        val response = model.generateContent(prompt)
        return response.text ?: "error: empty response"
    }

    private val getRoomTypesDeclaration = FunctionDeclaration(
        "getRoomTypes",
        "Input hotel id and check in date and check out date to get all the available room types/descriptions for that hotel during the specific time.",
        mapOf(
            "id" to Schema.string("The hotel id for which you want to get the room types."),
            "checkinDate" to Schema.string("The check in date in YYYY-MM-DD format for which you want to get the room types."),
            "checkoutDate" to Schema.string("The check out date in YYYY-MM-DD format for which you want to get the room types.")
        )
    )

    private suspend fun getRoomTypes(
        id: String,
        checkinDate: String,
        checkoutDate: String
    ): Any {
        return withContext(Dispatchers.IO) {
            try {
                val url = "http://34.63.109.78/api/hotels/room_types?id=$id&checkin_date=$checkinDate&checkout_date=$checkoutDate"
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val jsonString = response.body?.string() ?: return@withContext emptyMap<String, Any>()
                        val jsonArray = JSONArray(jsonString)
                        val roomTypes = mutableListOf<Map<String, Any>>()

                        for (i in 0 until jsonArray.length()) {
                            val jsonObject = jsonArray.getJSONObject(i)
                            roomTypes.add(
                                mapOf(
                                    "room_type_id" to jsonObject.getString("room_type_id"),
                                    "type" to jsonObject.getString("type"),
                                    "price" to jsonObject.getString("price"),
                                    "information" to jsonObject.getString("information"),
                                    "available_count" to jsonObject.getString("available_count")
                                )
                            )
                        }
                        roomTypes
                    } else if (response.code in listOf(400, 404)) {
                        emptyList<Map<String, Any>>()
                    } else {
                        throw IOException("Unexpected code ${response.code}: ${response.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e("RoomTypes", "無法取得房型資訊", e)
                mapOf("error" to e.message.toString())
            }
        }
    }

    private val bookingRoomDeclaration = FunctionDeclaration(
        "bookingRoom",
        "Place a booking order by provide information to 'POST' to a booking API for booking a room in hotel.",
        mapOf(
            "hotel_id" to Schema.string("The hotel id for which you want to book."),
            "room_type_id" to Schema.string("The room type id for the room you want to book."),
            "checkin_date" to Schema.string("The check-in date in YYYY-MM-DD format."),
            "checkout_date" to Schema.string("The check-out date in YYYY-MM-DD format."),
            "total_price" to Schema.string("The total price for the booking, calculated as price * days + extra_order."),
            "guest_name" to Schema.string("The name of the guest who is booking the room."),
            "guest_phone" to Schema.string("The phone number of the guest who is booking the room."),
            "extra_order" to Schema.string("Any extra orders or services requested by the guest, such as breakfast."),
            "special_requirement" to Schema.array(Schema.string("A special requirements or requests from the guest."), "A list of special requirements or requests from the guest."),
            "note" to Schema.string("Any additional notes or comments regarding the booking.")
        )
    )

    // Booking 回應資料類別
    data class BookingResponse(
        val message: String,
        val booking_id: String? = null,
        val error: String? = null
    )

    private suspend fun bookingRoom(
        hotel_id: String,
        room_type_id: String,
        checkin_date: String,
        checkout_date: String,
        total_price: String,
        guest_name: String,
        guest_phone: String,
        extra_order: String,
        special_requirement: List<String>,
        note: String
    ): BookingResponse = withContext(Dispatchers.IO) {
        val API_URL = "http://34.63.109.78/api/hotels/booking_room"
        val client = OkHttpClient()

        // 通用：去掉首尾空白 & 引號
        fun normalize(input: String): String {
            return input.trim().trim('"')
        }

        try {
            // ---- 清理輸入 ----
            val cleanHotelId = normalize(hotel_id)
            val cleanRoomTypeId = normalize(room_type_id)
            val cleanCheckinDate = normalize(checkin_date)
            val cleanCheckoutDate = normalize(checkout_date)
            val cleanTotalprice = normalize(total_price)
            val cleanGuestName = normalize(guest_name)
            val cleanGuestPhone = normalize(guest_phone)
            val cleanExtraOrder = normalize(extra_order)
            val cleanSpecialRequirement = normalize(special_requirement.toString())
            val cleanNote = normalize(note)

            // ---- 驗證日期格式 ----
            val dateFormat = Regex("""^\d{4}-\d{2}-\d{2}$""")
            if (!cleanCheckinDate.matches(dateFormat) || !cleanCheckoutDate.matches(dateFormat)) {
                return@withContext BookingResponse(
                    message = "無效的日期格式，請使用 YYYY-MM-DD。",
                    error = "日期格式錯誤：checkin_date='$cleanCheckinDate', checkout_date='$cleanCheckoutDate'"
                )
            }

            // ---- 驗證日期邏輯 ----
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            sdf.isLenient = false
            try {
                val checkin = sdf.parse(cleanCheckinDate)
                val checkout = sdf.parse(cleanCheckoutDate)
                val today = Calendar.getInstance().time

                if (checkin != null && checkin.before(today) && !sdf.format(checkin).equals(sdf.format(today))) {
                    return@withContext BookingResponse(
                        message = "入住日期不能是過去的日期。",
                        error = "無效的入住日期"
                    )
                }
                if (checkout != null && checkout <= checkin) {
                    return@withContext BookingResponse(
                        message = "退房日期必須晚於入住日期。",
                        error = "無效的退房日期"
                    )
                }
            } catch (e: ParseException) {
                return@withContext BookingResponse(
                    message = "無效的日期格式，請使用 YYYY-MM-DD。",
                    error = "日期解析錯誤：${e.message}"
                )
            }

            // ---- 驗證必要欄位 ----
            if (cleanHotelId.isBlank() || cleanRoomTypeId.isBlank() || guest_name.isBlank() || guest_phone.isBlank()) {
                return@withContext BookingResponse(
                    message = "缺少必要欄位。",
                    error = "必要欄位不可為空"
                )
            }

            // ---- 構造 JSON ----
            val json = JSONObject().apply {
                put("hotel_id", cleanHotelId)
                put("room_type_id", cleanRoomTypeId)
                put("checkin_date", cleanCheckinDate)
                put("checkout_date", cleanCheckoutDate)
                put("total_price", cleanTotalprice)
                put("guest_name", cleanGuestName)
                put("guest_phone", cleanGuestPhone)
                put("extra_order", cleanExtraOrder)
                put("special_requirement", JSONArray(cleanSpecialRequirement))
                put("note", cleanNote)
            }
            Log.d("BookingAPI", "發送請求: URL=$API_URL, JSON=$json")

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = json.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url(API_URL)
                .post(requestBody)
                .build()

            // ---- 發送請求 ----
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()
                Log.d("BookingAPI", "原始回應: $responseBody, 狀態碼: ${response.code}")

                when (response.code) {
                    201 -> {
                        val jsonResponse = JSONObject(responseBody ?: "{}")
                        val bookingId = jsonResponse.optString("booking_id", null)
                        BookingResponse(
                            message = jsonResponse.optString("message", "訂房完成"),
                            booking_id = bookingId,
                            error = if (bookingId == null) "回應中缺少 booking_id 或為 null" else null
                        )
                    }
                    400, 409, 500 -> {
                        val jsonResponse = JSONObject(responseBody ?: "{}")
                        BookingResponse(
                            message = jsonResponse.optString("message", "訂房失敗"),
                            error = jsonResponse.optString("error", response.message)
                        )
                    }
                    else -> {
                        BookingResponse(
                            message = "訂房過程中發生錯誤。",
                            error = "未知的狀態碼: ${response.code}"
                        )
                    }
                }
            }
        } catch (e: Exception) {
            BookingResponse(
                message = "訂房過程中發生錯誤。",
                error = e.message ?: "未知的錯誤"
            )
        }
    }

}