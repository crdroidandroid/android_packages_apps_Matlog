package com.pluscubed.logcat.data;

import android.view.MenuItem;
import android.view.View;
import android.widget.PopupMenu;

import org.omnirom.logcat.R;

import androidx.recyclerview.widget.RecyclerView;


/**
 * ViewHolder to show log entries
 *
 * @author nlawson
 */
public class LogLineViewHolder extends RecyclerView.ViewHolder implements PopupMenu.OnMenuItemClickListener, View.OnClickListener, View.OnLongClickListener {
    // id for context menu entry
    public static final int CONTEXT_MENU_FILTER_ID = 0;
    public static final int CONTEXT_MENU_COPY_ID = 1;
    public static final int CONTEXT_MENU_EXPAND_ALL = 2;
    public static final int CONTEXT_MENU_CLEAR = 3;

    LogLine logLine;

    private final OnClickListener clickListener;

    public LogLineViewHolder(View itemView, final OnClickListener clickListener) {
        super(itemView);

        this.clickListener = clickListener;

        itemView.setOnClickListener(this);
        itemView.setOnLongClickListener(this);
    }

    @Override
    public void onClick(View v) {
        clickListener.onClick(v, logLine);
    }

    @Override
    public boolean onLongClick(View v) {
        PopupMenu menu = new PopupMenu(v.getContext(), v);
        menu.getMenu().add(0, CONTEXT_MENU_FILTER_ID, 0, R.string.filter_choice);
        menu.getMenu().add(0, CONTEXT_MENU_COPY_ID, 0, R.string.copy_to_clipboard);
        menu.getMenu().add(1, CONTEXT_MENU_CLEAR, 0, R.string.clear);
        menu.getMenu().add(1, CONTEXT_MENU_EXPAND_ALL, 0, R.string.expand_or_collapse_all);
        menu.setOnMenuItemClickListener(LogLineViewHolder.this);
        menu.getMenu().setGroupDividerEnabled(true);
        menu.show();
        return true;
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        return clickListener.onMenuItemClick(item, logLine);
    }

    public interface OnClickListener {
        void onClick(View itemView, LogLine logLine);
        boolean onMenuItemClick(MenuItem item, LogLine logLine);
    }
}
