package io.lbry.browser.ui.findcontent;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.graphics.drawable.InsetDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.snackbar.Snackbar;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.lbry.browser.MainActivity;
import io.lbry.browser.R;
import io.lbry.browser.adapter.CinemaHomeAdapter;
import io.lbry.browser.data.DatabaseHelper;
import io.lbry.browser.dialog.ContentFromDialogFragment;
import io.lbry.browser.dialog.ContentScopeDialogFragment;
import io.lbry.browser.dialog.ContentSortDialogFragment;
import io.lbry.browser.dialog.CustomizeTagsDialogFragment;
import io.lbry.browser.listener.DownloadActionListener;
import io.lbry.browser.listener.TagListener;
import io.lbry.browser.model.Claim;
import io.lbry.browser.model.LbryFile;
import io.lbry.browser.model.NavMenuItem;
import io.lbry.browser.model.Tag;
import io.lbry.browser.model.ViewHistory;
import io.lbry.browser.model.lbryinc.Subscription;
import io.lbry.browser.tasks.claim.ClaimListResultHandler;
import io.lbry.browser.tasks.claim.ResolveTask;
import io.lbry.browser.tasks.localdata.FetchViewHistoryTask;
import io.lbry.browser.tasks.claim.ClaimSearchResultHandler;
import io.lbry.browser.tasks.claim.ClaimSearchTask;
import io.lbry.browser.tasks.FollowUnfollowTagTask;
import io.lbry.browser.ui.BaseFragment;
import io.lbry.browser.ui.library.LibraryFragment;
import io.lbry.browser.utils.Helper;
import io.lbry.browser.utils.Lbry;
import io.lbry.browser.utils.LbryAnalytics;
import io.lbry.browser.utils.LbryUri;
import io.lbry.browser.utils.Lbryio;
import io.lbry.browser.utils.Predefined;
import lombok.Getter;

// TODO: Similar code to FollowingFragment and Channel page fragment. Probably make common operations (sorting/filtering) into a control
public class AllContentFragment extends BaseFragment implements DownloadActionListener, SharedPreferences.OnSharedPreferenceChangeListener {

