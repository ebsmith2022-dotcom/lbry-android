package io.lbry.browser.adapter;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.lbry.browser.R;
import io.lbry.browser.model.Claim;
import io.lbry.browser.model.LbryFile;
import io.lbry.browser.utils.Helper;

/** One scrolling feed, with optional shelves backed by the viewer's own data. */
public class CinemaHomeAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int HERO = 1;
    private static final int CONTINUE = 2;
    private static final int FOLLOWING = 3;
    private static final int HEADING = 4;
    private static final int EXPLORE = 5;
    private static final int DISCOVER = 6;
    private static final int LOAD_MORE = 7;
    private static final int STREAM_TYPE_OFFSET = 100;
    private final Context context;
    private final List<Claim> items = new ArrayList<>();
    private final List<Claim> continueWatching = new ArrayList<>();
    private final List<Claim> following = new ArrayList<>();
    private final List<Claim> visibleContinue = new ArrayList<>();
    private final List<Claim> visibleFollowing = new ArrayList<>();
    private final List<Claim> explore = new ArrayList<>();
    private final List<Claim> discover = new ArrayList<>();
    private final List<Integer> prefixTypes = new ArrayList<>();
    private final ClaimListAdapter remainder;
    private final ClaimListAdapter.ClaimListItemListener listener;
    private final Runnable openHistory;
    private final Runnable openFollowing;
    private final Runnable loadMore;
    private boolean showPersonalShelves = true;
    private boolean loadMoreVisible;

    public CinemaHomeAdapter(Context context, ClaimListAdapter.ClaimListItemListener listener,
                             Runnable openHistory, Runnable openFollowing, Runnable loadMore) {
        this.context = context;
        this.listener = listener;
        this.openHistory = openHistory;
        this.openFollowing = openFollowing;
        this.loadMore = loadMore;
        remainder = new ClaimListAdapter(Collections.emptyList(), context);
        remainder.setListener(listener);
    }

    public int getContentCount() { return items.size(); }
    public int getDisplayContentCount() { return getItemCount() - (loadMoreVisible ? 1 : 0); }

    public void setLoadMoreVisible(boolean visible) {
        if (loadMoreVisible != visible) {
            loadMoreVisible = visible;
            notifyDataSetChanged();
        }
    }

    public void setShowPersonalShelves(boolean show) {
        if (showPersonalShelves != show) {
            showPersonalShelves = show;
            rebuildRows();
        }
    }

    public void setContinueWatching(List<Claim> claims) {
        continueWatching.clear();
        continueWatching.addAll(claims);
        rebuildRows();
    }

    public void setFollowing(List<Claim> claims) {
        following.clear();
        following.addAll(claims);
        rebuildRows();
    }

    public void clearItems() {
        items.clear();
        rebuildRows();
    }

    public void addItems(List<Claim> claims) {
        for (Claim claim : claims) {
            if (claim == null) continue;
            boolean exists = false;
            for (Claim current : items) {
                if (!Helper.isNullOrEmpty(claim.getClaimId()) && claim.getClaimId().equals(current.getClaimId())) {
                    exists = true;
                    break;
                }
            }
            if (!exists) items.add(claim);
        }
        rebuildRows();
    }

    public void clearFileForClaimOrUrl(String outpoint, String uri) {
        remainder.clearFileForClaimOrUrl(outpoint, uri);
        notifyDataSetChanged();
    }

    public void updateFileForClaimByIdOrUrl(LbryFile file, String claimId, String uri) {
        remainder.updateFileForClaimByIdOrUrl(file, claimId, uri);
        notifyDataSetChanged();
    }

    private void rebuildRows() {
        prefixTypes.clear();
        visibleContinue.clear();
        visibleFollowing.clear();
        explore.clear();
        discover.clear();
        Set<String> used = new HashSet<>();
        if (!items.isEmpty()) {
            prefixTypes.add(HERO);
            used.add(key(items.get(0)));
        }
        if (showPersonalShelves) visibleContinue.addAll(takeUnique(continueWatching, used, 12));
        // Reserve actual followed videos before filling fallback shelves from the general feed.
        if (showPersonalShelves) visibleFollowing.addAll(takeUnique(following, used, 12));
        if (!visibleContinue.isEmpty()) {
            prefixTypes.add(CONTINUE);
        } else {
            explore.addAll(takeUnique(items, used, 8));
            if (!explore.isEmpty()) prefixTypes.add(EXPLORE);
        }
        if (!visibleFollowing.isEmpty()) {
            prefixTypes.add(FOLLOWING);
        } else {
            discover.addAll(takeUnique(items, used, 8));
            if (!discover.isEmpty()) prefixTypes.add(DISCOVER);
        }
        List<Claim> remaining = takeUnique(items, used, Integer.MAX_VALUE);
        remainder.clearItems();
        remainder.setItems(remaining);
        if (!remaining.isEmpty()) prefixTypes.add(HEADING);
        notifyDataSetChanged();
    }

    private List<Claim> takeUnique(List<Claim> source, Set<String> used, int limit) {
        List<Claim> result = new ArrayList<>();
        for (Claim claim : source) {
            if (claim != null && used.add(key(claim))) {
                result.add(claim);
                if (result.size() == limit) break;
            }
        }
        return result;
    }

    private String key(Claim original) {
        Claim claim = Claim.TYPE_REPOST.equalsIgnoreCase(original.getValueType()) && original.getRepostedClaim() != null
                ? original.getRepostedClaim() : original;
        return !Helper.isNullOrEmpty(claim.getClaimId()) ? claim.getClaimId() : claim.getPermanentUrl();
    }

    @Override public int getItemCount() {
        return prefixTypes.size() + remainder.getItemCount() + (loadMoreVisible ? 1 : 0);
    }

    @Override public int getItemViewType(int position) {
        if (loadMoreVisible && position == getItemCount() - 1) return LOAD_MORE;
        if (position < prefixTypes.size()) return prefixTypes.get(position);
        return STREAM_TYPE_OFFSET + remainder.getItemViewType(position - prefixTypes.size());
    }

    @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (type == HERO) return new CardHolder(inflater.inflate(R.layout.cinema_b_home_hero, parent, false));
        if (type == LOAD_MORE) {
            TextView button = new TextView(parent.getContext());
            button.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    Math.round(56 * context.getResources().getDisplayMetrics().density)));
            button.setText(R.string.cinema_b_home_load_more);
            button.setTextColor(ContextCompat.getColor(context, R.color.cinemaAccent));
            button.setTypeface(ResourcesCompat.getFont(context, R.font.inter));
            button.setTextSize(14);
            button.setGravity(Gravity.CENTER);
            button.setFocusable(true);
            button.setOnClickListener(v -> loadMore.run());
            return new RecyclerView.ViewHolder(button) {};
        }
        if (type < STREAM_TYPE_OFFSET) {
            return new ShelfHolder(inflater.inflate(R.layout.cinema_b_home_shelf, parent, false));
        }
        return remainder.onCreateViewHolder(parent, type - STREAM_TYPE_OFFSET);
    }

    @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        int type = getItemViewType(position);
        if (type == LOAD_MORE) {
            holder.itemView.setOnClickListener(v -> loadMore.run());
        } else if (type == HERO) {
            bindCard((CardHolder) holder, items.get(0), false, true);
        } else if (type < STREAM_TYPE_OFFSET) {
            ShelfHolder shelf = (ShelfHolder) holder;
            boolean history = type == CONTINUE;
            shelf.title.setText(history ? R.string.cinema_b_home_continue :
                    type == FOLLOWING ? R.string.cinema_b_home_following :
                    type == EXPLORE ? R.string.cinema_b_home_explore_shelf :
                    type == DISCOVER ? R.string.cinema_b_home_discover_shelf : R.string.cinema_b_home_explore);
            shelf.list.setVisibility(type == HEADING ? View.GONE : View.VISIBLE);
            boolean personal = type == CONTINUE || type == FOLLOWING;
            shelf.more.setVisibility(personal ? View.VISIBLE : View.GONE);
            shelf.heading.setClickable(personal);
            shelf.heading.setFocusable(personal);
            shelf.heading.setContentDescription(null);
            shelf.heading.setOnClickListener(!personal ? null : v -> {
                if (history) openHistory.run(); else openFollowing.run();
            });
            if (type != HEADING) {
                if (personal) shelf.heading.setContentDescription(context.getString(history ?
                        R.string.cinema_b_home_open_history : R.string.cinema_b_home_open_following));
                shelf.cards.setItems(history ? visibleContinue : type == FOLLOWING ? visibleFollowing :
                        type == EXPLORE ? explore : discover, history);
            }
        } else {
            remainder.onBindViewHolder((ClaimListAdapter.ViewHolder) holder, position - prefixTypes.size());
        }
    }

    private final class ShelfHolder extends RecyclerView.ViewHolder {
        final TextView title;
        final View heading;
        final View more;
        final RecyclerView list;
        final CardAdapter cards;
        ShelfHolder(View view) {
            super(view);
            title = view.findViewById(R.id.cinema_b_home_shelf_title);
            heading = view.findViewById(R.id.cinema_b_home_shelf_heading);
            more = view.findViewById(R.id.cinema_b_home_shelf_more);
            list = view.findViewById(R.id.cinema_b_home_shelf_list);
            list.setLayoutManager(new LinearLayoutManager(context, RecyclerView.HORIZONTAL, false));
            list.setNestedScrollingEnabled(false);
            cards = new CardAdapter();
            list.setAdapter(cards);
        }
    }

    private static class CardHolder extends RecyclerView.ViewHolder {
        final ImageView thumbnail;
        final TextView title;
        final TextView metadata;
        final TextView duration;
        final View paid;
        final ProgressBar progress;
        final View target;
        CardHolder(View view) {
            super(view);
            thumbnail = view.findViewById(R.id.cinema_b_home_thumbnail);
            title = view.findViewById(R.id.cinema_b_home_title);
            metadata = view.findViewById(R.id.cinema_b_home_metadata);
            duration = view.findViewById(R.id.cinema_b_home_duration);
            paid = view.findViewById(R.id.cinema_b_home_paid);
            progress = view.findViewById(R.id.cinema_b_home_progress);
            View media = view.findViewById(R.id.cinema_b_home_media);
            target = media != null ? media : view;
        }
    }

    private final class CardAdapter extends RecyclerView.Adapter<CardHolder> {
        private final List<Claim> claims = new ArrayList<>();
        private boolean history;
        void setItems(List<Claim> items, boolean history) {
            claims.clear();
            claims.addAll(items);
            this.history = history;
            notifyDataSetChanged();
        }
        @NonNull @Override public CardHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.cinema_b_home_card, parent, false);
            int width = Math.round(Math.min(212, Math.max(164,
                    context.getResources().getDisplayMetrics().widthPixels / context.getResources().getDisplayMetrics().density * .47f))
                    * context.getResources().getDisplayMetrics().density);
            view.getLayoutParams().width = width;
            return new CardHolder(view);
        }
        @Override public void onBindViewHolder(@NonNull CardHolder holder, int position) {
            bindCard(holder, claims.get(position), history, false);
        }
        @Override public int getItemCount() { return claims.size(); }
    }

    private void bindCard(CardHolder holder, Claim original, boolean history, boolean hero) {
        Claim claim = Claim.TYPE_REPOST.equalsIgnoreCase(original.getValueType()) && original.getRepostedClaim() != null
                ? original.getRepostedClaim() : original;
        String title = Helper.isNullOrEmpty(claim.getTitle()) ? claim.getName() : claim.getTitle();
        holder.title.setText(title);
        Claim channel = claim.getSigningChannel();
        String creator = channel == null ? context.getString(R.string.anonymous) :
                !Helper.isNullOrEmpty(channel.getTitle()) ? channel.getTitle() : channel.getName();
        long timestamp = claim.getTimestamp();
        if (claim.getValue() instanceof Claim.StreamMetadata && ((Claim.StreamMetadata) claim.getValue()).getReleaseTime() > 0) {
            timestamp = ((Claim.StreamMetadata) claim.getValue()).getReleaseTime();
        }
        holder.metadata.setText(timestamp > 0 ? creator + " · " + DateUtils.getRelativeTimeSpanString(
                timestamp * 1000, System.currentTimeMillis(), 0, DateUtils.FORMAT_ABBREV_RELATIVE) : creator);
        int width = hero ? context.getResources().getDisplayMetrics().widthPixels : Math.round(212 * context.getResources().getDisplayMetrics().density);
        Glide.with(context.getApplicationContext()).load(claim.getThumbnailUrl(width, width * 9 / 16, 85))
                .centerCrop().placeholder(R.drawable.cinema_b_home_thumbnail).error(R.drawable.cinema_b_home_thumbnail).into(holder.thumbnail);
        long duration = claim.getDuration();
        holder.duration.setVisibility(duration > 0 ? View.VISIBLE : View.GONE);
        holder.duration.setText(duration > 0 ? Helper.formatDuration(duration) : "");
        boolean paid = !claim.isFree();
        holder.paid.setVisibility(paid ? View.VISIBLE : View.GONE);
        holder.target.setContentDescription(title + ", " + creator);
        holder.target.setOnClickListener(v -> listener.onClaimClicked(claim));
        if (holder.progress != null) {
            long playback = history ? playbackPosition(PreferenceManager.getDefaultSharedPreferences(context), claim) : 0;
            holder.progress.setVisibility(history && duration > 0 && playback > 0 ? View.VISIBLE : View.GONE);
            holder.progress.setProgress(duration > 0 ? (int) Math.min(1000, playback / duration) : 0);
        }
    }

    public static long playbackPosition(SharedPreferences preferences, Claim claim) {
        String url = !Helper.isNullOrEmpty(claim.getShortUrl()) ? claim.getShortUrl() : claim.getPermanentUrl();
        long position = Helper.isNullOrEmpty(url) ? -1 : preferences.getLong("PlayPos_" + url, -1);
        if (position < 0 && !Helper.isNullOrEmpty(claim.getPermanentUrl())) {
            position = preferences.getLong("PlayPos_" + claim.getPermanentUrl(), -1);
        }
        return position;
    }
}
