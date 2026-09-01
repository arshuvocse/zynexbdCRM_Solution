package com.zynexbd.crmsolution.activities

import android.app.DatePickerDialog
import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.zynexbd.crmsolution.R
import com.zynexbd.crmsolution.adapters.CrmAssignmentHistoryAdapter
import com.zynexbd.crmsolution.adapters.CrmFollowUpHistoryAdapter
import com.zynexbd.crmsolution.adapters.CrmRemarkHistoryAdapter
import com.zynexbd.crmsolution.adapters.CrmStatusHistoryAdapter
import com.zynexbd.crmsolution.databinding.*
import com.zynexbd.crmsolution.models.CreateFollowUpRequest
import com.zynexbd.crmsolution.viewmodel.CrmViewModel
import java.util.Calendar

class LeadDetailsActivity : BaseActivity() {

    private lateinit var binding: ActivityCrmLeadDetailsBinding
    private lateinit var viewModel: CrmViewModel

    private var leadId: Int = 0
    private var isManager: Boolean = false

    private val followUpAdapter = CrmFollowUpHistoryAdapter()
    private val remarkAdapter = CrmRemarkHistoryAdapter()
    private val assignmentAdapter = CrmAssignmentHistoryAdapter()
    private val statusHistoryAdapter = CrmStatusHistoryAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCrmLeadDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        leadId = intent.getIntExtra("LEAD_ID", 0)
        isManager = intent.getBooleanExtra("IS_MANAGER", false)

        viewModel = ViewModelProvider(this)[CrmViewModel::class.java]

        setupUI()
        observeViewModel()

