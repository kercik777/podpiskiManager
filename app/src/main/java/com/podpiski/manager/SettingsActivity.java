package com.podpiski.manager;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;

public class SettingsActivity extends AppCompatActivity {

    private DatabaseHelper dbHelper;
    private static final int REQ_IMPORT = 1001, REQ_PERM = 1002, REQ_EXPORT = 1003;
    private ProgressBar progressBar;
    private TextView progressText;
    private AlertDialog progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        dbHelper = new DatabaseHelper(this);
        initViews();
        initClickListeners();
    }

    private void initViews() {
        TextView tvVersion = findViewById(R.id.tv_version);
        tvVersion.setText(getString(R.string.settings_about_desc, getString(R.string.app_version)));
    }

    private void initClickListeners() {
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.settings_export).setOnClickListener(v -> {
            if (checkPermission()) prepareAndExportData();
        });
        findViewById(R.id.settings_import).setOnClickListener(v -> {
            if (checkPermission()) showImportConfirm();
        });
        // Корзина удалена
        findViewById(R.id.settings_duplicates).setOnClickListener(v -> showDuplicateDialog());
        findViewById(R.id.settings_about).setOnClickListener(v -> showAboutDialog());
    }

    private boolean checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return true;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED) return true;

        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE},
                REQ_PERM);
        return false;
    }

    private String generateExportJson() throws Exception {
        List<Folder> folders = dbHelper.getAllFolders();
        List<Subscription> allSubs = dbHelper.getAllSubscriptions();

        JSONObject root = new JSONObject();
        JSONArray fArr = new JSONArray();
        for (Folder f : folders) {
            JSONObject o = new JSONObject();
            o.put("id", f.getId());
            o.put("name", f.getName());
            o.put("position", f.getPosition());
            fArr.put(o);
        }
        root.put("folders", fArr);

        JSONArray sArr = new JSONArray();
        for (Subscription s : allSubs) {
            JSONObject o = new JSONObject();
            o.put("name", s.getName());
            o.put("link", s.getLink());
            o.put("folder_id", s.getFolderId());
            o.put("position", s.getPosition());
            o.put("created_at", s.getCreatedAt());
            sArr.put(o);
        }
        root.put("subscriptions", sArr);

        return root.toString(2);
    }

    private void prepareAndExportData() {
        try {
            List<Folder> folders = dbHelper.getAllFolders();
            List<Subscription> allSubs = dbHelper.getAllSubscriptions();

            if (folders.isEmpty() && allSubs.isEmpty()) {
                Toast.makeText(this, R.string.no_data, Toast.LENGTH_SHORT).show();
                return;
            }

            String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String fileName = "podpiski_export_" + ts + ".json";

            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_TITLE, fileName);

            try {
                Uri documentsUri = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3ADocuments");
                intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, documentsUri);
            } catch (Exception ignored) {}

            startActivityForResult(intent, REQ_EXPORT);

        } catch (Exception e) {
            Toast.makeText(this, R.string.export_error, Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    private void exportToFile(Uri uri, String jsonData) {
        showProgress(getString(R.string.export_progress));

        new Thread(() -> {
            try {
                try (FileOutputStream fos = (FileOutputStream) getContentResolver().openOutputStream(uri)) {
                    if (fos != null) {
                        fos.write(jsonData.getBytes(StandardCharsets.UTF_8));
                        fos.flush();
                    }
                }

                new Handler(Looper.getMainLooper()).post(() -> {
                    hideProgress();
                    Toast.makeText(this, R.string.export_success, Toast.LENGTH_SHORT).show();
                });

            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    hideProgress();
                    Toast.makeText(this, R.string.export_error, Toast.LENGTH_SHORT).show();
                    e.printStackTrace();
                });
            }
        }).start();
    }

    private void showProgress(String message) {
        View progressView = LayoutInflater.from(this).inflate(R.layout.dialog_progress, null);
        progressText = progressView.findViewById(R.id.progress_text);
        progressBar = progressView.findViewById(R.id.progress_bar);
        progressText.setText(message);
        progressBar.setIndeterminate(true);
        progressDialog = new AlertDialog.Builder(this, R.style.Theme_Podpiski_Dialog)
                .setView(progressView)
                .setCancelable(false)
                .create();

        if (progressDialog.getWindow() != null) {
            progressDialog.getWindow().setBackgroundDrawableResource(R.drawable.bg_dialog);
        }
        progressDialog.show();
    }

    private void hideProgress() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }

    private void showImportConfirm() {
        View v = getLayoutInflater().inflate(R.layout.dialog_confirm, null);
        ((TextView) v.findViewById(R.id.confirm_title)).setText(R.string.import_confirm_title);
        ((TextView) v.findViewById(R.id.confirm_message)).setText(R.string.import_confirm_message);
        TextView btn = v.findViewById(R.id.btn_confirm_delete);
        btn.setText(R.string.btn_confirm);
        btn.setTextColor(ContextCompat.getColor(this, R.color.accent_blue));
        btn.setBackground(ContextCompat.getDrawable(this, R.drawable.bg_button_primary));

        AlertDialog d = createDialog(v);
        v.findViewById(R.id.btn_cancel_delete).setOnClickListener(x -> d.dismiss());
        btn.setOnClickListener(x -> { d.dismiss(); openPicker(); });
    }

    private void openPicker() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("application/json");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(i, REQ_IMPORT);
        } catch (ActivityNotFoundException e) {
            i.setType("*/*");
            try {
                startActivityForResult(i, REQ_IMPORT);
            } catch (Exception e2) {
                Toast.makeText(this, R.string.import_error, Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode != RESULT_OK || data == null) return;

        if (requestCode == REQ_IMPORT && data.getData() != null) {
            importData(data.getData());
        } else if (requestCode == REQ_EXPORT && data.getData() != null) {
            try {
                String jsonData = generateExportJson();
                exportToFile(data.getData(), jsonData);
            } catch (Exception e) {
                Toast.makeText(this, R.string.export_error, Toast.LENGTH_SHORT).show();
                e.printStackTrace();
            }
        }
    }

    private void importData(Uri uri) {
        showProgress(getString(R.string.import_progress));

        new Thread(() -> {
            try {
                BufferedReader r = new BufferedReader(
                        new InputStreamReader(getContentResolver().openInputStream(uri), StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
                r.close();

                JSONObject root = new JSONObject(sb.toString());
                dbHelper.deleteAll();

                Map<Long, Long> idMap = new HashMap<>();
                int fc = 0, sc = 0;

                // Импорт папок
                JSONArray fArr = root.optJSONArray("folders");
                if (fArr != null) {
                    for (int i = 0; i < fArr.length(); i++) {
                        JSONObject o = fArr.getJSONObject(i);
                        long oldId = o.optLong("id", -1);
                        String name = o.getString("name");
                        int position = o.optInt("position", 0);

                        long newId = dbHelper.addFolder(name);
                        if (newId > 0) {
                            fc++;
                            idMap.put(oldId, newId);
                            dbHelper.updateFolderPosition(newId, position);
                        }
                    }
                }

                // Импорт подписок
                JSONArray sArr = root.optJSONArray("subscriptions");
                if (sArr != null) {
                    for (int i = 0; i < sArr.length(); i++) {
                        JSONObject o = sArr.getJSONObject(i);
                        String name = o.getString("name");
                        String link = o.getString("link");
                        long folderId = o.optLong("folder_id", -1);
                        int position = o.optInt("position", 0);
                        String createdAt = o.optString("created_at", "");

                        Long mapped = idMap.get(folderId);
                        if (mapped != null) folderId = mapped;
                        else folderId = -1;

                        long newId = dbHelper.addSubscription(name, link, folderId);
                        if (newId > 0) {
                            sc++;
                            dbHelper.updateSubscriptionPosition(newId, position);
                            if (!createdAt.isEmpty()) {
                                dbHelper.updateSubscriptionCreatedAt(newId, createdAt);
                            }
                        }
                    }
                }

                int finalFc = fc, finalSc = sc;
                new Handler(Looper.getMainLooper()).post(() -> {
                    hideProgress();
                    Toast.makeText(this, getString(R.string.import_success, finalFc, finalSc), Toast.LENGTH_SHORT).show();
                });

            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    hideProgress();
                    Toast.makeText(this, R.string.import_error, Toast.LENGTH_SHORT).show();
                    e.printStackTrace();
                });
            }
        }).start();
    }

    private void showDuplicateDialog() {
        View v = getLayoutInflater().inflate(R.layout.dialog_duplicate, null);
        LinearLayout prog = v.findViewById(R.id.dup_progress_container);
        LinearLayout none = v.findViewById(R.id.dup_none_container);
        ScrollView listC = v.findViewById(R.id.dup_list_container);
        LinearLayout list = v.findViewById(R.id.dup_list);
        TextView btnDel = v.findViewById(R.id.btn_delete_duplicates);

        prog.setVisibility(View.VISIBLE);
        none.setVisibility(View.GONE);
        listC.setVisibility(View.GONE);
        btnDel.setVisibility(View.GONE);

        AlertDialog d = createDialog(v);
        v.findViewById(R.id.btn_cancel_delete).setOnClickListener(x -> d.dismiss());

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            List<Subscription> dups = dbHelper.findDuplicates();
            prog.setVisibility(View.GONE);

            if (dups.isEmpty()) {
                none.setVisibility(View.VISIBLE);
            } else {
                listC.setVisibility(View.VISIBLE);
                list.removeAllViews();
                for (Subscription s : dups) {
                    View iv = LayoutInflater.from(this).inflate(R.layout.item_duplicate, list, false);
                    ((TextView) iv.findViewById(R.id.dup_item_name)).setText(s.getName());
                    ((TextView) iv.findViewById(R.id.dup_item_link)).setText(s.getLink());
                    list.addView(iv);
                }
                btnDel.setVisibility(View.VISIBLE);
                btnDel.setText(getString(R.string.btn_delete_duplicates) + " (" + dups.size() + ")");
                btnDel.setOnClickListener(x -> {
                    btnDel.setVisibility(View.GONE);
                    listC.setVisibility(View.GONE);
                    prog.setVisibility(View.VISIBLE);
                    ((TextView) v.findViewById(R.id.dup_progress_text)).setText(R.string.duplicates_deleting);

                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        int deleted = dbHelper.deleteDuplicates();
                        prog.setVisibility(View.GONE);
                        none.setVisibility(View.VISIBLE);
                        Toast.makeText(this, getString(R.string.duplicates_deleted, deleted), Toast.LENGTH_SHORT).show();
                    }, 500);
                });
            }
        }, 500);
    }

    private void showAboutDialog() {
        View v = getLayoutInflater().inflate(R.layout.dialog_confirm, null);
        ((TextView) v.findViewById(R.id.confirm_title)).setText(R.string.app_name);
        ((TextView) v.findViewById(R.id.confirm_message))
                .setText(getString(R.string.settings_about_desc, getString(R.string.app_version)));
        v.findViewById(R.id.btn_cancel_delete).setVisibility(View.GONE);

        TextView btn = v.findViewById(R.id.btn_confirm_delete);
        btn.setText("OK");
        btn.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        btn.setBackground(ContextCompat.getDrawable(this, R.drawable.bg_button_primary));

        AlertDialog d = createDialog(v);
        btn.setOnClickListener(x -> d.dismiss());
    }

    private AlertDialog createDialog(View v) {
        AlertDialog.Builder b = new AlertDialog.Builder(this, R.style.Theme_Podpiski_Dialog);
        b.setView(v);
        AlertDialog d = b.create();
        if (d.getWindow() != null) {
            d.getWindow().setBackgroundDrawableResource(R.drawable.bg_dialog);
        }
        d.show();
        return d;
    }
}