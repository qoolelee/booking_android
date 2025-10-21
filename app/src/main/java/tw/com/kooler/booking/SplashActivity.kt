package tw.com.kooler.booking

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject
import java.io.IOException

class SplashActivity : AppCompatActivity() {

    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val sharedPref = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        val user = sharedPref.getString("user", null)
        val pwd = sharedPref.getString("pwd", null)

        if (user != null && pwd != null) {
            // 已有登入資訊，自動登入
            autoLogin(user, pwd)
        } else {
            // 沒有登入資訊，顯示使用者同意書
            showUserAgreementDialog()
        }
    }

    private fun showUserAgreementDialog() {
        val agreementText = getString(R.string.user_agreement_content).trimIndent()

        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.user_agreement_title))
            .setMessage(agreementText)
            .setCancelable(false)
            .setPositiveButton(getString(R.string.agree)) { _, _ ->
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
            .setNegativeButton(getString(R.string.disagree)) { _, _ ->
                finish()
            }
            .create()

        dialog.show()
    }

    private fun autoLogin(user: String, pwd: String) {
        val json = JSONObject().apply {
            put("user", user)
            put("pwd", pwd)
        }

        val body = RequestBody.create(
            "application/json; charset=utf-8".toMediaTypeOrNull(),
            json.toString()
        )

        val request = Request.Builder()
            .url("http://34.63.109.78/api/hotels/login")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    startActivity(Intent(this@SplashActivity, LoginActivity::class.java))
                    finish()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                if (responseBody != null) {
                    val jsonResponse = JSONObject(responseBody)
                    val message = jsonResponse.optString("message")
                    runOnUiThread {
                        if (message == "success") {
                            loginSuccess()
                        } else {
                            startActivity(Intent(this@SplashActivity, LoginActivity::class.java))
                            finish()
                        }
                    }
                } else {
                    runOnUiThread {
                        startActivity(Intent(this@SplashActivity, LoginActivity::class.java))
                        finish()
                    }
                }
            }
        })
    }

    private fun loginSuccess() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
