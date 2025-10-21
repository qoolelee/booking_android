package tw.com.kooler.booking

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject
import java.io.IOException
import androidx.core.content.edit

class LoginActivity : AppCompatActivity() {

    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val editUser = findViewById<EditText>(R.id.editUser)
        val editPassword = findViewById<EditText>(R.id.editPassword)
        val buttonLogin = findViewById<Button>(R.id.buttonLogin)

        buttonLogin.setOnClickListener {
            val email = editUser.text.toString().trim()
            val password = editPassword.text.toString().trim()

            // 1️⃣ 檢查 email 格式
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                showAlert("Invalid Email Format", "Please enter a valid email address.")
                return@setOnClickListener
            }

            // 2️⃣ 發送 POST 請求
            loginRequest(email, password)
        }
    }

    private fun loginRequest(email: String, password: String) {
        val url = "http://34.63.109.78/api/hotels/login"
        val json = JSONObject()
        json.put("user", email)
        json.put("pwd", password)

        val body = RequestBody.create("application/json; charset=utf-8".toMediaTypeOrNull(), json.toString())
        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    showAlert("Network Error", "Failed to connect to server.")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        runOnUiThread {
                            showAlert("Server Error", "Unexpected response from server.")
                        }
                        return
                    }

                    val responseString = it.body?.string() ?: ""
                    val jsonResponse = JSONObject(responseString)
                    val message = jsonResponse.optString("message", "")
                    val reason = jsonResponse.optString("reason", "Unknown error")

                    // 3️⃣ 判斷成功與否
                    runOnUiThread {
                        if (message == "success") {
                            loginSuccess(email, password)
                        } else {
                            showAlert("Login Failed", reason)
                        }
                    }
                }
            }
        })
    }

    // 4️⃣ 登入成功後執行
    private fun loginSuccess(email: String, password: String) {
        // 儲存 user 與 pwd 到 SharedPreferences
        val prefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        prefs.edit {
            putString("user", email)
            putString("pwd", password)
        }

        // 跳轉至 MainActivity
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun showAlert(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
}
