package com.ternaryop.photoshelf.service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.io.Serializable;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.os.Environment;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Pair;

import com.ternaryop.photoshelf.R;
import com.ternaryop.photoshelf.birthday.BirthdayUtils;
import com.ternaryop.photoshelf.db.Birthday;
import com.ternaryop.photoshelf.db.BirthdayDAO;
import com.ternaryop.photoshelf.db.DBHelper;
import com.ternaryop.tumblr.Tumblr;
import com.ternaryop.tumblr.TumblrPhotoPost;
import com.ternaryop.utils.DateTimeUtils;

/**
 * Created by dave on 01/03/14.
 * Contains all methods used to publish posts
 */
public class PublishIntentService extends IntentService {
    private static final String NOTIFICATION_PUBLISH_ERROR_TAG = "com.ternaryop.photoshelf.publish.error";
    private static final String NOTIFICATION_BIRTHDAY_ERROR_TAG = "com.ternaryop.photoshelf.birthday.error";
    private static final String NOTIFICATION_BIRTHDAY_SUCCESS_TAG = "com.ternaryop.photoshelf.birthday.success";
    private static final int NOTIFICATION_ID = 1;

    public static final String URL_OR_FILE = "urlOrFile";
    public static final String BLOG_NAME = "blogName";
    public static final String POST_TITLE = "postTitle";
    public static final String POST_TAGS = "postTags";
    public static final String ACTION = "action";
    public static final String PUBLISH_ACTION_DRAFT = "draft";
    public static final String PUBLISH_ACTION_PUBLISH = "publish";

    public static final String BIRTHDAY_LIST_BY_DATE_ACTION = "birthdayListByDate";
    public static final String BIRTHDAY_DATE = "birthDate";
    public static final String RESULT_LIST1 = "list1";

    // Intents returned using local broadcast
    public static final String BIRTHDAY_INTENT = "birthdayIntent";

