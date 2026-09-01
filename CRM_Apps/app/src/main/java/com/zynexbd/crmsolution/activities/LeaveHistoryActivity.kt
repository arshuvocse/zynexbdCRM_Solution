package com.zynexbd.crmsolution.activities

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.zynexbd.crmsolution.adapters.LeaveApplicationAdapter
import com.zynexbd.crmsolution.databinding.ActivityLeaveHistoryBinding
import com.zynexbd.crmsolution.viewmodel.LeaveHistoryViewModel

class LeaveHistoryActivity : BaseActivity() {

    private lateinit var binding: ActivityLeaveHistoryBinding
    private lateinit var viewModel: LeaveHistoryViewModel
    private lateinit var adapter: LeaveApplicationAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLeaveHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[LeaveHistoryViewModel::class.java]
        binding.buttonBack.setOnClickListener { finish() }
        adapter = LeaveApplicationAdapter(
            mode = LeaveApplicationAdapter.Mode.USER,
            onCancel = { viewModel.cancel(it.id) }
        )
        binding.recyclerLeave.layoutManager = LinearLayoutManager(this)
        binding.recyclerLeave.adapter = adapter

        viewModel.applications.observe(this) { adapter.submitList(it) }
        viewModel.error.observe(this) { message ->
            if (!message.isNullOrBlank()) Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.load()
    }
}
