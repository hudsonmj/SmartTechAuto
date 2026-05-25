package com.smarttech.auto.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.smarttech.auto.R
import com.smarttech.auto.model.MacroScript

class MacroListAdapter(
    private val items: List<MacroScript>,
    private val callback: Callback
) : RecyclerView.Adapter<MacroListAdapter.ViewHolder>() {

    interface Callback {
        fun onRun(macro: MacroScript)
        fun onEdit(macro: MacroScript)
        fun onDelete(macro: MacroScript)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_macro_list, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val macro = items[position]
        holder.tvName.text = macro.name
        holder.tvApp.text = if (macro.targetAppName.isNotEmpty()) "📱 ${macro.targetAppName}" else "📱 앱 미지정"
        holder.tvStepCount.text = "${macro.steps.size}단계"

        holder.btnRun.setOnClickListener { callback.onRun(macro) }
        holder.btnDelete.setOnClickListener { callback.onDelete(macro) }
        holder.itemView.setOnClickListener { callback.onEdit(macro) }
    }

    override fun getItemCount() = items.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tv_name)
        val tvApp: TextView = view.findViewById(R.id.tv_app)
        val tvStepCount: TextView = view.findViewById(R.id.tv_step_count)
        val btnRun: MaterialButton = view.findViewById(R.id.btn_run)
        val btnDelete: MaterialButton = view.findViewById(R.id.btn_delete)
    }
}
