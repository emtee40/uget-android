package com.ugetdm.uget;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.ugetdm.uget.lib.Info;
import com.ugetdm.uget.lib.Node;

public class StateAdapter extends RecyclerView.Adapter<StateAdapter.ViewHolder> {
    // C pointer to state node
    protected long      pointer;
    // resource
    protected String[]  stateNames;
    // --- single choice ---
    protected int       selectedPosition = 0;
    protected View      selectedView;    // to speed up redrawing of view on selection changed

    protected static int[]  imageIds = {
            android.R.drawable.btn_star,                // all
            android.R.drawable.ic_media_play,           // active
            android.R.drawable.ic_media_pause,          // queuing
            android.R.drawable.ic_media_next,           // finished
            android.R.drawable.ic_menu_delete,          // recycled
    };

    protected static int[]  stateGroups = {
            0,
            Info.Group.active,
            Info.Group.queuing,
            Info.Group.finished,
            Info.Group.recycled,
    };

    StateAdapter(Context context, long nodePointer) {
        pointer = nodePointer;
        stateNames = context.getResources().getStringArray(R.array.cnode_state);

        // --- avoid losing focus ---  override getItemId() and call setHasStableIds(true)
        setHasStableIds(true);
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

        if (position == 0)
            currentNode = pointer;
        else
            currentNode = Node.getFakeByGroup(pointer, stateGroups[position]);

        if (currentNode == 0 || position > imageIds.length) {
            // display error state
            holder.image.setImageResource(android.R.drawable.stat_notify_error);
            holder.name.setText("----");
            holder.quantity.setText("--");
        }
        else {
            holder.image.setImageResource(imageIds[position]);
            holder.name.setText(stateNames[position]);
            nChildren = Node.nChildren(currentNode);
            holder.quantity.setText(Integer.toString(nChildren));
            // if "Queuing" is NOT empty, replace "pause" icon
            if (position == 2 && nChildren > 0)
                holder.image.setImageResource(android.R.drawable.ic_popup_sync);
        }

        // --- single choice ---
        if (selectedPosition == position) {
            selectedView = holder.itemView;    // to speed up redrawing of view on selection changed
            holder.itemView.setSelected(true);
        }
        else
            holder.itemView.setSelected(false);
    }

    @Override
    public int getItemCount() {
        return stateNames.length;
    }

    // --- avoid losing focus ---  override getItemId() and call setHasStableIds(true)
    @Override
    public long getItemId(int position) {
        return Node.getNthChild(pointer, position);
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
                    int  position = getAdapterPosition();
                    // --- If you click fast,  it some time throw the -1 position.
                    if (position == -1)
                        return;
                    // --- single choice ---
                    if (selectedPosition != position) {
                        if (selectedView != null)       // notifyItemChanged(selectedPosition);
                            selectedView.setSelected(false);
                        selectedView = itemView;    // to speed up redrawing of view on selection changed
                        selectedPosition = position;
                        itemView.setSelected(true);     // notifyItemChanged(position);
                    }
                    // --- notify ---
                    if (onItemClickListener != null)
                        onItemClickListener.onItemClick(view, selectedPosition);
                }
            });

            view.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View view) {
                    int  position = getAdapterPosition();
                    // --- notify ---
                    if (onItemLongClickListener != null)
                        return onItemLongClickListener.onItemLongClick(view, position);
                    // --- result ---
                    // return true if the callback consumed the long click, false otherwise.
                    return false;
                }
            });
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
    public void setItemChecked(int position, boolean checked) {
        if (position < getItemCount() && position >= 0) {
            if (selectedPosition != position) {
                notifyItemChanged(selectedPosition);
                selectedPosition = position;
                notifyItemChanged(position);
            }
        }
    }

    public int getCheckedItemPosition() {
        return selectedPosition;
    }

    // ------------------------------------------------------------------------
    // Notification
    public void notifyItemClicked(RecyclerView recyclerView) {
        ViewHolder viewHolder;
        viewHolder = (ViewHolder) recyclerView.findViewHolderForAdapterPosition(selectedPosition);
        if (onItemClickListener != null)
            onItemClickListener.onItemClick(viewHolder.itemView, selectedPosition);
    }
}
