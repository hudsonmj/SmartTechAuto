package com.smarttech.auto.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.smarttech.auto.R
import com.smarttech.auto.model.ActionStep

class StepAdapter(private val steps: List<ActionStep>) :
    RecyclerView.Adapter<StepAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_macro_step, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val step = steps[position]
        holder.tvStepNumber.text = "${position + 1}"

        val actionEmoji = when (step.action) {
            "click" -> "👆"
            "hold" -> "✊"
            "click_if_exists" -> "🔍"
            "click_until" -> "🔄"
            "find_and_click" -> "🔎"
            "wait" -> "⏳"
            "swipe" -> "👉"
            "scroll" -> "📜"
            "type" -> "⌨️"
            "back" -> "🔙"
            "home" -> "🏠"
            "launch_app" -> "📱"
            "loop" -> "🔁"
            "wait_until" -> "⏰"
            else -> "⚡"
        }

        val actionName = when (step.action) {
            "click" -> "클릭"
            "hold" -> "길게누르기"
            "click_if_exists" -> "있으면 클릭"
            "click_until" -> "조건까지 클릭"
            "find_and_click" -> "찾아서 클릭"
            "wait" -> "대기"
            "swipe" -> "스와이프"
            "scroll" -> "스크롤"
            "type" -> "입력"
            "back" -> "뒤로가기"
            "home" -> "홈"
            "launch_app" -> "앱 실행"
            "loop" -> "반복"
            "wait_until" -> "대기"
            else -> step.action
        }

        val target = when {
            step.description != null -> step.description
            step.targetValue != null -> step.targetValue
            step.text != null -> step.text
            step.packageName != null -> step.packageName
            step.x != null && step.y != null -> {
                val coord = "(${step.x}, ${step.y})"
                when {
                    step.count != null && step.count > 1 -> "${coord} ${step.count}회"
                    step.ms != null -> "${coord} ${step.ms / 1000}초"
                    else -> coord
                }
            }
            step.count != null -> "${step.count}회"
            step.ms != null -> "${step.ms}ms"
            else -> ""
        }

        holder.tvStepAction.text = "$actionEmoji $actionName"
        holder.tvStepTarget.text = target
    }

    override fun getItemCount() = steps.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvStepNumber: TextView = view.findViewById(R.id.tv_step_number)
        val tvStepAction: TextView = view.findViewById(R.id.tv_step_action)
        val tvStepTarget: TextView = view.findViewById(R.id.tv_step_target)
    }
}
