package com.example.gogdownloader.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.gogdownloader.R;
import com.example.gogdownloader.models.DownloadLink;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.stream.Collectors;

public class DownloadLinkAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ITEM = 1;

    private Context context;
    private List<Object> items;
    private OnDownloadLinkSelectedListener listener;

    public interface OnDownloadLinkSelectedListener {
        void onDownloadLinkSelected(DownloadLink downloadLink);
    }

    public DownloadLinkAdapter(Context context, List<DownloadLink> downloadLinks, OnDownloadLinkSelectedListener listener) {
        this.context = context;
        this.listener = listener;
        this.items = new ArrayList<>();

        // Group by type
        Map<DownloadLink.FileType, List<DownloadLink>> groupedLinks =
                downloadLinks.stream().collect(Collectors.groupingBy(DownloadLink::getType));

        for (Map.Entry<DownloadLink.FileType, List<DownloadLink>> entry : groupedLinks.entrySet()) {
            items.add(entry.getKey().name());
            items.addAll(entry.getValue());
        }
    }

    @Override
    public int getItemViewType(int position) {
        if (items.get(position) instanceof String) {
            return TYPE_HEADER;
        } else {
            return TYPE_ITEM;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_HEADER) {
            View view = LayoutInflater.from(context).inflate(android.R.layout.simple_list_item_1, parent, false);
            return new HeaderViewHolder(view);
        } else {
            View view = LayoutInflater.from(context).inflate(android.R.layout.simple_list_item_2, parent, false);
            return new ItemViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder.getItemViewType() == TYPE_HEADER) {
            HeaderViewHolder headerViewHolder = (HeaderViewHolder) holder;
            headerViewHolder.headerTitle.setText((String) items.get(position));
        } else {
            ItemViewHolder itemViewHolder = (ItemViewHolder) holder;
            DownloadLink downloadLink = (DownloadLink) items.get(position);
            itemViewHolder.text1.setText(downloadLink.getName());
            itemViewHolder.text2.setText(downloadLink.getFormattedSize());
            itemViewHolder.itemView.setOnClickListener(v -> listener.onDownloadLinkSelected(downloadLink));
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        TextView headerTitle;

        HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            headerTitle = itemView.findViewById(android.R.id.text1);
        }
    }

    static class ItemViewHolder extends RecyclerView.ViewHolder {
        TextView text1;
        TextView text2;

        ItemViewHolder(@NonNull View itemView) {
            super(itemView);
            text1 = itemView.findViewById(android.R.id.text1);
            text2 = itemView.findViewById(android.R.id.text2);
        }
    }
}
