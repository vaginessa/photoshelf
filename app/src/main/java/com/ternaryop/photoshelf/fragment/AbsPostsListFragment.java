package com.ternaryop.photoshelf.fragment;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.widget.SearchView;
import android.util.SparseBooleanArray;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AbsListView.MultiChoiceModeListener;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.PopupMenu;

import com.ternaryop.photoshelf.Constants;
import com.ternaryop.photoshelf.R;
import com.ternaryop.photoshelf.activity.ImageViewerActivity;
import com.ternaryop.photoshelf.activity.TagPhotoBrowserActivity;
import com.ternaryop.photoshelf.adapter.OnPhotoBrowseClick;
import com.ternaryop.photoshelf.adapter.PhotoAdapter;
import com.ternaryop.photoshelf.adapter.PhotoShelfPost;
import com.ternaryop.photoshelf.db.DBHelper;
import com.ternaryop.photoshelf.dialogs.TumblrPostDialog;
import com.ternaryop.tumblr.Tumblr;
import com.ternaryop.tumblr.TumblrAltSize;
import com.ternaryop.tumblr.TumblrPhotoPost;
import com.ternaryop.utils.AbsProgressIndicatorAsyncTask;
import com.ternaryop.utils.DialogUtils;
import com.ternaryop.utils.drawer.counter.CountChangedListener;
import com.ternaryop.utils.drawer.counter.CountProvider;

public abstract class AbsPostsListFragment extends AbsPhotoShelfFragment implements CountProvider, OnScrollListener, OnItemClickListener, MultiChoiceModeListener, OnPhotoBrowseClick, SearchView.OnQueryTextListener {
    protected static final int POST_ACTION_PUBLISH = 1;
    protected static final int POST_ACTION_DELETE = 2;
    protected static final int POST_ACTION_EDIT = 3;

    public static final int POST_ACTION_ERROR = 0;
    public static final int POST_ACTION_OK = -1;
    public static final int POST_ACTION_FIRST_USER = 1;

    private static final String LOADER_PREFIX_POSTS_THUMB = "postsThumb";

    protected PhotoAdapter photoAdapter;
    protected int offset;
    protected boolean hasMorePosts;
    protected boolean isScrolling;
    protected long totalPosts;
    protected ListView photoListView;
    protected SearchView searchView;

    private int[] singleSelectionMenuIds;

    private CountChangedListener countChangedListener;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View rootView = inflater.inflate(getPostListViewResource(), container, false);
        
        photoAdapter = new PhotoAdapter(getActivity(), LOADER_PREFIX_POSTS_THUMB);

        photoListView = (ListView)rootView.findViewById(R.id.list);
        photoListView.setOnItemClickListener(this);
        photoListView.setOnScrollListener(this);
        photoListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
        photoListView.setMultiChoiceModeListener(this);
        photoListView.setAdapter(photoAdapter);

        setHasOptionsMenu(true);
        
