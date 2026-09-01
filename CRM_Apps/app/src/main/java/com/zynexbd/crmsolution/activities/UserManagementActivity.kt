package com.zynexbd.crmsolution.activities

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import com.zynexbd.crmsolution.R
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.zynexbd.crmsolution.adapters.UserListAdapter
import com.zynexbd.crmsolution.databinding.ActivityUserManagementBinding
import com.zynexbd.crmsolution.models.OfficeLocation
import com.zynexbd.crmsolution.models.User
import com.zynexbd.crmsolution.utils.LanguageManager
import com.zynexbd.crmsolution.viewmodel.UserManagementViewModel

/**
 * Admin: list users, search/filter, disable/enable, reset password, navigate to create.
 */
class UserManagementActivity : BaseActivity() {

    private lateinit var binding: ActivityUserManagementBinding
    private lateinit var viewModel: UserManagementViewModel
    private lateinit var adapter: UserListAdapter
    private var officeLocations: List<OfficeLocation> = emptyList()
    private var allUsers: List<User> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserManagementBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[UserManagementViewModel::class.java]

        adapter = UserListAdapter(
            onToggleActive = { user -> viewModel.toggleActive(user) },
            onResetPassword = { user -> showResetPasswordDialog(user.id) },
            onViewLocation = { user -> openUserLocationInGoogleMaps(user) },
            onViewCustomers = { user ->
                val intent = Intent(this, CustomerListActivity::class.java).apply {
                    putExtra("EXTRA_USER_ID", user.id)
                    putExtra("EXTRA_USER_NAME", user.name.ifBlank { user.username })
                }
                startActivity(intent)
            },
            onViewFollowUps = { user ->
                val intent = Intent(this, FollowUpsActivity::class.java).apply {
                    putExtra("EXTRA_USER_ID", user.id)
                    putExtra("EXTRA_USER_NAME", user.name.ifBlank { user.username })
                }
                startActivity(intent)
            },
            onCallUser = { user ->
                showCallOptionDialog(user)
            },
            onResetDevice = { user ->
                showResetDeviceDialog(user)
            },
            onEditOffice = { user ->
                showEditOfficeDialog(user)
            }
        )
        binding.recyclerUsers.layoutManager = LinearLayoutManager(this)
        binding.recyclerUsers.adapter = adapter

        val navView = binding.root.findViewById<com.google.android.material.navigation.NavigationView>(R.id.navigationView)
        setupAdminDrawer(binding.drawerLayout, navView, binding.buttonBack, R.id.nav_users)

        binding.buttonAddUser.setOnClickListener {
            val q = viewModel.quota.value
            if (q != null && q.isLimitReached) {
                val isEn = LanguageManager.isEnglish(this)
                AlertDialog.Builder(this)
                    .setTitle(if (isEn) "User Limit Reached" else "ইউজার লিমিট পূর্ণ হয়েছে")
                    .setMessage(
                        if (isEn)
                            "You have reached the maximum allowed users for this admin account (${q.maxUserLimit}/${q.maxUserLimit}). Please contact system administrator to increase your quota."
                        else
                            "আপনার অ্যাডমিন অ্যাকাউন্টের সর্বোচ্চ নির্ধারিত ইউজার সংখ্যা (${q.maxUserLimit}/${q.maxUserLimit} জন) পূর্ণ হয়ে গেছে। কোটা বাড়াতে অ্যাডমিন/সাপোর্ট টিমের সাথে যোগাযোগ করুন।"
                    )
                    .setPositiveButton(if (isEn) "OK" else "ঠিক আছে", null)
                    .show()
            } else {
                startActivity(Intent(this, CreateUserActivity::class.java))
            }
        }

        // Search text box filtering
        binding.editSearchUser.addTextChangedListener { text ->
            filterUsers(text?.toString())
        }
        binding.buttonClearSearch.setOnClickListener {
            binding.editSearchUser.setText("")
        }

        viewModel.users.observe(this) { list ->
            allUsers = list ?: emptyList()
            filterUsers(binding.editSearchUser.text?.toString())
        }

