package com.huertointeligente.app.ui.settings

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.huertointeligente.app.BuildConfig
import com.huertointeligente.app.R
import com.huertointeligente.app.data.model.DataSourceMode
import com.huertointeligente.app.databinding.FragmentSettingsBinding
import com.huertointeligente.app.ui.MainViewModel
import com.huertointeligente.app.work.MonitorWorker

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val b get() = _binding!!
    private val vm: MainViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val prefs = vm.prefs

        b.rgSource.check(if (prefs.mode == DataSourceMode.HTTP) R.id.rb_http else R.id.rb_simulated)
        b.rgSource.setOnCheckedChangeListener { _, id ->
            prefs.mode = if (id == R.id.rb_http) DataSourceMode.HTTP else DataSourceMode.SIMULATED
            vm.onModeChanged()
        }

        b.sliderPoll.value = prefs.pollSeconds.toFloat().coerceIn(5f, 120f)
        b.tvPoll.text = getString(R.string.poll_seconds, prefs.pollSeconds)
        b.sliderPoll.addOnChangeListener { _, value, fromUser ->
            b.tvPoll.text = getString(R.string.poll_seconds, value.toInt())
            if (fromUser) {
                prefs.pollSeconds = value.toInt()
                vm.onPollChanged()
            }
        }

        b.switchNotifications.isChecked = prefs.notificationsEnabled
        b.switchNotifications.setOnCheckedChangeListener { _, checked ->
            prefs.notificationsEnabled = checked
        }

        b.switchBackground.isChecked = prefs.backgroundMonitoring
        b.switchBackground.setOnCheckedChangeListener { _, checked ->
            prefs.backgroundMonitoring = checked
            MonitorWorker.setEnabled(requireContext(), checked)
        }

        b.rowHuertos.setOnClickListener { findNavController().navigate(R.id.huertosFragment) }
        b.rowAbout.setOnClickListener { showAbout() }
        b.tvVersion.text = getString(R.string.version_fmt, BuildConfig.VERSION_NAME)

        b.btnRegen.setOnClickListener {
            vm.regenerateDemo()
            android.widget.Toast.makeText(requireContext(), R.string.demo_regenerated, android.widget.Toast.LENGTH_SHORT).show()
        }
        b.btnClear.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.confirm_clear_title)
                .setMessage(R.string.confirm_clear_msg)
                .setPositiveButton(R.string.delete) { _, _ ->
                    vm.clearHistory()
                    android.widget.Toast.makeText(requireContext(), R.string.history_cleared, android.widget.Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private fun showAbout() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.about_title)
            .setMessage(getString(R.string.about_message, getString(R.string.version_fmt, BuildConfig.VERSION_NAME)))
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
