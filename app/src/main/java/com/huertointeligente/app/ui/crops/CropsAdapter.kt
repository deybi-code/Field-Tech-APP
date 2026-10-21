package com.huertointeligente.app.ui.crops

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.huertointeligente.app.R
import com.huertointeligente.app.databinding.ItemCropBinding
import com.huertointeligente.app.domain.Analyzer

class CropsAdapter(private val onClick: (CropRow) -> Unit) : RecyclerView.Adapter<CropsAdapter.VH>() {

    private var items: List<CropRow> = emptyList()

    fun submit(list: List<CropRow>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemCropBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position], onClick)

    override fun getItemCount() = items.size

    class VH(private val b: ItemCropBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(row: CropRow, onClick: (CropRow) -> Unit) {
            val ctx = b.root.context
            b.tvName.text = ctx.getString(row.crop.nameRes)
            b.tvRanges.text = ctx.getString(
                R.string.range_fmt, row.crop.soil.min, row.crop.soil.max, ctx.getString(R.string.unit_percent)
            )
            b.tvScore.text = "${row.score}%"
            val colorRes = when {
                row.score >= 80 -> R.color.status_ok
                row.score >= 60 -> R.color.metric_soil
                row.score >= 40 -> R.color.status_warn
                else -> R.color.status_bad
            }
            val color = ContextCompat.getColor(ctx, colorRes)
            b.tvScore.setTextColor(color)
            b.tvLabel.setText(Analyzer.compatLabelRes(row.score))
            b.tvInUse.visibility = if (row.inUse) android.view.View.VISIBLE else android.view.View.GONE
            b.root.setOnClickListener { onClick(row) }
        }
    }
}