        return rootView;
    }

    protected int getPostListViewResource() {
        return R.layout.fragment_photo_list;
    }

    public void onPrepareOptionsMenu(Menu menu) {
        boolean isMenuVisible = !fragmentActivityStatus.isDrawerOpen();
        menu.setGroupVisible(R.id.menu_photo_action_bar, isMenuVisible);
        setupSearchView(menu);
        super.onPrepareOptionsMenu(menu);
    }

    protected abstract void readPhotoPosts();
    
    protected abstract int getActionModeMenuId();

    protected void saveAsDraft(final ActionMode mode, final List<PhotoShelfPost> postList) {
        DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                case DialogInterface.BUTTON_POSITIVE:
                    new ActionExecutor(getActivity(), R.string.saving_posts_to_draft_title, mode, postList) {
                        @Override
                        protected void executeAction(PhotoShelfPost post) {
                            Tumblr.getSharedTumblr(getContext()).saveDraft(
                                    getBlogName(),
                                    post.getPostId());
                        }
                    }.execute();
                    break;
                }
            }
        };

        String message = getResources().getQuantityString(R.plurals.save_to_draft_confirm,
                postList.size(),
                postList.size(),
                postList.get(0).getFirstTag());
        new AlertDialog.Builder(getActivity())
        .setMessage(message)
        .setPositiveButton(android.R.string.yes, dialogClickListener)
        .setNegativeButton(android.R.string.no, dialogClickListener)
        .show();        
    }

    private void deletePost(final ActionMode mode, final List<PhotoShelfPost> postList) {
        new ActionExecutor(getActivity(), R.string.deleting_posts_title, mode, postList) {
            @Override
            protected void executeAction(PhotoShelfPost post) {
                Tumblr.getSharedTumblr(getContext()).deletePost(getBlogName(),
                        post.getPostId());
                DBHelper.getInstance(getContext()).getPostTagDAO().deleteById(post.getPostId());
                onPostAction(post, POST_ACTION_DELETE, POST_ACTION_OK);
            }
        }.execute();
    }

    @Override
    public void onScroll(AbsListView view, int firstVisibleItem,
            int visibleItemCount, int totalItemCount) {
        boolean loadMore = totalItemCount > 0 &&
                (firstVisibleItem + visibleItemCount >= totalItemCount);

        if (loadMore && hasMorePosts && !isScrolling) {
            offset += Tumblr.MAX_POST_PER_REQUEST;
            readPhotoPosts();
        }
    }

    @Override
    public void onScrollStateChanged(AbsListView view, int scrollState) {
    }

    public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
        PhotoShelfPost item = (PhotoShelfPost) parent.getItemAtPosition(position);
        if (getActivity().getCallingActivity() == null) {
            ImageViewerActivity.startImageViewer(getActivity(),
                    item.getFirstPhotoAltSize().get(0).getUrl(),
                    item);
        } else {
            finish(item);
        }
    }

    public void finish(TumblrPhotoPost post) {
        Intent data = new Intent();
        data.putExtra(Constants.EXTRA_POST, post);
        // Activity finished ok, return the data
        getActivity().setResult(Activity.RESULT_OK, data);
        getActivity().finish();
    }
    
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        mode.setTitle(R.string.select_posts);
        mode.setSubtitle(getResources().getQuantityString(R.plurals.selected_items, 1, 1));
        MenuInflater inflater = mode.getMenuInflater();
        inflater.inflate(getActionModeMenuId(), menu);
        return true;
    }

    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        return true;
    }

    protected List<PhotoShelfPost> getSelectedPosts() {
        SparseBooleanArray checkedItemPositions = photoListView.getCheckedItemPositions();
        ArrayList<PhotoShelfPost> list = new ArrayList<PhotoShelfPost>();
        for (int i = 0; i < checkedItemPositions.size(); i++) {
            int key = checkedItemPositions.keyAt(i);
            if (checkedItemPositions.get(key)) {
                list.add(photoAdapter.getItem(key));
            }
        }
        return list;
    }
    
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        return handleMenuItem(item, getSelectedPosts(), mode);
    }

    public void onDestroyActionMode(ActionMode mode) {
    }

    public void onItemCheckedStateChanged(ActionMode mode, int position,
            long id, boolean checked) {
        int selectCount = photoListView.getCheckedItemCount();
        boolean singleSelection = selectCount == 1;

        for (int itemId : getSingleSelectionMenuIds()) {
            MenuItem item = mode.getMenu().findItem(itemId);
            if (item != null) {
                item.setVisible(singleSelection);
            }
        }

        mode.setSubtitle(getResources().getQuantityString(
                R.plurals.selected_items,
                selectCount,
                selectCount));
    }

    protected int[] getSingleSelectionMenuIds() {
        if (singleSelectionMenuIds == null) {
            singleSelectionMenuIds = new int[] {R.id.post_schedule, R.id.post_edit, R.id.group_menu_image_dimension};
        }
        return singleSelectionMenuIds;
    }

    public void browseImageBySize(final PhotoShelfPost post) {
        final ArrayAdapter<TumblrAltSize> arrayAdapter = new ArrayAdapter<TumblrAltSize>(
                getActivity(),
                android.R.layout.select_dialog_item,
                post.getFirstPhotoAltSize());

        // Show the cancel button without setting a listener
        // because it isn't necessary
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
        .setTitle(getString(R.string.menu_header_show_image, post.getFirstTag()))
        .setNegativeButton(android.R.string.cancel, null);

        builder.setAdapter(arrayAdapter,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String url = arrayAdapter.getItem(which).getUrl();
                        ImageViewerActivity.startImageViewer(getActivity(), url, post);
                    }
                });
        builder.show();
    }

    private void showConfirmDialog(final int postAction, final ActionMode mode, final List<PhotoShelfPost> postsList) {
        DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                case DialogInterface.BUTTON_POSITIVE:
                    switch (postAction) {
                    case POST_ACTION_PUBLISH:
                        publishPost(mode, postsList);
                        break;
                    case POST_ACTION_DELETE:
                        deletePost(mode, postsList);
                        break;
                    }
                    break;
                }
            }
        };

        String message = null;
        switch (postAction) {
        case POST_ACTION_PUBLISH:
            message = getResources().getQuantityString(R.plurals.publish_post_confirm,
                    postsList.size(),
                    postsList.size(),
                    postsList.get(0).getFirstTag());
            break;
        case POST_ACTION_DELETE:
            message = getResources().getQuantityString(R.plurals.delete_post_confirm,
                    postsList.size(),
                    postsList.size(),
                    postsList.get(0).getFirstTag());
            break;
        }
        
        new AlertDialog.Builder(getActivity())
        .setMessage(message)
        .setPositiveButton(android.R.string.yes, dialogClickListener)
        .setNegativeButton(android.R.string.no, dialogClickListener)
        .show();        
    }
    
    private void publishPost(ActionMode mode, final List<PhotoShelfPost> postList) {
        new ActionExecutor(getActivity(), R.string.publishing_posts_title, mode, postList) {
            @Override
            protected void executeAction(PhotoShelfPost post) {
                Tumblr.getSharedTumblr(getContext()).publishPost(getBlogName(),
                        post.getPostId());
                onPostAction(post, POST_ACTION_PUBLISH, POST_ACTION_OK);
            }
        }.execute();
    }

    protected void refreshUI() {
        if (searchView != null && searchView.isIconified()) {
            if (hasMorePosts) {
                getSupportActionBar().setSubtitle(getString(R.string.post_count_1_of_x,
                        photoAdapter.getCount(),
                        totalPosts));
            } else {
                getSupportActionBar().setSubtitle(getResources().getQuantityString(
                        R.plurals.posts_count,
                        photoAdapter.getCount(),
                        photoAdapter.getCount()));
                if (countChangedListener != null) {
                    countChangedListener.onChangeCount(this, photoAdapter.getCount());
                }
            }
        }
        photoAdapter.notifyDataSetChanged();
    }
    
    abstract class ActionExecutor extends AbsProgressIndicatorAsyncTask<Void, PhotoShelfPost, List<PhotoShelfPost>> {
        private final ActionMode mode;
        private final List<PhotoShelfPost> postList;
        Exception error; // contains the last error found

        public ActionExecutor(Context context, int resId, ActionMode mode, List<PhotoShelfPost> postList) {
            super(context, context.getString(resId));
            this.mode = mode;
            this.postList = postList;
        }

        @Override
        protected void onProgressUpdate(PhotoShelfPost... values) {
            PhotoShelfPost post = values[0];
            photoAdapter.remove(post);
            setProgressMessage(post.getTagsAsString());
        }
        
        @Override
        protected void onPostExecute(List<PhotoShelfPost> notDeletedPosts) {
            super.onPostExecute(null);

            refreshUI();
            // all posts have been deleted so call actionMode.finish() 
            if (notDeletedPosts.size() == 0) {
                if (mode != null) {
                    mode.finish();
                }
                return;
            }
            // leave posts not processed checked
            photoListView.clearChoices();
            for (PhotoShelfPost post : notDeletedPosts) {
                int position = photoAdapter.getPosition(post);
                photoListView.setItemChecked(position, true);
            }
            DialogUtils.showSimpleMessageDialog(getContext(),
                    R.string.generic_error,
                    getContext().getResources().getQuantityString(
                            R.plurals.general_posts_error,
                            notDeletedPosts.size(),
                            error.getMessage(),
                            notDeletedPosts.size()));
        }

        @Override
        protected List<PhotoShelfPost> doInBackground(Void... voidParams) {
            List<PhotoShelfPost> notDeletedPosts = new ArrayList<PhotoShelfPost>();

            for (final PhotoShelfPost post : postList) {
                try {
                    executeAction(post);
                    this.publishProgress(post);
                } catch (Exception e) {
                    error = e;
                    notDeletedPosts.add(post);
                }
            }
            return notDeletedPosts;
        }
        
        protected abstract void executeAction(PhotoShelfPost post);
    }

    @Override
    public void onTagClick(int position) {
        final PhotoShelfPost post = photoAdapter.getItem(position);
        TagPhotoBrowserActivity.startPhotoBrowserActivity(getActivity(), getBlogName(), post.getFirstTag(), false);
    }

    @Override
    public void onThumbnailImageClick(int position) {
        final PhotoShelfPost post = photoAdapter.getItem(position);
        ImageViewerActivity.startImageViewer(getActivity(), post.getFirstPhotoAltSize().get(0).getUrl(), post);
    }

    @Override
    public void onOverflowClick(View view, int position) {
        final PhotoShelfPost post = photoAdapter.getItem(position);
        PopupMenu popupMenu = new PopupMenu(getActivity(), view);
        MenuInflater inflater = popupMenu.getMenuInflater();
        inflater.inflate(getActionModeMenuId(), popupMenu.getMenu());
        popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                ArrayList<PhotoShelfPost> postList = new ArrayList<PhotoShelfPost>();
                postList.add(post);
                return handleMenuItem(item, postList, null);
            }
        });
        popupMenu.show();
    }

    protected boolean handleMenuItem(MenuItem item, List<PhotoShelfPost> postList, ActionMode mode) {
        switch (item.getItemId()) {
            case R.id.post_publish:
                showConfirmDialog(POST_ACTION_PUBLISH, mode, postList);
                return true;
            case R.id.group_menu_image_dimension:
                browseImageBySize(postList.get(0));
                return true;
            case R.id.post_delete:
                showConfirmDialog(POST_ACTION_DELETE, mode, postList);
                return true;
            case R.id.post_edit:
                showEditDialog(postList.get(0), mode);
                return true;
            case R.id.post_save_draft:
                saveAsDraft(mode, postList);
                return true;
            default:
                return false;
        }
    }

    @Override
    public void setCountChangedListener(CountChangedListener countChangedListener) {
        this.countChangedListener = countChangedListener;
    }

    @Override
    public CountChangedListener getCountChangedListener() {
        return countChangedListener;
    }

    protected SearchView setupSearchView(Menu menu) {
        MenuItem searchMenu = menu.findItem(R.id.action_search);
        if (searchMenu != null) {
            searchView = (SearchView)MenuItemCompat.getActionView(searchMenu);
            searchView.setQueryHint(getString(R.string.enter_tag_hint));
            searchView.setOnQueryTextListener(this);
        }
        return searchView;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        photoAdapter.getFilter().filter(newText);
        return true;
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        return true;
    }

    protected void resetAndReloadPhotoPosts() {
        offset = 0;
        totalPosts = 0;
        hasMorePosts = true;
        photoAdapter.clear();
        readPhotoPosts();
    }

    /**
     * Overridden (if necessary) by subclasses to be informed about post action result,
     * the default implementation does nothing
     * @param post the post processed by action
     * @param postAction the action executed
     * @param resultCode on success POST_ACTION_OK
     */
    public void onPostAction(TumblrPhotoPost post, int postAction, @SuppressWarnings("SameParameterValue") int resultCode) {
    }

    @Override
    public void onEditDone(TumblrPostDialog dialog, TumblrPhotoPost post) {
        super.onEditDone(dialog, post);
        onPostAction(post, POST_ACTION_EDIT, POST_ACTION_OK);
    }
}
