package com.huertointeligente.app.ui.crops

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
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.huertointeligente.app.R
import com.huertointeligente.app.databinding.FragmentCropsBinding
import com.huertointeligente.app.databinding.SheetCropDetailBinding
import com.huertointeligente.app.domain.Analyzer
import com.huertointeligente.app.domain.Crop
import com.huertointeligente.app.domain.Crops
import com.huertointeligente.app.ui.MainViewModel
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

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.selected.collect { h -> refresh() }
            }
        }
    }

    private fun refresh() {
        viewLifecycleOwner.lifecycleScope.launch {
            val h = vm.selected.value
            b.tvIntro.text = getString(R.string.crops_intro, h?.name ?: getString(R.string.no_huerto))
            val readings = vm.weekHistory()
            val activeCropId = h?.cropId
            val rows = Crops.all.map { crop ->
                CropRow(crop, Analyzer.compatibility(crop, readings), crop.id == activeCropId)
            }.sortedByDescending { it.score }
            adapter.submit(rows)
        }
    }

    private fun showDetail(row: CropRow) {
        val sheetBinding = SheetCropDetailBinding.inflate(layoutInflater)
        val dialog = BottomSheetDialog(requireContext())
        dialog.setContentView(sheetBinding.root)
        bindDetail(sheetBinding, row.crop, row.score)
        sheetBinding.btnUse.setOnClickListener {
            vm.setCrop(row.crop.id)
            dialog.dismiss()
            android.widget.Toast.makeText(
                requireContext(), getString(R.string.crop_set_done, getString(row.crop.nameRes)),
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
