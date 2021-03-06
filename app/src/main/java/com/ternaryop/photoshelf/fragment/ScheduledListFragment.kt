package com.ternaryop.photoshelf.fragment

import android.os.Bundle
import android.support.v4.widget.SwipeRefreshLayout
import android.text.format.DateUtils.SECOND_IN_MILLIS
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import com.ternaryop.photoshelf.R
import com.ternaryop.photoshelf.adapter.PhotoShelfPost
import com.ternaryop.photoshelf.event.CounterEvent
import com.ternaryop.photoshelf.view.PhotoShelfSwipe
import com.ternaryop.tumblr.TumblrPhotoPost
import com.ternaryop.tumblr.android.TumblrManager
import com.ternaryop.tumblr.getQueue
import io.reactivex.Observable
import io.reactivex.SingleObserver
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers

open class ScheduledListFragment : AbsPostsListFragment() {
    protected lateinit var photoShelfSwipe: PhotoShelfSwipe

    override val actionModeMenuId: Int
        get() = R.menu.scheduled_context

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?): View? {
        val rootView = super.onCreateView(inflater, container, savedInstanceState)

        photoAdapter.counterType = CounterEvent.SCHEDULE
        photoShelfSwipe = PhotoShelfSwipe(rootView!!,
            R.id.swipe_container, SwipeRefreshLayout.OnRefreshListener { resetAndReloadPhotoPosts() })
        return rootView
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        photoAdapter.setOnPhotoBrowseClick(this)

        if (blogName != null) {
            resetAndReloadPhotoPosts()
        }
    }

    override fun fetchPosts() {
        refreshUI()
        val params = HashMap<String, String>()
        params["offset"] = postFetcher.offset.toString()

        // we assume all returned items are photos, (we handle only photos)
        // if other posts type are returned, the getQueue() list size may be greater than photo list size
        Observable
            .just(params)
            .doFinally { postFetcher.isScrolling = false }
            .flatMap { params1 ->
                Observable.fromIterable(TumblrManager.getInstance(context!!)
                    .getQueue(blogName!!, params1))
            }
            .map { tumblrPost ->
                PhotoShelfPost(tumblrPost as TumblrPhotoPost,
                    tumblrPost.scheduledPublishTime * SECOND_IN_MILLIS)
            }
            .toList()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .compose(photoShelfSwipe.applySwipe())
            .subscribe(object : SingleObserver<List<PhotoShelfPost>> {
                override fun onSubscribe(d: Disposable) {
                    compositeDisposable.add(d)
                }

                override fun onSuccess(photoList: List<PhotoShelfPost>) {
                    postFetcher.incrementReadPostCount(photoList.size)
                    photoAdapter.addAll(photoList)
                    refreshUI()
                }

                override fun onError(t: Throwable) {
                    showSnackbar(makeSnake(recyclerView, t))
                }
            })
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.scheduler, menu)

        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_refresh -> {
                resetAndReloadPhotoPosts()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
