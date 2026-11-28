/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.pluscubed.logcat.ui

import android.annotation.SuppressLint
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.view.menu.ActionMenuItemView
import androidx.appcompat.widget.ActionMenuView
import androidx.appcompat.widget.Toolbar
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.ClearAll
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.FilterAltOff
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.SavedSearch
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import com.pluscubed.logcat.data.LogLineViewHolder
import com.pluscubed.logcat.ui.compose.MatlogTheme
import java.util.function.IntConsumer
import org.omnirom.logcat.R

private class MenuEntry(val id: Int, val title: Int, val icon: ImageVector, val checked: Boolean? = null)

// the log screen's menus, shown as Material 3 Expressive dropdowns
object LogcatOverflowMenu {

    private const val HOST_TAG = "matlog_overflow_menu"
    private var shown = 0

    @JvmStatic
    fun showMain(
        toolbar: Toolbar,
        anchorItemId: Int,
        kernelAvailable: Boolean,
        kernelMode: Boolean,
        pstoreAvailable: Boolean,
        onItemSelected: IntConsumer,
    ) {
        show(toolbar, anchorItemId, onItemSelected, listOf(
            listOfNotNull(
                MenuEntry(R.id.menu_log_level, R.string.log_level, Icons.Outlined.Tune),
                MenuEntry(R.id.menu_saved_searches, R.string.matlog_saved_searches, Icons.Outlined.SavedSearch),
                if (kernelAvailable) {
                    MenuEntry(R.id.menu_kernel_log, R.string.matlog_kernel_log, Icons.Outlined.Terminal, kernelMode)
                } else null,
            ),
            listOfNotNull(
                MenuEntry(R.id.menu_crashes, R.string.matlog_crashes, Icons.Outlined.BugReport),
                if (pstoreAvailable) MenuEntry(R.id.menu_pstore, R.string.matlog_pstore, Icons.Outlined.Memory) else null,
            ),
            listOf(MenuEntry(R.id.menu_export_log, R.string.matlog_export, Icons.Outlined.FileUpload)),
        ))
    }

    @JvmStatic
    fun showFind(toolbar: Toolbar, anchorItemId: Int, regex: Boolean, onItemSelected: IntConsumer) {
        show(toolbar, anchorItemId, onItemSelected, listOf(
            listOf(MenuEntry(R.id.menu_search_regex, R.string.matlog_search_regex, Icons.Outlined.Code, regex)),
        ))
    }

    @JvmStatic
    fun showSelection(toolbar: Toolbar, anchorItemId: Int, onItemSelected: IntConsumer) {
        show(toolbar, anchorItemId, onItemSelected, listOf(
            listOf(
                MenuEntry(R.id.menu_selection_export, R.string.matlog_export, Icons.Outlined.FileUpload),
                MenuEntry(R.id.menu_selection_extended_copy, R.string.matlog_extended_copy, Icons.Outlined.EditNote),
            ),
        ))
    }

    // the long-press menu of a log line, opened from its row
    @JvmStatic
    fun showLogLine(row: View, filterable: Boolean, onItemSelected: IntConsumer) {
        show(row, onItemSelected, listOfNotNull(
            listOf(
                MenuEntry(LogLineViewHolder.CONTEXT_MENU_COPY_ID, R.string.copy_to_clipboard, Icons.Outlined.ContentCopy),
                MenuEntry(LogLineViewHolder.CONTEXT_MENU_SELECT, R.string.matlog_select, Icons.Outlined.SelectAll),
            ),
            if (filterable) listOf(
                MenuEntry(LogLineViewHolder.CONTEXT_MENU_FILTER_ID, R.string.filter_choice, Icons.Outlined.Search),
                MenuEntry(LogLineViewHolder.CONTEXT_MENU_INCLUDE_FILTER, R.string.matlog_filter_from_line_include,
                    Icons.Outlined.FilterAlt),
                MenuEntry(LogLineViewHolder.CONTEXT_MENU_EXCLUDE_FILTER, R.string.matlog_filter_from_line_exclude,
                    Icons.Outlined.FilterAltOff),
            ) else null,
            listOf(
                MenuEntry(LogLineViewHolder.CONTEXT_MENU_EXPAND_ALL, R.string.expand_or_collapse_all, Icons.Outlined.UnfoldMore),
                MenuEntry(LogLineViewHolder.CONTEXT_MENU_CLEAR, R.string.clear, Icons.Outlined.ClearAll),
            ),
        ))
    }

