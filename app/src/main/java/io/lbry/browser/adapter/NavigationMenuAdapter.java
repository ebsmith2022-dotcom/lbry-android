package io.lbry.browser.adapter;

import android.content.Context;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.core.view.ViewCompat;

import java.util.ArrayList;
import java.util.List;

import io.lbry.browser.R;
import io.lbry.browser.model.NavMenuItem;
import io.lbry.browser.ui.controls.SolidIconView;
import io.lbry.browser.utils.Helper;
import lombok.Setter;

public class NavigationMenuAdapter extends RecyclerView.Adapter<NavigationMenuAdapter.ViewHolder> {
    private static final int TYPE_GROUP = 1;
    private static final int TYPE_ITEM = 2;

    private final Context context;
    private final List<NavMenuItem> menuItems;
    private NavMenuItem currentItem;
    @Setter
    private NavigationMenuItemClickListener listener;

    public NavigationMenuAdapter(List<NavMenuItem> menuItems, Context context) {
        this.menuItems = new ArrayList<>(menuItems);
        this.context = context;
    }

    public void setCurrentItem(int id) {
        for (NavMenuItem item : menuItems) {
            if (item.getId() == id) {
                this.currentItem = item;
                break;
            }
        }
        notifyDataSetChanged();
    }

    public void setExtraLabelForItem(int id, String extraLabel) {
        for (NavMenuItem item : menuItems) {
            if (item.getId() == id) {
                item.setExtraLabel(extraLabel);
                break;
            }
        }
        notifyDataSetChanged();
    }

    public void setCurrentItem(NavMenuItem currentItem) {
        this.currentItem = currentItem;
        notifyDataSetChanged();
    }

    public int getCurrentItemId() {
        return currentItem != null ? currentItem.getId() : -1;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        protected final SolidIconView iconView;
        protected final TextView titleView;
        public ViewHolder(View v) {
            super(v);
            titleView = v.findViewById(R.id.nav_menu_title);
            iconView = v.findViewById(R.id.nav_menu_item_icon);
        }
    }

    @Override
    public int getItemCount() {
        return menuItems != null ? menuItems.size() : 0;
    }

    @Override
    public int getItemViewType(int position) {
        return menuItems.get(position).isGroup() ? TYPE_GROUP : TYPE_ITEM;
    }

    @Override
    public NavigationMenuAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(viewType == TYPE_GROUP ?
                R.layout.list_item_nav_menu_group : R.layout.list_item_nav_menu_item, parent, false);
        return new NavigationMenuAdapter.ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(ViewHolder vh, int position) {
        int type = getItemViewType(position);
        NavMenuItem item = menuItems.get(position);
        String displayTitle = !Helper.isNullOrEmpty(item.getExtraLabel()) ? String.format("%s (%s)", item.getTitle(), item.getExtraLabel()) : item.getTitle();
        vh.titleView.setText(displayTitle);
        if (type == TYPE_ITEM && vh.iconView != null) {
            vh.iconView.setText(item.getIcon());
        }
        boolean selected = type == TYPE_ITEM && currentItem != null && item.getId() == currentItem.getId();
        vh.itemView.setSelected(selected);
        ViewCompat.setAccessibilityHeading(vh.titleView, type == TYPE_GROUP);
        if (type == TYPE_GROUP) {
            vh.itemView.findViewById(R.id.nav_menu_group_divider).setVisibility(position == 0 ? View.GONE : View.VISIBLE);
            // Section labels are headings, not navigation destinations.
            vh.itemView.setOnClickListener(null);
            vh.itemView.setClickable(false);
            vh.itemView.setFocusable(false);
            return;
        }
        vh.titleView.setTypeface(vh.titleView.getTypeface(), selected ? Typeface.BOLD : Typeface.NORMAL);
        vh.itemView.findViewById(R.id.nav_menu_selected_indicator).setVisibility(selected ? View.VISIBLE : View.INVISIBLE);
        vh.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (listener != null) {
                    listener.onNavigationMenuItemClicked(item);
                }
            }
        });
    }

    public interface NavigationMenuItemClickListener {
        void onNavigationMenuItemClicked(NavMenuItem menuItem);
    }
}
