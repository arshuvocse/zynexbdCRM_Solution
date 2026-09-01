package com.zynexbd.crmsolution.activities

import android.os.Bundle
import com.zynexbd.crmsolution.R
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.zynexbd.crmsolution.adapters.LeaveApplicationAdapter
import com.zynexbd.crmsolution.databinding.ActivityAdminLeaveBinding
import com.zynexbd.crmsolution.viewmodel.AdminLeaveViewModel

class AdminLeaveActivity : BaseActivity() {

    private lateinit var binding: ActivityAdminLeaveBinding
    private lateinit var viewModel: AdminLeaveViewModel
    private lateinit var adapter: LeaveApplicationAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminLeaveBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[AdminLeaveViewModel::class.java]

        val navView = binding.root.findViewById<com.google.android.material.navigation.NavigationView>(R.id.navigationView)
        setupAdminDrawer(binding.drawerLayout, navView, binding.buttonBack, R.id.nav_leave)

        adapter = LeaveApplicationAdapter(
            mode = LeaveApplicationAdapter.Mode.ADMIN,
            onApprove = { viewModel.approve(it.id) },
            onReject = { showRejectDialog(it.id) }
        )
        binding.recyclerLeave.layoutManager = LinearLayoutManager(this)
        binding.recyclerLeave.adapter = adapter

        binding.buttonFilterPending.setOnClickListener { viewModel.load("Pending") }
        binding.buttonFilterApproved.setOnClickListener { viewModel.load("Approved") }
        binding.buttonFilterRejected.setOnClickListener { viewModel.load("Rejected") }
        binding.buttonFilterAll.setOnClickListener { viewModel.load(null) }

        binding.buttonBulkApprove.setOnClickListener {
            val pendingList = viewModel.applications.value.orEmpty().filter { it.status.equals("Pending", ignoreCase = true) }
            if (pendingList.isEmpty()) {
                Toast.makeText(this, "অনুমোদনের জন্য কোনো পেন্ডিং ছুটির আবেদন নেই।", Toast.LENGTH_SHORT).show()
            } else {
                AlertDialog.Builder(this)
                    .setTitle("একসাথে সব ছুটি অনুমোদন")
                    .setMessage("পেন্ডিং থাকা সকল ${pendingList.size}টি ছুটির আবেদন অনুমোদন করতে চান?")
                    .setPositiveButton("সব অনুমোদন করুন") { _, _ ->
                        val ids = pendingList.map { it.id }
                        viewModel.bulkApprove(ids)
                    }
                    .setNegativeButton("বাতিল করুন", null)
                    .show()
            }
        }

        viewModel.applications.observe(this) { adapter.submitList(it) }
        viewModel.error.observe(this) { message ->
            if (!message.isNullOrBlank()) Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.load()
    }

    private fun showRejectDialog(id: Int) {
        val input = EditText(this).apply { hint = "বাতিল করার কারণ (ঐচ্ছিক)" }
        AlertDialog.Builder(this)
            .setTitle("ছুটির আবেদন বাতিল করুন")
            .setView(input)
            .setPositiveButton("বাতিল") { _, _ ->
                viewModel.reject(id, input.text.toString().ifBlank { null })
            }
            .setNegativeButton("বাতিল করুন", null)
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