    @Getter
    private boolean singleTagView;
    private List<String> tags;
    private View layoutFilterContainer;
    private View customizeLink;
    private View sortLink;
    private View contentFromLink;
    private View scopeLink;
    private TextView titleView;
    private TextView sortLinkText;
    private TextView contentFromLinkText;
    private TextView scopeLinkText;
    private RecyclerView contentList;
    private int currentSortBy;
    private int currentContentFrom;
    @Getter
    private int currentContentScope;
    private String contentReleaseTime;
    private List<String> contentSortOrder;
    private View fromPrefix;
    private View forPrefix;
    private View contentLoading;
    private View bigContentLoading;
    private View noContentView;
    private CinemaHomeAdapter contentListAdapter;
    private boolean contentClaimSearchLoading;
    private boolean contentHasReachedEnd;
    private boolean contentHasLoadedPage;
    private int currentClaimSearchPage;
    private ClaimSearchTask contentClaimSearchTask;
    private SwipeRefreshLayout refreshLayout;
    private LinearLayout topicContainer;
    private String selectedTopic;
    private int requestGeneration;
    private int shelfGeneration;
    private FetchViewHistoryTask historyTask;
    private ResolveTask historyResolveTask;
    private ClaimSearchTask followingTask;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_all_content, container, false);

        // All content page is sorted by trending by default, past week if sort is top
        currentSortBy = ContentSortDialogFragment.ITEM_SORT_BY_TRENDING;
        currentContentFrom = ContentFromDialogFragment.ITEM_FROM_PAST_WEEK;
        currentContentScope = ContentScopeDialogFragment.ITEM_EVERYONE;
        currentClaimSearchPage = 1;
        contentHasLoadedPage = false;
        contentHasReachedEnd = false;

        layoutFilterContainer = root.findViewById(R.id.all_content_filter_container);
        titleView = root.findViewById(R.id.all_content_page_title);
        sortLink = root.findViewById(R.id.all_content_sort_link);
        contentFromLink = root.findViewById(R.id.all_content_time_link);
        scopeLink = root.findViewById(R.id.all_content_scope_link);
        customizeLink = root.findViewById(R.id.all_content_customize_link);
        fromPrefix = root.findViewById(R.id.all_content_from_prefix);
        forPrefix = root.findViewById(R.id.all_content_for_prefix);

        sortLinkText = root.findViewById(R.id.all_content_sort_link_text);
        contentFromLinkText = root.findViewById(R.id.all_content_time_link_text);
        scopeLinkText = root.findViewById(R.id.all_content_scope_link_text);

        bigContentLoading = root.findViewById(R.id.all_content_main_progress);
        contentLoading = root.findViewById(R.id.all_content_load_progress);
        noContentView = root.findViewById(R.id.all_content_no_claim_search_content);
        noContentView.setOnClickListener(v -> fetchClaimSearchContent(true));
        refreshLayout = root.findViewById(R.id.cinema_b_home_refresh);
        refreshLayout.setColorSchemeColors(ContextCompat.getColor(requireContext(), R.color.cinemaAccent));
        refreshLayout.setProgressBackgroundColorSchemeColor(ContextCompat.getColor(requireContext(), R.color.actionBarBackground));
        refreshLayout.setOnRefreshListener(() -> {
            fetchClaimSearchContent(true);
            loadPersonalShelves();
        });
        topicContainer = root.findViewById(R.id.cinema_b_home_topics);
        buildTopicChips();

        contentList = root.findViewById(R.id.all_content_list);
        LinearLayoutManager llm = new LinearLayoutManager(getContext());
        contentList.setLayoutManager(llm);
        contentListAdapter = new CinemaHomeAdapter(requireContext(), this::openClaim,
                () -> openHomeDestination(false), () -> openHomeDestination(true), this::loadNextPage);
        contentList.setAdapter(contentListAdapter);
        contentList.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (contentClaimSearchLoading || dy <= 0 || contentListAdapter == null || contentListAdapter.getContentCount() == 0) {
                    return;
                }

                LinearLayoutManager lm = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (lm != null) {
                    int visibleItemCount = lm.getChildCount();
                    int totalItemCount = lm.getItemCount();
                    int pastVisibleItems = lm.findFirstVisibleItemPosition();
                    if (pastVisibleItems + visibleItemCount >= totalItemCount) {
                        loadNextPage();
                    }
                }
            }
        });

        sortLink.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ContentSortDialogFragment dialog = ContentSortDialogFragment.newInstance();
                dialog.setCurrentSortByItem(currentSortBy);
                dialog.setSortByListener(new ContentSortDialogFragment.SortByListener() {
                    @Override
                    public void onSortByItemSelected(int sortBy) {
                        onSortByChanged(sortBy);
                    }
                });

                Context context = getContext();
                if (context instanceof MainActivity) {
                    MainActivity activity = (MainActivity) context;
                    dialog.show(activity.getSupportFragmentManager(), ContentSortDialogFragment.TAG);
                }
            }
        });
        scopeLink.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ContentScopeDialogFragment dialog = ContentScopeDialogFragment.newInstance();
                dialog.setCurrentScopeItem(currentContentScope);
                dialog.setContentScopeListener(new ContentScopeDialogFragment.ContentScopeListener() {
                    @Override
                    public void onContentScopeItemSelected(int scopeItem) {
                        onContentScopeChanged(scopeItem);
                    }
                });

                Context context = getContext();
                if (context instanceof MainActivity) {
                    MainActivity activity = (MainActivity) context;
                    dialog.show(activity.getSupportFragmentManager(), ContentScopeDialogFragment.TAG);
                }
            }
        });
        contentFromLink.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ContentFromDialogFragment dialog = ContentFromDialogFragment.newInstance();
                dialog.setCurrentFromItem(currentContentFrom);
                dialog.setContentFromListener(new ContentFromDialogFragment.ContentFromListener() {
                    @Override
                    public void onContentFromItemSelected(int contentFromItem) {
                        onContentFromChanged(contentFromItem);
                    }
                });
                Context context = getContext();
                if (context instanceof MainActivity) {
                    MainActivity activity = (MainActivity) context;
                    dialog.show(activity.getSupportFragmentManager(), ContentFromDialogFragment.TAG);
                }
            }
        });
        customizeLink.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showCustomizeTagsDialog();
            }
        });

        checkParams(false);
        return root;
    }

    private void loadNextPage() {
        RecyclerView list = contentList;
        if (list == null) return;
        // Pagination can be requested from a scroll/layout callback; update the footer afterwards.
        list.post(() -> {
            if (contentList == list && !contentClaimSearchLoading && contentHasLoadedPage && !contentHasReachedEnd) {
                currentClaimSearchPage++;
                fetchClaimSearchContent();
            }
        });
    }

    private void openClaim(Claim claim) {
        Context context = getContext();
        if (!(context instanceof MainActivity) || claim == null) return;
        MainActivity activity = (MainActivity) context;
        if (Claim.TYPE_CHANNEL.equalsIgnoreCase(claim.getValueType())) activity.openChannelClaim(claim);
        else activity.openFileClaim(claim);
    }

    private void openHomeDestination(boolean following) {
        Context context = getContext();
        if (context instanceof MainActivity) {
            ((MainActivity) context).openFragment(following ? FollowingFragment.class : LibraryFragment.class,
                    true, following ? NavMenuItem.ID_ITEM_FOLLOWING : NavMenuItem.ID_ITEM_LIBRARY,
                    following ? null : Collections.singletonMap("filter", "history"));
        }
    }

    private void buildTopicChips() {
        topicContainer.removeAllViews();
        int[] labels = {R.string.cinema_b_home_all, R.string.cinema_b_home_technology,
                R.string.cinema_b_home_travel, R.string.cinema_b_home_science, R.string.cinema_b_home_creative,
                R.string.cinema_b_home_filters};
        String[] topics = {null, "technology", "travel", "science", "art"};
        float density = getResources().getDisplayMetrics().density;
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            TextView chip = new TextView(requireContext());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    Math.round(48 * density));
            lp.setMarginEnd(Math.round(8 * density));
            chip.setLayoutParams(lp);
            chip.setGravity(Gravity.CENTER);
            chip.setText(labels[i]);
            chip.setTextSize(12);
            chip.setTypeface(ResourcesCompat.getFont(requireContext(), R.font.inter));
            chip.setMinWidth(Math.round(48 * density));
            chip.setBackground(new InsetDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.cinema_b_home_chip),
                    0, Math.round(6 * density), 0, Math.round(6 * density)));
            chip.setPadding(Math.round(14 * density), 0, Math.round(14 * density), 0);
            chip.setFocusable(true);
            chip.setOnClickListener(v -> {
                if (index == labels.length - 1) {
                    layoutFilterContainer.setVisibility(layoutFilterContainer.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
                    return;
                }
                selectedTopic = topics[index];
                currentContentScope = ContentScopeDialogFragment.ITEM_EVERYONE;
                updateContentScopeLinkText();
                Helper.setViewVisibility(customizeLink, View.GONE);
                updateTopicChips();
                contentListAdapter.setShowPersonalShelves(!singleTagView && selectedTopic == null);
                contentList.scrollToPosition(0);
                fetchClaimSearchContent(true);
            });
            chip.setTag(i < topics.length ? topics[i] : "filters");
            topicContainer.addView(chip);
        }
        updateTopicChips();
    }

    private void updateTopicChips() {
        for (int i = 0; i < topicContainer.getChildCount(); i++) {
            TextView chip = (TextView) topicContainer.getChildAt(i);
            boolean selected = selectedTopic == null ? i == 0 : selectedTopic.equals(chip.getTag());
            chip.setSelected(selected);
            chip.setTextColor(ContextCompat.getColor(requireContext(), selected ?
                    R.color.pageBackground : R.color.darkForeground));
        }
    }

    public void setParams(Map<String, Object> params) {
        super.setParams(params);
        if (getView() != null) {
            checkParams(true);
        }
    }

    private void checkParams(boolean reload) {
        boolean wasSingleTagView = singleTagView;
        Map<String, Object> params = getParams();
        if (params != null && params.containsKey("singleTag")) {
            String tagName = "";
            Object o = params.get("singleTag");
            if (o != null) {
                tagName = o.toString();
            }
            singleTagView = true;
            tags = Arrays.asList(tagName);
            titleView.setText(Helper.capitalize(tagName));
            Helper.setViewVisibility(customizeLink, View.GONE);
        } else {
            singleTagView = false;
            tags = Helper.getTagsForTagObjects(Lbry.followedTags);
            titleView.setText(getString(R.string.all_content));
        }

        titleView.setVisibility(singleTagView ? View.VISIBLE : View.GONE);
        if (singleTagView) layoutFilterContainer.setVisibility(View.VISIBLE);
        else if (wasSingleTagView) layoutFilterContainer.setVisibility(View.GONE);
        if (topicContainer != null) {
            ((View) topicContainer.getParent()).setVisibility(singleTagView ? View.GONE : View.VISIBLE);
        }
        if (contentListAdapter != null) contentListAdapter.setShowPersonalShelves(!singleTagView && selectedTopic == null);

        Helper.setViewVisibility(forPrefix, singleTagView ? View.GONE : View.VISIBLE);
        Helper.setViewVisibility(scopeLink, singleTagView ? View.GONE : View.VISIBLE);

        if (reload) {
            fetchClaimSearchContent(true);
        }
    }

    private void onContentFromChanged(int contentFrom) {
        currentContentFrom = contentFrom;

        // rebuild options and search
        updateContentFromLinkText();
        contentReleaseTime = Helper.buildReleaseTime(currentContentFrom);
        fetchClaimSearchContent(true);
    }

    private void onContentScopeChanged(int contentScope) {
        currentContentScope = contentScope;
        selectedTopic = null;
        updateTopicChips();
        contentListAdapter.setShowPersonalShelves(!singleTagView);

        // rebuild options and search
        updateContentScopeLinkText();
        boolean isTagScope = currentContentScope == ContentScopeDialogFragment.ITEM_TAGS;
        if (isTagScope) {
            tags = Helper.getTagsForTagObjects(Lbry.followedTags);
            // Update tags list with the user's followed tags
            if (tags == null || tags.size() == 0) {
                Snackbar.make(getView(), R.string.customize_tags_hint, Snackbar.LENGTH_LONG).setAction(R.string.customize, new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        // show customize
                        showCustomizeTagsDialog();
                    }
                }).show();
            }
        }
        Helper.setViewVisibility(customizeLink, isTagScope ? View.VISIBLE : View.GONE);
        fetchClaimSearchContent(true);
    }

    private void showCustomizeTagsDialog() {
        CustomizeTagsDialogFragment dialog = CustomizeTagsDialogFragment.newInstance();
        dialog.setListener(new TagListener() {
            @Override
            public void onTagAdded(Tag tag) {
                // heavy-lifting
                // save to local, save to wallet and then sync
                FollowUnfollowTagTask task = new FollowUnfollowTagTask(tag, false, getContext(), followUnfollowHandler);
                task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            }

            @Override
            public void onTagRemoved(Tag tag) {
                // heavy-lifting
                // save to local, save to wallet and then sync
                FollowUnfollowTagTask task = new FollowUnfollowTagTask(tag, true, getContext(), followUnfollowHandler);
                task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            }
        });
        Context context = getContext();
        if (context instanceof MainActivity) {
            MainActivity activity = (MainActivity) context;
            dialog.show(activity.getSupportFragmentManager(), CustomizeTagsDialogFragment.TAG);
        }
    }

    private final FollowUnfollowTagTask.FollowUnfollowTagHandler followUnfollowHandler = new FollowUnfollowTagTask.FollowUnfollowTagHandler() {
        @Override
        public void onSuccess(Tag tag, boolean unfollowing) {
            if (tags != null) {
                if (unfollowing) {
                    tags.remove(tag.getLowercaseName());
                } else {
                    tags.add(tag.getLowercaseName());
                }
                fetchClaimSearchContent(true);
            }

            Bundle bundle = new Bundle();
            bundle.putString("tag", tag.getLowercaseName());
            LbryAnalytics.logEvent(unfollowing ? LbryAnalytics.EVENT_TAG_UNFOLLOW : LbryAnalytics.EVENT_TAG_FOLLOW, bundle);

            Context context = getContext();
            if (context instanceof MainActivity) {
                ((MainActivity) context).saveSharedUserState();
            }
        }

        @Override
        public void onError(Exception error) {
            // pass
        }
    };

    private void onSortByChanged(int sortBy) {
        currentSortBy = sortBy;

        // rebuild options and search
        Helper.setViewVisibility(fromPrefix, currentSortBy == ContentSortDialogFragment.ITEM_SORT_BY_TOP ? View.VISIBLE : View.GONE);
        Helper.setViewVisibility(contentFromLink, currentSortBy == ContentSortDialogFragment.ITEM_SORT_BY_TOP ? View.VISIBLE : View.GONE);
        currentContentFrom = currentSortBy == ContentSortDialogFragment.ITEM_SORT_BY_TOP ?
                (currentContentFrom == 0 ? ContentFromDialogFragment.ITEM_FROM_PAST_WEEK : currentContentFrom) : 0;

        updateSortByLinkText();
        contentSortOrder = Helper.buildContentSortOrder(currentSortBy);
        contentReleaseTime = Helper.buildReleaseTime(currentContentFrom);
        fetchClaimSearchContent(true);
    }

    private void updateSortByLinkText() {
        int stringResourceId = -1;
        switch (currentSortBy) {
            case ContentSortDialogFragment.ITEM_SORT_BY_NEW: default: stringResourceId = R.string.new_text; break;
            case ContentSortDialogFragment.ITEM_SORT_BY_TOP: stringResourceId = R.string.top; break;
            case ContentSortDialogFragment.ITEM_SORT_BY_TRENDING: stringResourceId = R.string.trending; break;
        }

        Helper.setViewText(sortLinkText, stringResourceId);
    }

    private void updateContentScopeLinkText() {
        int stringResourceId = -1;
        switch (currentContentScope) {
            case ContentScopeDialogFragment.ITEM_EVERYONE: default: stringResourceId = R.string.everyone; break;
            case ContentScopeDialogFragment.ITEM_TAGS: stringResourceId = R.string.tags; break;
        }

        Helper.setViewText(scopeLinkText, stringResourceId);
    }

    private void updateContentFromLinkText() {
        int stringResourceId = -1;
        switch (currentContentFrom) {
            case ContentFromDialogFragment.ITEM_FROM_PAST_24_HOURS: stringResourceId = R.string.past_24_hours; break;
            case ContentFromDialogFragment.ITEM_FROM_PAST_WEEK: default: stringResourceId = R.string.past_week; break;
            case ContentFromDialogFragment.ITEM_FROM_PAST_MONTH: stringResourceId = R.string.past_month; break;
            case ContentFromDialogFragment.ITEM_FROM_PAST_YEAR: stringResourceId = R.string.past_year; break;
            case ContentFromDialogFragment.ITEM_FROM_ALL_TIME: stringResourceId = R.string.all_time; break;
        }

        Helper.setViewText(contentFromLinkText, stringResourceId);
    }

    public void onResume() {
        super.onResume();
        Helper.setWunderbarValue(null, getContext());
        checkParams(false);

        Context context = getContext();
        if (context instanceof MainActivity) {
            MainActivity activity = (MainActivity) context;
            if (singleTagView) {
                LbryAnalytics.setCurrentScreen(activity, "Tag", "Tag");
            } else {
                LbryAnalytics.setCurrentScreen(activity, "All Content", "AllContent");
            }
            activity.addDownloadActionListener(this);
        }

        PreferenceManager.getDefaultSharedPreferences(getContext()).registerOnSharedPreferenceChangeListener(this);
        updateContentFromLinkText();
        updateContentScopeLinkText();
        updateSortByLinkText();
        if (contentListAdapter == null || contentListAdapter.getContentCount() == 0) fetchClaimSearchContent();
        loadPersonalShelves();
    }

    public void onPause() {
        Context context = getContext();
        if (context != null) {
            if (context instanceof MainActivity) ((MainActivity) context).removeDownloadActionListener(this);
            PreferenceManager.getDefaultSharedPreferences(context).unregisterOnSharedPreferenceChangeListener(this);
        }
        super.onPause();
    }

    private Map<String, Object> buildContentOptions() {
        Context context = getContext();
        boolean canShowMatureContent = false;
        if (context != null) {
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getContext());
            canShowMatureContent = sp.getBoolean(MainActivity.PREFERENCE_KEY_SHOW_MATURE_CONTENT, false);
        }

        return Lbry.buildClaimSearchOptions(
                Arrays.asList(Claim.TYPE_STREAM, Claim.TYPE_REPOST),
                singleTagView ? tags : selectedTopic != null ? Collections.singletonList(selectedTopic) :
                        (currentContentScope == ContentScopeDialogFragment.ITEM_EVERYONE) ? null : tags,
                canShowMatureContent ? null : new ArrayList<>(Predefined.MATURE_TAGS),
                null,
                null,
                getContentSortOrder(),
                contentReleaseTime,
                0,
                0,
                currentClaimSearchPage == 0 ? 1 : currentClaimSearchPage,
                Helper.CONTENT_PAGE_SIZE,
                true);
    }

    private List<String> getContentSortOrder() {
        if (contentSortOrder == null) {
            return Arrays.asList(Claim.ORDER_BY_TRENDING_GROUP, Claim.ORDER_BY_TRENDING_MIXED);
        }
        return contentSortOrder;
    }

    private View getLoadingView() {
        return (contentListAdapter == null || contentListAdapter.getContentCount() == 0) ? bigContentLoading : contentLoading;
    }

    private void fetchClaimSearchContent() {
        fetchClaimSearchContent(false);
    }

    public void fetchClaimSearchContent(boolean reset) {
        if (getContext() == null || contentListAdapter == null) return;
        if (reset) {
            if (contentClaimSearchTask != null) contentClaimSearchTask.cancel(true);
            contentListAdapter.clearItems();
            currentClaimSearchPage = 1;
            contentHasReachedEnd = false;
            contentHasLoadedPage = false;
        } else if (contentClaimSearchLoading) {
            return;
        }

        final int generation = ++requestGeneration;
        contentClaimSearchLoading = true;
        contentListAdapter.setLoadMoreVisible(false);
        Helper.setViewVisibility(bigContentLoading, View.GONE);
        Helper.setViewVisibility(contentLoading, View.GONE);
        Helper.setViewVisibility(noContentView, View.GONE);
        Map<String, Object> claimSearchOptions = buildContentOptions();
        contentClaimSearchTask = new ClaimSearchTask(claimSearchOptions, Lbry.LBRY_TV_CONNECTION_STRING, getLoadingView(), new ClaimSearchResultHandler() {
            @Override
            public void onSuccess(List<Claim> claims, boolean hasReachedEnd) {
                if (generation != requestGeneration || getView() == null || contentListAdapter == null) return;
                claims = Helper.filterClaimsByOutpoint(claims);
                contentListAdapter.addItems(claims);

                contentHasReachedEnd = hasReachedEnd;
                contentHasLoadedPage = true;
                contentClaimSearchLoading = false;
                contentListAdapter.setLoadMoreVisible(!hasReachedEnd);
                refreshLayout.setRefreshing(false);
                ((TextView) noContentView).setText(R.string.no_claim_search_content);
                checkNoContent();
            }

            @Override
            public void onError(Exception error) {
                if (generation != requestGeneration || getView() == null || contentListAdapter == null) return;
                contentClaimSearchLoading = false;
                currentClaimSearchPage = Math.max(1, currentClaimSearchPage - 1);
                contentListAdapter.setLoadMoreVisible(contentHasLoadedPage && !contentHasReachedEnd);
                refreshLayout.setRefreshing(false);
                ((TextView) noContentView).setText(R.string.cinema_b_home_retry);
                checkNoContent();
            }
        });
        contentClaimSearchTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private void checkNoContent() {
        boolean noContent = contentListAdapter == null || contentListAdapter.getDisplayContentCount() == 0;
        Helper.setViewVisibility(noContentView, noContent ? View.VISIBLE : View.GONE);
    }

    public void onSharedPreferenceChanged(SharedPreferences sp, String key) {
        if (MainActivity.PREFERENCE_KEY_SHOW_MATURE_CONTENT.equalsIgnoreCase(key)) {
            fetchClaimSearchContent(true);
            loadPersonalShelves();
        }
    }

    private void loadPersonalShelves() {
        if (getContext() == null || contentListAdapter == null) return;
        final int generation = ++shelfGeneration;
        if (historyTask != null) historyTask.cancel(true);
        if (historyResolveTask != null) historyResolveTask.cancel(true);
        if (followingTask != null) followingTask.cancel(true);
        contentListAdapter.setContinueWatching(Collections.emptyList());
        contentListAdapter.setFollowing(Collections.emptyList());
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(requireContext());
        final boolean mature = preferences.getBoolean(MainActivity.PREFERENCE_KEY_SHOW_MATURE_CONTENT, false);
        DatabaseHelper database = DatabaseHelper.getInstance();
        if (database != null) {
            historyTask = new FetchViewHistoryTask(null, 24, database, (history, reachedEnd) -> {
                if (!isShelfCurrent(generation) || history.isEmpty()) return;
                List<String> urls = new ArrayList<>();
                List<String> recentIds = new ArrayList<>();
                for (ViewHistory item : history) {
                    if (item.getUri() != null && !recentIds.contains(item.getClaimId())) {
                        urls.add(item.getUri().toString());
                        recentIds.add(item.getClaimId());
                    }
                }
                if (urls.isEmpty()) return;
                historyResolveTask = new ResolveTask(urls, Lbry.LBRY_TV_CONNECTION_STRING, null, new ClaimListResultHandler() {
                    @Override public void onSuccess(List<Claim> claims) {
                        if (!isShelfCurrent(generation)) return;
                        Map<String, Claim> resolved = new LinkedHashMap<>();
                        for (Claim claim : Helper.filterClaimsByOutpoint(claims)) {
                            long position = CinemaHomeAdapter.playbackPosition(preferences, claim);
                            long duration = claim.getDuration() * 1000L;
                            if (claim.isPlayable() && (mature || !claim.isMature()) &&
                                    position > 0 && duration > 0 && position < duration * .95) {
                                resolved.put(claim.getClaimId(), claim);
                            }
                        }
                        List<Claim> ordered = new ArrayList<>();
                        for (String id : recentIds) if (resolved.containsKey(id)) ordered.add(resolved.get(id));
                        contentListAdapter.setContinueWatching(ordered);
                        if (!contentClaimSearchLoading) checkNoContent();
                    }
                    @Override public void onError(Exception error) { /* Optional shelf; keep the main feed available. */ }
                });
                historyResolveTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            });
            historyTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
        List<String> channelIds = new ArrayList<>();
        if (Lbryio.subscriptions != null) {
            for (Subscription subscription : new ArrayList<>(Lbryio.subscriptions)) {
                LbryUri uri = LbryUri.tryParse(subscription.getUrl());
                if (uri != null && !Helper.isNullOrEmpty(uri.getClaimId()) && !channelIds.contains(uri.getClaimId())) {
                    channelIds.add(uri.getClaimId());
                }
            }
        }
        if (!channelIds.isEmpty()) {
            Map<String, Object> options = Lbry.buildClaimSearchOptions(
                    Arrays.asList(Claim.TYPE_STREAM, Claim.TYPE_REPOST), null,
                    mature ? null : new ArrayList<>(Predefined.MATURE_TAGS), channelIds, null,
                    Collections.singletonList(Claim.ORDER_BY_RELEASE_TIME), null, 0, 0, 1, 12, true);
            followingTask = new ClaimSearchTask(options, Lbry.LBRY_TV_CONNECTION_STRING, null, new ClaimSearchResultHandler() {
                @Override public void onSuccess(List<Claim> claims, boolean reachedEnd) {
                    if (!isShelfCurrent(generation)) return;
                    List<Claim> safe = new ArrayList<>();
                    for (Claim claim : Helper.filterClaimsByOutpoint(claims)) {
                        Claim actual = Claim.TYPE_REPOST.equalsIgnoreCase(claim.getValueType()) && claim.getRepostedClaim() != null ? claim.getRepostedClaim() : claim;
                        if (mature || !actual.isMature()) safe.add(claim);
                    }
                    contentListAdapter.setFollowing(safe);
                    if (!contentClaimSearchLoading) checkNoContent();
                }
                @Override public void onError(Exception error) { /* A failed optional shelf must not block browsing. */ }
            });
            followingTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
    }

    private boolean isShelfCurrent(int generation) {
        return generation == shelfGeneration && getView() != null && contentListAdapter != null;
    }

    @Override public void onDestroyView() {
        ++requestGeneration;
        ++shelfGeneration;
        if (contentClaimSearchTask != null) contentClaimSearchTask.cancel(true);
        if (historyTask != null) historyTask.cancel(true);
        if (historyResolveTask != null) historyResolveTask.cancel(true);
        if (followingTask != null) followingTask.cancel(true);
        contentClaimSearchLoading = false;
        if (contentList != null) contentList.setAdapter(null);
        contentListAdapter = null;
        contentList = null;
        topicContainer = null;
        refreshLayout = null;
        super.onDestroyView();
    }

    public void onDownloadAction(String downloadAction, String uri, String outpoint, String fileInfoJson, double progress) {
        if ("abort".equals(downloadAction)) {
            if (contentListAdapter != null) {
                contentListAdapter.clearFileForClaimOrUrl(outpoint, uri);
            }
            return;
        }

        try {
            JSONObject fileInfo = new JSONObject(fileInfoJson);
            LbryFile claimFile = LbryFile.fromJSONObject(fileInfo);
            String claimId = claimFile.getClaimId();
            if (contentListAdapter != null) {
                contentListAdapter.updateFileForClaimByIdOrUrl(claimFile, claimId, uri);
            }
        } catch (JSONException ex) {
            // invalid file info for download
        }
    }
}