    private fun show(toolbar: Toolbar, anchorItemId: Int, onItemSelected: IntConsumer, groups: List<List<MenuEntry>>) {
        show(findItemView(toolbar, anchorItemId) ?: toolbar, onItemSelected, groups)
    }

    private fun show(anchor: View, onItemSelected: IntConsumer, groups: List<List<MenuEntry>>) {
        val token = ++shown
        hostOver(anchor).setContent {
            MatlogTheme(surface = false) {
                key(token) { OverflowMenu(groups) { onItemSelected.accept(it) } }
            }
        }
    }

    @SuppressLint("RestrictedApi")
    private fun findItemView(toolbar: Toolbar, itemId: Int): View? {
        for (i in 0 until toolbar.childCount) {
            val menuView = toolbar.getChildAt(i) as? ActionMenuView ?: continue
            for (j in 0 until menuView.childCount) {
                val child = menuView.getChildAt(j)
                if ((child as? ActionMenuItemView)?.itemData?.itemId == itemId) return child
            }
        }
        return null
    }

    // a host laid over the anchor so the dropdown opens from it
    private fun hostOver(anchor: View): ComposeView {
        val root = anchor.rootView.findViewById<ViewGroup>(android.R.id.content)
        val host = root.findViewWithTag<ComposeView>(HOST_TAG) ?: ComposeView(anchor.context).also {
            it.tag = HOST_TAG
            root.addView(it, FrameLayout.LayoutParams(0, 0))
        }
        val anchorLocation = IntArray(2).also { anchor.getLocationInWindow(it) }
        val rootLocation = IntArray(2).also { root.getLocationInWindow(it) }
        host.layoutParams = (host.layoutParams as FrameLayout.LayoutParams).apply {
            width = anchor.width
            height = anchor.height
            leftMargin = anchorLocation[0] - rootLocation[0]
            topMargin = anchorLocation[1] - rootLocation[1]
        }
        return host
    }
}

@Composable
private fun OverflowMenu(groups: List<List<MenuEntry>>, onItemSelected: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(true) }
    val select = { id: Int ->
        expanded = false
        onItemSelected(id)
    }
    DropdownMenuPopup(expanded = expanded, onDismissRequest = { expanded = false }) {
        groups.forEachIndexed { groupIndex, entries ->
            if (groupIndex > 0) Spacer(Modifier.height(MenuDefaults.GroupSpacing))
            DropdownMenuGroup(shapes = MenuDefaults.groupShape(groupIndex, groups.size)) {
                entries.forEachIndexed { index, entry ->
                    MenuItem(entry, MenuDefaults.itemShape(index, entries.size).shape) { select(entry.id) }
                }
            }
        }
    }
}

@Composable
private fun MenuItem(entry: MenuEntry, shape: Shape, onClick: () -> Unit) {
    val checked = entry.checked
    DropdownMenuItem(
        onClick = onClick,
        text = { Text(stringResource(entry.title)) },
        shape = shape,
        modifier = if (checked == null) Modifier else Modifier.semantics {
            role = Role.Switch
            toggleableState = ToggleableState(checked)
        },
        leadingIcon = { Icon(entry.icon, contentDescription = null) },
        trailingIcon = checked?.let { isChecked ->
            @Composable {
                Switch(
                    checked = isChecked,
                    onCheckedChange = null,
                    thumbContent = {
                        Icon(
                            imageVector = if (isChecked) Icons.Filled.Check else Icons.Filled.Close,
                            contentDescription = null,
                            modifier = Modifier.size(SwitchDefaults.IconSize),
                        )
                    },
                )
            }
        },
    )
}
