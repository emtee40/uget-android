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

public class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.ViewHolder> {
    // C pointer to category node
    protected long    pointer;
    protected long    pointerMix;    // "All Category" at first item
    // --- single choice ---
    protected int     selectedPosition = 0;
    protected View    selectedView;    // to speed up redrawing of view on selection changed

    CategoryAdapter(long nodePointer, long nodeMix) {
        // if pointerMix == 0, CategoryAdapter will remove first item - "All Category"
        pointer = nodePointer;
        pointerMix = nodeMix;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Context context = parent.getContext();
        View view = LayoutInflater.from(context).inflate(R.layout.item_category, parent, false);
        ViewHolder holder = new ViewHolder(view);
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        long nodePointer;
        long infoPointer;

        // if pointerMix == 0, CategoryAdapter will remove first item - "All Category"
        if (pointerMix == 0)
            position++;
        // set icon and name
        if (position == 0) {
            nodePointer = Node.getNthChild(pointerMix, 0);
            holder.image.setImageResource(R.drawable.ic_all_inclusive);
            holder.name.setText(R.string.cnode_total);
        } else {
            nodePointer = Node.getNthChild(pointer, position - 1);
            infoPointer = Node.info(nodePointer);
            holder.image.setImageResource(R.drawable.ic_category);
            holder.name.setText(Info.getName(infoPointer));
        }
        holder.quantity.setText(Integer.toString(Node.nChildren(nodePointer)));

        // if pointerMix == 0, CategoryAdapter will remove first item - "All Category"
        if (pointerMix == 0)
            position--;

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
        int  count = Node.nChildren(pointer);

        // if pointerMix == 0, CategoryAdapter will remove first item - "All Category"
        if (pointerMix == 0)
            return count;
        else
            return count + 1;
    }

    // avoid that RecyclerView's views are blinking when notifyDataSetChanged()
    @Override
    public long getItemId(int position) {
        return Node.getNthChild(pointer, position);
    }

    // ------------------------------------------------------------------------
    // ViewHolder
    public class ViewHolder extends RecyclerView.ViewHolder {
        public ImageView image;
        public TextView name;
        public TextView quantity;

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
                        onItemClickListener.onItemClick(view, position);
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