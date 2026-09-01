package com.zynexbd.crmsolution.activities

import android.app.DatePickerDialog
import android.os.Bundle
import com.zynexbd.crmsolution.R
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.zynexbd.crmsolution.adapters.AdminPaymentAdapter
import com.zynexbd.crmsolution.databinding.ActivityPaymentManagementBinding
import com.zynexbd.crmsolution.models.AdminPaymentInfo
import com.zynexbd.crmsolution.models.UpdatePaymentDueDateRequest
import com.zynexbd.crmsolution.network.ApiClient
import com.zynexbd.crmsolution.utils.LanguageManager
import com.zynexbd.crmsolution.utils.SessionManager
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

import android.content.Intent
import android.net.Uri
import com.zynexbd.crmsolution.adapters.SubscriptionPlanAdapter
import com.zynexbd.crmsolution.models.SubscriptionPlan

/**
 * Admin Payment Management Screen.
 * Shows all admins + their payment due dates, allows updating payment due dates.
 * Only accessible to Admin role users.
 */
class PaymentManagementActivity : BaseActivity() {

    private lateinit var binding: ActivityPaymentManagementBinding
    private lateinit var adapter: AdminPaymentAdapter
    private lateinit var plansAdapter: SubscriptionPlanAdapter
    private lateinit var session: SessionManager
    private var adminSupportPhone: String = "01618888251"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPaymentManagementBinding.inflate(layoutInflater)
        setContentView(binding.root)

        session = SessionManager(this)

        // Setup Subscription Plans (Offers)
        plansAdapter = SubscriptionPlanAdapter { plan ->
            showPlanContactDialog(plan)
        }
        binding.recyclerSubscriptionPlans.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.recyclerSubscriptionPlans.adapter = plansAdapter

        adapter = AdminPaymentAdapter()

        binding.recyclerAdminPayments.layoutManager = LinearLayoutManager(this)
        binding.recyclerAdminPayments.adapter = adapter

        val navView = binding.root.findViewById<com.google.android.material.navigation.NavigationView>(R.id.navigationView)
        setupAdminDrawer(binding.drawerLayout, navView, binding.buttonBack, R.id.nav_payment)

