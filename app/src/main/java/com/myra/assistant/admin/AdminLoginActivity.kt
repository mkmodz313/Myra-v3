package com.myra.assistant.admin

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.myra.assistant.R
import com.myra.assistant.wallpaper.CloudConfig
import java.util.concurrent.Executors

/** Email + password login. After a successful login the wallpaper upload / manage panel opens. */
class AdminLoginActivity : AppCompatActivity() {

    private val io = Executors.newSingleThreadExecutor()
    private val ui = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (AdminApi.isSignedIn()) { openPanel(); return }

        setContentView(R.layout.activity_admin_login)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.adminLoginRoot)) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, b.top, 0, b.bottom)
            insets
        }

        val email = findViewById<EditText>(R.id.adminEmail)
        val pass = findViewById<EditText>(R.id.adminPassword)
        val err = findViewById<TextView>(R.id.adminLoginError)
        val btn = findViewById<Button>(R.id.adminLoginBtn)
        val prog = findViewById<ProgressBar>(R.id.adminLoginProgress)
        email.setText(CloudConfig.ADMIN_EMAIL)

        fun doLogin() {
            val e = email.text.toString().trim()
            val p = pass.text.toString()
            if (e.isEmpty() || p.isEmpty()) {
                err.text = "Email aur password dono likhein."
                err.visibility = View.VISIBLE
                return
            }
            err.visibility = View.GONE
            btn.isEnabled = false
            prog.visibility = View.VISIBLE
            io.execute {
                try {
                    AdminApi.signIn(e, p)
                    ui.post { pass.setText(""); openPanel() }
                } catch (ex: Exception) {
                    ui.post {
                        btn.isEnabled = true
                        prog.visibility = View.GONE
                        err.text = ex.message ?: "Login failed."
                        err.visibility = View.VISIBLE
                    }
                }
            }
        }
        btn.setOnClickListener { doLogin() }
        pass.setOnEditorActionListener { _, id, _ -> if (id == EditorInfo.IME_ACTION_DONE) { doLogin(); true } else false }
    }

    private fun openPanel() {
        startActivity(Intent(this, AdminPanelActivity::class.java))
        finish()
    }

    override fun onDestroy() {
        ui.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
