package tw.com.kooler.booking

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import android.widget.ImageButton
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

class BookingListActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_booking_list)

        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        val backButton = findViewById<ImageButton>(R.id.buttonBack)
        backButton.setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        fetchBookingList()
    }

    private fun fetchBookingList() {
        val url = "http://34.63.109.78/api/hotels/all_bookings"

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(url).get().build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
                    val jsonStr = response.body?.string() ?: "[]"
                    val jsonArray = JSONArray(jsonStr)

                    val bookingList = mutableListOf<Booking>()
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        bookingList.add(
                            Booking(
                                booking_id = obj.getInt("booking_id"),
                                room_id = obj.optInt("room_id"),
                                check_in = obj.optString("check_in"),
                                check_out = obj.optString("check_out"),
                                guest_name = obj.optString("guest_name"),
                                guest_phone = obj.optString("guest_phone"),
                                extra_order = obj.optString("extra_order"),
                                total_price = obj.optString("total_price"),
                                special_requirement = obj.optString("special_requirement"),
                                note = obj.optString("note")
                            )
                        )
                    }

                    withContext(Dispatchers.Main) {
                        recyclerView.adapter = BookingAdapter(bookingList)
                    }
                }
            } catch (e: Exception) {
                Log.e("BookingList", "Error fetching bookings: ${e.message}")
            }
        }
    }
}
