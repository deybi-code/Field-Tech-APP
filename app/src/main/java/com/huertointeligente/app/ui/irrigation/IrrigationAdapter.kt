package com.huertointeligente.app.ui.irrigation

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.huertointeligente.app.R
import com.huertointeligente.app.data.model.IrrigationLog
import com.huertointeligente.app.databinding.ItemIrrigationBinding
import com.huertointeligente.app.util.relativeTime

class IrrigationAdapter : RecyclerView.Adapter<IrrigationAdapter.VH>() {

    private var items: List<IrrigationLog> = emptyList()

    fun submit(list: List<IrrigationLog>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemIrrigationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

    override fun getItemCount() = items.size

    class VH(private val b: ItemIrrigationBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: IrrigationLog) {
            b.tvWhen.text = relativeTime(item.timestamp)
            b.tvMinutes.text = b.root.context.getString(R.string.irrigation_minutes_fmt, item.minutes)
            b.tvNote.text = item.note
            b.tvNote.visibility = if (item.note.isBlank()) android.view.View.GONE else android.view.View.VISIBLE
        }
    }
}
