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
import java.text.SimpleDateFormat
import java.util.*
import java.io.IOException
import android.view.KeyEvent
import androidx.annotation.RequiresApi
import androidx.room.Room
import com.google.firebase.ai.type.FunctionDeclaration
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.Tool
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
            loadMessagesFromRoom()
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
        var text = editText.text.toString()
        if (text.isNotEmpty()) {
            // 本地訊息
            addMessage(text, true)
            editText.text.clear()

            // 送出至 chat
            lifecycleScope.launch {
                var response = chat.sendMessage(text)

                // 處理 functionCalls
                val functionCalls = response.functionCalls
                val getExactDateFunction = functionCalls.find { it.name == "getExactDate" }
                if (getExactDateFunction != null) {
                    // 從 Map 中提取 approximateDate
                    val functionResponse = getExactDateFunction?.let {
                        val approximateDate = it.args["approximateDate"].toString()
                        getExactDate(approximateDate)
                    }

                    text = "$text is $functionResponse"
                    response = chat.sendMessage(text)
                }

                val getRoomTypesFunction = functionCalls.find { it.name == "getRoomTypes" }
                if (getRoomTypesFunction != null) {
                    val id = getRoomTypesFunction.args["id"].toString()
                    val checkinDate = getRoomTypesFunction.args["checkinDate"].toString()
                    val checkoutDate = getRoomTypesFunction.args["checkoutDate"].toString()
                    val roomTypes = getRoomTypes(id, checkinDate, checkoutDate).toString()

                    // 前對話+" has room types " + 將回傳結果轉換為字串
                    text = "$text, during the check in and check out date, there are room types $roomTypes available, be sure to answer the user according to those room types, do list them all, no more, no less."
                    response = chat.sendMessage(text)
                }

                addMessage(response.text, false)
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
                recyclerView.smoothScrollToPosition(messageList.size - 1)
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

                // 初始化 Gemini Developer API 後端服務
                model = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
                    modelName = "gemini-2.5-flash",
                    systemInstruction = content(role = "system") { text(instructions) },
                    tools = listOf(Tool.functionDeclarations(listOf(getExactDateDeclaration, getRoomTypesDeclaration)))
                )

                // 初始化聊天
                val loc = getDeviceLocale()
                chat = model.startChat(
                    history = listOf(
                        content(role = "user") { text("I am a customer language $loc.") },
                        //content(role = "model") { text("Great to meet you. What would you like to know?") }
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
                    recyclerView.smoothScrollToPosition(messageList.size - 1)
                }
                emptyList()
            }
        }
    }

    private fun addMessage(str: String?, isUser: Boolean) {
        val message = Message(str.toString(), isUser, getTime())
        messageList.add(message)
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                db.messageDao().insertMessage(
                    MessageEntity(
                        text = message.text,
                        isUser = message.isUser,
                        time = message.time
                    )
                )
            }
            withContext(Dispatchers.Main) {
                adapter.notifyItemInserted(messageList.size - 1)
                recyclerView.smoothScrollToPosition(messageList.size - 1)
            }
        }
    }

    private fun getDeviceLocale(): String {
        val locale = Locale.getDefault()
        return locale.toString()
    }

    // --------- AI called functions -----------

    // getExactDate
    private val getExactDateDeclaration = FunctionDeclaration(
        "getExactDate",
        "Converts approximate dates in a calculation, such as \"the day after tomorrow\", \"this weekend\", \"next Thursday\", etc., to precise dates, such as in the format: YYYY-MM-DD.",
        mapOf(
            "approximateDate" to Schema.string("approximate dates in a calculation, such as \"the day after tomorrow\", \"this weekend\", \"next Thursday\", etc."
            ),
        )
    )

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun getExactDate(approximateDate: String): String {
        /*
        Converts approximate dates in a calculation, such as 'the day after tomorrow', 'this weekend', 'next Thursday', etc., to precise dates, such as 'December 3, 2025'.

        Args:
            approximateDate (str): An approximate date description.

        Returns:
            A date format string 'YYYY-MM-DD' if successful.

            If an error occurs, it will return a string with an "error".
        */

        // 取得今天的日期，提供給模型作為計算基準
        val today: LocalDate = LocalDate.now()
        val formatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE // Predefined format for YYYY-MM-DD
        val todayStr: String = today.format(formatter) // Formats to YYYY-MM-DD

        val model = Firebase.ai(backend = GenerativeBackend.googleAI())
            .generativeModel("gemini-2.5-flash")

        // --- 設計給模型的提示 (Prompt) ---
        // 這是最關鍵的部分。我們需要清楚地告訴模型它的任務和輸出要求。
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

    // getRoomTypes
    val getRoomTypesDeclaration = FunctionDeclaration(
        "getRoomTypes",
    "Input hotel id and check in date and check out date to get all the available room types/descriptions for that hotel during the specific time.",
        mapOf(
            "id" to Schema.string("The hotel id for which you want to get the room types."),
            "checkinDate" to Schema.string("The check in date in YYYY-MM-DD format for which you want to get the room types."),
            "checkoutDate" to Schema.string("The check out date in YYYY-MM-DD format for which you want to get the room types."),
        )
    )

    private suspend fun getRoomTypes(
        id: String,
        checkinDate: String,
        checkoutDate: String
    ): Any {
        /*
        Input hotel id and check in date and checkout date to get all the available room types/descriptions for that hotel.

        Args:
            id: The hotel id for which you want to get the room types.
            checkinDate: The check-in date in YYYY-MM-DD format.
            checkoutDate: The check-out date in YYYY-MM-DD format.

        Returns:
            A list of maps with room_type_id, type, price, information, available_count. For example:
            [
                {
                    "room_type_id": "1",
                    "type": "Deluxe King",
                    "price": "400",
                    "information": "A spacious room with a king-sized bed, perfect for couples or solo travelers.",
                    "available_count": "5"
                },
                ...
            ]
            If an error occurs, it will return a map with an "error" key.
        */
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
}