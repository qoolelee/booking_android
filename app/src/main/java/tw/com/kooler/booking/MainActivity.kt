package tw.com.kooler.booking

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.widget.Button
import android.widget.EditText
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: MessageAdapter
    private val messageList = mutableListOf<Message>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recyclerView = findViewById(R.id.recyclerView)
        val editText = findViewById<EditText>(R.id.editTextMessage)
        val buttonSend = findViewById<Button>(R.id.buttonSend)

        adapter = MessageAdapter(messageList)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // 初始假資料
        messageList.add(Message("嗨！我是系統", false, getTime()))
        messageList.add(Message("哈囉！我是使用者", true, getTime()))
        adapter.notifyDataSetChanged()

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
    }

    private fun getTime(): String {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        return sdf.format(Date())
    }
}