        loadData()
    }

    private fun loadData() {
        binding.progressLoading.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val api = ApiClient.getApiService(this@PaymentManagementActivity)

                // Load my own subscription status + plans + all admin users
                val myStatusDeferred = async { api.getSubscriptionStatus() }
                val plansDeferred = async { api.getSubscriptionPlans() }
                val usersDeferred = async { api.getUsers() }

                val myStatusRes = myStatusDeferred.await()
                val plansRes = plansDeferred.await()
                val usersRes = usersDeferred.await()

                // Show Subscription Plans / Offers
                val plans = if (plansRes.isSuccessful) plansRes.body().orEmpty() else emptyList()
                if (plans.isNotEmpty()) {
                    binding.layoutPlansSection.visibility = View.VISIBLE
                    plansAdapter.setItems(plans)
                } else {
                    binding.layoutPlansSection.visibility = View.GONE
                }

                // Show current admin's subscription info
                if (myStatusRes.isSuccessful && myStatusRes.body() != null) {
                    val sub = myStatusRes.body()!!
                    if (!sub.adminPhone.isNullOrBlank()) {
                        adminSupportPhone = sub.adminPhone
                    }
                    val isEn = LanguageManager.isEnglish(this@PaymentManagementActivity)
                    binding.cardCurrentSubscription.visibility = View.VISIBLE
                    binding.textMyDueDate.text = formatUtcDate(sub.paymentDueDate) ?: "Not Set"
                    binding.textMyDaysRemaining.text = "${sub.daysRemaining} ${if (isEn) "days" else "দিন"}"
                    binding.textMyStatus.text = sub.statusText

                    when {
                        sub.isExpired -> binding.textMyDaysRemaining.setTextColor(android.graphics.Color.parseColor("#EF4444"))
                        sub.isWarningPeriod -> binding.textMyDaysRemaining.setTextColor(android.graphics.Color.parseColor("#D97706"))
                        else -> binding.textMyDaysRemaining.setTextColor(android.graphics.Color.parseColor("#059669"))
                    }
                }

                // Load all admin accounts' payment status
                val adminUsers = usersRes.body()?.filter { it.role == "Admin" } ?: emptyList()

                // Fetch subscription status for each admin in parallel
                val adminPayments = mutableListOf<AdminPaymentInfo>()
                for (admin in adminUsers) {
                    try {
                        val subRes = api.getUserSubscriptionStatus(admin.id)
                        if (subRes.isSuccessful && subRes.body() != null) {
                            val sub = subRes.body()!!
                            adminPayments.add(
                                AdminPaymentInfo(
                                    adminId = admin.id,
                                    adminName = admin.name.ifBlank { admin.username },
                                    adminUsername = admin.username,
                                    adminPhone = admin.phoneNumber,
                                    paymentDueDate = sub.paymentDueDate,
                                    daysRemaining = sub.daysRemaining,
                                    isExpired = sub.isExpired,
                                    isWarningPeriod = sub.isWarningPeriod,
                                    statusText = sub.statusText
                                )
                            )
                        } else {
                            adminPayments.add(
                                AdminPaymentInfo(
                                    adminId = admin.id,
                                    adminName = admin.name.ifBlank { admin.username },
                                    adminUsername = admin.username,
                                    adminPhone = admin.phoneNumber
                                )
                            )
                        }
                    } catch (e: Exception) {
                        adminPayments.add(
                            AdminPaymentInfo(
                                adminId = admin.id,
                                adminName = admin.name.ifBlank { admin.username },
                                adminUsername = admin.username,
                                adminPhone = admin.phoneNumber
                            )
                        )
                    }
                }

                adapter.setItems(adminPayments)
            } catch (e: Exception) {
                Toast.makeText(this@PaymentManagementActivity, "ডেটা লোড ব্যর্থ হয়েছে: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressLoading.visibility = View.GONE
            }
        }
    }

    private fun showPlanContactDialog(plan: SubscriptionPlan) {
        val isEn = LanguageManager.isEnglish(this)
        val title = if (isEn) plan.title else plan.titleBn
        val price = plan.price.toInt()

        val options = arrayOf(
            if (isEn) "📞 Call Hotline" else "📞 হটলাইনে কল করুন",
            if (isEn) "💬 WhatsApp Support" else "💬 হোয়াটসঅ্যাপে মেসেজ পাঠান"
        )

        android.app.AlertDialog.Builder(this)
            .setTitle(if (isEn) "Subscribe: $title" else "প্যাকেজ নির্বাচন: $title")
            .setMessage(
                if (isEn) "Plan: $title\nPrice: ৳$price\nDiscount: ${plan.discountPercent}%\n\nChoose how you want to contact support to activate this subscription:"
                else "প্যাকেজ: $title\nমূল্য: ৳$price\nছাড়: ${plan.discountPercent}%\n\nপ্যাকেজটি সক্রিয় করতে কীভাবে যোগাযোগ করতে চান?"
            )
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        try {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$adminSupportPhone"))
                            startActivity(intent)
                        } catch (_: Exception) {
                            Toast.makeText(this, "Unable to dial", Toast.LENGTH_SHORT).show()
                        }
                    }
                    1 -> {
                        try {
                            val clean = adminSupportPhone.replace("+", "").replace(" ", "").replace("-", "")
                            val formatted = if (clean.startsWith("0")) "88$clean" else clean
                            val msg = Uri.encode("Hello, I want to activate the ${plan.tierName} subscription plan: $title (৳$price).")
                            val url = "https://api.whatsapp.com/send?phone=$formatted&text=$msg"
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        } catch (_: Exception) {
                            Toast.makeText(this, "WhatsApp not available", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton(if (isEn) "Close" else "বন্ধ", null)
            .show()
    }

    private fun formatUtcDate(dateStr: String?): String? {
        if (dateStr.isNullOrBlank()) return null
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val date = parser.parse(dateStr) ?: return dateStr.take(10)
            SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(date)
        } catch (e: Exception) {
            dateStr.take(10)
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
