package com.ternaryop.photoshelf.fragment;

import android.app.Activity;
import android.app.Fragment;
import android.content.DialogInterface;
import android.view.ActionMode;

import com.ternaryop.photoshelf.dialogs.TumblrPostDialog;
import com.ternaryop.tumblr.TumblrPhotoPost;

public abstract class AbsPhotoShelfFragment extends Fragment {
    protected FragmentActivityStatus fragmentActivityStatus;

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
     
        // all Activities must adhere to FragmentActivityStatus
        fragmentActivityStatus = (FragmentActivityStatus)activity;
    }
    
    public String getBlogName() {
        return fragmentActivityStatus.getAppSupport().getSelectedBlogName();
    }

    protected void refreshUI() {
        
    }

    protected void showEditDialog(final TumblrPhotoPost item) {
        showEditDialog(item, null);
    }
    
    protected void showEditDialog(final TumblrPhotoPost item, final ActionMode mode) {
        TumblrPostDialog editDialog = new TumblrPostDialog(getActivity(), item.getPostId());

        DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                case DialogInterface.BUTTON_POSITIVE:
                    item.setTags(((TumblrPostDialog)dialog).getPostTags());
                    item.setCaption(((TumblrPostDialog)dialog).getPostTitle());
                    refreshUI();
                    if (mode != null) {
                        mode.finish();
                    }
                    break;
                }
            }
        };
        editDialog.setPostTitle(item.getCaption());
        editDialog.setPostTags(item.getTags());
        editDialog.setEditButton(dialogClickListener);
        
        editDialog.show();
    }
}