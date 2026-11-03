package com.huertointeligente.app.ui.crops

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.huertointeligente.app.R
import com.huertointeligente.app.databinding.FragmentCropsBinding
import com.huertointeligente.app.databinding.SheetCropDetailBinding
import com.huertointeligente.app.domain.AdviceType
import com.huertointeligente.app.domain.Analyzer
import com.huertointeligente.app.domain.Crop
import com.huertointeligente.app.domain.Crops
import com.huertointeligente.app.ui.MainViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class CropsFragment : Fragment() {

    private var _binding: FragmentCropsBinding? = null
    private val b get() = _binding!!
    private val vm: MainViewModel by activityViewModels()
    private lateinit var adapter: CropsAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCropsBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = CropsAdapter { showDetail(it) }
        b.rvCrops.layoutManager = LinearLayoutManager(requireContext())
        b.rvCrops.adapter = adapter

        // Toggler para lista desplegable bacán de cultivos
        b.cardCropsHeader.setOnClickListener {
            val isCurrentlyVisible = b.rvCrops.visibility == View.VISIBLE
            b.rvCrops.visibility = if (isCurrentlyVisible) View.GONE else View.VISIBLE
            b.tvCropsSubtitle.text = if (isCurrentlyVisible) {
                "Toca para desplegar la lista de cultivos (${Crops.all.size} disponibles)"
            } else {
                "Toca para contraer la lista"
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(vm.selected, vm.reading) { h, r -> h to r }.collect { (h, r) -> refresh(h, r) }
            }
        }
    }

    private fun refresh(h: com.huertointeligente.app.data.model.Huerto?, r: com.huertointeligente.app.data.model.SensorReading?) {
        if (_binding == null) return
        viewLifecycleOwner.lifecycleScope.launch {
            val ctx = context ?: return@launch
            if (_binding == null) return@launch
            b.tvIntro.text = getString(R.string.crops_intro, h?.name ?: getString(R.string.no_huerto))

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

            val readings = vm.weekHistory()
            if (_binding == null || context == null) return@launch
            val activeCropId = h?.cropId
            val rows = Crops.all.map { c ->
                CropRow(c, Analyzer.compatibility(c, readings), c.id == activeCropId)
            }.sortedByDescending { it.score }
            adapter.submit(rows)
        }
    }

    private fun showDetail(row: CropRow) {
        val safeCtx = context ?: return
        val sheetBinding = SheetCropDetailBinding.inflate(layoutInflater)
        val dialog = BottomSheetDialog(safeCtx)
        dialog.setContentView(sheetBinding.root)
        bindDetail(sheetBinding, row.crop, row.score)
        sheetBinding.btnUse.setOnClickListener {
            vm.setCrop(row.crop.id)
            dialog.dismiss()
            android.widget.Toast.makeText(
                safeCtx, getString(R.string.crop_set_done, getString(row.crop.nameRes)),
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
        dialog.show()
    }

    private fun bindDetail(sb: SheetCropDetailBinding, crop: Crop, score: Int) {
        sb.tvName.text = getString(crop.nameRes)
        sb.tvScore.text = getString(R.string.compat_score, score)
        sb.tvDesc.text = getString(crop.descRes)
        sb.tvSoil.text = getString(R.string.range_fmt, crop.soil.min, crop.soil.max, "%")
        sb.tvAir.text = getString(R.string.range_fmt, crop.airTemp.min, crop.airTemp.max, "°C")
        sb.tvHum.text = getString(R.string.range_fmt, crop.airHumidity.min, crop.airHumidity.max, "%")
        sb.tvTip.text = getString(crop.tipRes)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}

data class CropRow(val crop: Crop, val score: Int, val inUse: Boolean)
