package com.zynexbd.crmsolution.activities

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.ViewModelProvider
import com.zynexbd.crmsolution.R
import com.zynexbd.crmsolution.databinding.ActivityCreateUserBinding
import com.zynexbd.crmsolution.models.CreateUserRequest
import com.zynexbd.crmsolution.models.OfficeLocation
import com.zynexbd.crmsolution.viewmodel.UserManagementViewModel

class CreateUserActivity : BaseActivity() {

    private lateinit var binding: ActivityCreateUserBinding
    private lateinit var viewModel: UserManagementViewModel
    private var officeLocations: List<OfficeLocation> = emptyList()
    private val selectedAdminOfficeIds = mutableListOf<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateUserBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[UserManagementViewModel::class.java]

        binding.buttonBack.setOnClickListener { finish() }

        binding.layoutSingleOffice.visibility = View.VISIBLE
        binding.layoutMultiOffice.visibility = View.GONE

        val roleOptions = listOf("User (ফিল্ড অফিসার)", "Manager (সিআরএম ম্যানেজার)")
        binding.spinnerRole.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, roleOptions)

        viewModel.officeLocations.observe(this) { locations ->
            officeLocations = locations
            val names = mutableListOf("অফিস লোকেশন সিলেক্ট করুন (Select Office)").apply { addAll(locations.map { it.name }) }
            binding.spinnerOfficeLocation.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)
        }
        viewModel.loadOfficeLocations()

        binding.buttonSave.setOnClickListener {
            val selectedIndex = binding.spinnerOfficeLocation.selectedItemPosition
            if (selectedIndex <= 0 || selectedIndex - 1 !in officeLocations.indices) {
                showErrorDialog("⚠️ অফিস লোকেশন বেছে নিন", "নতুন ফিল্ড অফিসারের জন্য অবশ্যই একটি নির্দিষ্ট অফিস লোকেশন সিলেক্ট করতে হবে।")
                return@setOnClickListener
            }
            val singleOfficeLocationId = officeLocations[selectedIndex - 1].id

            val selectedRole = if (binding.spinnerRole.selectedItemPosition == 1) "Manager" else "User"
            val request = CreateUserRequest(
                name = binding.editFullName.text.toString().trim(),
                username = binding.editUsername.text.toString().trim(),
                password = binding.editPassword.text.toString(),
                role = selectedRole,
                phoneNumber = binding.editMobileNumber.text.toString().trim(),
                officeLocationId = singleOfficeLocationId,
                assignedOfficeLocationIds = null
            )

            val mobile = binding.editMobileNumber.text.toString().trim()
            if (mobile.isNotBlank() && (mobile.length != 11 || !mobile.matches(Regex("""^01\d{9}$""")))) {
                showErrorDialog("⚠️ সঠিক মোবাইল নম্বর দিন", "মোবাইল নম্বরটি অবশ্যই ১১ ডিজিটের হতে হবে (যেমন: 017xxxxxxxx)।")
                binding.editMobileNumber.requestFocus()
                return@setOnClickListener
            }

            if (request.username.isBlank() || request.password.isBlank() || request.name.isBlank()) {
                showErrorDialog("⚠️ তথ্য অসম্পূর্ণ", "ইউজারনেম, পাসওয়ার্ড এবং পুরো নাম সবকয়টি ফিল্ড পূরণ করা আবশ্যক।")
                return@setOnClickListener
            }
            if (request.password.length < 6) {
                showErrorDialog("⚠️ দুর্বল পাসওয়ার্ড", "পাসওয়ার্ড কমপক্ষে ৬ অক্ষরের হতে হবে।")
                return@setOnClickListener
            }

            binding.buttonSave.isEnabled = false
            binding.buttonSave.text = "ইউজার তৈরি করা হচ্ছে..."
            viewModel.createUser(request) { success ->
                binding.buttonSave.isEnabled = true
                binding.buttonSave.text = "SAVE USER"
                if (success) {
                    showSuccessDialog("🎉 সফলভাবে সংরক্ষিত!", "নতুন ইউজার একাউন্টটি সফলভাবে তৈরি এবং সিস্টেমে যুক্ত করা হয়েছে।") {
                        finish()
                    }
                } else {
                    val rawError = viewModel.error.value
                    if (rawError?.contains("লিমিট", ignoreCase = true) == true || rawError?.contains("limit", ignoreCase = true) == true) {
                        val errorMsg = rawError.takeIf { it.isNotBlank() }
                            ?: "আপনার অ্যাকাউন্টের সর্বোচ্চ নির্ধারিত ইউজার সংখ্যা পূর্ণ হয়ে গেছে। কোটা বাড়াতে সাপোর্ট টিমের সাথে যোগাযোগ করুন।"
                        showErrorDialog("⚠️ ইউজার কোটা পূর্ণ", errorMsg)
                    } else {
                        val banglaMsg = translateErrorMessage(rawError)
                        showErrorDialog("❌ ইউজার তৈরি সম্ভব হয়নি", banglaMsg)
                    }
                }
            }
        }
    }

    private fun showAdminOfficeSelectionDialog() {
        if (officeLocations.isEmpty()) {
            Toast.makeText(this, "কোনো অফিস লোকেশন পাওয়া যায়নি", Toast.LENGTH_SHORT).show()
            return
        }

        val officeNames = officeLocations.map { it.name }.toTypedArray()
        val checkedItems = BooleanArray(officeLocations.size) { index ->
            selectedAdminOfficeIds.contains(officeLocations[index].id)
        }

        AlertDialog.Builder(this)
            .setTitle("অফিস অ্যাক্সেস নির্বাচন করুন (Select Offices)")
            .setMultiChoiceItems(officeNames, checkedItems) { _, which, isChecked ->
                val officeId = officeLocations[which].id
                if (isChecked) {
                    if (!selectedAdminOfficeIds.contains(officeId)) {
                        selectedAdminOfficeIds.add(officeId)
                    }
                } else {
                    selectedAdminOfficeIds.remove(officeId)
                }
            }
            .setPositiveButton("সম্পন্ন (Done)") { dialog, _ ->
                dialog.dismiss()
                updateSelectedOfficesText()
            }
            .setNegativeButton("বাতিল (Cancel)", null)
            .show()
    }

    private fun updateSelectedOfficesText() {
        val selectedNames = officeLocations
            .filter { selectedAdminOfficeIds.contains(it.id) }
            .map { it.name }

        if (selectedNames.isEmpty()) {
            binding.textSelectedOffices.text = ""
            binding.textSelectedOffices.hint = "অফিস নির্বাচন করতে এখানে ট্যাপ করুন..."
        } else {
            binding.textSelectedOffices.text = selectedNames.joinToString(", ")
        }
    }

    private fun showSuccessDialog(title: String, message: String, onDismiss: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setIcon(android.R.drawable.ic_dialog_info)
            .setPositiveButton("ঠিক আছে") { dialog, _ ->
                dialog.dismiss()
                onDismiss()
            }
            .setCancelable(false)
            .show()
    }

    private fun showErrorDialog(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton("ঠিক আছে") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun translateErrorMessage(rawError: String?): String {
        if (rawError.isNullOrBlank()) return "ইউজার তৈরি করতে ব্যর্থ হয়েছে।"
        return when {
            rawError.contains("10 users", ignoreCase = true) || rawError.contains("limit", ignoreCase = true) -> "সর্বোচ্চ নির্ধারিত ইউজার সংখ্যার বেশি তৈরি করা যাবে না।"
            rawError.contains("already exists", ignoreCase = true) -> "এই ইউজারনেমটি আগে থেকেই ব্যবহার করা হয়েছে।"
            rawError.contains("MinimumLength", ignoreCase = true) || rawError.contains("6 characters", ignoreCase = true) -> "পাসওয়ার্ড কমপক্ষে ৬ অক্ষরের হতে হবে।"
            rawError.contains("Unauthorized", ignoreCase = true) -> "অনুমতি নেই বা সেসন মেয়াদের বাইরে।"
            rawError.contains("Connection", ignoreCase = true) || rawError.contains("Network", ignoreCase = true) -> "সারভারে কানেক্ট করা যাচ্ছে না। নেটওয়ার্ক সংযোগ চেক করুন।"
            else -> rawError
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadOfficeLocations()
    }
}
