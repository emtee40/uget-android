package com.ugetdm.uget;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.Rect;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.ugetdm.uget.lib.Info;
import com.ugetdm.uget.lib.Node;
import com.ugetdm.uget.lib.Progress;
import com.ugetdm.uget.lib.Util;

public class DownloadAdapter extends RecyclerView.Adapter<DownloadAdapter.ViewHolder> {
    // C pointer to node
    protected long    pointer;
    // calculate minimum width of strings
    protected int     percentMinWidth = 0;
    protected int     retryMinWidth = 0;
    protected int     speedMinWidth = 0;
    protected int     sizeMinWidth = 0;
    // resource
    protected String  stringRetry;
    protected String  stringLeft;
    // --- multiple choice ---
    protected SparseBooleanArray selections;


    public DownloadAdapter(long nodePointer) {
        pointer = nodePointer;
        selections = new SparseBooleanArray(100);
    }

    public int calcTextWidth(TextView textView, String text) {
        Paint paint = new Paint();
        Rect bounds = new Rect();

        paint.setTypeface(textView.getTypeface());
        float textSize = textView.getTextSize();
        paint.setTextSize(textSize);
        paint.getTextBounds(text, 0, text.length(), bounds);

        return bounds.width();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Context context = parent.getContext();
        View view = LayoutInflater.from(context).inflate(R.layout.item_download,parent,false);
        ViewHolder holder = new ViewHolder(view);

        // calculate minimum width of strings
        if (retryMinWidth == 0) {
            stringRetry = context.getResources().getString(R.string.dnode_retry);
            stringLeft = context.getResources().getString(R.string.dnode_left);
            retryMinWidth = calcTextWidth(holder.retry, ' ' + stringRetry + ">999");  // + '9'
            percentMinWidth = calcTextWidth(holder.percent, "0000%");
            speedMinWidth = calcTextWidth(holder.speed, "00000 WiB/s");  // + '0'
            sizeMinWidth = calcTextWidth(holder.size, "00000 WiB / 00000 WiB");  // + '0' + '0'
        }
        holder.retry.getLayoutParams().width = retryMinWidth;
        holder.percent.getLayoutParams().width = percentMinWidth;
        holder.speed.getLayoutParams().width = speedMinWidth;
        holder.size.getLayoutParams().width = sizeMinWidth;
        // holder.percent.setText(String.format("%1$.1f%%", progress.percent));
        // holder.percent.setLayoutParams(new LinearLayout.LayoutParams(percentMinWidth,
        //         LinearLayout.LayoutParams.WRAP_CONTENT));
        // holder.percent.setMinimumWidth(percentMinWidth);

        holder.retry.requestLayout();
        holder.percent.requestLayout();
        holder.speed.requestLayout();
        holder.size.requestLayout();
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        long       nodePointer;
        long       infoPointer;
        int        state = 0;
        Progress   progress = null;
        String     message = null;
        String     name = null;

        nodePointer = Node.getNthChild(pointer, position);
        if (nodePointer != 0) {
            infoPointer = Node.info(nodePointer);
            state = Info.getGroup(infoPointer);
            name = Info.getName(infoPointer);
            message = Info.getMessage(infoPointer);
            // get progress info
            progress = new Progress();
        }

        // --- show error/debug message if this item does not exist ---
        if (nodePointer == 0) {
            String errorMessage = null;
            errorMessage = "This item does not exist.";

            holder.name.setText("Removed");
            holder.image.setImageResource(android.R.drawable.ic_delete);
            holder.retry.setText(' ' + stringRetry + ">55");
            holder.progress.setProgress((int) 55);
            // holder.progress.getProgressDrawable().setColorFilter(Color.YELLOW, PorterDuff.Mode.SRC_IN);
            holder.percent.setText(Integer.toString(55) + '%');
            if (errorMessage == null) {
                holder.message.setVisibility(View.GONE);
                holder.speed.setVisibility(View.VISIBLE);
                holder.speed.setText(Util.stringFromIntUnit(1024000, 1));
                holder.left.setVisibility(View.VISIBLE);
                holder.left.setText("55:55:55" + " " + stringLeft);
            }
            else {
                holder.message.setVisibility(View.VISIBLE);
                holder.message.setText(errorMessage);
                holder.speed.setVisibility(View.GONE);
                holder.left.setVisibility(View.GONE);
            }
            return;
        }

        // status icon
        if ((state & Node.Group.finished) > 0)
            holder.image.setImageResource(android.R.drawable.ic_media_next);
        else if ((state & Node.Group.recycled) > 0)
            holder.image.setImageResource(android.R.drawable.ic_menu_delete);
        else if ((state & Node.Group.pause) > 0)
            holder.image.setImageResource(android.R.drawable.ic_media_pause);
        else if ((state & Node.Group.error) > 0)
            holder.image.setImageResource(android.R.drawable.stat_notify_error);
        else if ((state & Node.Group.upload) > 0)
            holder.image.setImageResource(android.R.drawable.stat_sys_upload);
        else if ((state & Node.Group.queuing) > 0)
            holder.image.setImageResource(android.R.drawable.presence_invisible);
        else if ((state & Node.Group.active) > 0)
            holder.image.setImageResource(android.R.drawable.ic_media_play);
        else //  if (state == 0)    //  == Node.Group.queuing
            holder.image.setImageResource(android.R.drawable.presence_invisible);
        // else
        //     holder.image.setImageResource(0);

        // ------------------------------------------------
        // line 1: name + retry count

        // name
        holder.name.setText(name);

        // retry count
        if (progress.retryCount == 0) {
            // holder.retry.setVisibility(View.GONE);
            holder.retry.setText("");
        }
        else {
            // holder.retry.setVisibility(View.VISIBLE);
            if (progress.retryCount > 99)
                holder.retry.setText(' ' + stringRetry + ">99");
            else
                holder.retry.setText(' ' + stringRetry + ":" + Integer.toString(progress.retryCount));
            // holder.retry.setLayoutParams(new LinearLayout.LayoutParams(retryMinWidth,
            //         LinearLayout.LayoutParams.WRAP_CONTENT));
            // holder.retry.setMinimumWidth(retryMinWidth);
        }

        // ------------------------------------------------
        // line 2: progress bar + percent

        // --- progress bar ---
        holder.progress.setProgress((int) progress.percent);
        // holder.progress.getProgressDrawable().setColorFilter(Color.YELLOW, PorterDuff.Mode.SRC_IN);

        // --- percent ---
        if (progress.total > 0 && progress.percent <= 100)
            holder.percent.setText(Integer.toString(progress.percent) + '%');
        else
            holder.percent.setText("");

        // ------------------------------------------------
        // line 3: (message + size) or (speed + time left + size)

        if ((state & Node.Group.error) != 0 ) {
            // message
            holder.message.setVisibility(View.VISIBLE);
            if (message == null)
                holder.message.setText("");
            else
                holder.message.setText(message);
            // clear speed
            holder.speed.setVisibility(View.GONE);
            holder.speed.setText("");
            // clear time left
            holder.left.setVisibility(View.GONE);
            holder.left.setText("");  // + ' '
        }
        else {
            // --- clear message ---
            holder.message.setVisibility(View.GONE);
            holder.message.setText("");
            // --- speed ---
            holder.speed.setVisibility(View.VISIBLE);
            if ((state & Node.Group.active) == 0)
                holder.speed.setText("");
            else
                holder.speed.setText(Util.stringFromIntUnit(progress.downloadSpeed, 1));
            // holder.speed.setTextColor(holder.speed.getResources().getColor(android.R.color.primary_text_dark));
            // holder.speed.setTextColor(holder.speed.getResources().getColor(android.R.color.white));

            // --- time left ---
            holder.left.setVisibility(View.VISIBLE);
            if ((state & Node.Group.active) == 0 || progress.remainTime == 0)
                holder.left.setText("");
            else {
                String timeLeftString = Util.stringFromSeconds((int) progress.remainTime, 1);
                // if (timeLeftString.startsWith("00:"))
                //     timeLeftString.replace("00:", "   ");
                holder.left.setText(timeLeftString + " " + stringLeft);
            }
        }

        // size
        String sizeText;
        int    sizeTextWidth;
        if (progress.total == 0) {
            sizeTextWidth = 0;
            holder.size.setText("");
        }
        else if (progress.total == progress.complete) {
            sizeText = Util.stringFromIntUnit(progress.total, 0);
            sizeTextWidth = calcTextWidth(holder.size, sizeText);
            holder.size.setText(sizeText);
        }
        else {
            sizeText = Util.stringFromIntUnit(progress.complete, 0) + " / " +
                    Util.stringFromIntUnit(progress.total, 0);
            sizeTextWidth = calcTextWidth(holder.size, sizeText);
            holder.size.setText(sizeText);
        }
        // adjust width of size field
        if (message != null) {
            if (progress.total == 0)
                holder.size.setVisibility(View.GONE);
            else {
                holder.size.getLayoutParams().width = sizeTextWidth;
                holder.size.requestLayout();
                holder.size.setVisibility(View.VISIBLE);
            }
        }
        else {
            holder.size.setVisibility(View.VISIBLE);
        }

        // --- multiple choice ---
        if (selections.get(position))
            holder.itemView.setSelected(true);
        else
            holder.itemView.setSelected(false);
    }

