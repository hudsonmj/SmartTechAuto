package com.smarttech.auto.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.smarttech.auto.R
import com.smarttech.auto.model.RecordedAction

class RecordedActionAdapter(private val items: MutableList<RecordedAction>) :
    RecyclerView.Adapter<RecordedActionAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val action = items[position]
        val (icon, title, subtitle) = when (action.type) {
            RecordedAction.Type.TOUCH -> {
                val info = buildString {
                    append("(${action.x}, ${action.y})")
                    if (!action.uiText.isNullOrEmpty()) append(" · ${action.uiText.take(30)}")
                }
                Triple("👆", "터치", info)
            }
            RecordedAction.Type.NOTE -> {
                Triple("📝", "설명", action.note ?: "")
            }
        }

        holder.tvTitle.text = "$icon ${position + 1}. $title"
        holder.tvSubtitle.text = subtitle
    }

    override fun getItemCount() = items.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(android.R.id.text1)
        val tvSubtitle: TextView = view.findViewById(android.R.id.text2)
    }
}