        viewModel.quota.observe(this) { quota ->
            val isEn = LanguageManager.isEnglish(this)
            binding.textQuotaTitle.text = if (isEn) "👤 User Creation Quota" else "👤 ইউজার তৈরির কোটা"
            binding.textQuotaCount.text = "${quota.usedUserCount} / ${quota.maxUserLimit} ${if (isEn) "Active" else "সক্রিয়"}"
            binding.progressQuota.max = quota.maxUserLimit.coerceAtLeast(1)
            binding.progressQuota.progress = quota.usedUserCount

            if (quota.isLimitReached) {
                binding.textQuotaRemaining.text = if (isEn)
                    "⚠️ Maximum limit reached (${quota.maxUserLimit}/${quota.maxUserLimit}). Contact support to increase."
                else
                    "⚠️ সর্বোচ্চ কোটা পূর্ণ (${quota.maxUserLimit}/${quota.maxUserLimit} জন)। কোটা বাড়াতে যোগাযোগ করুন।"
                binding.textQuotaRemaining.setTextColor(Color.parseColor("#EF4444"))
                binding.textQuotaCount.setTextColor(Color.parseColor("#EF4444"))
            } else {
                binding.textQuotaRemaining.text = if (isEn)
                    "Remaining: ${quota.remainingUserCount} user slot(s) available"
                else
                    "অবশিষ্ট: ${quota.remainingUserCount} জন নতুন ইউজার যোগ করতে পারবেন"
                binding.textQuotaRemaining.setTextColor(Color.parseColor("#64748B"))
                binding.textQuotaCount.setTextColor(Color.parseColor("#4F46E5"))
            }
        }
        viewModel.officeLocations.observe(this) { officeLocations = it }
        viewModel.error.observe(this) { message ->
            if (!message.isNullOrBlank()) Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
        viewModel.loadOfficeLocations()
        viewModel.loadUsers()
    }

    private fun filterUsers(query: String?) {
        val q = query?.trim()?.lowercase() ?: ""
        if (q.isEmpty()) {
            adapter.submitList(allUsers)
            binding.buttonClearSearch.visibility = View.GONE
        } else {
            binding.buttonClearSearch.visibility = View.VISIBLE
            val filtered = allUsers.filter { u ->
                u.username.lowercase().contains(q) ||
                u.name.lowercase().contains(q) ||
                (u.phoneNumber != null && u.phoneNumber.lowercase().contains(q)) ||
                u.role.lowercase().contains(q) ||
                (u.officeLocationName != null && u.officeLocationName.lowercase().contains(q)) ||
                (u.deviceModel != null && u.deviceModel.lowercase().contains(q))
            }
            adapter.submitList(filtered)
        }
    }

