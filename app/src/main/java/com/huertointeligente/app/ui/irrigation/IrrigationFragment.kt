package com.huertointeligente.app.ui.irrigation

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.huertointeligente.app.R
import com.huertointeligente.app.data.model.Huerto
import com.huertointeligente.app.data.model.SensorReading
import com.huertointeligente.app.databinding.DialogIrrigationBinding
import com.huertointeligente.app.databinding.FragmentIrrigationBinding
import com.huertointeligente.app.domain.AdviceType
import com.huertointeligente.app.domain.Analyzer
import com.huertointeligente.app.domain.Crops
import com.huertointeligente.app.ui.MainViewModel
import com.huertointeligente.app.util.relativeTime
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class IrrigationFragment : Fragment() {

    private var _binding: FragmentIrrigationBinding? = null
    private val b get() = _binding!!
    private val vm: MainViewModel by activityViewModels()
    private lateinit var adapter: IrrigationAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentIrrigationBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = IrrigationAdapter()
        b.rvLogs.layoutManager = LinearLayoutManager(requireContext())
        b.rvLogs.adapter = adapter
        b.btnLog.setOnClickListener { showLogDialog() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    combine(vm.selected, vm.reading) { h, r -> h to r }.collect { (h, r) -> render(h, r) }
                }
                launch {
                    vm.irrigations.collect { list ->
                        adapter.submit(list)
                        _binding?.tvLast?.text = if (list.isEmpty()) getString(R.string.irrigation_none_logged)
                        else getString(R.string.irrigation_last, relativeTime(list.first().timestamp))
                    }
                }
            }
        }
    }

    private fun render(h: Huerto?, r: SensorReading?) {
        val ctx = requireContext()
        val crop = Crops.forHuerto(h)
        val advice = Analyzer.irrigationAdvice(ctx, r, crop)

        val colorRes = when (advice.type) {
            AdviceType.IRRIGATE_NOW -> R.color.status_bad
            AdviceType.IRRIGATE_SOON -> R.color.status_warn
            AdviceType.NO_IRRIGATION -> R.color.status_ok
            AdviceType.EXCESS_WATER -> R.color.status_info
            AdviceType.NO_DATA -> R.color.text_secondary
        }
        val icon = when (advice.type) {
            AdviceType.IRRIGATE_NOW -> R.drawable.ic_water
            AdviceType.IRRIGATE_SOON -> R.drawable.ic_schedule
            AdviceType.NO_IRRIGATION -> R.drawable.ic_check
            AdviceType.EXCESS_WATER -> R.drawable.ic_warning
            AdviceType.NO_DATA -> R.drawable.ic_info
        }
        val color = ContextCompat.getColor(ctx, colorRes)
        b.adviceCard.strokeColor = color
        b.adviceCard.strokeWidth = android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_DIP, 2f, resources.displayMetrics
        ).toInt()
        b.adviceCard.setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.surface))
        b.adviceIcon.setImageResource(icon)
        b.adviceIcon.imageTintList = ColorStateList.valueOf(color)
        b.adviceTitle.text = advice.title
        b.adviceTitle.setTextColor(color)
        b.adviceDetail.text = advice.detail

        if (advice.intensity != null) {
            b.adviceIntensity.visibility = View.VISIBLE
            b.adviceIntensity.text = advice.intensity
            b.adviceIntensity.setTextColor(color)
            b.adviceIntensity.backgroundTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(color, 36))
        } else {
            b.adviceIntensity.visibility = View.GONE
        }
        if (advice.tip.isNotEmpty()) {
            b.adviceTip.visibility = View.VISIBLE
            b.adviceTip.text = advice.tip
        } else {
            b.adviceTip.visibility = View.GONE
        }

        b.rangeBar.markerColor = color
        if (r != null) {
            b.tvCurrent.text = getString(R.string.irrigation_current, r.soilMoisture)
            b.rangeBar.set(r.soilMoisture, crop.soil.min, crop.soil.max)
        } else {
            b.tvCurrent.text = getString(R.string.dash)
            b.rangeBar.clear()
        }
        b.tvIdeal.text = getString(R.string.ideal_range, crop.soil.min, crop.soil.max, "%")
        b.tvCrop.text = getString(
            R.string.irrigation_crop,
            if (h?.cropId != null) getString(crop.nameRes) else getString(R.string.crop_none)
        )
    }

    private fun showLogDialog() {
        val dialogBinding = DialogIrrigationBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.irrigation_dialog_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val minutesText = dialogBinding.etMinutes.text?.toString().orEmpty()
                val minutes = minutesText.toIntOrNull()
                if (minutes == null || minutes <= 0) {
                    dialogBinding.tilMinutes.error = getString(R.string.irrigation_minutes_required)
                    return@setOnClickListener
                }
                vm.addIrrigation(minutes, dialogBinding.etNote.text?.toString().orEmpty().trim())
                android.widget.Toast.makeText(requireContext(), R.string.irrigation_saved, android.widget.Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
