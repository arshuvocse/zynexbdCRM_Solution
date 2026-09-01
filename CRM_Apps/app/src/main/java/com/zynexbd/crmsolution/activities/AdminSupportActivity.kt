package com.zynexbd.crmsolution.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import com.zynexbd.crmsolution.databinding.ActivityAdminSupportBinding

class AdminSupportActivity : BaseActivity() {

    private lateinit var binding: ActivityAdminSupportBinding
    private val whatsAppNumber: String = "01618888251"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminSupportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navView = binding.root.findViewById<com.google.android.material.navigation.NavigationView>(com.zynexbd.crmsolution.R.id.navigationView)
        setupAdminDrawer(binding.drawerLayout, navView, binding.buttonBack, com.zynexbd.crmsolution.R.id.nav_support)

        binding.textWhatsAppNumber.text = "+880 1618-888251 (WhatsApp Only)"

        // WhatsApp Direct Click
        binding.buttonWhatsAppDirect.setOnClickListener {
            openWhatsAppChat()
        }
    }

    private fun openWhatsAppChat() {
        try {
            val clean = whatsAppNumber.replace("+", "").replace(" ", "").replace("-", "")
            val formatted = if (clean.startsWith("0")) "88$clean" else clean
            val message = Uri.encode("Hello Zynex Support, I need assistance with my Live Tracking system.")
            val url = "https://api.whatsapp.com/send?phone=$formatted&text=$message"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "WhatsApp app is not installed", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(androidx.core.view.GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}
