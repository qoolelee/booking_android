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
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: MessageAdapter
    private val messageList = mutableListOf<Message>()
    private lateinit var model: GenerativeModel
    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recyclerView = findViewById(R.id.recyclerView)
        val editText = findViewById<EditText>(R.id.editTextMessage)
        val buttonSend = findViewById<ImageButton>(R.id.buttonSend)

        adapter = MessageAdapter(messageList)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        buttonSend.setOnClickListener {
            val text = editText.text.toString()
            if (text.isNotEmpty()) {
                // 本地訊息
                messageList.add(Message(text, true, getTime()))
                adapter.notifyItemInserted(messageList.size - 1)
                recyclerView.scrollToPosition(messageList.size - 1)
                editText.text.clear()

                // 模擬系統回應
                recyclerView.postDelayed({
                    messageList.add(Message("收到: $text", false, getTime()))
                    adapter.notifyItemInserted(messageList.size - 1)
                    recyclerView.scrollToPosition(messageList.size - 1)
                }, 1000)
            }
        }

        // Initialize AI
        initAI()
    }

    private fun getTime(): String {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        return sdf.format(Date())
    }

    private fun initAI() {
        val hotelName = "Grand Hyatt Taipei"
        lifecycleScope.launch {
            try {
                val hotelInfo = getHotelInfo(hotelName)
                Log.d("HotelInfo", "Hotel Info: $hotelInfo")

                // Initialize the Gemini Developer API backend service
                model = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
                    modelName = "gemini-2.5-flash"
                )

                // Initialize the chat
                val chat = model.startChat(
                    history = listOf(
                        content(role = "user") { text("Hello, I have 2 dogs in my house.") },
                        content(role = "model") { text("Great to meet you. What would you like to know?") }
                    )
                )

                val response = chat.sendMessage("How many paws are in my house?")
                Log.d("AI", response.text.toString())
            } catch (e: IOException) {
                Log.e("HotelInfo", "Failed to fetch hotel info: ${e.message}")
            }
        }
    }

    private suspend fun getHotelInfo(hotelName: String): Map<String, Any> {
        return withContext(Dispatchers.IO) {
            val url = "http://34.63.109.78/api/hotels/search?name=$hotelName"
            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Unexpected code $response")

                val jsonString = response.body?.string() ?: throw IOException("Empty response body")
                val jsonObject = JSONObject(jsonString)

                // 將 JSON 轉為 Map
                mapOf(
                    "id" to jsonObject.getString("id"),
                    "name" to jsonObject.getString("name"),
                    "address" to jsonObject.getString("address"),
                    "rating" to jsonObject.getString("rating"),
                    "info" to jsonObject.getString("info")
                )
            }
        }
    }
}