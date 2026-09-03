package com.zynexbd.crmsolution.adapters

import android.graphics.Color
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.zynexbd.crmsolution.R
import com.zynexbd.crmsolution.databinding.ItemSubscriptionPlanCardBinding
import com.zynexbd.crmsolution.models.SubscriptionPlan
import com.zynexbd.crmsolution.utils.LanguageManager

class SubscriptionPlanAdapter(
    private val onSubscribeClick: (SubscriptionPlan) -> Unit
) : RecyclerView.Adapter<SubscriptionPlanAdapter.ViewHolder>() {

    private var items: List<SubscriptionPlan> = emptyList()

    fun setItems(list: List<SubscriptionPlan>) {
        items = list
        notifyDataSetChanged()
    }

    inner class ViewHolder(val binding: ItemSubscriptionPlanCardBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSubscriptionPlanCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val plan = items[position]
        val context = holder.itemView.context
        val isEn = LanguageManager.isEnglish(context)

        with(holder.binding) {
            textTierName.text = plan.tierName
            textPlanTitle.text = if (isEn) plan.title else plan.titleBn

            // Price formatting
            val priceInt = plan.price.toInt()
            val originalInt = plan.originalPrice.toInt()

            textPrice.text = "৳$priceInt"

            if (originalInt > priceInt) {
                textOriginalPrice.visibility = View.VISIBLE
                textOriginalPrice.text = "৳$originalInt"
                textOriginalPrice.paintFlags = textOriginalPrice.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                textOriginalPrice.visibility = View.GONE
            }

            // Duration and Savings
            val durationText = if (isEn) "${plan.durationMonths} Month${if (plan.durationMonths > 1) "s" else ""}" else "${plan.durationMonths} মাস"
            val savings = originalInt - priceInt
            if (savings > 0) {
                textDurationDiscount.text = if (isEn) "Validity: $durationText • Save ৳$savings" else "মেয়াদ: $durationText • সাশ্রয় ৳$savings (${plan.discountPercent}% ছাড়)"
                textDurationDiscount.setTextColor(Color.parseColor("#059669"))
            } else {
                textDurationDiscount.text = if (isEn) "Validity: $durationText" else "মেয়াদ: $durationText"
                textDurationDiscount.setTextColor(Color.parseColor("#64748B"))
            }

            // Badge Pill
            val badge = if (isEn) plan.badgeText else plan.badgeTextBn
            if (!badge.isNullOrBlank()) {
                badgeOffer.visibility = View.VISIBLE
                badgeOffer.text = badge
                badgeOffer.setTextColor(Color.WHITE)
                when (plan.tierName.lowercase()) {
                    "silver" -> {
                        badgeOffer.setBackgroundResource(R.drawable.bg_badge_primary)
                        textTierName.setTextColor(Color.parseColor("#1D4ED8"))
                    }
                    "gold" -> {
                        badgeOffer.setBackgroundResource(R.drawable.bg_badge_warning)
                        textTierName.setTextColor(Color.parseColor("#B45309"))
                    }
                    "platinum" -> {
                        badgeOffer.setBackgroundResource(R.drawable.bg_badge_purple)
                        textTierName.setTextColor(Color.parseColor("#6D28D9"))
                    }
                    else -> {
                        badgeOffer.setBackgroundResource(R.drawable.bg_status_active_pill)
                        textTierName.setTextColor(Color.parseColor("#0F172A"))
                    }
                }
            } else {
                badgeOffer.visibility = View.GONE
            }

            // Feature bullets
            layoutFeatures.removeAllViews()
            val features = plan.getFeaturesList()
            for (feature in features) {
                val featureRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(0, 4, 0, 4)
                    }
                }

                val icon = TextView(context).apply {
                    text = "✓ "
                    setTextColor(Color.parseColor("#059669"))
                    textSize = 12f
                }

                val text = TextView(context).apply {
                    this.text = feature
                    setTextColor(Color.parseColor("#334155"))
                    textSize = 12f
                }

                featureRow.addView(icon)
                featureRow.addView(text)
                layoutFeatures.addView(featureRow)
            }

            buttonSubscribe.setOnClickListener {
                onSubscribeClick(plan)
            }
        }
    }
}
