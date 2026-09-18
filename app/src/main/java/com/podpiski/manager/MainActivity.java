package com.podpiski.manager;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private DatabaseHelper dbHelper;
    private MainAdapter adapter;
    private ItemTouchHelper touchHelper;
    private RecyclerView recyclerView;
    private LinearLayout emptyState, searchEmptyState;
    private TextView tvCount;
    private FloatingActionButton fabAdd;
    private Toolbar toolbar;
    private SearchView searchView;

    private boolean isInsideFolder = false;
    private long currentFolderId = -1;
    private String currentFolderName = "";
    private final List<Long> folderStack = new ArrayList<>();

    private String currentSortMode = "asc";
    private SharedPreferences prefs;
    private static final String PREF_SORT_MODE = "sort_mode";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        dbHelper = new DatabaseHelper(this);
        
        prefs = getSharedPreferences("app_settings", MODE_PRIVATE);
        currentSortMode = prefs.getString(PREF_SORT_MODE, "asc");
        
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        recyclerView = findViewById(R.id.recycler_view);
        emptyState = findViewById(R.id.empty_state);
        searchEmptyState = findViewById(R.id.search_empty_state);
        tvCount = findViewById(R.id.tv_count);
        fabAdd = findViewById(R.id.fab_add);

        adapter = new MainAdapter(dbHelper);
        recyclerView.setAdapter(adapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        MainAdapter.DragCallback cb = new MainAdapter.DragCallback(adapter);
        touchHelper = new ItemTouchHelper(cb);
        touchHelper.attachToRecyclerView(recyclerView);
        adapter.setTouchHelper(touchHelper);

        adapter.setListener(new MainAdapter.Listener() {
            @Override public void onFolderClick(Folder f) { openFolder(f); }
            @Override public void onFolderEdit(Folder f) { showEditFolderDialog(f); }
            @Override public void onFolderDelete(Folder f) { deleteFolder(f); }
            @Override public void onSubCopy(Subscription s) { copyLink(s.getLink()); }
            @Override public void onSubEdit(Subscription s) { showEditSubDialog(s); }
            @Override public void onSubDelete(Subscription s) { deleteSubscription(s); }
            @Override public void onSubClick(Subscription s) { copyLink(s.getLink()); }
            @Override public void onSubLongClick(Subscription s) { showMoveToFolderDialog(s); }
            @Override public void onOrderChanged() { 
                if (!isInsideFolder) {
                    saveFolderOrder();
                }
            }
        });

        toolbar.setNavigationOnClickListener(v -> navigateBack());
        fabAdd.setOnClickListener(v -> {
            if (isInsideFolder) {
                showAddSubDialog();
            } else {
                showAddFolderDialog();
            }
        });
        loadRoot();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        MenuItem si = menu.findItem(R.id.action_search);
        searchView = (SearchView) si.getActionView();
        if (searchView != null) {
            searchView.setQueryHint(getString(R.string.search_hint));
            searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override public boolean onQueryTextSubmit(String q) { performSearch(q); return true; }
                @Override public boolean onQueryTextChange(String q) { performSearch(q); return true; }
            });
            searchView.setOnCloseListener(() -> { refresh(); return false; });
        }
        menu.findItem(R.id.action_settings).setOnMenuItemClickListener(i -> {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        });
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem sortItem = menu.findItem(R.id.action_sort);
        if (sortItem != null) {
            sortItem.setVisible(isInsideFolder);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_sort && isInsideFolder) {
            showSortDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showSortDialog() {
        String[] options = {"По возрастанию", "По убыванию"};
        int checked = currentSortMode.equals("asc") ? 0 : 1;

        AlertDialog dialog = new AlertDialog.Builder(this, R.style.Theme_Podpiski_Dialog)
                .setTitle("Сортировка подписок")
                .setSingleChoiceItems(options, checked, (d, which) -> {
                    currentSortMode = which == 0 ? "asc" : "desc";
                    prefs.edit().putString(PREF_SORT_MODE, currentSortMode).apply();
                    d.dismiss();
                    refresh();
                })
                .create();
        
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.bg_dialog);
        }
        dialog.show();
    }

    private void loadRoot() {
        isInsideFolder = false;
        currentFolderId = -1;
        currentFolderName = "";
        folderStack.clear();
        toolbar.setNavigationIcon(null);
        toolbar.setTitle(R.string.app_name);
        adapter.setInsideFolder(false, -1);
        refresh();
        supportInvalidateOptionsMenu();
    }

    private void openFolder(Folder f) {
        folderStack.add(currentFolderId);
        isInsideFolder = true;
        currentFolderId = f.getId();
        currentFolderName = f.getName();
        toolbar.setNavigationIcon(R.drawable.ic_back);
        toolbar.setTitle(f.getName());
        adapter.setInsideFolder(true, f.getId());
        refresh();
        supportInvalidateOptionsMenu();
    }

    private void navigateBack() {
        if (folderStack.isEmpty()) {
            loadRoot();
            return;
        }
        long prev = folderStack.remove(folderStack.size() - 1);
        if (prev == -1) {
            loadRoot();
            return;
        }
        for (Folder f : dbHelper.getAllFolders()) {
            if (f.getId() == prev) {
                currentFolderId = prev;
                currentFolderName = f.getName();
                toolbar.setTitle(f.getName());
                adapter.setInsideFolder(true, prev);
                refresh();
                supportInvalidateOptionsMenu();
                return;
            }
        }
        loadRoot();
    }

    @Override
    public void onBackPressed() {
        if (isInsideFolder) navigateBack();
        else super.onBackPressed();
    }

    private void refresh() {
        if (isInsideFolder) {
            List<Subscription> subs = dbHelper.getSubscriptionsByFolder(currentFolderId);
            
            // Сортируем по позиции
            subs.sort((a, b) -> Integer.compare(a.getPosition(), b.getPosition()));
            
            // Если выбран режим "по убыванию", переворачиваем список
            if (currentSortMode.equals("desc")) {
                List<Subscription> reversed = new ArrayList<>();
                for (int i = subs.size() - 1; i >= 0; i--) {
                    reversed.add(subs.get(i));
                }
                subs = reversed;
            }
            
            adapter.setSubscriptions(subs);
            updateEmpty(subs.isEmpty(), false);
            updateCount(subs.size());
        } else {
            List<Folder> folders = dbHelper.getAllFolders();
            adapter.setFolders(folders);
            updateEmpty(folders.isEmpty(), false);
            updateCount(folders.size());
        }
    }

    private void performSearch(String q) {
        if (q == null || q.trim().isEmpty()) {
            refresh();
            return;
        }
        List<Subscription> r = dbHelper.searchSubscriptions(q);
        adapter.setSubscriptions(r);
        updateEmpty(r.isEmpty(), true);
    }

    private void updateEmpty(boolean empty, boolean isSearch) {
        if (empty) {
            recyclerView.setVisibility(View.GONE);
            if (isSearch) {
                emptyState.setVisibility(View.GONE);
                searchEmptyState.setVisibility(View.VISIBLE);
            } else {
                emptyState.setVisibility(View.VISIBLE);
                searchEmptyState.setVisibility(View.GONE);
            }
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            emptyState.setVisibility(View.GONE);
            searchEmptyState.setVisibility(View.GONE);
        }
    }

    private void updateCount(int c) {
        if (c > 0) {
            tvCount.setVisibility(View.VISIBLE);
            tvCount.setText(getString(R.string.count_items, c));
        } else {
            tvCount.setVisibility(View.GONE);
        }
    }

    private void saveFolderOrder() {
        List<Object> items = adapter.getItems();
        List<Folder> folders = new ArrayList<>();
        for (Object o : items) {
            if (o instanceof Folder) {
                folders.add((Folder) o);
            }
        }
        dbHelper.updateFolderPositions(folders);
    }

    private void deleteFolder(Folder folder) {
        String folderName = folder.getName();
        int subCount = dbHelper.getSubscriptionCountInFolder(folder.getId());
        String message;
        if (subCount > 0) {
            message = getString(R.string.delete_folder_with_subs_confirm, folderName, subCount);
        } else {
            message = getString(R.string.delete_folder_confirm, folderName);
        }

        new AlertDialog.Builder(this, R.style.Theme_Podpiski_Dialog)
                .setTitle(R.string.delete_folder_title)
                .setMessage(message)
                .setPositiveButton(R.string.btn_delete, (d, w) -> {
                    dbHelper.deleteFolder(folder.getId());
                    refresh();
                    Snackbar.make(findViewById(android.R.id.content),
                            getString(R.string.folder_deleted, folderName), Snackbar.LENGTH_SHORT)
                            .setBackgroundTint(ContextCompat.getColor(this, R.color.snackbar_bg))
                            .setTextColor(ContextCompat.getColor(this, R.color.text_primary))
                            .show();
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private void deleteSubscription(Subscription sub) {
        new AlertDialog.Builder(this, R.style.Theme_Podpiski_Dialog)
                .setTitle(R.string.delete_sub_title)
                .setMessage(getString(R.string.delete_sub_confirm, sub.getName()))
                .setPositiveButton(R.string.btn_delete, (d, w) -> {
                    dbHelper.deleteSubscription(sub.getId());
                    refresh();
                    Snackbar.make(findViewById(android.R.id.content),
                            getString(R.string.sub_deleted, sub.getName()), Snackbar.LENGTH_SHORT)
                            .setBackgroundTint(ContextCompat.getColor(this, R.color.snackbar_bg))
                            .setTextColor(ContextCompat.getColor(this, R.color.text_primary))
                            .show();
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private void showMoveToFolderDialog(Subscription subscription) {
        List<Folder> folders = dbHelper.getAllFolders();
        if (folders.isEmpty()) {
            Toast.makeText(this, "Нет доступных папок", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] folderNames = new String[folders.size()];
        final long[] folderIds = new long[folders.size()];
        for (int i = 0; i < folders.size(); i++) {
            folderNames[i] = folders.get(i).getName();
            folderIds[i] = folders.get(i).getId();
        }

        AlertDialog dialog = new AlertDialog.Builder(this, R.style.Theme_Podpiski_Dialog)
                .setTitle("Переместить \"" + subscription.getName() + "\" в папку:")
                .setItems(folderNames, (d, which) -> {
                    long targetFolderId = folderIds[which];
                    if (targetFolderId == subscription.getFolderId()) {
                        Toast.makeText(this, "Подписка уже в этой папке", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    boolean success = dbHelper.moveSubscriptionToFolder(subscription.getId(), targetFolderId);
                    if (success) {
                        refresh();
                        Snackbar.make(findViewById(android.R.id.content),
                                "Подписка перемещена в папку \"" + folderNames[which] + "\"", Snackbar.LENGTH_SHORT)
                                .show();
                    } else {
                        Toast.makeText(this, "Ошибка перемещения", Toast.LENGTH_SHORT).show();
                    }
                })
                .create();
        
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.bg_dialog);
        }
        dialog.show();
    }

    private void showAddFolderDialog() {
        View v = getLayoutInflater().inflate(R.layout.dialog_add_edit, null);
        TextView title = v.findViewById(R.id.dialog_title);
        title.setText(R.string.dialog_add_folder);
        v.<TextView>findViewById(R.id.label_name).setText(R.string.hint_folder_name);
        EditText etName = v.findViewById(R.id.et_name);
        etName.setHint(R.string.hint_folder_name);
        v.findViewById(R.id.link_container).setVisibility(View.GONE);
        TextView tvErr = v.findViewById(R.id.tv_name_error);
        tvErr.setVisibility(View.GONE);

        AlertDialog.Builder b = new AlertDialog.Builder(this, R.style.Theme_Podpiski_Dialog);
        b.setView(v);
        b.setCancelable(true);
        AlertDialog d = b.create();
        if (d.getWindow() != null) d.getWindow().setBackgroundDrawableResource(R.drawable.bg_dialog);

        v.<TextView>findViewById(R.id.btn_cancel).setOnClickListener(x -> d.dismiss());
        v.<TextView>findViewById(R.id.btn_confirm).setOnClickListener(x -> {
            String name = etName.getText().toString().trim();
            tvErr.setVisibility(View.GONE);
            if (name.isEmpty()) {
                tvErr.setText(R.string.error_empty_folder_name);
                tvErr.setVisibility(View.VISIBLE);
                return;
            }
            if (dbHelper.folderNameExists(name, -1)) {
                tvErr.setText(R.string.error_folder_exists);
                tvErr.setVisibility(View.VISIBLE);
                return;
            }
            dbHelper.addFolder(name);
            d.dismiss();
            refresh();
        });
        d.show();
    }

    private void showEditFolderDialog(Folder f) {
        View v = getLayoutInflater().inflate(R.layout.dialog_add_edit, null);
        TextView title = v.findViewById(R.id.dialog_title);
        title.setText(R.string.dialog_edit_folder);
        v.<TextView>findViewById(R.id.label_name).setText(R.string.hint_folder_name);
        EditText etName = v.findViewById(R.id.et_name);
        etName.setText(f.getName());
        v.findViewById(R.id.link_container).setVisibility(View.GONE);
        TextView tvErr = v.findViewById(R.id.tv_name_error);
        tvErr.setVisibility(View.GONE);
        v.<TextView>findViewById(R.id.btn_confirm).setText(R.string.btn_save);

        AlertDialog.Builder b = new AlertDialog.Builder(this, R.style.Theme_Podpiski_Dialog);
        b.setView(v);
        b.setCancelable(true);
        AlertDialog d = b.create();
        if (d.getWindow() != null) d.getWindow().setBackgroundDrawableResource(R.drawable.bg_dialog);

        v.<TextView>findViewById(R.id.btn_cancel).setOnClickListener(x -> d.dismiss());
        v.<TextView>findViewById(R.id.btn_confirm).setOnClickListener(x -> {
            String name = etName.getText().toString().trim();
            tvErr.setVisibility(View.GONE);
            if (name.isEmpty()) {
                tvErr.setText(R.string.error_empty_folder_name);
                tvErr.setVisibility(View.VISIBLE);
                return;
            }
            if (dbHelper.folderNameExists(name, f.getId())) {
                tvErr.setText(R.string.error_folder_exists);
                tvErr.setVisibility(View.VISIBLE);
                return;
            }
            dbHelper.updateFolder(f.getId(), name);
            d.dismiss();
            refresh();
        });
        d.show();
    }

    private void showAddSubDialog() {
        long folderId = isInsideFolder ? currentFolderId : -1;
        View v = getLayoutInflater().inflate(R.layout.dialog_add_edit, null);
        TextView title = v.findViewById(R.id.dialog_title);
        title.setText(R.string.dialog_add_subscription);
        v.<TextView>findViewById(R.id.label_name).setText(R.string.hint_name);
        v.<EditText>findViewById(R.id.et_name).setHint(R.string.hint_name);
        v.findViewById(R.id.link_container).setVisibility(View.VISIBLE);
        TextView tvNameErr = v.findViewById(R.id.tv_name_error);
        tvNameErr.setVisibility(View.GONE);
        TextView tvLinkErr = v.findViewById(R.id.tv_link_error);
        tvLinkErr.setVisibility(View.GONE);

        AlertDialog.Builder b = new AlertDialog.Builder(this, R.style.Theme_Podpiski_Dialog);
        b.setView(v);
        b.setCancelable(true);
        AlertDialog d = b.create();
        if (d.getWindow() != null) d.getWindow().setBackgroundDrawableResource(R.drawable.bg_dialog);

        v.<TextView>findViewById(R.id.btn_cancel).setOnClickListener(x -> d.dismiss());
        v.<TextView>findViewById(R.id.btn_confirm).setOnClickListener(x -> {
            String name = v.<EditText>findViewById(R.id.et_name).getText().toString().trim();
            String link = v.<EditText>findViewById(R.id.et_link).getText().toString().trim();
            tvNameErr.setVisibility(View.GONE);
            tvLinkErr.setVisibility(View.GONE);

            if (name.isEmpty()) {
                tvNameErr.setText(R.string.error_empty_name);
                tvNameErr.setVisibility(View.VISIBLE);
                return;
            }
            if (link.isEmpty()) {
                tvLinkErr.setText(R.string.error_empty_link);
                tvLinkErr.setVisibility(View.VISIBLE);
                return;
            }

            DatabaseHelper.DuplicateInfo nameDup = dbHelper.checkNameDuplicateGlobal(name, -1);
            if (nameDup != null) {
                tvNameErr.setText(getString(R.string.error_name_exists_global, name, nameDup.existingFolderName));
                tvNameErr.setVisibility(View.VISIBLE);
                return;
            }

            DatabaseHelper.DuplicateInfo linkDup = dbHelper.checkLinkDuplicateGlobal(link, -1);
            if (linkDup != null) {
                tvLinkErr.setText(getString(R.string.error_link_exists_global, linkDup.existingName, linkDup.existingFolderName));
                tvLinkErr.setVisibility(View.VISIBLE);
                return;
            }

            // Получаем максимальную позицию
            List<Subscription> currentSubs = dbHelper.getSubscriptionsByFolder(folderId);
            int maxPosition = 0;
            for (Subscription sub : currentSubs) {
                if (sub.getPosition() > maxPosition) {
                    maxPosition = sub.getPosition();
                }
            }
            
            // Новая подписка получает следующую позицию (всегда в конец по позиции)
            int newPosition = maxPosition + 1;
            
            long result = dbHelper.addSubscription(name, link, folderId);
            if (result > 0) {
                dbHelper.updateSubscriptionPosition(result, newPosition);
                d.dismiss();
                refresh();
            } else {
                tvLinkErr.setText(R.string.error_unknown);
                tvLinkErr.setVisibility(View.VISIBLE);
            }
        });
        d.show();
    }

    private void showEditSubDialog(Subscription s) {
        View v = getLayoutInflater().inflate(R.layout.dialog_add_edit, null);
        TextView title = v.findViewById(R.id.dialog_title);
        title.setText(R.string.dialog_edit_subscription);
        v.<TextView>findViewById(R.id.label_name).setText(R.string.hint_name);
        v.<EditText>findViewById(R.id.et_name).setText(s.getName());
        v.findViewById(R.id.link_container).setVisibility(View.VISIBLE);
        v.<EditText>findViewById(R.id.et_link).setText(s.getLink());
        v.<TextView>findViewById(R.id.btn_confirm).setText(R.string.btn_save);
        TextView tvNameErr = v.findViewById(R.id.tv_name_error);
        tvNameErr.setVisibility(View.GONE);
        TextView tvLinkErr = v.findViewById(R.id.tv_link_error);
        tvLinkErr.setVisibility(View.GONE);

        AlertDialog.Builder b = new AlertDialog.Builder(this, R.style.Theme_Podpiski_Dialog);
        b.setView(v);
        b.setCancelable(true);
        AlertDialog d = b.create();
        if (d.getWindow() != null) d.getWindow().setBackgroundDrawableResource(R.drawable.bg_dialog);

        v.<TextView>findViewById(R.id.btn_cancel).setOnClickListener(x -> d.dismiss());
        v.<TextView>findViewById(R.id.btn_confirm).setOnClickListener(x -> {
            String name = v.<EditText>findViewById(R.id.et_name).getText().toString().trim();
            String link = v.<EditText>findViewById(R.id.et_link).getText().toString().trim();
            tvNameErr.setVisibility(View.GONE);
            tvLinkErr.setVisibility(View.GONE);

            if (name.isEmpty()) {
                tvNameErr.setText(R.string.error_empty_name);
                tvNameErr.setVisibility(View.VISIBLE);
                return;
            }
            if (link.isEmpty()) {
                tvLinkErr.setText(R.string.error_empty_link);
                tvLinkErr.setVisibility(View.VISIBLE);
                return;
            }

            DatabaseHelper.DuplicateInfo nameDup = dbHelper.checkNameDuplicateGlobal(name, s.getId());
            if (nameDup != null) {
                tvNameErr.setText(getString(R.string.error_name_exists_global, name, nameDup.existingFolderName));
                tvNameErr.setVisibility(View.VISIBLE);
                return;
            }

            DatabaseHelper.DuplicateInfo linkDup = dbHelper.checkLinkDuplicateGlobal(link, s.getId());
            if (linkDup != null) {
                tvLinkErr.setText(getString(R.string.error_link_exists_global, linkDup.existingName, linkDup.existingFolderName));
                tvLinkErr.setVisibility(View.VISIBLE);
                return;
            }

            dbHelper.updateSubscription(s.getId(), name, link);
            d.dismiss();
            refresh();
        });
        d.show();
    }

    private void copyLink(String link) {
        ((ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE))
                .setPrimaryClip(ClipData.newPlainText("link", link));
        Snackbar.make(findViewById(android.R.id.content), getString(R.string.link_copied), Snackbar.LENGTH_SHORT)
                .setBackgroundTint(ContextCompat.getColor(this, R.color.snackbar_bg))
                .setTextColor(ContextCompat.getColor(this, R.color.text_primary))
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
        supportInvalidateOptionsMenu();
    }
}