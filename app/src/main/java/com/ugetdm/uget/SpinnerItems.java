/*
 *
 *   Copyright (C) 2018-2020 by C.H. Huang
 *   plushuang.tw@gmail.com
 */

package com.ugetdm.uget;

import android.content.Context;
import android.view.*;
import android.widget.*;

import static java.lang.Math.max;

public class SpinnerItems extends BaseAdapter implements SpinnerAdapter {
	protected String[]  names = null;
	protected int[]     imageIds;
	protected Context   context;

	SpinnerItems(Context context) {
		this.context = context;
	}

	@Override
	public int getCount() {
		int  length1 = 0, length2 = 0;

		if (names != null)
			length1 = names.length;
		if (imageIds != null)
			length2 = imageIds.length;
		return max(length1, length2);
	}

	@Override
	public Object getItem(int position) {
		return position;
	}

	@Override
	public long getItemId(int position)	{
		return position;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		TextView  textView;
		ImageView imageView;

		if (convertView == null) {
			LayoutInflater  lInflater = (LayoutInflater) context.getSystemService(
					Context.LAYOUT_INFLATER_SERVICE);
			convertView = lInflater.inflate(R.layout.item_spinner, parent, false);
		}

		if (names != null) {
			textView = (TextView) convertView.findViewById(R.id.spinner_item_name);
			textView.setText(names[position]);
		}
		if (imageIds != null) {
			imageView = (ImageView) convertView.findViewById(R.id.spinner_item_image);
			if (position >= imageIds.length)
				position = imageIds.length - 1;
			imageView.setImageResource(imageIds[position]);
		}
		return convertView;
	}
}

// android.R.drawable.presence_offline
// android.R.drawable.presence_online