    public PublishIntentService() {
        super("publishIntent");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Object url = intent.getSerializableExtra(URL_OR_FILE);
        String selectedBlogName = intent.getStringExtra(BLOG_NAME);
        String postTitle = intent.getStringExtra(POST_TITLE);
        String postTags = intent.getStringExtra(POST_TAGS);
        String action = intent.getStringExtra(ACTION);

        try {
            addBithdateFromTags(postTags, selectedBlogName);
            if (PUBLISH_ACTION_DRAFT.equals(action)) {
                Tumblr.getSharedTumblr(getApplicationContext()).draftPhotoPost(selectedBlogName,
                        url, postTitle, postTags);
            } else if (PUBLISH_ACTION_PUBLISH.equals(action)) {
                Tumblr.getSharedTumblr(getApplicationContext()).publishPhotoPost(selectedBlogName,
                        url, postTitle, postTags);
            } else if (BIRTHDAY_LIST_BY_DATE_ACTION.equals(action)) {
                broadcastBirthdaysByDate(intent);
            }
        } catch (Exception ex) {
            try {
                File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "publish_errors.txt");
                String date = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date());
                FileOutputStream fos = new FileOutputStream(file, true);
                PrintStream ps = new PrintStream(fos);
                ps.println(date + " Error on url " + url);
                ps.println(date + " tags " + postTags);
                ex.printStackTrace(ps);
                ps.flush();
                ps.close();
                fos.close();
            } catch (Exception ignored) {
            }
            notifyError(intent);
        }
            
    }

    private void addBithdateFromTags(final String postTags, final String blogName) {
        if (postTags == null) {
            return;
        }
        String[] tags = postTags.split(",");
        if (tags.length == 0) {
            return;
        }
        final String name = tags[0].trim();

        new Thread(new Runnable() {
            public void run() {
                BirthdayDAO birthdayDAO = DBHelper.getInstance(getApplicationContext()).getBirthdayDAO();
                SQLiteDatabase db = birthdayDAO.getDbHelper().getWritableDatabase();
                NotificationManager notificationManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
                db.beginTransaction();

                try {
                    if (birthdayDAO.getBirthdayByName(name, blogName) != null) {
                        return;
                    }
                    Birthday birthday = BirthdayUtils.searchBirthday(name, blogName);
                    if (birthday != null) {
                        birthdayDAO.insert(birthday);
                        db.setTransactionSuccessful();
                        String date = DateFormat.getDateInstance().format(birthday.getBirthDate());
                        String strAage = String.valueOf(DateTimeUtils.yearsBetweenDates(birthday.getBirthDateCalendar(), Calendar.getInstance()));

                        Notification notification = createNotification(
                                getString(R.string.name_with_date_age, name, date, strAage),
                                getString(R.string.new_birthday_ticker, name),
                                R.drawable.stat_notify_bday);
                        // if notification is already visible the user doesn't receive any visual feedback so we clear it
                        notificationManager.cancel(NOTIFICATION_BIRTHDAY_SUCCESS_TAG, NOTIFICATION_ID);
                        notificationManager.notify(NOTIFICATION_BIRTHDAY_SUCCESS_TAG, NOTIFICATION_ID, notification);
                    }
                } catch (Exception e) {
                    Notification notification = createNotification(name + ": " + e.getLocalizedMessage(),
                            R.string.birthday_add_error_ticker,
                            R.drawable.stat_notify_error);
                    notificationManager.notify(NOTIFICATION_BIRTHDAY_ERROR_TAG, NOTIFICATION_ID, notification);
                } finally {
                    db.endTransaction();
                }
            }
        }).start();
    }

    private void notifyError(Intent intent) {
        Object url = intent.getSerializableExtra(URL_OR_FILE).toString();
        String postTags = intent.getStringExtra(POST_TAGS);

        NotificationManager notificationManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        // add url.hashCode() to ensure every notification is shown
        Notification notification = createNotification(postTags, R.string.upload_error_ticker, R.drawable.stat_notify_error);
        notificationManager.notify(NOTIFICATION_PUBLISH_ERROR_TAG, NOTIFICATION_ID + url.hashCode(), notification);
    }

    private Notification createNotification(String contentText, String stringTicker, int iconId) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(
                this)
                .setTicker(stringTicker)
                .setSmallIcon(iconId);

        builder.setContentText(contentText);
        // remove notification when user clicks on it
        builder.setAutoCancel(true);

        return builder.build();
    }

    private Notification createNotification(String contentText, int stringTickerId, int iconId) {
        return createNotification(contentText, getString(stringTickerId), iconId);
    }

    private static void startActionIntent(Context context,
                                            Object urlOrFile,
                                            String blogName,
                                            String postTitle,
                                            String postTags,
                                            String publishAction) {
        Intent intent = new Intent(context, PublishIntentService.class);
        intent.putExtra(URL_OR_FILE, (Serializable)urlOrFile);
        intent.putExtra(BLOG_NAME, blogName);
        intent.putExtra(POST_TITLE, postTitle);
        intent.putExtra(POST_TAGS, postTags);
        intent.putExtra(ACTION, publishAction);

        context.startService(intent);
    }

    public static void startSaveAsDraftIntent(Context context,
                                              Object urlOrFile,
                                              String blogName,
                                              String postTitle,
                                              String postTags) {
        startActionIntent(context, urlOrFile, blogName, postTitle, postTags, PUBLISH_ACTION_DRAFT);
    }

    public static void startPublishIntent(Context context,
                                              Object urlOrFile,
                                              String blogName,
                                              String postTitle,
                                              String postTags) {
        startActionIntent(context, urlOrFile, blogName, postTitle, postTags, PUBLISH_ACTION_PUBLISH);
    }

    public static void startBirthdayListIntent(Context context,
                                               Calendar date) {
        Intent intent = new Intent(context, PublishIntentService.class);
        intent.putExtra(BIRTHDAY_DATE, date);
        intent.putExtra(ACTION, BIRTHDAY_LIST_BY_DATE_ACTION);

        context.startService(intent);
    }

    private void broadcastBirthdaysByDate(Intent intent) {
        Calendar birthday = (Calendar) intent.getSerializableExtra(BIRTHDAY_DATE);
        if (birthday == null) {
            birthday = Calendar.getInstance(Locale.US);
        }
        ArrayList<Pair<Birthday, TumblrPhotoPost>> list;
        try {
            list = BirthdayUtils.getPhotoPosts(getApplicationContext(), birthday);
        } catch (Exception ex) {
            // we can't use Collections.emptyList() because java.util.List isn't Serializable
            list = new ArrayList<Pair<Birthday, TumblrPhotoPost>>();
        }
        Intent countIntent = new Intent(BIRTHDAY_INTENT);
        countIntent.putExtra(RESULT_LIST1, list);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(countIntent);
    }
}