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
    protected long    pointerMix;
    // if mixMode == true, place pointerMix ("All Category") at first.
    protected boolean mixMode;
    // --- single choice ---
    protected int     selectedPosition = 0;
    protected View    selectedView;    // to speed up redrawing of view on selection changed

    CategoryAdapter(long nodePointer, long nodeMix) {
        pointer = nodePointer;
        pointerMix = nodeMix;

        // if mixMode == false, CategoryAdapter will remove first item - "All Category"
        if (pointerMix == 0)
            mixMode = false;
        else
            mixMode = true;
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

        // if mixMode == false, CategoryAdapter will remove first item - "All Category"
        if (mixMode == false)
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

        // if mixMode == false, CategoryAdapter will remove first item - "All Category"
        if (mixMode == false)
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

        // if mixMode == false, CategoryAdapter will remove first item - "All Category"
        if (mixMode == false)
            return count;
        else
            return count + 1;
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

    // ------------------------------------------------------------------------
    // Mix Mode
    public void setMixMode(boolean enable) {
        if (mixMode != enable) {
            mixMode  = enable;
            if (enable == false) {
				// remove first item - "All Category"
                notifyItemRemoved(0);
                selectedPosition--;
                // select first item if original selected item has gone.
                if (selectedPosition < 0) {
                    selectedPosition = 0;
                    notifyItemChanged(0);
                }
            }
            else {
				// restore first item - "All Category"
                selectedPosition++;
                notifyItemInserted(0);
            }
        }
    }

}