    @Override
    public int getItemCount() {
        return Node.nChildren(pointer);
    }

    // ------------------------------------------------------------------------
    // ViewHolder
    public  class ViewHolder extends RecyclerView.ViewHolder {
        public ImageView   image;
        public TextView    name;
        public TextView    retry;
        public ProgressBar progress;
        public TextView    percent;
        public TextView    message;
        public TextView    speed;
        public TextView    left;
        public TextView    size;

        public class ItemListener implements View.OnClickListener, View.OnLongClickListener {
            @Override
            public void onClick(View view) {
                int  position = getAdapterPosition();
                // --- selection mode ---
                if (selections.size() > 0)
                    toggleSelection(view, position);
                // --- notify ---
                if (onItemClickListener != null)
                    onItemClickListener.onItemClick(view, position);
            }

            @Override
            public boolean onLongClick(View view) {
                int  position = getAdapterPosition();
                toggleSelection(view, position);
                // --- notify ---
                if (onItemLongClickListener != null)
                    return onItemLongClickListener.onItemLongClick(view, position);
                // --- result ---
                // return true if the callback consumed the long click, false otherwise.
                return true;
            }

            public void toggleSelection(View view, int position) {
                // --- multiple choice ---
                if (selections.get(position)) {
                    selections.delete(position);
                    view.setSelected(false);    // notifyItemChanged(position)
                }
                else {
                    selections.put(position, true);
                    view.setSelected(true);     // notifyItemChanged(position)
                }
            }
        }

