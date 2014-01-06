package com.ternaryop.photoshelf.fragment;

import java.text.DateFormatSymbols;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import android.app.ActionBar;
import android.os.Bundle;
import android.text.Html;
import android.text.method.ScrollingMovementMethod;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.ternaryop.photoshelf.R;
import com.ternaryop.photoshelf.db.Birthday;
import com.ternaryop.photoshelf.db.BirthdayDAO;
import com.ternaryop.photoshelf.db.DBHelper;

public class BirthdaysFragment extends AbsPhotoShelfFragment implements ActionBar.OnNavigationListener {
    private TextView textView;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.activity_birthdays_main, container, false);

        textView = (TextView)rootView.findViewById(R.id.text);
        // allow textView to scroll
        textView.setMovementMethod(new ScrollingMovementMethod());

        setupActionBar();
        setHasOptionsMenu(true);
        
        BirthdayDAO birthdayDatabaseHelper = DBHelper
                .getInstance(getActivity())
                .getBirthdayDAO();
        List<Birthday> list = birthdayDatabaseHelper.getBirthdayByDate(new Date());
        fillList(list);
        
        return rootView;
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        ActionBar actionBar = getActivity().getActionBar();

        if (fragmentActivityStatus.isDrawerOpen()) {
            actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
            actionBar.setDisplayShowTitleEnabled(true);
        } else {
            actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
            actionBar.setDisplayShowTitleEnabled(false);
        }
        super.onPrepareOptionsMenu(menu);
    }
    
    private void fillList(List<Birthday> list) {
        StringBuilder sb = new StringBuilder();
        Calendar now = Calendar.getInstance();
        int dayOfMonth = now.get(Calendar.DAY_OF_MONTH);
        int month = now.get(Calendar.MONTH);
        Calendar date = Calendar.getInstance();

        for (Birthday birthday : list) {
            date.setTime(birthday.getBirthDate());
        
            if (date.get(Calendar.DAY_OF_MONTH) == dayOfMonth
                    && date.get(Calendar.MONTH) == month) {
                sb.append("<b>" + birthday + "</b>");
            } else {
                sb.append(birthday);
            }
            sb.append("<br/>");
        }
        textView.setText(Html.fromHtml(sb.toString()));
    }

    private void setupActionBar() {
        ActionBar actionBar = getActivity().getActionBar();
        ArrayAdapter<String> monthAdapter = new ArrayAdapter<String>(
                actionBar.getThemedContext(),
                android.R.layout.simple_spinner_item,
                new DateFormatSymbols().getMonths());
        monthAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
        actionBar.setDisplayShowTitleEnabled(false);
        actionBar.setListNavigationCallbacks(monthAdapter, this);
        actionBar.setSelectedNavigationItem(Calendar.getInstance().get(Calendar.MONTH));
    }

    @Override
    public boolean onNavigationItemSelected(int itemPosition, long itemId) {
        List<Birthday> list = DBHelper.getInstance(getActivity())
                .getBirthdayDAO()
                .getBirthdayByMonth(itemPosition + 1, getBlogName());
        fillList(list);

        return true;
    }
}
