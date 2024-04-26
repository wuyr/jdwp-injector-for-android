package com.wuyr.jdwp_injector_test.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding


/**
 * @author wuyr
 * @github https://github.com/wuyr/jdwp-injector-for-android
 * @since 2023-11-16 上午11:58
 *
 *   用法：
 *
 *   ```
 *   val context: Context
 *   context.createAdapter<数据类型, ViewBinding类型>(初始数据List) { holder, position ->
 *       // 取得对应的ViewBinding实例
 *       holder.binding.apply {
 *           // do somethings
 *       }
 *   }
 *   ```
 *
 *   示例：
 *
 *   ```
 *   val items = listOf("01", "02", "03", "04", "05", "06")
 *   val adapter = context.createAdapter<String, ItemViewBinding>(items) { holder, position ->
 *       holder.binding.apply {
 *           val itemData = holder.adapter.data[position]
 *           // 设置文本
 *           textView.text = itemData
 *       }
 *       // 如需监听事件
 *       holder.adapter.apply {
 *           // 初始化点击事件
 *           holder.setupItemClickListener()
 *           // 初始化长按事件
 *           holder.setupItemLongClickListener()
 *       }
 *   }.apply {
 *       // 监听点击
 *       onItemClickListener = { itemData, position ->
 *           Toast.makeText(context, "点击了第${position}个item，内容为:$itemData", Toast.LENGTH_SHORT).show()
 *       }
 *       // 监听长按
 *       onItemLongClickListener = { itemData, position ->
 *           Toast.makeText(context, "长按了第${position}个item，内容为:$itemData", Toast.LENGTH_SHORT).show()
 *           true
 *       }
 *   }
 *   ```
 */
abstract class SingleTypeAdapter<DATA_TYPE, VIEW_BINDING : ViewBinding>(
    protected var context: Context, items: MutableList<DATA_TYPE>, private val viewBindingClass: Class<VIEW_BINDING>
) : RecyclerView.Adapter<SingleTypeAdapter<DATA_TYPE, VIEW_BINDING>.ViewHolder>() {

    companion object {
        inline fun <DATA_TYPE, reified VIEW_BINDING : ViewBinding> Context.createAdapter(
            items: MutableList<DATA_TYPE> = ArrayList(), crossinline onBind: (SingleTypeAdapter<DATA_TYPE, VIEW_BINDING>.ViewHolder, Int) -> Unit
        ) = object : SingleTypeAdapter<DATA_TYPE, VIEW_BINDING>(this@createAdapter, items, VIEW_BINDING::class.java) {
            override fun onBindViewHolder(holder: ViewHolder, position: Int) {
                if (position > -1 && position < holder.adapter.itemCount) {
                    onBind(holder, position)
                }
            }
        }
    }

    open var items: MutableList<DATA_TYPE> = items
        set(value) {
            val itemCount = itemCount
            field.clear()
            notifyItemRangeRemoved(0, itemCount)
            field = value
            notifyItemRangeInserted(0, field.size)
            onSizeChangedListener?.invoke(itemCount)
        }

    open fun setItemsWithoutUpdate(newList: List<DATA_TYPE>) {
        items.clear()
        items.addAll(newList)
    }

    open fun appendItem(index: Int, item: DATA_TYPE) {
        items.add(index, item)
        notifyItemInserted(index)
        onSizeChangedListener?.invoke(itemCount)
    }

    open fun appendItem(item: DATA_TYPE) {
        items.add(item)
        notifyItemInserted(itemCount - 1)
        onSizeChangedListener?.invoke(itemCount)
    }

    open fun appendData(index: Int, appendList: List<DATA_TYPE>) {
        items.addAll(index, appendList)
        notifyItemRangeInserted(itemCount, appendList.size)
        onSizeChangedListener?.invoke(itemCount)
    }

    open fun appendData(appendList: List<DATA_TYPE>) {
        items.addAll(appendList)
        notifyItemRangeInserted(itemCount, appendList.size)
        onSizeChangedListener?.invoke(itemCount)
    }

    open fun removeItem(item: DATA_TYPE): Boolean {
        val index = items.indexOf(item)
        if (index > -1) {
            items.removeAt(index)
            notifyItemRemoved(index)
            onSizeChangedListener?.invoke(itemCount)
            return true
        }
        return false
    }

    open fun removeItemAt(index: Int): Boolean {
        if (index > -1 && index < itemCount) {
            items.removeAt(index)
            notifyItemRemoved(index)
            onSizeChangedListener?.invoke(itemCount)
            return true
        }
        return false
    }

    open fun clear() {
        val itemCount = itemCount
        items.clear()
        notifyItemRangeRemoved(0, itemCount)
        onSizeChangedListener?.invoke(itemCount)
    }

    private val inflateMethod by lazy {
        viewBindingClass.getMethod("inflate", LayoutInflater::class.java, ViewGroup::class.java, Boolean::class.java)
    }

    @Suppress("UNCHECKED_CAST")
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(this, inflateMethod.invoke(null, LayoutInflater.from(context), parent, false) as VIEW_BINDING)

    var onItemClickListener: ((DATA_TYPE, Int) -> Unit)? = null
    var onItemLongClickListener: ((DATA_TYPE, Int) -> Unit)? = null
    var onSizeChangedListener: ((Int) -> Unit)? = null

    fun ViewHolder.setupItemClickListener() = itemView.setOnClickListener {
        val index = adapterPosition
        if (index > -1 && index < itemCount) {
            onItemClickListener?.invoke(items[index], index)
        }
    }

    fun ViewHolder.setupItemLongClickListener() = itemView.setOnLongClickListener {
        val index = adapterPosition
        if (index > -1 && index < itemCount) {
            onItemLongClickListener?.invoke(items[index], index)
        }
        true
    }

    override fun getItemCount() = items.size

    inner class ViewHolder(val adapter: SingleTypeAdapter<DATA_TYPE, VIEW_BINDING>, var binding: VIEW_BINDING) : RecyclerView.ViewHolder(binding.root)

}