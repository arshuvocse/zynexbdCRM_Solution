package com.zynexbd.crmsolution.activities

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.zynexbd.crmsolution.R
import com.zynexbd.crmsolution.adapters.OfficeLocationAdapter
import com.zynexbd.crmsolution.databinding.ActivityAdminOfficeLocationsBinding
import com.zynexbd.crmsolution.databinding.DialogEditOfficeLocationBinding
import com.zynexbd.crmsolution.models.CreateOfficeLocationRequest
import com.zynexbd.crmsolution.models.OfficeLocation
import com.zynexbd.crmsolution.models.UpdateOfficeLocationRequest
import com.zynexbd.crmsolution.viewmodel.UserManagementViewModel

class AdminOfficeLocationsActivity : BaseActivity() {

    private lateinit var binding: ActivityAdminOfficeLocationsBinding
    private lateinit var viewModel: UserManagementViewModel
    private lateinit var adapter: OfficeLocationAdapter
    private var allOffices: List<OfficeLocation> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminOfficeLocationsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[UserManagementViewModel::class.java]

        val navView = binding.root.findViewById<com.google.android.material.navigation.NavigationView>(R.id.navigationView)
        setupAdminDrawer(binding.drawerLayout, navView, binding.buttonBack, R.id.nav_offices)

        adapter = OfficeLocationAdapter(
            onEdit = { office -> showEditOfficeDialog(office) },
            onToggle = { office -> toggleOfficeStatus(office) }
        )

        binding.recyclerOffices.layoutManager = LinearLayoutManager(this)
        binding.recyclerOffices.adapter = adapter

        binding.buttonAddOffice.setOnClickListener {
            showAddOfficeDialog()
        }

        binding.editSearchOffice.addTextChangedListener { text ->
            filterOffices(text?.toString())
        }

        viewModel.allOfficeLocations.observe(this) { list ->
            allOffices = list ?: emptyList()
            filterOffices(binding.editSearchOffice.text?.toString())
        }

        viewModel.error.observe(this) { err ->
            if (!err.isNullOrBlank()) Toast.makeText(this, err, Toast.LENGTH_SHORT).show()
        }

        viewModel.loadOfficeLocations(all = true)
    }

    private fun filterOffices(query: String?) {
        val q = query?.trim()?.lowercase() ?: ""
        val filtered = if (q.isEmpty()) {
            allOffices
        } else {
            allOffices.filter {
                it.name.lowercase().contains(q) || (it.address != null && it.address.lowercase().contains(q))
            }
        }
        adapter.submitList(filtered)
        binding.textEmptyState.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showAddOfficeDialog() {
        val dialogBinding = DialogEditOfficeLocationBinding.inflate(LayoutInflater.from(this))

        AlertDialog.Builder(this)
            .setTitle("নতুন অফিস যোগ করুন (Add Office)")
            .setView(dialogBinding.root)
            .setPositiveButton("যোগ করুন (Add)") { _, _ ->
                val name = dialogBinding.editOfficeName.text.toString().trim()
                val latStr = dialogBinding.editOfficeLat.text.toString().trim()
                val lngStr = dialogBinding.editOfficeLng.text.toString().trim()
                val radStr = dialogBinding.editOfficeRadius.text.toString().trim()
                val addr = dialogBinding.editOfficeAddress.text.toString().trim()

                if (name.isBlank() || latStr.isBlank() || lngStr.isBlank()) {
                    Toast.makeText(this, "অফিসের নাম এবং ল্যাটিটিউড/লঙ্গিটিউড দেওয়া আবশ্যক।", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val lat = latStr.toDoubleOrNull() ?: 0.0
                val lng = lngStr.toDoubleOrNull() ?: 0.0
                val rad = radStr.toDoubleOrNull() ?: 200.0

                val request = CreateOfficeLocationRequest(name, lat, lng, rad, addr.ifBlank { null })
                viewModel.createOfficeLocation(request) { success ->
                    val msg = if (success) "নতুন অফিস লোকেশন সফলভাবে যোগ করা হয়েছে!" else "অফিস যোগ করতে ব্যর্থ হয়েছে।"
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("বাতিল", null)
            .show()
    }

    private fun showEditOfficeDialog(office: OfficeLocation) {
        val dialogBinding = DialogEditOfficeLocationBinding.inflate(LayoutInflater.from(this))
        dialogBinding.editOfficeName.setText(office.name)
        dialogBinding.editOfficeLat.setText(office.latitude.toString())
        dialogBinding.editOfficeLng.setText(office.longitude.toString())
        dialogBinding.editOfficeRadius.setText(office.radiusMeters.toInt().toString())
        dialogBinding.editOfficeAddress.setText(office.address ?: "")

        AlertDialog.Builder(this)
            .setTitle("অফিস এডিট করুন (Edit Office)")
            .setView(dialogBinding.root)
            .setPositiveButton("আপডেট (Update)") { _, _ ->
                val name = dialogBinding.editOfficeName.text.toString().trim()
                val latStr = dialogBinding.editOfficeLat.text.toString().trim()
                val lngStr = dialogBinding.editOfficeLng.text.toString().trim()
                val radStr = dialogBinding.editOfficeRadius.text.toString().trim()
                val addr = dialogBinding.editOfficeAddress.text.toString().trim()

                if (name.isBlank() || latStr.isBlank() || lngStr.isBlank()) {
                    Toast.makeText(this, "অফিসের নাম এবং ল্যাটিটিউড/লঙ্গিটিউড দেওয়া আবশ্যক।", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val lat = latStr.toDoubleOrNull() ?: office.latitude
                val lng = lngStr.toDoubleOrNull() ?: office.longitude
                val rad = radStr.toDoubleOrNull() ?: office.radiusMeters

                val request = UpdateOfficeLocationRequest(name, lat, lng, rad, addr.ifBlank { null }, office.isActive)
                viewModel.updateOfficeLocationDetails(office.id, request) { success ->
                    val msg = if (success) "অফিস লোকেশন সফলভাবে আপডেট করা হয়েছে!" else "অফিস আপডেট ব্যর্থ হয়েছে।"
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("বাতিল", null)
            .show()
    }

    private fun toggleOfficeStatus(office: OfficeLocation) {
        val newStatus = !office.isActive
        val request = UpdateOfficeLocationRequest(
            name = office.name,
            latitude = office.latitude,
            longitude = office.longitude,
            radiusMeters = office.radiusMeters,
            address = office.address,
            isActive = newStatus
        )
        viewModel.updateOfficeLocationDetails(office.id, request) { success ->
            val msg = if (success) "অফিস স্ট্যাটাস সফলভাবে পরিবর্তন করা হয়েছে।" else "অফিস স্ট্যাটাস পরিবর্তন ব্যর্থ হয়েছে।"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
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
