/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pluscubed.logcat.data

import android.annotation.SuppressLint
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import androidx.recyclerview.widget.RecyclerView
import org.omnirom.logcat.R
import com.pluscubed.logcat.filter.FilterSet
import com.pluscubed.logcat.helper.PreferenceHelper
import com.pluscubed.logcat.util.LogLineAdapterUtil

// the log list; while filtered, allValues keeps every line and lines only the visible ones
class LogLineAdapter(
    private val clickListener: LogLineViewHolder.OnClickListener,
    private val selection: LogSelection,
    private val search: LogSearch,
) : RecyclerView.Adapter<LogLineViewHolder>(), Filterable {

    private val lock = Any()
    private var lines: MutableList<LogLine> = ArrayList()
    private var allValues: MutableList<LogLine>? = null
    private val lineFilter = LineFilter()

    @Volatile
    var searchRegex = false

    @Volatile
    var logLevelLimit = 0

    @Volatile
    var userFilters: FilterSet = FilterSet.EMPTY

    init {
        setHasStableIds(true)
    }

    val objects: List<LogLine>
        get() = lines

    val trueValues: List<LogLine>
        get() = allValues ?: lines

    fun addWithFilter(line: LogLine, query: CharSequence?) {
        synchronized(lock) {
            if (allValues == null && !userFilters.isEmpty) allValues = ArrayList(lines)
            val all = allValues
            if (all == null) {
                lines.add(line)
            } else {
                all.add(line)
                lines.addAll(lineFilter.filterLines(listOf(line), query))
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun removeFirst(count: Int) {
        synchronized(lock) {
            val all = allValues
            if (all != null) {
                val removed = all.subList(0, count).toHashSet()
                lines.removeAll { it in removed }
                allValues = ArrayList(all.subList(count, all.size))
            } else {
                lines = ArrayList(lines.subList(count, lines.size))
            }
        }
        notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun clear() {
        synchronized(lock) {
            allValues?.clear()
            lines.clear()
        }
        notifyDataSetChanged()
    }

    fun getItem(position: Int): LogLine = lines[position]

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogLineViewHolder =
        LogLineViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.list_item_logcat, parent, false),
            clickListener,
        )

    override fun onBindViewHolder(holder: LogLineViewHolder, position: Int) {
        val context = holder.itemView.context
        val line = lines.getOrNull(position) ?: return
        holder.logLine = line
        holder.itemView.isActivated = selection.isSelected(line)

        val textSize = PreferenceHelper.getTextSizePreference(context)
        val options = LogDisplayOptions.get(context)
        val isLogLine = line.logLevel != -1
        val searching = search.isActive

        holder.levelText.apply {
            text = line.processIdText
            setBackgroundColor(LogLineAdapterUtil.getBackgroundColorForLogLevel(context, line.logLevel))
            setTextColor(LogLineAdapterUtil.getForegroundColorForLogLevel(context, line.logLevel))
            visibility = if (isLogLine) View.VISIBLE else View.GONE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize)
        }
        holder.outputText.apply {
            isSingleLine = !line.isExpanded
            text = if (searching) search.highlightOutput(line.logOutput, position) else line.logOutput
            setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize)
        }
        holder.tagText.apply {
            isSingleLine = !line.isExpanded
            text = if (searching) search.highlightTag(line.tag, position) else line.tag
            visibility = if (isLogLine && options.showTag) View.VISIBLE else View.GONE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize)
            setTextColor(LogLineAdapterUtil.getOrCreateTagColor(context, line.tag))
        }

        val showDetails = line.isExpanded && options.hasDetails(line)
        holder.pidText.visibility =
            if (showDetails && options.showPid && line.processId != -1) View.VISIBLE else View.GONE
        holder.timestampText.visibility = if (showDetails) View.VISIBLE else View.GONE
        if (showDetails) {
            holder.pidText.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize)
            holder.timestampText.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize)
            holder.pidText.text = line.processId.toString()
            holder.timestampText.text = options.details(line)
        }
    }

    override fun getItemId(position: Int): Long = synchronized(lock) {
        lines[position].originalLine?.hashCode()?.toLong() ?: position.toLong()
    }

    override fun getItemCount(): Int = synchronized(lock) { lines.size }

    override fun getFilter(): Filter = lineFilter

    private inner class LineFilter : Filter() {

        override fun performFiltering(query: CharSequence?): FilterResults {
            val source = synchronized(lock) {
                val all = allValues ?: ArrayList(lines).also { allValues = it }
                ArrayList(all)
            }
            val values = filterLines(source, query)
            return FilterResults().apply {
                this.values = values
                count = values.size
            }
        }

        fun filterLines(input: List<LogLine>, query: CharSequence?): List<LogLine> {
            val criteria = SearchCriteria(query, searchRegex)
            val limit = logLevelLimit
            val filters = userFilters
            return input.filter { line ->
                LogLineAdapterUtil.logLevelIsAcceptableGivenLogLevelLimit(line.logLevel, limit) &&
                    filters.accepts(line) && (criteria.isEmpty || criteria.matches(line))
            }
        }

        @SuppressLint("NotifyDataSetChanged")
        @Suppress("UNCHECKED_CAST")
        override fun publishResults(query: CharSequence?, results: FilterResults) {
            synchronized(lock) { lines = (results.values as List<LogLine>).toMutableList() }
            notifyDataSetChanged()
        }
    }
}
