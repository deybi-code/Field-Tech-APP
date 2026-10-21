package com.huertointeligente.app.ui.home

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.huertointeligente.app.R
import com.huertointeligente.app.data.model.DataSourceMode
import com.huertointeligente.app.data.model.Huerto
import com.huertointeligente.app.data.model.SensorReading
import com.huertointeligente.app.databinding.FragmentHomeBinding
import com.huertointeligente.app.databinding.ItemMetricCardBinding
import com.huertointeligente.app.domain.AdviceType
import com.huertointeligente.app.domain.Analyzer
import com.huertointeligente.app.domain.Crops
import com.huertointeligente.app.domain.Level
import com.huertointeligente.app.domain.Range
import com.huertointeligente.app.ui.MainViewModel
import com.huertointeligente.app.ui.widgets.ChartPoint
import com.huertointeligente.app.util.fmt0
import com.huertointeligente.app.util.fmt1
import com.huertointeligente.app.util.fmt2
import com.huertointeligente.app.util.levelColor
import com.huertointeligente.app.util.relativeTime
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import android.widget.Toast

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val b get() = _binding!!
    private val vm: MainViewModel by activityViewModels()

    private var chartHuertoId = -1L
    private var lastChartLoad = 0L

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        b.swipe.setOnRefreshListener { vm.refresh() }
        b.bannerCard.setOnClickListener { findNavController().navigate(R.id.irrigationFragment) }
        b.btnHistory.setOnClickListener { findNavController().navigate(R.id.historyFragment) }
        b.btnScanQr.setOnClickListener { scanQrAndSaveHuerto() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(vm.selected, vm.reading, vm.conn) { h, r, c -> Triple(h, r, c) }
                    .collect { (h, r, c) -> render(h, r, c) }
            }
        }
    }

    private fun render(h: Huerto?, r: SensorReading?, c: MainViewModel.Conn) {
        val ctx = requireContext()
        val crop = Crops.forHuerto(h)
        b.swipe.isRefreshing = c is MainViewModel.Conn.Loading && r != null

        // Encabezado
        val cropLabel = if (h?.cropId != null) getString(crop.nameRes) else getString(R.string.crop_none)
        b.tvHuertoInfo.text = if (h == null) getString(R.string.no_huerto)
        else listOf(cropLabel, h.location).filter { it.isNotBlank() }.joinToString(" · ")
        b.tvUpdated.text = if (r != null) getString(R.string.home_updated, relativeTime(r.timestamp))
        else getString(R.string.home_waiting)
        renderConnection(r, c)

        // Recomendación principal
        val advice = Analyzer.irrigationAdvice(ctx, r, crop)
        val (adviceColor, adviceIcon) = when (advice.type) {
            AdviceType.IRRIGATE_NOW -> R.color.status_bad to R.drawable.ic_water
            AdviceType.IRRIGATE_SOON -> R.color.status_warn to R.drawable.ic_schedule
            AdviceType.NO_IRRIGATION -> R.color.status_ok to R.drawable.ic_check
            AdviceType.EXCESS_WATER -> R.color.status_info to R.drawable.ic_warning
            AdviceType.NO_DATA -> R.color.text_secondary to R.drawable.ic_info
        }
        val color = ContextCompat.getColor(ctx, adviceColor)
        b.bannerIcon.setImageResource(adviceIcon)
        b.bannerIcon.imageTintList = ColorStateList.valueOf(color)
        b.bannerCard.strokeColor = color
        b.bannerCard.strokeWidth = android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_DIP, 2f, resources.displayMetrics
        ).toInt()
        b.bannerCard.setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.surface))
        b.bannerTitle.text = advice.title
        b.bannerSubtitle.text = if (r == null) advice.detail else getString(R.string.tap_for_advice)

        // Humedad del suelo
        b.gauge.setIdeal(crop.soil.min, crop.soil.max)
        if (r != null) {
            val level = Analyzer.level(r.soilMoisture, crop.soil)
            val soilColor = if (level == Level.HIGH) ContextCompat.getColor(ctx, R.color.status_info)
            else ctx.levelColor(level)
            b.gauge.arcColor = soilColor
            b.gauge.setValue(r.soilMoisture)
            b.tvSoilStatus.text = Analyzer.levelLabel(ctx, level)
            b.tvSoilStatus.setTextColor(soilColor)
            b.rangeBar.markerColor = soilColor
            b.rangeBar.set(r.soilMoisture, crop.soil.min, crop.soil.max)
        } else {
            b.gauge.setValue(0f, animate = false)
            b.tvSoilStatus.text = getString(R.string.dash)
            b.rangeBar.clear()
        }
        b.tvSoilRange.text = getString(R.string.ideal_range, crop.soil.min, crop.soil.max, "%")

        // Tarjetas de métricas
        bindMetric(
            b.cardSoilTemp, R.drawable.ic_thermostat, R.color.metric_soil_temp, R.string.soil_temp,
            r?.soilTemp, "°C", Analyzer.SOIL_TEMP_RANGE, ::fmt1Value
        )
        bindMetric(
            b.cardAirTemp, R.drawable.ic_sun, R.color.metric_air_temp, R.string.air_temp,
            r?.airTemp, "°C", crop.airTemp, ::fmt1Value
        )
        bindMetric(
            b.cardAirHum, R.drawable.ic_water, R.color.metric_air_hum, R.string.air_humidity,
            r?.airHumidity, "%", crop.airHumidity, ::fmt0Value
        )
        val vpd = r?.let { Analyzer.vpd(it.airTemp, it.airHumidity) }
        bindMetric(
            b.cardVpd, R.drawable.ic_air, R.color.metric_vpd, R.string.vpd,
            vpd, "kPa", Analyzer.VPD_RANGE, ::fmt2Value, vpdStyle = true
        )

        // Dispositivo
        renderDevice(r)

        // Mini gráfico
        val now = System.currentTimeMillis()
        if (h != null && (h.id != chartHuertoId || now - lastChartLoad > 60_000L)) {
            chartHuertoId = h.id
            lastChartLoad = now
            loadChart(crop.soil)
        }
        if (h == null) b.chartMini.setData(emptyList(), "%", ContextCompat.getColor(ctx, R.color.metric_soil))
    }

    private fun fmt1Value(v: Float) = v.fmt1()
    private fun fmt0Value(v: Float) = v.fmt0()
    private fun fmt2Value(v: Float) = v.fmt2()

    private fun bindMetric(
        card: ItemMetricCardBinding,
        icon: Int,
        colorRes: Int,
        label: Int,
        value: Float?,
        unit: String,
        range: Range,
        formatter: (Float) -> String,
        vpdStyle: Boolean = false
    ) {
        val ctx = requireContext()
        val accent = ContextCompat.getColor(ctx, colorRes)
        card.metricIcon.setImageResource(icon)
        card.metricIcon.imageTintList = ColorStateList.valueOf(accent)
        card.metricLabel.setText(label)
        if (value == null) {
            card.metricValue.text = getString(R.string.dash)
            setPill(card.metricStatus, getString(R.string.dash), ContextCompat.getColor(ctx, R.color.text_secondary))
            return
        }
        card.metricValue.text = "${formatter(value)} $unit"
        val level = Analyzer.level(value, range)
        val text = if (vpdStyle) Analyzer.vpdLabel(ctx, level) else Analyzer.levelLabel(ctx, level)
        setPill(card.metricStatus, text, ctx.levelColor(level))
    }

    private fun renderConnection(r: SensorReading?, c: MainViewModel.Conn) {
        val ctx = requireContext()
        val stale = r != null && System.currentTimeMillis() - r.timestamp > 15 * 60 * 1000L
        val (text, color) = when {
            vm.prefs.mode == DataSourceMode.SIMULATED ->
                getString(R.string.conn_demo) to R.color.status_info
            c is MainViewModel.Conn.Loading && r == null ->
                getString(R.string.conn_loading) to R.color.text_secondary
            c is MainViewModel.Conn.Error || stale ->
                getString(R.string.conn_offline) to R.color.status_bad
            r == null -> getString(R.string.conn_loading) to R.color.text_secondary
            else -> getString(R.string.conn_online) to R.color.status_ok
        }
        setPill(b.tvConn, text, ContextCompat.getColor(ctx, color))
    }

    private fun renderDevice(r: SensorReading?) {
        val ctx = requireContext()
        if (r == null || r.battery < 0) {
            b.tvBattery.text = getString(R.string.dash)
            b.progressBattery.progress = 0
        } else {
            b.tvBattery.text = "${r.battery}%"
            b.progressBattery.progress = r.battery
            val color = when {
                r.battery < 20 -> R.color.status_bad
                r.battery < 40 -> R.color.status_warn
                else -> R.color.status_ok
            }
            b.progressBattery.setIndicatorColor(ContextCompat.getColor(ctx, color))
        }
        if (r == null || r.rssi == 0) {
            b.tvRssi.text = getString(R.string.dash)
        } else {
            val quality = when {
                r.rssi > -60 -> R.string.signal_excellent
                r.rssi > -75 -> R.string.signal_good
                else -> R.string.signal_weak
            }
            b.tvRssi.text = getString(R.string.signal_value, getString(quality), r.rssi)
        }
        b.tvLastReading.text = if (r == null) getString(R.string.dash) else relativeTime(r.timestamp)
    }

    private fun loadChart(idealSoil: Range) {
        viewLifecycleOwner.lifecycleScope.launch {
            val list = vm.history(System.currentTimeMillis() - 24L * 3600 * 1000, 96)
            _binding?.chartMini?.setData(
                list.map { ChartPoint(it.timestamp, it.soilMoisture) },
                "%",
                ContextCompat.getColor(requireContext(), R.color.metric_soil),
                idealSoil.min, idealSoil.max
            )
        }
    }

    private fun scanQrAndSaveHuerto() {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        val scanner = GmsBarcodeScanning.getClient(requireContext(), options)

        scanner.startScan()
            .addOnSuccessListener { barcode ->
                barcode.rawValue?.let { scannedUrl ->
                    // Crear un nuevo huerto temporal con la URL escaneada
                    val newHuerto = Huerto(
                        name = "Nuevo Huerto QR",
                        location = "Desconocida",
                        deviceUrl = scannedUrl
                    )
                    vm.saveHuerto(newHuerto)
                    Toast.makeText(requireContext(), "Dispositivo añadido correctamente", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error al escanear: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setPill(tv: TextView, text: String, color: Int) {
        tv.text = text
        tv.setTextColor(color)
        tv.backgroundTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(color, 36))
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
