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

import java.util.Arrays;

public class DownloadAdapter extends RecyclerView.Adapter<DownloadAdapter.ViewHolder> {
    // C pointer to node
    protected long    pointer;
    // calculate minimum width of strings
    private   int     percentMinWidth = 0;
    private   int     retryMinWidth = 0;
    private   int     speedMinWidth = 0;
    private   int     sizeMinWidth = 0;
    //private   int     leftMinWidth = 0;
    //private   int     leftMaxWidth = 0;
    // --- multiple choice ---
    private   SparseBooleanArray selections;
    // --- single choice ---
    public    boolean singleSelection;


    public DownloadAdapter(long nodePointer) {
        pointer = nodePointer;
        selections = new SparseBooleanArray(100);

        // --- avoid losing focus ---  override getItemId() and call setHasStableIds(true)
        //setHasStableIds(true);
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
            //leftMaxWidth = calcTextWidth(holder.left, context.getString(R.string.dnode_time_left, "000:00:00"));  // + '0'
            //leftMinWidth = calcTextWidth(holder.left, "000:00:00");    // + '0'
            retryMinWidth = calcTextWidth(holder.retry, context.getString(R.string.dnode_retry_counts, 999));  // + '9'
            percentMinWidth = calcTextWidth(holder.percent, "000%") + 4;  // + padding
            speedMinWidth = calcTextWidth(holder.speed, "00000 WiB/s");  // + '0'
            sizeMinWidth = calcTextWidth(holder.size, "0000 WiB / 00000 WiB");  // + '0'
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
        Context    context = holder.itemView.getContext();
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
            Info.get(infoPointer, progress);
        }

        // --- show error/debug message if this item does not exist ---
        if (nodePointer == 0) {
            message = null;
            // message = "This item does not exist.";

            holder.name.setText(android.R.string.no);
            holder.image.setImageResource(android.R.drawable.ic_delete);
            holder.retry.setText(context.getString(R.string.dnode_retry_counts, 55));
            holder.retry.getLayoutParams().width = retryMinWidth;
            holder.retry.requestLayout();
            holder.progress.setProgress((int) 55);
            // holder.progress.getProgressDrawable().setColorFilter(Color.YELLOW, PorterDuff.Mode.SRC_IN);
            holder.percent.setText(Integer.toString(55) + '%');
            if (message == null) {
                holder.message.setVisibility(View.GONE);
                holder.speed.setVisibility(View.VISIBLE);
                holder.speed.setText(Util.stringFromIntUnit(1024000, 1));
                holder.left.setVisibility(View.VISIBLE);
                holder.left.setText(context.getString(R.string.dnode_time_left,"55:55:55"));
                holder.size.setVisibility(View.VISIBLE);
                holder.size.setText("1055 KiB / 2055 KiB");
            }
            else {
                holder.message.setVisibility(View.VISIBLE);
                holder.message.setText(message);
                holder.speed.setVisibility(View.GONE);
                holder.left.setVisibility(View.GONE);
            }
            return;
        }

        // status icon
        if ((state & Info.Group.finished) > 0)
            holder.image.setImageResource(android.R.drawable.ic_media_next);
        else if ((state & Info.Group.recycled) > 0)
            holder.image.setImageResource(android.R.drawable.ic_menu_delete);
        else if ((state & Info.Group.pause) > 0)
            holder.image.setImageResource(android.R.drawable.ic_media_pause);
        else if ((state & Info.Group.error) > 0)
            holder.image.setImageResource(R.drawable.ic_error);
        else if ((state & Info.Group.upload) > 0)
            holder.image.setImageResource(android.R.drawable.ic_menu_upload);
        else if ((state & Info.Group.queuing) > 0)
            holder.image.setImageResource(android.R.drawable.presence_invisible);
        else if ((state & Info.Group.active) > 0)
            holder.image.setImageResource(android.R.drawable.ic_media_play);
        else //  if (state == 0)    //  == Info.Group.queuing
            holder.image.setImageResource(android.R.drawable.presence_invisible);
        // else
        //     holder.image.setImageResource(0);

        // ------------------------------------------------
        // line 1: name + retry count

        // name
        holder.name.setText(name);

        // retry count
        if (progress.retryCount == 0) {
            // if no retry counts, don't show retry counts.
            // avoid overlap percent
            holder.retry.setText("");
            holder.retry.getLayoutParams().width = percentMinWidth;
        }
        else {
            // holder.retry.setVisibility(View.VISIBLE);
            if (progress.retryCount > 99)
                progress.retryCount = 99;
            holder.retry.setText(context.getString(R.string.dnode_retry_counts, progress.retryCount));
            holder.retry.getLayoutParams().width = retryMinWidth;
            // holder.retry.setLayoutParams(new LinearLayout.LayoutParams(retryMinWidth,
            //         LinearLayout.LayoutParams.WRAP_CONTENT));
            // holder.retry.setMinimumWidth(retryMinWidth);
        }
        holder.retry.requestLayout();

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

