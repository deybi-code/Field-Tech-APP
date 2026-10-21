package com.huertointeligente.app.ui.huertos

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.huertointeligente.app.R
import com.huertointeligente.app.data.model.Huerto
import com.huertointeligente.app.databinding.ItemHuertoBinding
import com.huertointeligente.app.domain.Crops

class HuertosAdapter(
    private val selectedId: () -> Long?,
    private val onClick: (Huerto) -> Unit,
    private val onEdit: (Huerto) -> Unit,
    private val onDelete: (Huerto) -> Unit
) : RecyclerView.Adapter<HuertosAdapter.VH>() {

    private var items: List<Huerto> = emptyList()

    fun submit(list: List<Huerto>, selected: Long?) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemHuertoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val h = items[position]
        holder.bind(h, h.id == selectedId(), onClick, onEdit, onDelete)
    }

    override fun getItemCount() = items.size

    class VH(private val b: ItemHuertoBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(
            h: Huerto,
            isSelected: Boolean,
            onClick: (Huerto) -> Unit,
            onEdit: (Huerto) -> Unit,
            onDelete: (Huerto) -> Unit
        ) {
            val ctx = b.root.context
            b.tvName.text = h.name
            val crop = Crops.byId(h.cropId)
            val parts = listOfNotNull(
                crop?.let { ctx.getString(it.nameRes) },
                h.location.takeIf { it.isNotBlank() },
                if (h.areaHa > 0) ctx.getString(R.string.area_fmt, h.areaHa) else null
            )
            b.tvDetail.text = parts.joinToString(" · ")
            b.tvActive.visibility = if (isSelected) android.view.View.VISIBLE else android.view.View.GONE
            b.root.setOnClickListener { onClick(h) }
            b.btnEdit.setOnClickListener { onEdit(h) }
            b.btnDelete.setOnClickListener { onDelete(h) }
        }
    }
}
