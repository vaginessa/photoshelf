package com.ternaryop.phototumblrshare.activity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;

import com.ternaryop.phototumblrshare.R;
import com.ternaryop.phototumblrshare.list.PhotoAdapter;
import com.ternaryop.phototumblrshare.list.PhotoSharePost;
import com.ternaryop.tumblr.Tumblr;
import com.ternaryop.tumblr.TumblrPhotoPost;
import com.ternaryop.tumblr.TumblrPost;
import com.ternaryop.utils.DialogUtils;

public class ScheduledListActivity extends PhotoTumblrActivity implements OnScrollListener {
 	private static final String LOADER_PREFIX_POSTS_THUMB = "postsThumb";
	private static final String BLOG_NAME = "blogName";
	private PhotoAdapter adapter;
	private String blogName;
	private int offset;
	private boolean hasMorePosts;
	private boolean isScrolling;
	private long totalPosts;

	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo_list);
        
        adapter = new PhotoAdapter(this, LOADER_PREFIX_POSTS_THUMB);

        ListView list = (ListView)findViewById(R.id.list);
        list.setAdapter(adapter);
        list.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
        		PhotoSharePost item = (PhotoSharePost) parent.getItemAtPosition(position);
        		ImageViewerActivity.startImageViewer(ScheduledListActivity.this, item.getFirstPhotoAltSize().get(0).getUrl());
        	}
		});
        list.setOnScrollListener(this);
        
        Bundle bundle = getIntent().getExtras();
		blogName = bundle.getString(BLOG_NAME);
		if (blogName != null) {
			offset = 0;
			hasMorePosts = true;
			readPhotoPosts();
		}
	}

	private void refreshUI() {
		setTitle(getResources().getString(R.string.browser_sheduled_images_title, adapter.getCount(), totalPosts));
		adapter.notifyDataSetChanged();
	}
	
	private void readPhotoPosts() {
		if (isScrolling) {
			return;
		}
		refreshUI();

		final Context activityContext = this;
		new AsyncTask<Void, String, Void>() {
			ProgressDialog progressDialog;
			Exception error;

			protected void onPreExecute() {
				progressDialog = new ProgressDialog(activityContext);
				progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
				progressDialog.setMessage(getString(R.string.reading_draft_posts));
				progressDialog.show();
				isScrolling = true;
			}
			
			@Override
			protected void onProgressUpdate(String... values) {
				progressDialog.setMessage(values[0]);
			}
			
			@Override
			protected void onPostExecute(Void result) {
				progressDialog.dismiss();
				
				if (error == null) {
					refreshUI();
				} else {
					DialogUtils.showErrorDialog(activityContext, error);
				}
				isScrolling = false;
			}

			@Override
			protected Void doInBackground(Void... voidParams) {
				try {
					HashMap<String, String> params = new HashMap<String, String>();
					params.put("offset", String.valueOf(offset));
					List<TumblrPost> photoPosts = Tumblr.getSharedTumblr(activityContext).getQueue(blogName, params);

					List<PhotoSharePost> photoShareList = new ArrayList<PhotoSharePost>(); 
			    	for (TumblrPost post : photoPosts) {
				    	if (post.getType().equals("photo")) {
				    		photoShareList.add(new PhotoSharePost((TumblrPhotoPost)post,
				    				post.getScheduledPublishTime() * 1000));
				    	}
					}
			    	if (offset == 0) {
			    		adapter.setItems(photoShareList);
			    	} else {
			    		adapter.addItems(photoShareList);
			    	}
			    	if (photoPosts.size() > 0) {
			    		totalPosts = photoPosts.get(0).getTotalPosts();
			    		hasMorePosts = true;
			    	} else {
			    		totalPosts = adapter.getCount();
			    		hasMorePosts = false;
			    	}
				} catch (Exception e) {
					e.printStackTrace();
					error = e;
				}
				return null;
			}
		}.execute();
	}

	public static void startScheduledListActivity(Context context, String blogName) {
		Intent intent = new Intent(context, ScheduledListActivity.class);
		Bundle bundle = new Bundle();

		bundle.putString(BLOG_NAME, blogName);
		intent.putExtras(bundle);

		context.startActivity(intent);
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
}