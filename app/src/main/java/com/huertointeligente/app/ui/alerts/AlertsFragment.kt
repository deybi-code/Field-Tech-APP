package com.huertointeligente.app.ui.alerts

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.huertointeligente.app.R
import com.huertointeligente.app.databinding.FragmentAlertsBinding
import com.huertointeligente.app.ui.MainViewModel
import kotlinx.coroutines.launch

class AlertsFragment : Fragment() {

    private var _binding: FragmentAlertsBinding? = null
    private val b get() = _binding!!
    private val vm: MainViewModel by activityViewModels()
    private lateinit var adapter: AlertsAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAlertsBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = AlertsAdapter()
        b.rvAlerts.layoutManager = LinearLayoutManager(requireContext())
        b.rvAlerts.adapter = adapter

        b.btnMarkRead.setOnClickListener { vm.markAlertsRead() }
        b.btnClear.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.alerts_clear)
                .setPositiveButton(R.string.delete) { _, _ ->
                    vm.clearAlerts()
                    android.widget.Toast.makeText(requireContext(), R.string.alerts_cleared, android.widget.Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.alerts.collect { list ->
                    adapter.submit(list)
                    b.emptyView.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                    val hasContent = list.isNotEmpty()
                    b.btnMarkRead.visibility = if (hasContent) View.VISIBLE else View.GONE
                    b.btnClear.visibility = if (hasContent) View.VISIBLE else View.GONE
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.markAlertsRead()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
