package com.example.gogdownloader.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.gogdownloader.R;
import com.example.gogdownloader.models.DownloadLink;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class DownloadLinkAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ITEM = 1;

    private final Context context;
    private final List<Object> items;
    private final Set<DownloadLink> selectedLinks = new HashSet<>();

    public DownloadLinkAdapter(Context context, List<DownloadLink> downloadLinks) {
        this.context = context;
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
            View view = LayoutInflater.from(context).inflate(R.layout.item_download_link, parent, false);
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
            itemViewHolder.bind(downloadLink);
            itemViewHolder.checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    selectedLinks.add(downloadLink);
                } else {
                    selectedLinks.remove(downloadLink);
                }
            });
            itemViewHolder.checkBox.setChecked(selectedLinks.contains(downloadLink));
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public List<DownloadLink> getSelectedLinks() {
        return new ArrayList<>(selectedLinks);
    }

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        TextView headerTitle;

        HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            headerTitle = itemView.findViewById(android.R.id.text1);
        }
    }

    static class ItemViewHolder extends RecyclerView.ViewHolder {
        CheckBox checkBox;
        TextView nameTextView;
        TextView sizeTextView;

        ItemViewHolder(View itemView) {
            super(itemView);
            checkBox = itemView.findViewById(R.id.downloadCheckBox);
            nameTextView = itemView.findViewById(R.id.downloadLinkName);
            sizeTextView = itemView.findViewById(R.id.downloadLinkSize);
        }

        void bind(final DownloadLink downloadLink) {
            nameTextView.setText(downloadLink.getName());
            sizeTextView.setText(downloadLink.getFormattedSize());
            itemView.setOnClickListener(v -> checkBox.toggle());
        }
    }
}
