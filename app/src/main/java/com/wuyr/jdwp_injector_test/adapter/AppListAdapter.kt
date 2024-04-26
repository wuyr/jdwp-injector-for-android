package com.wuyr.jdwp_injector_test.adapter

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.View
import com.bumptech.glide.Glide
import com.wuyr.jdwp_injector_test.databinding.ItemAppBinding

/**
 * @author wuyr
 * @github https://github.com/wuyr/jdwp-injector-for-android
 * @since 2023-11-16 上午11:58
 */
class AppListAdapter(context: Context, items: MutableList<AppItem> = mutableListOf()) :
    SingleTypeAdapter<AppItem, ItemAppBinding>(context, items, ItemAppBinding::class.java) {

    var onOpenButtonClick: ((AppItem) -> Unit)? = null
    var onShowDialogButtonClick: ((AppItem) -> Unit)? = null
    var onShowToastButtonClick: ((AppItem) -> Unit)? = null

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.binding.apply {
            Glide.with(context).load(item.icon).into(iconView)
            nameView.text = item.appName
            packageView.text = item.packageName
            val onClickListener = View.OnClickListener {
                when (it) {
                    openButton -> onOpenButtonClick?.invoke(items[holder.adapterPosition])
                    showDialogButton -> onShowDialogButtonClick?.invoke(items[holder.adapterPosition])
                    showToastButton -> onShowToastButtonClick?.invoke(items[holder.adapterPosition])
                }
            }
            arrayOf(openButton, showDialogButton, showToastButton).forEach { it.setOnClickListener(onClickListener) }
        }
    }
}

data class AppItem(val icon: Drawable, val appName: String, val packageName: String, val activityClassName: String)