    private fun showEditOfficeDialog(user: User) {
        if (officeLocations.isEmpty()) {
            Toast.makeText(this, "অফিস লোকেশন লোড হয়নি, একটু পর আবার চেষ্টা করুন।", Toast.LENGTH_SHORT).show()
            viewModel.loadOfficeLocations()
            return
        }

        if (user.role.equals("Admin", ignoreCase = true)) {
            val currentAssignedIds = (user.assignedOfficeLocationIds ?: emptyList()).toMutableList()
            if (currentAssignedIds.isEmpty() && user.officeLocationId != null) {
                currentAssignedIds.add(user.officeLocationId)
            }

            val officeNames = officeLocations.map { it.name }.toTypedArray()
            val checkedItems = BooleanArray(officeLocations.size) { idx ->
                currentAssignedIds.contains(officeLocations[idx].id)
            }

            AlertDialog.Builder(this)
                .setTitle("${user.name.ifBlank { user.username }} (Admin) — অফিস অ্যাক্সেস")
                .setMultiChoiceItems(officeNames, checkedItems) { _, which, isChecked ->
                    val officeId = officeLocations[which].id
                    if (isChecked) {
                        if (!currentAssignedIds.contains(officeId)) currentAssignedIds.add(officeId)
                    } else {
                        currentAssignedIds.remove(officeId)
                    }
                }
                .setPositiveButton("সেভ করুন (Save)") { _, _ ->
                    viewModel.updateAdminOffices(user, currentAssignedIds) { success ->
                        val msg = if (success) "অ্যাডমিন অফিস অ্যাক্সেস সফলভাবে আপডেট করা হয়েছে।" else "অফিস আপডেট করতে ব্যর্থ হয়েছে।"
                        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("বাতিল", null)
                .show()
        } else {
            val names = officeLocations.map { it.name }.toTypedArray()
            val currentIndex = officeLocations.indexOfFirst { it.id == user.officeLocationId }.let { if (it >= 0) it else 0 }

            val spinner = Spinner(this).apply {
                adapter = ArrayAdapter(this@UserManagementActivity, android.R.layout.simple_spinner_dropdown_item, names)
                setSelection(currentIndex)
            }

            AlertDialog.Builder(this)
                .setTitle("${user.name.ifBlank { user.username }} — অফিস পরিবর্তন করুন")
                .setView(spinner)
                .setPositiveButton("সেভ করুন") { _, _ ->
                    val selected = officeLocations.getOrNull(spinner.selectedItemPosition) ?: return@setPositiveButton
                    viewModel.updateOfficeLocation(user, selected.id) { success ->
                        val msg = if (success) "অফিস সফলভাবে পরিবর্তন করা হয়েছে।" else "অফিস পরিবর্তন করতে ব্যর্থ হয়েছে।"
                        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("বাতিল", null)
                .show()
        }
    }

    private fun openUserLocationInGoogleMaps(user: com.zynexbd.crmsolution.models.User) {
        Toast.makeText(this, "${user.username} এর লোকেশন আনা হচ্ছে...", Toast.LENGTH_SHORT).show()
        viewModel.getUserLocation(user.id) { loc ->
            if (loc != null && loc.latitude != null && loc.longitude != null) {
                val displayName = user.name.ifBlank { user.username }
                val uri = android.net.Uri.parse("geo:${loc.latitude},${loc.longitude}?q=${loc.latitude},${loc.longitude}(${android.net.Uri.encode(displayName)})")
                val mapIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
                mapIntent.setPackage("com.google.android.apps.maps")
                try {
                    startActivity(mapIntent)
                } catch (e: Exception) {
                    val genericIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
                    startActivity(genericIntent)
                }
            } else {
                Toast.makeText(this, "${user.username} এর কোনো লোকেশন রেকর্ড এখনো নেই।", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadUsers()
    }

    private fun showResetPasswordDialog(userId: Int) {
        val input = EditText(this).apply { hint = "নতুন পাসওয়ার্ড (কমপক্ষে ৬ অক্ষর)" }
        AlertDialog.Builder(this)
            .setTitle("পাসওয়ার্ড রিসেট করুন")
            .setView(input)
            .setPositiveButton("রিসেট") { _, _ ->
                val newPassword = input.text.toString()
                if (newPassword.isBlank()) return@setPositiveButton
                if (newPassword.length < 6) {
                    Toast.makeText(this, "পাসওয়ার্ড কমপক্ষে ৬ অক্ষরের হতে হবে।", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                viewModel.resetPassword(userId, newPassword) { success ->
                    val msg = if (success) "পাসওয়ার্ড সফলভাবে রিসেট করা হয়েছে।" else "পাসওয়ার্ড রিসেট করতে ব্যর্থ হয়েছে।"
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("বাতিল", null)
            .show()
    }

    private fun showCallOptionDialog(user: com.zynexbd.crmsolution.models.User) {
        val phone = user.phoneNumber
        if (phone.isNullOrBlank()) {
            Toast.makeText(this, "${user.name.ifBlank { user.username }} এর কোনো মোবাইল নম্বর সেভ করা নেই।", Toast.LENGTH_SHORT).show()
            return
        }

        val options = arrayOf(
            "📞 Cellular SIM Call (সাধারণ ডায়াল কল)",
            "💬 WhatsApp Call / Message (হোয়াটসঅ্যাপ কল/মেসেজ)",
            "📋 Copy Number (নম্বর কপি করুন)"
        )

        AlertDialog.Builder(this)
            .setTitle("যোগাযোগের মাধ্যম বেছে নিন (${user.name.ifBlank { user.username }})")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        try {
                            val intent = Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:$phone"))
                            startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(this, "কল করা সম্ভব হচ্ছে না", Toast.LENGTH_SHORT).show()
                        }
                    }
                    1 -> {
                        try {
                            val cleanNumber = phone.replace("+", "").replace(" ", "").replace("-", "")
                            val formatted = if (cleanNumber.startsWith("0")) "88$cleanNumber" else cleanNumber
                            val url = "https://api.whatsapp.com/send?phone=$formatted"
                            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                            startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(this, "হোয়াটসঅ্যাপ অ্যাপটি পাওয়া যায়নি", Toast.LENGTH_SHORT).show()
                        }
                    }
                    2 -> {
                        val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("Phone Number", phone)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(this, "নম্বর কপি করা হয়েছে: $phone", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("বাতিল", null)
            .show()
    }

    private fun showResetDeviceDialog(user: com.zynexbd.crmsolution.models.User) {
        val isEn = LanguageManager.isEnglish(this)
        val deviceName = user.deviceModel?.takeIf { it.isNotBlank() } ?: "নিবন্ধিত ডিভাইস"
        val displayName = user.name.ifBlank { user.username }

        AlertDialog.Builder(this)
            .setTitle(if (isEn) "🔄 Reset Device Binding" else "🔄 ডিভাইস বাইন্ডিং রিসেট করুন")
            .setMessage(
                if (isEn)
                    "Are you sure you want to unbind '$displayName' from device '$deviceName'?\n\nThe employee will be able to log in on any new device on their next login."
                else
                    "আপনি কি নিশ্চিতভাবে '$displayName' এর ডিভাইস '$deviceName' আনবাইন্ড / রিসেট করতে চান?\n\nরিসেট করার পর ইউজার পরবর্তী লগইনে যেকোনো নতুন ফোনে লগইন করতে পারবে।"
            )
            .setPositiveButton(if (isEn) "Yes, Reset" else "হ্যাঁ, রিসেট করুন") { _, _ ->
                Toast.makeText(this, "$displayName এর ডিভাইস রিসেট করা হচ্ছে...", Toast.LENGTH_SHORT).show()
                viewModel.resetUserDevice(user.id) { success ->
                    val msg = if (success) {
                        if (isEn) "Device binding reset successfully." else "ডিভাইস সফলভাবে রিসেট করা হয়েছে।"
                    } else {
                        if (isEn) "Failed to reset device." else "ডিভাইস রিসেট ব্যর্থ হয়েছে।"
                    }
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(if (isEn) "Cancel" else "বাতিল", null)
            .show()
    }

    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(androidx.core.view.GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}
