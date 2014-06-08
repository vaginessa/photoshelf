package com.ternaryop.photoshelf.db;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.text.Html;
import android.text.Spanned;
import android.view.View;
import android.widget.FilterQueryProvider;
import android.widget.SimpleCursorAdapter;
import android.widget.SimpleCursorAdapter.ViewBinder;
import android.widget.TextView;

import com.ternaryop.photoshelf.R;
import com.ternaryop.photoshelf.activity.TagPhotoBrowserActivity;
import com.ternaryop.utils.DateTimeUtils;
import com.ternaryop.utils.StringUtils;

/**
 * Used by searchView in actionBar
 * @author dave
 *
 */
public class BirthdayCursorAdapter extends SimpleCursorAdapter implements FilterQueryProvider, ViewBinder {
    private String blogName;
    private DBHelper dbHelper;
    private String pattern = "";
    private Context context;
    private SimpleDateFormat dateFormat;

    public BirthdayCursorAdapter(Context context, String blogName) {
        super(context,
                android.R.layout.simple_list_item_2,
                null,
                new String[] { BirthdayDAO.NAME, BirthdayDAO.BIRTH_DATE },
                new int[] { android.R.id.text1, android.R.id.text2 },
                0);
        this.context = context;
        this.blogName = blogName;
        dbHelper = DBHelper.getInstance(context);
        dateFormat = new SimpleDateFormat("d MMMM, yyyy");

        setViewBinder(this);
        setFilterQueryProvider(this);
    }

    @Override
    public Cursor runQuery(CharSequence constraint) {
        this.pattern = constraint == null ? "" : constraint.toString().trim();
        return dbHelper.getBirthdayDAO().getBirthdayCursorByName(pattern, blogName);
    }

    public String convertToString(final Cursor cursor) {
        final int columnIndex = cursor.getColumnIndexOrThrow(BirthdayDAO.NAME);
        return cursor.getString(columnIndex);
    }

    @Override
    public boolean setViewValue(final View view, final Cursor cursor, final int columnIndex) {
        if (columnIndex == cursor.getColumnIndexOrThrow(BirthdayDAO.NAME)) {
            if (pattern.length() > 0) {
                final String htmlHighlightPattern = StringUtils.htmlHighlightPattern(
                        pattern, cursor.getString(columnIndex));
                final Spanned spanned = Html.fromHtml(htmlHighlightPattern);
                ((TextView) view).setText(spanned);
            } else {
                ((TextView) view).setText(cursor.getString(columnIndex));
            }
        } else if (columnIndex == cursor.getColumnIndexOrThrow(BirthdayDAO.BIRTH_DATE)) {
            try {
                Calendar c = Calendar.getInstance();
                c.setTime(Birthday.ISO_DATE_FORMAT.parse(cursor.getString(columnIndex)));

                String age = String.valueOf(DateTimeUtils.yearsBetweenDates(c, Calendar.getInstance()));
                String dateStr = dateFormat.format(c.getTime());

                ((TextView) view).setText(context.getString(R.string.name_with_age, dateStr, age));
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }
        return true;
    }

    public String getBlogName() {
        return blogName;
    }

    public void setBlogName(String blogName) {
        this.blogName = blogName;
    }

    public void browsePhotos(Activity activity, int position) {
        String tag = getCursor().getString(getCursor().getColumnIndex(BirthdayDAO.NAME));
        TagPhotoBrowserActivity.startPhotoBrowserActivity(activity, getBlogName(), tag, false);

    }
}