        public ViewHolder(View view) {
            super(view);
            image = view.findViewById(R.id.dnode_image);
            name = view.findViewById(R.id.dnode_name);
            retry = view.findViewById(R.id.dnode_retry);
            progress = view.findViewById(R.id.dnode_progress);
            percent = view.findViewById(R.id.dnode_percent);
            message = view.findViewById(R.id.dnode_message);
            speed = view.findViewById(R.id.dnode_speed);
            left = view.findViewById(R.id.dnode_left);
            size = view.findViewById(R.id.dnode_size);

            // view.setLongClickable(true);
            ItemListener itemListener = new ItemListener();
            view.setOnClickListener(itemListener);
            view.setOnLongClickListener(itemListener);
        }
    }

    // ------------------------------------------------------------------------
    // Listener
    private OnItemClickListener     onItemClickListener;
    private OnItemLongClickListener onItemLongClickListener;

    public interface OnItemClickListener {
        void onItemClick(View view, int position);
    }

    public interface OnItemLongClickListener {
        boolean onItemLongClick(View view, int position);
    }

    public void setOnItemClickListener(OnItemClickListener clickListener) {
        onItemClickListener = clickListener;
    }

    public void setOnItemLongClickListener(OnItemLongClickListener longClickListener) {
        onItemLongClickListener = longClickListener;
    }

    // ------------------------------------------------------------------------
    // Selection - implement ListView API

    public void clearChoices() {
        selections.clear();
        notifyDataSetChanged();
    }

    public int getCheckedItemCount() {
        return selections.size();
    }

    SparseBooleanArray getCheckedItemPositions() {
        return selections.clone();
    }

    public boolean isItemChecked (int position) {
        return selections.get(position);
    }

    public void setItemChecked(int position, boolean checked) {
        if (checked) {
            if (selections.get(position) == false)
                selections.put(position, true);
        }
        else
            selections.delete(position);
        // --- notify ---
        notifyItemChanged(position);
    }
}

// --- icon list ---
// Queuing   - android.R.drawable.ic_popup_sync
// Queuing   - android.R.drawable.presence_invisible
// Active    - android.R.drawable.ic_media_play
// Pause     - android.R.drawable.ic_media_pause
// Warning   - android.R.drawable.ic_dialog_alert
// Warning   - android.R.drawable.stat_sys_warning
// Error     - android.R.drawable.stat_notify_error
// Info      - android.R.drawable.ic_dialog_info
// Upload    - android.R.drawable.stat_sys_upload
// Finished  - android.R.drawable.ic_media_next
// Finished  - android.R.drawable.stat_sys_download_done
// Recycled  - android.R.drawable.ic_menu_delete
// Batch A-Z - android.R.drawable.ic_menu_sort_alphabetically

// android.R.drawable.picture_frame
// android.R.drawable.ic_delete
// android.R.drawable.ic_menu_upload
// android.R.drawable.ic_menu_preferences
