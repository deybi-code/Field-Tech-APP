package com.huertointeligente.app.ui.huertos

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.huertointeligente.app.R
import com.huertointeligente.app.data.model.Huerto
import com.huertointeligente.app.databinding.DialogHuertoBinding
import com.huertointeligente.app.databinding.FragmentHuertosBinding
import com.huertointeligente.app.domain.Crops
import com.huertointeligente.app.ui.MainViewModel
import kotlinx.coroutines.launch

class HuertosFragment : Fragment() {

    private var _binding: FragmentHuertosBinding? = null
    private val b get() = _binding!!
    private val vm: MainViewModel by activityViewModels()
    private lateinit var adapter: HuertosAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHuertosBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = HuertosAdapter(
            selectedId = { vm.selected.value?.id },
            onClick = { vm.selectHuerto(it.id) },
            onEdit = { showEditor(it) },
            onDelete = { confirmDelete(it) }
        )
        b.rvHuertos.layoutManager = LinearLayoutManager(requireContext())
        b.rvHuertos.adapter = adapter
        b.fabAdd.setOnClickListener { showEditor(null) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.huertos.collect { list ->
                    adapter.submit(list, vm.selected.value?.id)
                    b.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    private fun confirmDelete(h: Huerto) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.huerto_delete_title)
            .setMessage(R.string.huerto_delete_msg)
            .setPositiveButton(R.string.delete) { _, _ ->
                if (vm.deleteHuerto(h)) {
                    Toast.makeText(requireContext(), R.string.huerto_deleted, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), R.string.huerto_last_one, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showEditor(existing: Huerto?) {
        val db = DialogHuertoBinding.inflate(layoutInflater)
        val cropNames = listOf(getString(R.string.crop_none)) + Crops.all.map { getString(it.nameRes) }
        val cropIds = listOf<String?>(null) + Crops.all.map { it.id }
        db.acCrop.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, cropNames)
        )

        existing?.let {
            db.etName.setText(it.name)
            db.etLocation.setText(it.location)
            db.etArea.setText(if (it.areaHa > 0) it.areaHa.toString() else "")
            db.etUrl.setText(it.deviceUrl)
            val idx = cropIds.indexOf(it.cropId).coerceAtLeast(0)
            db.acCrop.setText(cropNames[idx], false)
        } ?: db.acCrop.setText(cropNames[0], false)

        db.etUrl.doAfterTextChanged { db.tvTestResult.visibility = View.GONE }
        
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        val scanner = GmsBarcodeScanning.getClient(requireContext(), options)

        db.tilUrl.setEndIconOnClickListener {
            scanner.startScan()
                .addOnSuccessListener { barcode ->
                    barcode.rawValue?.let { scannedUrl ->
                        db.etUrl.setText(scannedUrl)
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(requireContext(), "Error al escanear: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }

        db.btnTest.setOnClickListener {
            val url = db.etUrl.text?.toString()?.trim().orEmpty()
            if (url.isEmpty()) {
                db.tvTestResult.visibility = View.VISIBLE
                db.tvTestResult.text = getString(R.string.huerto_test_empty)
                db.tvTestResult.setTextColor(requireContext().getColor(R.color.status_warn))
                return@setOnClickListener
            }
            db.btnTest.isEnabled = false
            viewLifecycleOwner.lifecycleScope.launch {
                val result = vm.testConnection(url)
                db.btnTest.isEnabled = true
                db.tvTestResult.visibility = View.VISIBLE
                result.onSuccess {
                    db.tvTestResult.text = getString(R.string.huerto_test_ok, it.soilMoisture)
                    db.tvTestResult.setTextColor(requireContext().getColor(R.color.status_ok))
                }.onFailure {
                    db.tvTestResult.text = getString(R.string.huerto_test_fail, it.message ?: it.javaClass.simpleName)
                    db.tvTestResult.setTextColor(requireContext().getColor(R.color.status_bad))
                }
            }
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (existing == null) R.string.huerto_add else R.string.huerto_edit)
            .setView(db.root)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = db.etName.text?.toString()?.trim().orEmpty()
                if (name.isEmpty()) {
                    db.tilName.error = getString(R.string.huerto_name_required)
                    return@setOnClickListener
                }
                val cropIdx = cropNames.indexOf(db.acCrop.text?.toString()).let { if (it < 0) 0 else it }
                val h = Huerto(
                    id = existing?.id ?: 0L,
                    name = name,
                    location = db.etLocation.text?.toString()?.trim().orEmpty(),
                    cropId = cropIds[cropIdx],
                    areaHa = db.etArea.text?.toString()?.toDoubleOrNull() ?: 0.0,
                    deviceUrl = db.etUrl.text?.toString()?.trim().orEmpty()
                )
                vm.saveHuerto(h)
                Toast.makeText(requireContext(), R.string.huerto_saved, Toast.LENGTH_SHORT).show()
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
