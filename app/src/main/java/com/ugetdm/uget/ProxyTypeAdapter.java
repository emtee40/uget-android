/*
 *
 *   Copyright (C) 2018-2019 by C.H. Huang
 *   plushuang.tw@gmail.com
 */

package com.ugetdm.uget;

import android.content.Context;
import android.view.*;
import android.widget.*;

public class ProxyTypeAdapter extends BaseAdapter implements SpinnerAdapter {
	protected String[]  typeNames;
	protected Context   context;

	ProxyTypeAdapter (Context context)
	{
		this.context = context;
		this.typeNames = context.getResources().getStringArray(R.array.dnode_proxy_type);
	}

	@Override
	public int getCount ()
	{
		return typeNames.length;
	}

	@Override
	public Object getItem (int position)
	{
		return position;
	}

	@Override
	public long getItemId (int position)
	{
		return position;
	}

	@Override
	public View getView (int position, View convertView, ViewGroup parent)
	{
		TextView  textView;
		ImageView imageView;

		if (convertView == null) {
			LayoutInflater  lInflater = (LayoutInflater) context.getSystemService(
					Context.LAYOUT_INFLATER_SERVICE);
			convertView = lInflater.inflate (R.layout.item_proxy, parent, false);
		}

		imageView = (ImageView) convertView.findViewById (R.id.proxy_item_image);
		if (position == 0)
			imageView.setImageResource(android.R.drawable.ic_delete);  // android.R.drawable.presence_offline
		else
			imageView.setImageResource(android.R.drawable.presence_online);

		textView = (TextView) convertView.findViewById (R.id.proxy_item_name);
		textView.setText(typeNames[position]);
		return convertView;
	}
}

