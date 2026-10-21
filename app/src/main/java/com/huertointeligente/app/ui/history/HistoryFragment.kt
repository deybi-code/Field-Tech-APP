package com.huertointeligente.app.ui.history

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
import com.huertointeligente.app.R
import com.huertointeligente.app.data.model.Metric
import com.huertointeligente.app.data.model.metric
import com.huertointeligente.app.databinding.FragmentHistoryBinding
import com.huertointeligente.app.domain.Crops
import com.huertointeligente.app.domain.Range
import com.huertointeligente.app.ui.MainViewModel
import com.huertointeligente.app.ui.widgets.ChartPoint
import com.huertointeligente.app.util.fmt1
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val b get() = _binding!!
    private val vm: MainViewModel by activityViewModels()

    private var metric = Metric.SOIL_MOISTURE
    private var hours = 24
    private var loadJob: Job? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        savedInstanceState?.let {
            metric = Metric.values().getOrElse(it.getInt(KEY_METRIC, 0)) { Metric.SOIL_MOISTURE }
            hours = it.getInt(KEY_HOURS, 24)
        }
        b.chipGroupMetric.check(chipForMetric(metric))
        b.chipGroupRange.check(chipForHours(hours))

        b.chipGroupMetric.setOnCheckedStateChangeListener { _, ids ->
            metric = when (ids.firstOrNull()) {
                R.id.chip_soil_temp -> Metric.SOIL_TEMP
                R.id.chip_air_temp -> Metric.AIR_TEMP
                R.id.chip_air_hum -> Metric.AIR_HUMIDITY
                else -> Metric.SOIL_MOISTURE
            }
            load()
        }
        b.chipGroupRange.setOnCheckedStateChangeListener { _, ids ->
            hours = when (ids.firstOrNull()) {
                R.id.chip_r6 -> 6
                R.id.chip_r7d -> 168
                else -> 24
            }
            load()
        }

        // Al iniciar, y cada vez que cambie el huerto, se recarga el gráfico
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.selected.collect { load() }
            }
        }
    }

    private fun chipForMetric(m: Metric) = when (m) {
        Metric.SOIL_MOISTURE -> R.id.chip_soil
        Metric.SOIL_TEMP -> R.id.chip_soil_temp
        Metric.AIR_TEMP -> R.id.chip_air_temp
        Metric.AIR_HUMIDITY -> R.id.chip_air_hum
    }

    private fun chipForHours(h: Int) = when (h) {
        6 -> R.id.chip_r6
        168 -> R.id.chip_r7d
        else -> R.id.chip_r24
    }

    private fun load() {
        loadJob?.cancel()
        loadJob = viewLifecycleOwner.lifecycleScope.launch {
            val since = System.currentTimeMillis() - hours * 3_600_000L
            val list = vm.history(since)
            val binding = _binding ?: return@launch
            val crop = Crops.forHuerto(vm.selected.value)

            val (colorRes, unit, ideal) = when (metric) {
                Metric.SOIL_MOISTURE -> Triple(R.color.metric_soil, "%", crop.soil as Range?)
                Metric.SOIL_TEMP -> Triple(R.color.metric_soil_temp, "°C", null as Range?)
                Metric.AIR_TEMP -> Triple(R.color.metric_air_temp, "°C", crop.airTemp as Range?)
                Metric.AIR_HUMIDITY -> Triple(R.color.metric_air_hum, "%", crop.airHumidity as Range?)
            }
            val values = list.map { it.metric(metric) }
            val empty = values.size < 2
            binding.tvEmpty.visibility = if (empty) View.VISIBLE else View.GONE
            binding.chart.visibility = if (empty) View.INVISIBLE else View.VISIBLE

            binding.chart.setData(
                list.map { ChartPoint(it.timestamp, it.metric(metric)) },
                unit,
                ContextCompat.getColor(requireContext(), colorRes),
                ideal?.min, ideal?.max
            )
            if (values.isEmpty()) {
                binding.tvMin.text = getString(R.string.dash)
                binding.tvAvg.text = getString(R.string.dash)
                binding.tvMax.text = getString(R.string.dash)
            } else {
                binding.tvMin.text = "${values.min().fmt1()} $unit"
                binding.tvAvg.text = "${values.average().toFloat().fmt1()} $unit"
                binding.tvMax.text = "${values.max().fmt1()} $unit"
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_METRIC, metric.ordinal)
        outState.putInt(KEY_HOURS, hours)
    }

    override fun onDestroyView() {
        loadJob?.cancel()
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val KEY_METRIC = "metric"
        const val KEY_HOURS = "hours"
    }
}
