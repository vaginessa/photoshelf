package com.ternaryop.photoshelf.adapter.feedly;

import android.content.res.TypedArray;
import android.os.Build;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;

import com.ternaryop.feedly.FeedlyContent;
import com.ternaryop.photoshelf.R;

/**
 * Created by dave on 24/02/17.
 * The ViewHolder used by the Feedly list
 */
class FeedlyContentViewHolder extends RecyclerView.ViewHolder {
    final TextView title;
    final TextView subtitle;
    final CheckBox checkbox;

    public FeedlyContentViewHolder(View itemView) {
        super(itemView);
        title = (TextView) itemView.findViewById(android.R.id.text1);
        subtitle = (TextView) itemView.findViewById(android.R.id.text2);
        checkbox = (CheckBox) itemView.findViewById(android.R.id.checkbox);
    }

    public void bindModel(FeedlyContentDelegate content) {
        // setting listener to null resolved the lost of unchecked state
        // http://stackoverflow.com/a/32428115/195893
        checkbox.setOnCheckedChangeListener(null);
        updateCheckbox(content);
        updateTitles(content);
        updateItemColors(content);
    }

    private void updateCheckbox(FeedlyContentDelegate content) {
        checkbox.setButtonDrawable(R.drawable.checkbox_bookmark);
        checkbox.setVisibility(View.VISIBLE);
        checkbox.setChecked(content.checked);
    }

    private void updateTitles(FeedlyContentDelegate content) {
        title.setText(content.getTitle());

        subtitle.setText(String.format("%s / %s / %s", content.getOrigin().getTitle(),
                content.getActionTimestampAsString(itemView.getContext()),
                content.getLastPublishTimestampAsString(itemView.getContext())));
    }

    private void updateItemColors(FeedlyContentDelegate content) {
        if (content.lastPublishTimestamp < 0) {
            setColors(R.array.post_never);
        } else {
            setColors(R.array.post_normal);
        }
    }
    @SuppressWarnings("ResourceType")
    private void setColors(int resArray) {
        TypedArray array = itemView.getContext().getResources().obtainTypedArray(resArray);
        itemView.setBackground(array.getDrawable(0));

        if (Build.VERSION.SDK_INT < 23) {
            title.setTextAppearance(itemView.getContext(), array.getResourceId(5, 0));
        } else {
            title.setTextAppearance(array.getResourceId(5, 0));
        }
        subtitle.setTextColor(array.getColorStateList(1));
        array.recycle();
    }

    public void setOnClickListeners(FeedlyContent content, View.OnClickListener listener) {
        if (listener == null) {
            return;
        }
        final int position = getAdapterPosition();
        itemView.setOnClickListener(listener);
        itemView.setTag(position);
    }

    public void setOnCheckedChangeListener(FeedlyContentDelegate content, CompoundButton.OnCheckedChangeListener listener) {
        if (listener == null) {
            return;
        }
        final int position = getAdapterPosition();
        checkbox.setOnCheckedChangeListener(listener);
        checkbox.setTag(position);
    }
}
