package com.ugetdm.uget;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.ugetdm.uget.lib.Node;

/* ViewHolder.itemView
You must use selector and add below line in top itemView in resource.xml
android:background="@drawable/selector_recycler_item"
 */
public class StateAdapter extends RecyclerView.Adapter<StateAdapter.ViewHolder> {
    // C pointer to node
    protected long      nodePointer;
    // resource
    protected String[]  stateNames;
    // variable for single choice
    protected View      selectedView;
    public    int       selectedPosition = 0;

    protected static int[]  imageIds = {
            android.R.drawable.star_off,                // all
            android.R.drawable.ic_media_play,           // active
            android.R.drawable.ic_media_pause,          // queuing
            android.R.drawable.ic_media_next,           // finished
            android.R.drawable.ic_menu_delete,          // recycled
    };

    protected static int[]  stateValue = {
            0,
            Node.Group.active,
            Node.Group.queuing,
            Node.Group.finished,
            Node.Group.recycled,
    };

    StateAdapter(Context context, long nodePointer) {
        this.nodePointer = nodePointer;
        this.stateNames = context.getResources().getStringArray(R.array.cnode_state);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Context context = parent.getContext();
        View view = LayoutInflater.from(context).inflate(R.layout.item_category,parent,false);
        ViewHolder holder = new ViewHolder(view);
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        int       nChildren;
        long      currentNode;

        holder.image.setImageResource(imageIds[position]);
        holder.name.setText (stateNames[position]);
        holder.name.setPadding (3,3,3,3);

        if (position == 0)
            currentNode = nodePointer;
        else
            currentNode = Node.getFakeByGroup(nodePointer, stateValue[position]);

        if (currentNode != 0) {
            nChildren = Node.nChildren(currentNode);
            holder.quantity.setText(Integer.toString(nChildren));

            // Queuing is NOT empty
            if (position == 2 && nChildren > 0)
                holder.image.setImageResource(android.R.drawable.ic_popup_sync);
        }
        else
            holder.quantity.setText("0");

        // --- single choice ---
        if (selectedPosition == position) {
            holder.itemView.setSelected(true);
            selectedView = holder.itemView;
        }
    }

    @Override
    public int getItemCount() {
        return stateNames.length;
    }

    // ------------------------------------------------------------------------
    // ViewHolder
    public  class ViewHolder extends RecyclerView.ViewHolder {
        public ImageView image;
        public TextView  name;
        public TextView  quantity;

        public ViewHolder(View view) {
            super(view);
            image = view.findViewById(R.id.cnode_image);
            name = view.findViewById(R.id.cnode_name);
            quantity = view.findViewById(R.id.cnode_quantity);

            view.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    // --- single choice ---
                    if (selectedView != itemView) {
                        selectedView.setSelected(false);
                        itemView.setSelected(true);
                        selectedView = itemView;
                    }
                    selectedPosition = getAdapterPosition();
                    // notifyItemChanged(selectedPosition);
                    // --- notify ---
                    if (onItemClickListener != null)
                        onItemClickListener.onItemClick(view, selectedPosition);
                }
            });
        }
    }

    // ------------------------------------------------------------------------
    // Listener
    private ItemClickListener onItemClickListener;

    public interface ItemClickListener {
        void onItemClick(View view, int position);
    }

    public void setItemClickListener(ItemClickListener clickListener) {
        onItemClickListener = clickListener;
    }
}

//	iButton.setImageDrawable (context.getResources().getDrawable(
//	android.R.drawable.picture_frame));
//android.R.drawable.picture_frame
//android.R.drawable.ic_delete
//android.R.drawable.ic_menu_upload

//Active    - android.R.drawable.ic_media_play
//Queuing   - android.R.drawable.ic_popup_sync
//Pause     - android.R.drawable.ic_media_pause
//Warning   - android.R.drawable.ic_dialog_alert
//Warning   - android.R.drawable.stat_sys_warning
//Info      - android.R.drawable.ic_dialog_info
//Upload    - android.R.drawable.stat_sys_upload
//Finished  - android.R.drawable.ic_media_next
//Finished  - android.R.drawable.stat_sys_download_done
//Recycled  - android.R.drawable.ic_menu_delete
//Batch A-Z - android.R.drawable.ic_menu_sort_alphabetically

//
//android.R.drawable.ic_menu_preferences