        viewModel.loadLeadDetails(leadId, isManager)
    }

    private fun setupUI() {
        binding.buttonBack.setOnClickListener { finish() }

        binding.buttonRefresh.setOnClickListener {
            viewModel.loadLeadDetails(leadId, isManager)
        }

        // Setup History Recyclers
        binding.recyclerFollowUpHistory.layoutManager = LinearLayoutManager(this)
        binding.recyclerFollowUpHistory.adapter = followUpAdapter

        binding.recyclerRemarksHistory.layoutManager = LinearLayoutManager(this)
        binding.recyclerRemarksHistory.adapter = remarkAdapter

        binding.recyclerAssignmentHistory.layoutManager = LinearLayoutManager(this)
        binding.recyclerAssignmentHistory.adapter = assignmentAdapter

        binding.recyclerStatusHistory.layoutManager = LinearLayoutManager(this)
        binding.recyclerStatusHistory.adapter = statusHistoryAdapter

        // Action Buttons
        binding.buttonAddFollowUp.setOnClickListener { showAddFollowUpDialog() }
        binding.buttonUpdateStatus.setOnClickListener { showUpdateStatusDialog() }
        binding.buttonAddRemark.setOnClickListener { showAddRemarkDialog() }

        if (isManager) {
            binding.buttonAssignEmployee.visibility = View.VISIBLE
            binding.buttonAssignEmployee.setOnClickListener { showAssignDialog() }
        } else {
            binding.buttonAssignEmployee.visibility = View.GONE
        }
    }

    private fun observeViewModel() {
        viewModel.errorMessage.observe(this) { msg ->
            if (!msg.isNullOrBlank()) {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
        }

        viewModel.successMessage.observe(this) { msg ->
            if (!msg.isNullOrBlank()) {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
        }

        viewModel.leadDetails.observe(this) { lead ->
            if (lead != null) {
                binding.textLeadTitle.text = lead.leadName
                binding.textLeadName.text = lead.leadName
                binding.textLeadStatus.text = lead.leadStatus

                // High-contrast status badge
                binding.textLeadStatus.setTextColor(android.graphics.Color.WHITE)
                when (lead.leadStatus) {
                    "Interested" -> {
                        binding.textLeadStatus.background = ContextCompat.getDrawable(this, R.drawable.bg_badge_success)
                    }
                    "Closed" -> {
                        binding.textLeadStatus.background = ContextCompat.getDrawable(this, R.drawable.bg_badge_purple)
                    }
                    "Not Interested", "Lost" -> {
                        binding.textLeadStatus.background = ContextCompat.getDrawable(this, R.drawable.bg_badge_danger)
                    }
                    "Follow Up", "Follow-up" -> {
                        binding.textLeadStatus.background = ContextCompat.getDrawable(this, R.drawable.bg_badge_warning)
                    }
                    else -> { // New Lead, Contacted
                        binding.textLeadStatus.background = ContextCompat.getDrawable(this, R.drawable.bg_badge_primary)
                    }
                }

                binding.textContactPerson.text = if (!lead.contactPerson.isNullOrBlank()) "👤 Contact: ${lead.contactPerson}" else "👤 Contact: N/A"

                if (!lead.phone.isNullOrBlank()) {
                    binding.textPhone.text = "📞 ${lead.phone}"
                    binding.textPhone.setOnClickListener {
                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${lead.phone}"))
                        startActivity(intent)
                    }
                } else {
                    binding.textPhone.text = "📞 N/A"
                }

                if (!lead.email.isNullOrBlank()) {
                    binding.textEmail.text = "✉️ ${lead.email}"
                    binding.textEmail.setOnClickListener {
                        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${lead.email}"))
                        startActivity(intent)
                    }
                } else {
                    binding.textEmail.text = "✉️ N/A"
                }

                binding.textAddress.text = if (!lead.address.isNullOrBlank()) "📍 ${lead.address}" else "📍 Address: N/A"
                binding.textProductService.text = "📦 Product: ${lead.productServiceName ?: "General"}"
                binding.textLeadSource.text = "🏷️ Source: ${lead.leadSourceName ?: lead.leadSourceType}"
                binding.textAssignedTo.text = "👤 Assigned: ${lead.assignedUserName ?: "Unassigned"}"
                binding.textNextFollowUp.text = "Next: ${lead.nextFollowUpDate?.take(10) ?: "None"}"

                followUpAdapter.submitList(lead.followUps)
                remarkAdapter.submitList(lead.remarksHistory)
                assignmentAdapter.submitList(lead.assignments)
                statusHistoryAdapter.submitList(lead.statusHistory)
            }
        }
    }

    private fun showAddFollowUpDialog() {
        val dialog = Dialog(this)
        val dBinding = DialogCrmAddFollowupBinding.inflate(layoutInflater)
        dialog.setContentView(dBinding.root)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val statuses = listOf("Follow Up", "Interested", "Not Interested", "Closed")
        val statusAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, statuses)
        dBinding.spinnerFollowUpStatus.adapter = statusAdapter

        var nextDate: String? = null
        dBinding.buttonPickNextDate.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(this, { _, year, month, dayOfMonth ->
                nextDate = String.format("%04d-%02d-%02d", year, month + 1, dayOfMonth)
                dBinding.textSelectNextDate.text = nextDate
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        dBinding.buttonCancel.setOnClickListener { dialog.dismiss() }

        dBinding.buttonSubmit.setOnClickListener {
            val remarks = dBinding.editFollowUpRemarks.text.toString().trim()
            if (remarks.isEmpty()) {
                dBinding.editFollowUpRemarks.error = "Remarks are required"
                return@setOnClickListener
            }
            val status = dBinding.spinnerFollowUpStatus.selectedItem.toString()

            val req = CreateFollowUpRequest(
                status = status,
                nextFollowUpDate = nextDate,
                remarks = remarks
            )

            viewModel.addFollowUp(leadId, req, isManager) { success ->
                if (success) dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun showUpdateStatusDialog() {
        val dialog = Dialog(this)
        val dBinding = DialogCrmUpdateStatusBinding.inflate(layoutInflater)
        dialog.setContentView(dBinding.root)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val statuses = listOf("New Lead", "Follow Up", "Interested", "Not Interested", "Closed")
        val statusAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, statuses)
        dBinding.spinnerStatus.adapter = statusAdapter

        var nextDate: String? = null
        dBinding.buttonPickNextDate.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(this, { _, year, month, dayOfMonth ->
                nextDate = String.format("%04d-%02d-%02d", year, month + 1, dayOfMonth)
                dBinding.textSelectNextDate.text = nextDate
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        dBinding.buttonCancel.setOnClickListener { dialog.dismiss() }

        dBinding.buttonSubmit.setOnClickListener {
            val status = dBinding.spinnerStatus.selectedItem.toString()
            val remarks = dBinding.editStatusRemarks.text.toString().trim().ifBlank { null }

            viewModel.updateLeadStatus(leadId, status, nextDate, remarks) { success ->
                if (success) dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun showAddRemarkDialog() {
        val dialog = Dialog(this)
        val dBinding = DialogCrmAddRemarkBinding.inflate(layoutInflater)
        dialog.setContentView(dBinding.root)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dBinding.buttonCancel.setOnClickListener { dialog.dismiss() }

        dBinding.buttonSubmit.setOnClickListener {
            val remark = dBinding.editRemarkContent.text.toString().trim()
            if (remark.isEmpty()) {
                dBinding.editRemarkContent.error = "Remark is required"
                return@setOnClickListener
            }

            viewModel.addRemark(leadId, remark, isManager) { success ->
                if (success) dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun showAssignDialog() {
        val dialog = Dialog(this)
        val dBinding = DialogCrmAssignLeadBinding.inflate(layoutInflater)
        dialog.setContentView(dBinding.root)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dBinding.buttonCancel.setOnClickListener { dialog.dismiss() }

        // Load employees from productivity / performance list
        val perf = viewModel.managerDashboard.value?.employeePerformance ?: emptyList()
        val empNames = perf.map { "${it.employeeName} (ID: ${it.userId})" }
        val empAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, empNames)
        dBinding.spinnerAssignEmployee.adapter = empAdapter

        dBinding.buttonSubmit.setOnClickListener {
            if (perf.isEmpty() || dBinding.spinnerAssignEmployee.selectedItemPosition < 0) {
                Toast.makeText(this, "No employee selected", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val selectedEmp = perf[dBinding.spinnerAssignEmployee.selectedItemPosition]
            val remarks = dBinding.editAssignRemarks.text.toString().trim().ifBlank { null }

            viewModel.assignLead(leadId, selectedEmp.userId, remarks) { success ->
                if (success) dialog.dismiss()
            }
        }

        dialog.show()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadLeadDetails(leadId, isManager)
    }
}
