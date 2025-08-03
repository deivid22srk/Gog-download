package com.example.gogdownloader.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.gogdownloader.R;
import com.example.gogdownloader.models.DownloadLink;

import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class DownloadLinkAdapter extends RecyclerView.Adapter<DownloadLinkAdapter.DownloadLinkViewHolder> {

    private Context context;
    private List<DownloadLink> downloadLinks;
    private Set<DownloadLink> selectedLinks;
    private OnSelectionChangedListener listener;

    public interface OnSelectionChangedListener {
        void onSelectionChanged(Set<DownloadLink> selectedLinks);
    }

    public DownloadLinkAdapter(Context context, List<DownloadLink> downloadLinks, OnSelectionChangedListener listener) {
        this.context = context;
        this.downloadLinks = new ArrayList<>(downloadLinks);
        this.selectedLinks = new HashSet<>();
        this.listener = listener;
    }

    public void selectAll() {
        selectedLinks.clear();
        selectedLinks.addAll(downloadLinks);
        notifyDataSetChanged();
        if (listener != null) {
            listener.onSelectionChanged(selectedLinks);
        }
    }
    
    public void selectNone() {
        selectedLinks.clear();
        notifyDataSetChanged();
        if (listener != null) {
            listener.onSelectionChanged(selectedLinks);
        }
    }
    
    public Set<DownloadLink> getSelectedLinks() {
        return new HashSet<>(selectedLinks);
    }
    
    public long getTotalSelectedSize() {
        long totalSize = 0;
        for (DownloadLink link : selectedLinks) {
            totalSize += link.getSize();
        }
        return totalSize;
    }

    @NonNull
    @Override
    public DownloadLinkViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_download_link, parent, false);
        return new DownloadLinkViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DownloadLinkViewHolder holder, int position) {
        DownloadLink downloadLink = downloadLinks.get(position);
        
        // Set file name
        holder.fileName.setText(downloadLink.getName());
        
        // Set file type
        holder.fileType.setText(downloadLink.getTypeDisplayName());
        
        // Set platform
        holder.filePlatform.setText(downloadLink.getPlatformDisplayName());
        
        // Set language (show only if not English)
        if (downloadLink.getLanguage() != null && !"en".equals(downloadLink.getLanguage())) {
            holder.fileLanguage.setText(downloadLink.getLanguage().toUpperCase());
            holder.fileLanguage.setVisibility(View.VISIBLE);
        } else {
            holder.fileLanguage.setVisibility(View.GONE);
        }
        
        // Set file size
        holder.fileSize.setText(downloadLink.getFormattedSize());
        
        // Set file type icon
        int iconRes = getFileTypeIcon(downloadLink.getType());
        holder.fileTypeIcon.setImageResource(iconRes);
        
        // Set checkbox state
        boolean isSelected = selectedLinks.contains(downloadLink);
        holder.downloadCheckbox.setChecked(isSelected);
        
        // Handle checkbox changes
        holder.downloadCheckbox.setOnCheckedChangeListener(null); // Remove previous listener
        holder.downloadCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                selectedLinks.add(downloadLink);
            } else {
                selectedLinks.remove(downloadLink);
            }
            
            if (listener != null) {
                listener.onSelectionChanged(selectedLinks);
            }
        });
        
        // Handle item click to toggle checkbox
        holder.itemView.setOnClickListener(v -> {
            holder.downloadCheckbox.setChecked(!holder.downloadCheckbox.isChecked());
        });
    }
    
    private int getFileTypeIcon(DownloadLink.FileType type) {
        switch (type) {
            case INSTALLER:
                return android.R.drawable.stat_sys_download;
            case PATCH:
                return android.R.drawable.ic_menu_edit;
            case EXTRA:
                return android.R.drawable.ic_menu_gallery;
            case DLC:
                return android.R.drawable.ic_menu_add;
            case LANGUAGE_PACK:
                return android.R.drawable.ic_menu_agenda;
            default:
                return android.R.drawable.stat_sys_download;
        }
    }

    @Override
    public int getItemCount() {
        return downloadLinks.size();
    }

    static class DownloadLinkViewHolder extends RecyclerView.ViewHolder {
        CheckBox downloadCheckbox;
        ImageView fileTypeIcon;
        TextView fileName;
        TextView fileType;
        TextView filePlatform;
        TextView fileLanguage;
        TextView fileSize;
        ImageView downloadStatus;

        DownloadLinkViewHolder(@NonNull View itemView) {
            super(itemView);
            downloadCheckbox = itemView.findViewById(R.id.downloadCheckbox);
            fileTypeIcon = itemView.findViewById(R.id.fileTypeIcon);
            fileName = itemView.findViewById(R.id.fileName);
            fileType = itemView.findViewById(R.id.fileType);
            filePlatform = itemView.findViewById(R.id.filePlatform);
            fileLanguage = itemView.findViewById(R.id.fileLanguage);
            fileSize = itemView.findViewById(R.id.fileSize);
            downloadStatus = itemView.findViewById(R.id.downloadStatus);
        }
    }
}
