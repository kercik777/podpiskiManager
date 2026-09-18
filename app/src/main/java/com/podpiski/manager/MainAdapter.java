package com.podpiski.manager;

import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class MainAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_FOLDER = 0, TYPE_SUB = 1;
    private List<Object> items = new ArrayList<>();
    private Listener listener;
    private ItemTouchHelper touchHelper;
    private boolean isInsideFolder = false;
    private DatabaseHelper dbHelper;

    public interface Listener {
        void onFolderClick(Folder f);
        void onFolderEdit(Folder f);
        void onFolderDelete(Folder f);
        void onSubCopy(Subscription s);
        void onSubEdit(Subscription s);
        void onSubDelete(Subscription s);
        void onSubClick(Subscription s);
        void onSubLongClick(Subscription s);
        void onOrderChanged();
    }

    public MainAdapter(DatabaseHelper db) { this.dbHelper = db; }
    public void setListener(Listener l) { this.listener = l; }
    public void setTouchHelper(ItemTouchHelper h) { this.touchHelper = h; }
    public void setInsideFolder(boolean v, long id) { this.isInsideFolder = v; }
    public List<Object> getItems() { return items; }

    public void setFolders(List<Folder> list) { items.clear(); items.addAll(list); notifyDataSetChanged(); }
    public void setSubscriptions(List<Subscription> list) { items.clear(); items.addAll(list); notifyDataSetChanged(); }

    @Override public int getItemViewType(int p) { return items.get(p) instanceof Folder ? TYPE_FOLDER : TYPE_SUB; }

    @NonNull @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int vt) {
        if (vt == TYPE_FOLDER) return new FolderHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_folder, parent, false));
        return new SubHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_subscription, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int pos) {
        if (h instanceof FolderHolder) {
            Folder f = (Folder) items.get(pos);
            FolderHolder fh = (FolderHolder) h;
            fh.tvName.setText(f.getName());
            int cnt = dbHelper.getSubscriptionCountInFolder(f.getId());
            fh.tvCount.setText(cnt > 0 ? String.format(Locale.getDefault(), "%d подписок", cnt) : fh.tvCount.getContext().getString(R.string.folder_empty));
            fh.dragHandle.setOnTouchListener((v, e) -> { if (e.getAction() == MotionEvent.ACTION_DOWN && touchHelper != null) touchHelper.startDrag(h); return false; });
            fh.btnEdit.setOnClickListener(v -> { if (listener != null) listener.onFolderEdit(f); });
            fh.btnDelete.setOnClickListener(v -> { if (listener != null) listener.onFolderDelete(f); });
            fh.itemView.setOnClickListener(v -> { if (listener != null) listener.onFolderClick(f); });
        } else if (h instanceof SubHolder) {
            Subscription s = (Subscription) items.get(pos);
            SubHolder sh = (SubHolder) h;
            sh.tvName.setText(s.getName());
            sh.tvLink.setText(s.getLink());
            sh.dragHandle.setOnTouchListener((v, e) -> { if (e.getAction() == MotionEvent.ACTION_DOWN && touchHelper != null) touchHelper.startDrag(h); return false; });
            sh.btnCopy.setOnClickListener(v -> { if (listener != null) listener.onSubCopy(s); });
            sh.btnEdit.setOnClickListener(v -> { if (listener != null) listener.onSubEdit(s); });
            sh.btnDelete.setOnClickListener(v -> { if (listener != null) listener.onSubDelete(s); });
            sh.itemView.setOnClickListener(v -> { if (listener != null) listener.onSubClick(s); });
            sh.itemView.setOnLongClickListener(v -> {
                if (listener != null) listener.onSubLongClick(s);
                return true;
            });
        }
    }

    @Override public int getItemCount() { return items.size(); }

    static class FolderHolder extends RecyclerView.ViewHolder {
        ImageView dragHandle, btnEdit, btnDelete; TextView tvName, tvCount;
        FolderHolder(View v) { super(v); dragHandle = v.findViewById(R.id.drag_handle); btnEdit = v.findViewById(R.id.btn_edit_folder); btnDelete = v.findViewById(R.id.btn_delete_folder); tvName = v.findViewById(R.id.tv_folder_name); tvCount = v.findViewById(R.id.tv_folder_count); }
    }
    static class SubHolder extends RecyclerView.ViewHolder {
        ImageView dragHandle, btnCopy, btnEdit, btnDelete; TextView tvName, tvLink;
        SubHolder(View v) { super(v); dragHandle = v.findViewById(R.id.drag_handle); btnCopy = v.findViewById(R.id.btn_copy_sub); btnEdit = v.findViewById(R.id.btn_edit_sub); btnDelete = v.findViewById(R.id.btn_delete_sub); tvName = v.findViewById(R.id.tv_sub_name); tvLink = v.findViewById(R.id.tv_sub_link); }
    }

    public static class DragCallback extends ItemTouchHelper.SimpleCallback {
        private final MainAdapter a;
        public DragCallback(MainAdapter a) { super(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0); this.a = a; }
        @Override public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh, @NonNull RecyclerView.ViewHolder t) {
            int f = vh.getAdapterPosition(), to = t.getAdapterPosition();
            if (f == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false;
            if (!a.isInsideFolder && vh.getItemViewType() != t.getItemViewType()) return false;
            Collections.swap(a.items, f, to); a.notifyItemMoved(f, to); return true;
        }
        @Override public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int d) {}
        @Override public void clearView(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh) {
            super.clearView(rv, vh); vh.itemView.setAlpha(1f); vh.itemView.setScaleX(1f); vh.itemView.setScaleY(1f);
            if (a.listener != null) a.listener.onOrderChanged();
        }
        @Override public void onSelectedChanged(RecyclerView.ViewHolder vh, int as) {
            super.onSelectedChanged(vh, as);
            if (as == ItemTouchHelper.ACTION_STATE_DRAG && vh != null) { vh.itemView.setAlpha(0.85f); vh.itemView.setScaleX(1.03f); vh.itemView.setScaleY(1.03f); }
        }
        @Override public boolean isLongPressDragEnabled() { return false; }
    }
}