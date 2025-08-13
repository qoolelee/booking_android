package tw.com.kooler.booking

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
import java.io.IOException
import android.view.KeyEvent

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: MessageAdapter
    private val messageList = mutableListOf<Message>()
    private lateinit var model: GenerativeModel
    private val client = OkHttpClient()
    private lateinit var chat: Chat

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recyclerView = findViewById(R.id.recyclerView)
        val editText = findViewById<EditText>(R.id.editTextMessage)
        val buttonSend = findViewById<ImageButton>(R.id.buttonSend)

        adapter = MessageAdapter(messageList)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

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

        // 初始化 AI
        initAI()
    }

    private fun sendMessage(editText: EditText) {
        val text = editText.text.toString()
        if (text.isNotEmpty()) {
            // 本地訊息
            addMessage(text, true)
            editText.text.clear()

            // 送出至 chat
            lifecycleScope.launch {
                val response = chat.sendMessage(text)
                addMessage(response.text, false)
            }
        }
    }

    private fun getTime(): String {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        return sdf.format(Date())
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
                      'In each conversation, limit the number of questions in two or fewer.',
                      'Before starting the reservation, be sure to list all the information gathered from the user, and ask for confirmation.',
                      'Once the reservation is completed, politely inform the customer that the reservation is successful and provide them with the reservation details.',
                    ]

                }"""

                // 初始化 Gemini Developer API 後端服務
                model = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
                    modelName = "gemini-2.5-flash",
                    systemInstruction = content(role = "system") { text(instructions) },
                )

                // 初始化聊天
                chat = model.startChat(
                    history = listOf(
                        //content(role = "user") { text("Hello, I have 2 dogs in my house.") },
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
                    recyclerView.scrollToPosition(messageList.size - 1)
                }
                emptyList()
            }
        }
    }

    private fun addMessage(str: String?, isUser: Boolean) {
        messageList.add(Message(str.toString(), isUser, getTime()))
        adapter.notifyItemInserted(messageList.size - 1)
        recyclerView.scrollToPosition(messageList.size - 1)
    }
}