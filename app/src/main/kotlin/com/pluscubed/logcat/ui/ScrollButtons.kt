/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pluscubed.logcat.ui

import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton

// shows a jump button in the direction the user scrolls and hides it once scrolling settles
class ScrollButtons(
    private val list: RecyclerView,
    private val top: FloatingActionButton,
    private val bottom: FloatingActionButton,
    onTop: () -> Unit,
    onBottom: () -> Unit,
) {

    private val hide = Runnable { hideAll() }

    init {
        top.setOnClickListener {
            hideAll()
            onTop()
        }
        bottom.setOnClickListener {
            hideAll()
            onBottom()
        }
        list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                list.removeCallbacks(hide)
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    list.postDelayed(hide, HIDE_DELAY_MS)
                }
            }

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy == 0 || recyclerView.scrollState == RecyclerView.SCROLL_STATE_IDLE) return
                if (dy < 0 && recyclerView.canScrollVertically(-1)) {
                    bottom.hide()
                    top.show()
                } else if (dy > 0 && recyclerView.canScrollVertically(1)) {
                    top.hide()
                    bottom.show()
                } else {
                    hideAll()
                }
            }
        })
    }

    fun hideAll() {
        list.removeCallbacks(hide)
        top.hide()
        bottom.hide()
    }

    private companion object {
        const val HIDE_DELAY_MS = 2000L
    }
}