        if ((state & Info.Group.active) == 0) {
            holder.speed.setVisibility(View.GONE);
            holder.left.setVisibility(View.GONE);
            if (message == null)
                holder.message.setText("");
            else {
                holder.message.setText(message);
                holder.message.setVisibility(View.VISIBLE);
                if ((state & Info.Group.error) == 0)
                    holder.message.setTextColor(holder.message.getResources().getColor(android.R.color.tab_indicator_text));
                else
                    holder.message.setTextColor(holder.message.getResources().getColor(android.R.color.holo_red_dark));
                // android.R.color.tab_indicator_text =  0x1060009
            }
        }
        else {
            holder.speed.setVisibility(View.VISIBLE);
            holder.left.setVisibility(View.VISIBLE);
            holder.message.setVisibility(View.GONE);
            // --- speed & left ---
            holder.speed.setText(Util.stringFromIntUnit(progress.downloadSpeed, 1));
            if (progress.remainTime == 0)
                holder.left.setText("");
            else {
                String timeLeftString = Util.stringFromSeconds((int) progress.remainTime, 1);
                // if (timeLeftString.startsWith("00:"))
                //     timeLeftString.replace("00:", "   ");
                holder.left.setText(context.getString(R.string.dnode_time_left, timeLeftString));
            }
        }

        // --- size ---
        String sizeText;
        int    sizeTextWidth;
        if (progress.total == 0) {
            sizeText = "";
            sizeTextWidth = 0;
        }
        else if (progress.total == progress.complete) {
            sizeText = Util.stringFromIntUnit(progress.total, 0);
            sizeTextWidth = calcTextWidth(holder.size, sizeText) + 4;    // + padding
        }
        else {
            sizeText = Util.stringFromIntUnit(progress.complete, 0) + " / " +
                    Util.stringFromIntUnit(progress.total, 0);
            sizeTextWidth = calcTextWidth(holder.size, sizeText) + 4;    // + padding
        }
        holder.size.setText(sizeText);
        if ((state & Info.Group.active) == 0)
            holder.size.getLayoutParams().width = sizeTextWidth;
        else
            holder.size.getLayoutParams().width = sizeMinWidth;
        holder.size.requestLayout();

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

    // --- avoid losing focus ---  override getItemId() and call setHasStableIds(true)
    //@Override
    //public long getItemId(int position) {
    //    return Node.getNthChild(pointer, position);
    //}

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
                int position;
                // --- selection mode ---
                if (selections.size() == 0)
                    singleSelection = true;
                else if (singleSelection) {
                    position = getCheckedItemPosition();
                    if (position >= 0)
                        toggleSelection(view, position);
                }
                // --- If you click fast, getAdapterPosition() sometimes throw the -1 position.
                position = getAdapterPosition();
                if (position == -1)
                    return;
                toggleSelection(view, position);
                // --- notify ---
                if (onItemClickListener != null)
                    onItemClickListener.onItemClick(view, position);
            }

            @Override
            public boolean onLongClick(View view) {
                int position;
                // --- selection mode ---
                if (singleSelection) {
                    singleSelection = false;
                    position = getCheckedItemPosition();
                    if (position >= 0)
                        toggleSelection(view, position);
                }
                position = getAdapterPosition();
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
                if (selections.get(position))
                    selections.delete(position);
                else
                    selections.put(position, true);
                notifyItemChanged(position);
            }
        }

        public ViewHolder(View view) {
            super(view);
            image = view.findViewById(R.id.dnode_image);
            name = view.findViewById(R.id.dnode_name);
            retry = view.findViewById(R.id.dnode_retry_counts);
            progress = view.findViewById(R.id.dnode_progress);
            percent = view.findViewById(R.id.dnode_percent);
            message = view.findViewById(R.id.dnode_message);
            speed = view.findViewById(R.id.dnode_speed);
            left = view.findViewById(R.id.dnode_time_left);
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

    public void clearChoices(boolean notification) {
        if (notification) {
            int  size = selections.size();
            int  position;
            for (int i = 0;  i < size;  i++) {
                position = selections.keyAt(i);
                notifyItemChanged(position);
            }
        }
        selections.clear();
    }

    public int getCheckedItemCount() {
        return selections.size();
    }

    public int getCheckedItemPosition() {
        if (selections.size() != 0)
            return selections.keyAt(0);
        else
            return -1;
    }

    SparseBooleanArray getCheckedItemPositions() {
        return selections.clone();
    }

    public boolean isItemChecked (int position) {
        return selections.get(position);
    }

    public void setItemChecked(int position, boolean checked) {
        if (position >= 0 && position < getItemCount()) {
            if (checked)
                selections.put(position, true);
            else
                selections.delete(position);
            // --- notify ---
            notifyItemChanged(position);
        }
    }

    // ------------------------------------------------------------------------
    // Selection - UgetNode pointer

    public long[] getCheckedNodes() {
        int  size = selections.size();
        if (size == 0)
            return null;

        long nodeArray[] = new long[size];
        for (int i = 0;  i < size;  i++)
            nodeArray[i] = selections.keyAt(i);
        Arrays.sort(nodeArray);
        Node.getChildrenByPositions(pointer, nodeArray);
        return nodeArray;
    }

    public int setCheckedNodes(long[] nodeArray) {
        int   position;

        clearChoices(false);   // clear selection
        if (nodeArray != null) {
            Node.getPositionsByChildren(pointer, nodeArray);

            for (int i = 0;  i < nodeArray.length;  i++) {
                position = (int) nodeArray[i];
                if (position < 0)
                    continue;
                selections.put(position, true);
            }
        }
        notifyDataSetChanged();
        return selections.size();
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
// Upload    - android.R.drawable.ic_menu_upload
// Upload    - android.R.drawable.stat_sys_upload
// Finished  - android.R.drawable.ic_media_next
// Finished  - android.R.drawable.stat_sys_download_done
// Recycled  - android.R.drawable.ic_menu_delete
// Batch A-Z - android.R.drawable.ic_menu_sort_alphabetically

// android.R.drawable.picture_frame
// android.R.drawable.ic_delete
// android.R.drawable.ic_menu_upload
// android.R.drawable.ic_menu_preferences
