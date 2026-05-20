package com.psecurity.psblock.support

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.psecurity.psblock.AppLanguageManager
import com.psecurity.psblock.R

class SupportActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguageManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_support)

        findViewById<TextView>(R.id.tvSupportPixType)?.text = SupportConfig.PIX_KEY_TYPE
        findViewById<TextView>(R.id.tvSupportPixKey)?.text = SupportConfig.PIX_KEY

        findViewById<Button>(R.id.btnSupportCopyPix)?.setOnClickListener {
            copyPixKey()
        }

        findViewById<Button>(R.id.btnSupportPaypal)?.setOnClickListener {
            openExternal(SupportConfig.PAYPAL_URL)
        }

        val sponsorsButton = findViewById<Button>(R.id.btnSupportGithubSponsors)
        if (SupportConfig.GITHUB_SPONSORS_URL.isBlank()) {
            sponsorsButton?.visibility = View.GONE
        } else {
            sponsorsButton?.setOnClickListener {
                openExternal(SupportConfig.GITHUB_SPONSORS_URL)
            }
        }

        findViewById<Button>(R.id.btnSupportBack)?.setOnClickListener {
            finish()
        }
    }

    private fun copyPixKey() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(
            ClipData.newPlainText(SupportConfig.PIX_LABEL, SupportConfig.PIX_KEY)
        )
        Toast.makeText(this, getString(R.string.support_pix_copied), Toast.LENGTH_SHORT).show()
    }

    private fun openExternal(url: String) {
        if (url.isBlank()) {
            Toast.makeText(this, getString(R.string.support_unavailable), Toast.LENGTH_SHORT).show()
            return
        }

        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            Toast.makeText(this, getString(R.string.support_no_browser), Toast.LENGTH_SHORT).show()
        }
    }
}
