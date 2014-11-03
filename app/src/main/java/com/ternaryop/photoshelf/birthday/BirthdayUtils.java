package com.ternaryop.photoshelf.birthday;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Environment;
import android.support.v4.app.NotificationCompat;
import android.util.Pair;

import com.ternaryop.photoshelf.R;
import com.ternaryop.photoshelf.activity.BirthdaysPublisherActivity;
import com.ternaryop.photoshelf.db.Birthday;
import com.ternaryop.photoshelf.db.BirthdayDAO;
import com.ternaryop.photoshelf.db.DBHelper;
import com.ternaryop.photoshelf.db.PostTag;
import com.ternaryop.photoshelf.db.PostTagDAO;
import com.ternaryop.tumblr.Tumblr;
import com.ternaryop.tumblr.TumblrPhotoPost;
import com.ternaryop.utils.ImageUtils;
import com.ternaryop.utils.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

public class BirthdayUtils {
    private static final String BIRTHDAY_NOTIFICATION_TAG = "com.ternaryop.photoshelf.bday";
    private static final int BIRTHDAY_NOTIFICATION_ID = 1;
    public static final String DESKTOP_USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.8; rv:25.0) Gecko/20100101 Firefox/25.0";

    private final static BirthdaySearcher[] BIRTHDAY_SEARCHERS = new BirthdaySearcher[3];

    static {
        BIRTHDAY_SEARCHERS[0] = new GoogleBirthdaySearcher();
        BIRTHDAY_SEARCHERS[1] = new WikipediaBirthdaySearch();
        BIRTHDAY_SEARCHERS[2] = new FamousBirthdaySearch();
    }

    public static boolean notifyBirthday(Context context) {
        BirthdayDAO birthdayDatabaseHelper = DBHelper
                .getInstance(context.getApplicationContext())
                .getBirthdayDAO();
        Calendar now = Calendar.getInstance(Locale.US);
        List<Birthday> list = birthdayDatabaseHelper.getBirthdayByDate(now.getTime());
        if (list.isEmpty()) {
            return false;
        }

        int currYear = now.get(Calendar.YEAR);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(
                context.getApplicationContext())
                .setContentIntent(createPendingIntent(context))
                .setSmallIcon(R.drawable.stat_notify_bday);
        if (list.size() == 1) {
            Birthday birthday = list.get(0);
            builder.setContentTitle(context.getResources().getQuantityString(R.plurals.birthday_title, list.size()));
            Calendar cal = Calendar.getInstance(Locale.US);
            cal.setTime(birthday.getBirthDate());
            int years = currYear - cal.get(Calendar.YEAR);
            builder.setContentText(context.getString(R.string.birthday_years_old, birthday.getName(), years));
        } else {
            builder.setContentTitle(context.getResources().getQuantityString(R.plurals.birthday_title, list.size(), list.size()));
            NotificationCompat.InboxStyle inboxStyle = new NotificationCompat.InboxStyle();
            inboxStyle.setBigContentTitle(context.getString(R.string.birthday_notification_title));
            for (Birthday birthday : list) {
                Calendar cal = Calendar.getInstance(Locale.US);
                cal.setTime(birthday.getBirthDate());
                int years = currYear - cal.get(Calendar.YEAR);
                inboxStyle.addLine(context.getString(R.string.birthday_years_old, birthday.getName(), years));
            }
            builder.setStyle(inboxStyle);
        }

        // remove notification when user clicks on it
        builder.setAutoCancel(true);
        
        Notification notification = builder.build();
        NotificationManager notificationManager = (NotificationManager)context.getApplicationContext()
                .getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(BIRTHDAY_NOTIFICATION_TAG, BIRTHDAY_NOTIFICATION_ID, notification);

        return true;
    }

    private static PendingIntent createPendingIntent(Context context) {
        // Define Activity to start
        Intent resultIntent = new Intent(context, BirthdaysPublisherActivity.class);
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
        // Adds the back stack
        stackBuilder.addParentStack(BirthdaysPublisherActivity.class);
        // Adds the Intent to the top of the stack
        stackBuilder.addNextIntent(resultIntent);
        // Gets a PendingIntent containing the entire back stack
        return stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
    }
    
    public static ArrayList<Pair<Birthday, TumblrPhotoPost>> getPhotoPosts(final Context context, Calendar birthDate) {
        DBHelper dbHelper = DBHelper
                .getInstance(context.getApplicationContext());
        List<Birthday> birthDays = dbHelper
                .getBirthdayDAO()
                .getBirthdayByDate(birthDate.getTime());
        ArrayList<Pair<Birthday, TumblrPhotoPost>> posts = new ArrayList<Pair<Birthday, TumblrPhotoPost>>();

        PostTagDAO postTagDAO = dbHelper.getPostTagDAO();
        Map<String, String> params = new HashMap<String, String>(2);
        params.put("type", "photo");
        for (Birthday b : birthDays) {
            PostTag postTag = postTagDAO.getRandomPostByTag(b.getName(), b.getTumblrName());
            if (postTag != null) {
                params.put("id", String.valueOf(postTag.getId()));
                TumblrPhotoPost post = (TumblrPhotoPost)Tumblr.getSharedTumblr(context)
                        .getPublicPosts(b.getTumblrName(), params).get(0);
                posts.add(Pair.create(b, post));
            }
        }
        return posts;
    }

    public static void publishedInAgeRange(Context context, int fromAge, int toAge, int daysPeriod, String postTags, String tumblrName) {
        if (fromAge != 0 && toAge != 0) {
            throw new IllegalArgumentException("fromAge or toAge can't be both set to 0");
        }
        String message;

        if (fromAge == 0) {
            message = context.getString(R.string.week_selection_under_age, toAge);
        } else if (toAge == 0) {
            message = context.getString(R.string.week_selection_over_age, fromAge);
        } else {
            throw new IllegalArgumentException("fromAge or toAge are both greater than 0");
        }

        final int THUMBS_COUNT = 9;
        DBHelper dbHelper = DBHelper.getInstance(context);
        List<Map<String, String>> birthdays = dbHelper.getBirthdayDAO()
                .getBirthdayByAgeRange(fromAge, toAge == 0 ? Integer.MAX_VALUE : toAge, daysPeriod, tumblrName);
        Collections.shuffle(birthdays);

        StringBuilder sb = new StringBuilder();

        Map<String, String> params = new HashMap<String, String>(2);
        TumblrPhotoPost post = null;
        params.put("type", "photo");

        int count = 0;
        for (Map<String, String> info : birthdays) {
            params.put("id", info.get("postId"));
            post = (TumblrPhotoPost)Tumblr.getSharedTumblr(context)
                        .getPublicPosts(tumblrName, params).get(0);
            String imageUrl = post.getClosestPhotoByWidth(250).getUrl();

            sb.append("<a href=\"")
                    .append(post.getPostUrl())
                    .append("\">");
            sb.append("<p>")
                    .append(context.getString(R.string.name_with_age, post.getTags().get(0), info.get("age")))
                    .append("</p>");
            sb.append("<img style=\"width: 250px !important\" src=\"")
                    .append(imageUrl)
                    .append("\"/>");
            sb.append("</a>");
            sb.append("<br/>");

            if (++count > THUMBS_COUNT) {
                break;
            }
        }
        if (post != null) {
            message = message + " (" + formatPeriodDate(-daysPeriod) + ")";

            Tumblr.getSharedTumblr(context)
                    .draftTextPost(tumblrName,
                            message,
                            sb.toString(),
                            postTags);
        }
    }

    private static String formatPeriodDate(int daysPeriod) {
        Calendar now = Calendar.getInstance();
        Calendar period = Calendar.getInstance();
        period.add(Calendar.DAY_OF_MONTH, daysPeriod);
        SimpleDateFormat sdf = new SimpleDateFormat("MMMM", Locale.US);

        if (now.get(Calendar.YEAR) == period.get(Calendar.YEAR)) {
            if (now.get(Calendar.MONTH) == period.get(Calendar.MONTH)) {
                return period.get(Calendar.DAY_OF_MONTH) + "-" + now.get(Calendar.DAY_OF_MONTH) + " " + sdf.format(now.getTime()) + ", " + now.get(Calendar.YEAR);
            }
            return period.get(Calendar.DAY_OF_MONTH) + " " + sdf.format(period.getTime())
                    + " - " + now.get(Calendar.DAY_OF_MONTH) + " " + sdf.format(now.getTime())
                    + ", " + now.get(Calendar.YEAR);
        }
        return period.get(Calendar.DAY_OF_MONTH) + " " + sdf.format(period.getTime()) + " " + period.get(Calendar.YEAR)
                + " - " + now.get(Calendar.DAY_OF_MONTH) + " " + sdf.format(now.getTime()) + " " + now.get(Calendar.YEAR);
    }

    public static class GoogleBirthdaySearcher implements BirthdaySearcher {

        @Override
        public Birthday searchBirthday(String name, String blogName) throws IOException, ParseException {
            String cleanName = name
                    .replaceAll(" ", "+")
                    .replaceAll("\"", "");
            String url = "https://www.google.com/search?hl=en&q=" + cleanName;
            String text = Jsoup.connect(url)
                    .userAgent(DESKTOP_USER_AGENT)
                    .get()
                    .text();
            // match only dates in expected format (ie. "Born: month_name day, year")
            Pattern pattern = Pattern.compile("Born: ([a-zA-Z]+ \\d{1,2}, \\d{4})");
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                String textDate = matcher.group(1);

                SimpleDateFormat dateFormat = new SimpleDateFormat("MMMM d, yyyy", Locale.US);
                Date date = dateFormat.parse(textDate);
                return new Birthday(name, date, blogName);
            }
            return null;
        }
    }

    public static class WikipediaBirthdaySearch implements BirthdaySearcher {

        @Override
        public Birthday searchBirthday(String name, String blogName) throws IOException, ParseException {
            String cleanName = StringUtils
                    .capitalize(name)
                    .replaceAll(" ", "_")
                    .replaceAll("\"", "");
            String url = "http://en.wikipedia.org/wiki/" + cleanName;
            Document document = Jsoup.connect(url)
                    .userAgent(DESKTOP_USER_AGENT)
                    .get();
            // protect against redirect
            if (document.title().toLowerCase(Locale.US).contains(name)) {
                Elements el = document.select(".bday");
                if (el.size() > 0) {
                    String birthDate = el.get(0).text();
                    return new Birthday(name, birthDate, blogName);
                }
            }
            return null;
        }
    }

    public static class FamousBirthdaySearch implements BirthdaySearcher {

        @Override
        public Birthday searchBirthday(String name, String blogName) throws IOException, ParseException {
            String cleanName = name.toLowerCase(Locale.US)
                    .replaceAll("_", "-")
                    .replaceAll(" ", "-")
                    .replaceAll("\"", "");
            String url = "http://www.famousbirthdays.com/people/" + cleanName + ".html";
            Document document = Jsoup.connect(url)
                    .userAgent(DESKTOP_USER_AGENT)
                    .get();
            Elements el = document.select("time");
            if (el.size() > 0) {
                String birthDate = el.get(0).attr("datetime");
                return new Birthday(name, birthDate, blogName);
            }
            return null;
        }
    }

    public static Birthday searchBirthday(String name, String blogName) {
        for (BirthdaySearcher bs : BIRTHDAY_SEARCHERS) {
            try {
                Birthday birthday = bs.searchBirthday(name, blogName);
                if (birthday != null) {
                    return birthday;
                }
            } catch (Exception ignored) {
            }
        }

        return null;
    }

    public static void createBirthdayPost(Context context, Bitmap cakeImage, TumblrPhotoPost post, String blogName, boolean saveAsDraft)
            throws IOException {
        String imageUrl = post.getClosestPhotoByWidth(400).getUrl();
        Bitmap image = ImageUtils.readImage(imageUrl);

        final int IMAGE_SEPARATOR_HEIGHT = 10;
        int canvasWidth = image.getWidth();
        int canvasHeight = cakeImage.getHeight() + IMAGE_SEPARATOR_HEIGHT + image.getHeight();

        Bitmap.Config config = image.getConfig() == null ? Bitmap.Config.ARGB_8888 : image.getConfig();
        Bitmap destBmp = Bitmap.createBitmap(canvasWidth, canvasHeight, config);
        Canvas canvas = new Canvas(destBmp);

        canvas.drawBitmap(cakeImage, (image.getWidth() - cakeImage.getWidth()) / 2, 0, null);
        canvas.drawBitmap(image, 0, cakeImage.getHeight() + IMAGE_SEPARATOR_HEIGHT, null);
        String name = post.getTags().get(0);
        File file = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), "birth-" + name + ".png");
        ImageUtils.saveImageAsPNG(destBmp, file);
        if (saveAsDraft) {
            Tumblr.getSharedTumblr(context).draftPhotoPost(blogName,
                    file,
                    getBirthdayCaption(context, name, blogName),
                    "Birthday, " + name);
        } else {
            Tumblr.getSharedTumblr(context).publishPhotoPost(blogName,
                    file,
                    getBirthdayCaption(context, name, blogName),
                    "Birthday, " + name);
        }
        file.delete();
    }

    public static String getBirthdayCaption(Context context, String name, String blogName) {
        DBHelper dbHelper = DBHelper
                .getInstance(context.getApplicationContext());
        Birthday birthDay = dbHelper
                .getBirthdayDAO()
                .getBirthdayByName(name, blogName);
        Calendar birthDate = Calendar.getInstance();
        birthDate.setTime(birthDay.getBirthDate());
        int age = Calendar.getInstance().get(Calendar.YEAR) - birthDate.get(Calendar.YEAR);
        // caption must not be localized
        String caption = "Happy %1$dth Birthday, %2$s!!";
        return String.format(caption, age, name);
    }

    public interface BirthdaySearcher {
        public Birthday searchBirthday(String name, String blogName) throws IOException, ParseException;
    }
}