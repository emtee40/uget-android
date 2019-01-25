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
    // C pointer to node
    protected long pointer;
    protected long pointerGlobal;

    // variable for single choice
    protected View      selectedView;
    public    int       selectedPosition = 0;

    CategoryAdapter(long nodePointer, long pointerGlobal) {
        this.pointer = nodePointer;
        this.pointerGlobal = pointerGlobal;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Context context = parent.getContext();
        View view = LayoutInflater.from(context).inflate(R.layout.category_item, parent, false);
        ViewHolder holder = new ViewHolder(view);
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        long nodePointer;
        long infoPointer;

        if (position == 0) {
            nodePointer = Node.getNthChild(pointerGlobal, 0);
            holder.image.setImageResource(R.drawable.ic_all_inclusive);
            holder.name.setText(R.string.cnode_total);
        } else {
            nodePointer = Node.getNthChild(pointer, position - 1);
            infoPointer = Node.info(nodePointer);
            holder.image.setImageResource(R.drawable.ic_category);
            holder.name.setText(Info.getName(infoPointer));
        }
        holder.name.setPadding(3, 3, 3, 3);
        holder.quantity.setText(Integer.toString(Node.nChildren(nodePointer)));

        // --- single choice ---
        if (selectedPosition == position) {
            holder.itemView.setSelected(true);
            selectedView = holder.itemView;
        }
    }

    @Override
    public int getItemCount() {
        return Node.nChildren(pointer) + 1;
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