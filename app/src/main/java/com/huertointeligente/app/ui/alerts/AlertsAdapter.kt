package com.huertointeligente.app.ui.alerts

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.huertointeligente.app.R
import com.huertointeligente.app.data.model.AlertItem
import com.huertointeligente.app.data.model.AlertType
import com.huertointeligente.app.data.model.Severity
import com.huertointeligente.app.databinding.ItemAlertBinding
import com.huertointeligente.app.util.relativeTime

class AlertsAdapter : RecyclerView.Adapter<AlertsAdapter.VH>() {

    private var items: List<AlertItem> = emptyList()

    fun submit(list: List<AlertItem>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemAlertBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

    override fun getItemCount() = items.size

    class VH(private val b: ItemAlertBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: AlertItem) {
            val ctx = b.root.context
            val icon = when (item.type) {
                AlertType.SOIL_DRY -> R.drawable.ic_water
                AlertType.SOIL_WET -> R.drawable.ic_water
                AlertType.HEAT -> R.drawable.ic_sun
                AlertType.COLD -> R.drawable.ic_thermostat
                AlertType.LOW_BATTERY -> R.drawable.ic_battery
                AlertType.OFFLINE -> R.drawable.ic_wifi
            }
            val colorRes = when (item.severity) {
                Severity.CRITICAL -> R.color.status_bad
                Severity.WARNING -> R.color.status_warn
                Severity.INFO -> R.color.status_info
            }
            val color = ContextCompat.getColor(ctx, colorRes)
            b.ivIcon.setImageResource(icon)
            b.ivIcon.imageTintList = android.content.res.ColorStateList.valueOf(color)
            b.tvTitle.text = item.title
            b.tvTitle.setTextColor(color)
            b.tvMessage.text = item.message
            b.tvMeta.text = relativeTime(item.timestamp)
            b.unreadDot.visibility = if (item.read) android.view.View.INVISIBLE else android.view.View.VISIBLE
        }
    }
}
