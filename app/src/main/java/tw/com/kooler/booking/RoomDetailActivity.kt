package tw.com.kooler.booking

import android.os.Bundle
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions

class RoomDetailActivity : AppCompatActivity() {
    private lateinit var indicatorsLayout: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_room_detail)

        val viewPager = findViewById<ViewPager2>(R.id.viewPagerRoomImages)
        val textRoomType = findViewById<TextView>(R.id.textRoomTypeDetail)
        val textPrice = findViewById<TextView>(R.id.textPriceDetail)
        val textFeatures = findViewById<TextView>(R.id.textFeaturesDetail)
        indicatorsLayout = findViewById(R.id.layoutIndicators)

        val type = intent.getStringExtra("type")
        val price = intent.getStringExtra("price")
        val features = intent.getStringExtra("features")
        val pictures = intent.getStringArrayListExtra("pictures") ?: arrayListOf()

        val buttonBack = findViewById<ImageButton>(R.id.buttonBack)
        buttonBack.setOnClickListener {
            finish()
        }

        textRoomType.text = type
        textPrice.text = "NT$$price"
        textFeatures.text = features

        // 設定圖片滑動 Adapter
        val adapter = RoomImageAdapter(pictures)
        viewPager.adapter = adapter

        // ✅ 建立指示點
        setupIndicators(pictures.size)
        setCurrentIndicator(0)

        // ✅ 當頁面滑動時更新指示點
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                setCurrentIndicator(position)
            }
        })
    }

    // 建立指示點
    private fun setupIndicators(count: Int) {
        val indicators = Array(count) { ImageView(this) }
        val layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        layoutParams.setMargins(8, 0, 8, 0)

        for (i in indicators.indices) {
            indicators[i] = ImageView(this)
            indicators[i].setImageResource(R.drawable.indicator_inactive)
            indicators[i].layoutParams = layoutParams
            indicatorsLayout.addView(indicators[i])
        }
    }

    // 更新目前指示點
    private fun setCurrentIndicator(index: Int) {
        val count = indicatorsLayout.childCount
        for (i in 0 until count) {
            val imageView = indicatorsLayout.getChildAt(i) as ImageView
            imageView.setImageResource(
                if (i == index) R.drawable.indicator_active
                else R.drawable.indicator_inactive
            )
        }
    }
}
