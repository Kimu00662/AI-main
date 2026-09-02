package com.aihellotalk;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.util.Base64;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.drawerlayout.widget.DrawerLayout;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private static final int REQUEST_CODE_PICK_IMAGE = 101;
    private static final int REQUEST_CODE_PICK_FILE = 102;

    private DrawerLayout drawerLayout;
    private LinearLayout messageContainer;
    private ScrollView messageScrollView;
    private EditText inputBox;
    private Button sendBtn;
    private Button attachBtn;
    private LinearLayout drawerContent;

    private LinearLayout imagePreviewBar;
    private ImageView previewImage;
    private ImageButton previewCloseBtn;
    private String pendingImageBase64 = "";

    private Spinner modelSpinner;
    private List<String> modelList = new ArrayList<>();

    private String currentChatId = "";
    private String currentChatName = "";
    private Handler mainHandler;

    private String cachedApiKey = "";
    private String cachedApiUrl = "";
    private String cachedModel = "";
    private SharedPreferences prefs;

    private TextView memStatus;
    private volatile boolean claimDialogShowing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("htai_settings", MODE_PRIVATE);
        mainHandler = new Handler(Looper.getMainLooper());

        loadConfigOnce();

        drawerLayout = new DrawerLayout(this);
        LinearLayout mainContent = new LinearLayout(this);
        mainContent.setOrientation(LinearLayout.VERTICAL);
        mainContent.setBackgroundColor(Color.WHITE);

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setPadding(16, 48, 16, 16);
        topBar.setBackgroundColor(Color.parseColor("#F5F5F5"));

        Button leftMenuBtn = new Button(this);
        leftMenuBtn.setText("☰");
        leftMenuBtn.setTextSize(20f);
        leftMenuBtn.setBackgroundColor(Color.TRANSPARENT);
        leftMenuBtn.setOnClickListener(v -> drawerLayout.openDrawer(Gravity.LEFT));

        TextView title = new TextView(this);
        title.setText("HT 翻译遥控器");
        title.setTextSize(18f);
        title.setTextColor(Color.BLACK);
        title.setGravity(Gravity.CENTER);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView modelLabel = new TextView(this);
        modelLabel.setText(cachedModel.isEmpty() ? "未选择" : cachedModel);
        modelLabel.setTextSize(12f);
        modelLabel.setTextColor(Color.parseColor("#666666"));
        modelLabel.setPadding(0, 0, 8, 0);
        modelLabel.setGravity(Gravity.CENTER_VERTICAL);
        modelLabel.setTag("modelLabel");

        Button rightMenuBtn = new Button(this);
        rightMenuBtn.setText("⋮");
        rightMenuBtn.setTextSize(24f);
        rightMenuBtn.setBackgroundColor(Color.TRANSPARENT);
        rightMenuBtn.setOnClickListener(this::showPopupMenu);

        topBar.addView(leftMenuBtn);
        topBar.addView(title);
        topBar.addView(modelLabel);
        topBar.addView(rightMenuBtn);
        mainContent.addView(topBar);

        memStatus = new TextView(this);
        memStatus.setTag("memStatus");
        memStatus.setText("🧠 记忆：检测中...");
        memStatus.setPadding(16, 8, 16, 8);
        memStatus.setTextSize(13f);
        memStatus.setTextColor(Color.parseColor("#0B5ED7"));
        memStatus.setBackgroundColor(Color.parseColor("#F1F8FF"));
        memStatus.setOnLongClickListener(v -> {
            showMemoryMenu();
            return true;
        });
        mainContent.addView(memStatus);

        TextView chatTitle = new TextView(this);
        chatTitle.setText("当前: 未选择好友");
        chatTitle.setTag("chatTitle");
        chatTitle.setPadding(16, 12, 16, 12);
        chatTitle.setTextSize(14f);
        chatTitle.setTextColor(Color.GRAY);
        chatTitle.setBackgroundColor(Color.parseColor("#E9ECEF"));
        mainContent.addView(chatTitle);

        messageScrollView = new ScrollView(this);
        messageScrollView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        messageScrollView.setPadding(16, 8, 16, 8);
        messageContainer = new LinearLayout(this);
        messageContainer.setOrientation(LinearLayout.VERTICAL);
        messageScrollView.addView(messageContainer);
        mainContent.addView(messageScrollView);

        imagePreviewBar = new LinearLayout(this);
        imagePreviewBar.setOrientation(LinearLayout.HORIZONTAL);
        imagePreviewBar.setPadding(8, 4, 8, 4);
        imagePreviewBar.setBackgroundColor(Color.parseColor("#EEEEEE"));
        imagePreviewBar.setVisibility(View.GONE);

        previewImage = new ImageView(this);
        previewImage.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(56), dpToPx(56)));
        previewImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        previewImage.setPadding(4, 4, 4, 4);

        TextView previewName = new TextView(this);
        previewName.setText("已选择图片");
        previewName.setTextSize(13f);
        previewName.setTextColor(Color.DKGRAY);
        previewName.setGravity(Gravity.CENTER_VERTICAL);
        previewName.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        previewName.setPadding(12, 0, 0, 0);
        previewName.setTag("previewName");

        previewCloseBtn = new ImageButton(this);
        previewCloseBtn.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
        previewCloseBtn.setBackgroundColor(Color.TRANSPARENT);
        previewCloseBtn.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(44), dpToPx(44)));
        previewCloseBtn.setOnClickListener(v -> clearImagePreview());

        imagePreviewBar.addView(previewImage);
        imagePreviewBar.addView(previewName);
        imagePreviewBar.addView(previewCloseBtn);
        mainContent.addView(imagePreviewBar);

        LinearLayout bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setPadding(8, 6, 8, 6);
        bottomBar.setBackgroundColor(Color.parseColor("#F0F0F0"));

        attachBtn = new Button(this);
        attachBtn.setText("+");
        attachBtn.setTextSize(24f);
        attachBtn.setBackgroundColor(Color.TRANSPARENT);
        attachBtn.setMinWidth(dpToPx(42));
        attachBtn.setMinimumHeight(dpToPx(42));
        attachBtn.setOnClickListener(v -> showAttachMenu());
        bottomBar.addView(attachBtn);

        modelList = loadModelList();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, modelList);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        modelSpinner = new Spinner(this);
        modelSpinner.setAdapter(adapter);
        LinearLayout.LayoutParams spinnerParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.3f);
        spinnerParams.setMarginEnd(dpToPx(4));
        modelSpinner.setLayoutParams(spinnerParams);
        modelSpinner.setMinimumHeight(dpToPx(42));

        if (!cachedModel.isEmpty() && modelList.contains(cachedModel)) {
            modelSpinner.setSelection(modelList.indexOf(cachedModel));
        }

        modelSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selected = modelList.get(position);
                if (!selected.isEmpty() && !selected.equals(cachedModel)) {
                    cachedModel = selected;
                    prefs.edit().putString("model", selected).apply();
                    TextView ml = (TextView) drawerLayout.findViewWithTag("modelLabel");
                    if (ml != null) ml.setText(selected);

                    updateModelInConfig(selected);
                    Toast.makeText(MainActivity.this, "底层翻译模型已实时切换为: " + selected, Toast.LENGTH_SHORT).show();
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        bottomBar.addView(modelSpinner);

        inputBox = new EditText(this);
        inputBox.setHint("输入对 AI 的调教指令...");
        inputBox.setBackgroundColor(Color.WHITE);
        inputBox.setPadding(12, 8, 12, 8);
        inputBox.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        inputBox.setMinimumHeight(dpToPx(42));
        bottomBar.addView(inputBox);

        sendBtn = new Button(this);
        sendBtn.setText("注入指令");
        sendBtn.setTextSize(14f);
        sendBtn.setMinWidth(dpToPx(56));
        sendBtn.setMinimumHeight(dpToPx(42));
        sendBtn.setTextColor(Color.WHITE);
        sendBtn.setBackgroundColor(Color.parseColor("#007BFF"));
        sendBtn.setOnClickListener(v -> sendMessage());
        bottomBar.addView(sendBtn);

        mainContent.addView(bottomBar);
        drawerLayout.addView(mainContent);

        drawerContent = new LinearLayout(this);
        drawerContent.setOrientation(LinearLayout.VERTICAL);
        drawerContent.setPadding(20, 90, 20, 20);
        drawerContent.setBackgroundColor(Color.parseColor("#FAFAFA"));
        DrawerLayout.LayoutParams dlp = new DrawerLayout.LayoutParams(dpToPx(280), ViewGroup.LayoutParams.MATCH_PARENT);
        dlp.gravity = Gravity.LEFT;
        drawerContent.setLayoutParams(dlp);

        TextView drawerTitle = new TextView(this);
        drawerTitle.setText("HT 遥控好友列表");
        drawerTitle.setTextSize(18f);
        drawerTitle.setTextColor(Color.BLACK);
        drawerTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        drawerTitle.setPadding(10, 0, 0, 30);
        drawerContent.addView(drawerTitle);

        refreshDrawerList();
        drawerLayout.addView(drawerContent);
        setContentView(drawerLayout);
    }

@Override
protected void onResume() {
    super.onResume();

    // 刷新记忆状态
    checkMemoryClaim();

    // HelloTalk 新打开聊天后可能刚刚写入 htai_friends.json，
    // 每次回到 HT 遥控页面都重新读取一次好友列表。
    refreshDrawerList();
}

    private void checkMemoryClaim() {
        new Thread(() -> {
String out = runRoot(
        "cat /data/local/tmp/htai_mem_mode.txt 2>/dev/null; echo '<<<HTAI_SEP>>>';"
        + " rm -rf /data/local/tmp/htai_sandbox_check; mkdir -p /data/local/tmp/htai_sandbox_check; cp /data/data/com.hellotalk/files/htai_* /data/local/tmp/htai_sandbox_check/ 2>/dev/null; ls /data/local/tmp/htai_sandbox_check/ 2>/dev/null; echo '<<<HTAI_SEP>>>';"
        + " ls /data/local/tmp/htai_store/htai_* 2>/dev/null");

            if (out == null) {
                runOnUiThread(() -> updateMemStatus("noroot"));
                return;
            }

            String[] parts = out.split("<<<HTAI_SEP>>>", -1);
            String marker = parts.length > 0 ? parts[0].trim() : "";
            String sandboxLs = parts.length > 1 ? parts[1].trim() : "";
            String storeLs = parts.length > 2 ? parts[2].trim() : "";
            boolean sandboxHas = !sandboxLs.isEmpty();
            boolean storeHas = !storeLs.isEmpty();

            final boolean pending = storeHas && !"temp".equals(marker)
                    && ("pending".equals(marker) || !sandboxHas);
            final String fMarker = marker;

            runOnUiThread(() -> {
                if (pending) {
                    updateMemStatus("pending");
                    showClaimDialog();
                } else if ("pending".equals(fMarker)) {
                    new Thread(() ->
                            runRoot("echo main > /data/local/tmp/htai_mem_mode.txt && chmod 644 /data/local/tmp/htai_mem_mode.txt")
                    ).start();
                    updateMemStatus("main");
                } else {
                    updateMemStatus(fMarker);
                }
            });
        }).start();
    }

    private void updateMemStatus(String marker) {
        TextView ms = (TextView) drawerLayout.findViewWithTag("memStatus");
        if (ms == null) return;
        if ("noroot".equals(marker)) {
            ms.setText("⚠️ 记忆：遥控器未获得root权限，无法检测/切换记忆");
            ms.setTextColor(Color.parseColor("#B02A37"));
            ms.setBackgroundColor(Color.parseColor("#FDECEE"));
        } else if ("temp".equals(marker)) {
            ms.setText("🧠 记忆：一次性模式（功能照常，不备份，清数据即焚）");
            ms.setTextColor(Color.parseColor("#B45309"));
            ms.setBackgroundColor(Color.parseColor("#FFF7E6"));
        } else if ("pending".equals(marker)) {
            ms.setText("🧠 记忆：待认领（请在弹窗中选择）");
            ms.setTextColor(Color.parseColor("#B02A37"));
            ms.setBackgroundColor(Color.parseColor("#FDECEE"));
        } else {
            ms.setText("🧠 记忆：主账号（自动备份中）｜长按管理");
            ms.setTextColor(Color.parseColor("#0B5ED7"));
            ms.setBackgroundColor(Color.parseColor("#F1F8FF"));
        }
    }

    private void showClaimDialog() {
        if (claimDialogShowing || isFinishing()) return;
        claimDialogShowing = true;

        CheckBox restoreDbBox = new CheckBox(this);
        restoreDbBox.setText("同时恢复 HelloTalk 官方聊天列表 (⚠️跨版本极易闪退，小白勿选)");
        restoreDbBox.setChecked(false);
        restoreDbBox.setTextSize(14f);
        restoreDbBox.setPadding(0, dpToPx(8), 0, dpToPx(8));

        new AlertDialog.Builder(this)
                .setTitle("这次登录的是谁？")
                .setMessage("检测到 HelloTalk 数据被清空。\n\n" +
                        "【主账号】把保险箱里的全部记忆装回去\n" +
                        "【一次性】本次瞎聊不备份，清数据后自动烧掉")
                .setView(restoreDbBox)
                .setPositiveButton("主账号：恢复记忆", (d, w) -> claimMain(restoreDbBox.isChecked()))
                .setNegativeButton("一次性：不保存", (d, w) -> claimTemp())
                .setOnDismissListener(d -> claimDialogShowing = false)
                .setCancelable(true)
                .show();
    }

    private void claimMain(boolean restoreNativeDb) {
        Toast.makeText(this, restoreNativeDb ? "正在恢复主账号记忆及官方聊天列表..." : "正在恢复主账号记忆...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            // 无论是否勾选，都必须恢复 AI 核心记忆
            runRoot("mkdir -p /data/data/com.hellotalk/files");
            runRoot("chown $(stat -c %u:%g /data/data/com.hellotalk) /data/data/com.hellotalk/files 2>/dev/null");
            runRoot("chmod 777 /data/data/com.hellotalk/files 2>/dev/null");
            runRoot("cp /data/local/tmp/htai_store/htai_* /data/data/com.hellotalk/files/ 2>/dev/null");
            runRoot("chmod 666 /data/data/com.hellotalk/files/htai_* 2>/dev/null");
            runRoot("chown $(stat -c %u:%g /data/data/com.hellotalk) /data/data/com.hellotalk/files/htai_* 2>/dev/null");

            // 仅在用户勾选时恢复原生数据库（带版本校验）
            String dbMsg = "";
            if (restoreNativeDb) {
                dbMsg = restoreNativeDbNow()
                        ? "\n官方聊天列表已恢复"
                        : "\n⚠️ 官方聊天列表未恢复：备份版本与当前版本不一致";
            }

            runRoot("echo main > /data/local/tmp/htai_mem_mode.txt && chmod 644 /data/local/tmp/htai_mem_mode.txt");
            runRoot("am force-stop com.hellotalk");

            final String finalDbMsg = dbMsg;
            runOnUiThread(() -> {
                updateMemStatus("main");
                refreshDrawerList();
                Toast.makeText(MainActivity.this, "✅ 主账号记忆已恢复" + finalDbMsg + "，HelloTalk 已重启", Toast.LENGTH_LONG).show();
            });
        }).start();
    }

    private boolean restoreNativeDbNow() {
        String backupVer = runRoot("cat /data/local/tmp/htai_store/htai_db_version.txt 2>/dev/null");
        String curVer = getHTVersion();
        if (backupVer == null || backupVer.trim().isEmpty()
                || curVer == null || curVer.trim().isEmpty()
                || !backupVer.trim().equals(curVer.trim())) {
            return false;
        }
        runRoot("am force-stop com.hellotalk");
        runRoot("mkdir -p /data/data/com.hellotalk/databases");
        runRoot("chown $(stat -c %u:%g /data/data/com.hellotalk) /data/data/com.hellotalk/databases 2>/dev/null");
        runRoot("chmod 771 /data/data/com.hellotalk/databases 2>/dev/null");
        runRoot("rm -f /data/data/com.hellotalk/databases/*-wal /data/data/com.hellotalk/databases/*-shm 2>/dev/null");
        runRoot("cp /data/local/tmp/htai_store/db_backup/* /data/data/com.hellotalk/databases/ 2>/dev/null");
        runRoot("chown $(stat -c %u:%g /data/data/com.hellotalk) /data/data/com.hellotalk/databases/* 2>/dev/null");
        return true;
    }

    private void claimTemp() {
        new Thread(() -> {
            runRoot("echo temp > /data/local/tmp/htai_mem_mode.txt && chmod 644 /data/local/tmp/htai_mem_mode.txt");
            runOnUiThread(() -> {
                updateMemStatus("temp");
                Toast.makeText(MainActivity.this, "已进入一次性模式", Toast.LENGTH_LONG).show();
            });
        }).start();
    }

    private void showMemoryMenu() {
        new AlertDialog.Builder(this)
                .setTitle("记忆管理")
                .setItems(new String[]{
                        "📦 立即备份（沙箱 → 保险箱）",
                        "🕶 切换一次性模式（小号瞎聊用）",
                        "👑 切换主账号模式（恢复记忆）",
                        "🔍 查看记忆文件（诊断）",
                        "🔥 一键焚毁沙盒（清空当前记忆）",
                        "🗑️ 清空保险箱 (删错号/死机救砖)"
                }, (d, w) -> {
                    if (w == 0) backupNow();
                    else if (w == 1) confirmSwitchToTemp();
                    else if (w == 2) confirmSwitchToMain();
                    else if (w == 3) showMemoryFiles();
                    else if (w == 4) confirmClearSandbox();
                    else if (w == 5) confirmClearStore();
                })
                .show();
    }

    private void confirmClearStore() {
        new AlertDialog.Builder(this)
                .setTitle("彻底清空保险箱？")
                .setMessage("这会删除所有已备份的 AI 记忆和原生数据库，不可逆！")
                .setPositiveButton("🗑️ 彻底清空", (d, w) -> clearStoreNow())
                .setNegativeButton("取消", null)
                .show();
    }

    private void clearStoreNow() {
        new Thread(() -> {
            runRoot("rm -rf /data/local/tmp/htai_store/*");
            runOnUiThread(() -> Toast.makeText(MainActivity.this, "保险箱已彻底清空", Toast.LENGTH_SHORT).show());
        }).start();
    }

    private void confirmClearSandbox() {
        new AlertDialog.Builder(this)
                .setTitle("高能预警：焚毁沙盒")
                .setMessage("这将彻底使用 Root 权限强制删除 HelloTalk 沙盒下的所有 AI 记忆和草稿缓存。\n\n（放心，这绝不会影响你早已存放在保险箱里的主账号备份）。\n\n确定要清空吗？")
                .setPositiveButton("🔥 立即焚毁", (d, w) -> clearSandboxNow())
                .setNegativeButton("取消", null)
                .show();
    }

    private void clearSandboxNow() {
        Toast.makeText(this, "正在焚毁沙盒记忆...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            // Root 强制删除沙盒内所有的模块文件
            runRoot("rm -f /data/data/com.hellotalk/files/htai_* 2>/dev/null");
            // 杀掉 HelloTalk 进程，确保它释放内存锁
            runRoot("am force-stop com.hellotalk");
            
            runOnUiThread(() -> {
                // 刷新 UI 状态
                checkMemoryClaim();
                refreshDrawerList();
                Toast.makeText(MainActivity.this, "🔥 沙盒已干干净净！HelloTalk 已强制重启", Toast.LENGTH_LONG).show();
            });
        }).start();
    }

    private void backupNow() {
        Toast.makeText(this, "备份中...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            runRoot("mkdir -p /data/local/tmp/htai_store"
                    + " && cp /data/data/com.hellotalk/files/htai_* /data/local/tmp/htai_store/ 2>/dev/null; "
                    + "chmod 600 /data/local/tmp/htai_store/htai_* 2>/dev/null");
            backupNativeDb();
            String storeLs = runRoot("ls /data/local/tmp/htai_store/htai_* 2>/dev/null");
            boolean ok = storeLs != null && !storeLs.trim().isEmpty();
            runOnUiThread(() -> Toast.makeText(MainActivity.this,
                    ok ? "✅ 保险箱已有备份" : "❌ 备份失败",
                    Toast.LENGTH_LONG).show());
        }).start();
    }

    private void backupNativeDb() {
        runRoot("mkdir -p /data/local/tmp/htai_store/db_backup"
                + " && cp /data/data/com.hellotalk/databases/* /data/local/tmp/htai_store/db_backup/ 2>/dev/null");
        String ver = getHTVersion();
        if (ver != null && !ver.isEmpty()) {
            runRoot("echo '" + ver + "' > /data/local/tmp/htai_store/htai_db_version.txt");
            runRoot("chmod 600 /data/local/tmp/htai_store/htai_db_version.txt");
        }
    }

    private String getHTVersion() {
        String raw = runRoot("dumpsys package com.hellotalk | grep -m1 versionName");
        if (raw == null) return "";
        String v = raw.trim();
        int idx = v.indexOf("versionName=");
        if (idx >= 0) {
            v = v.substring(idx + "versionName=".length()).trim();
        }
        return v;
    }

    private void confirmSwitchToTemp() {
        new AlertDialog.Builder(this)
                .setTitle("切换一次性模式")
                .setMessage("将依次执行：\n1. 备份主账号记忆\n2. 清空沙箱\n3. 标记为一次性\n\n确定切换？")
                .setPositiveButton("切换", (d, w) -> switchToTemp())
                .setNegativeButton("取消", null)
                .show();
    }

    private void switchToTemp() {
        Toast.makeText(this, "正在切换一次性模式...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            String sandboxLs0 = runRoot("ls /data/data/com.hellotalk/files/htai_* 2>/dev/null");
            boolean sandboxHas0 = sandboxLs0 != null && !sandboxLs0.trim().isEmpty();

            if (sandboxHas0) {
                runRoot("mkdir -p /data/local/tmp/htai_store"
                        + " && cp /data/data/com.hellotalk/files/htai_* /data/local/tmp/htai_store/ 2>/dev/null; "
                        + "chmod 600 /data/local/tmp/htai_store/htai_* 2>/dev/null");
                String storeLs = runRoot("ls /data/local/tmp/htai_store/htai_* 2>/dev/null");
                boolean storeOk = storeLs != null && !storeLs.trim().isEmpty();
                if (!storeOk) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this,
                            "❌ 备份失败，已中止切换",
                            Toast.LENGTH_LONG).show());
                    return;
                }
                runRoot("rm -f /data/data/com.hellotalk/files/htai_* 2>/dev/null");
            }

            backupNativeDb();

            runRoot("echo temp > /data/local/tmp/htai_mem_mode.txt && chmod 644 /data/local/tmp/htai_mem_mode.txt");
            runRoot("am force-stop com.hellotalk");
            runOnUiThread(() -> {
                updateMemStatus("temp");
                refreshDrawerList();
                Toast.makeText(MainActivity.this, "🕶 已进入一次性模式", Toast.LENGTH_LONG).show();
            });
        }).start();
    }

    private void confirmSwitchToMain() {
        CheckBox cb = new CheckBox(this);
        cb.setText("同时恢复 HelloTalk 官方聊天列表 (⚠️跨版本极易闪退，小白勿选)");
        cb.setChecked(false);
        cb.setTextSize(14f);
        cb.setPadding(0, dpToPx(8), 0, dpToPx(8));

        new AlertDialog.Builder(this)
                .setTitle("切换主账号模式")
                .setMessage("将把保险箱里的 AI 记忆装回沙箱。")
                .setView(cb)
                .setPositiveButton("切换", (d, w) -> switchToMain(cb.isChecked()))
                .setNegativeButton("取消", null)
                .show();
    }

    private void switchToMain(boolean restoreNativeDb) {
        Toast.makeText(this, "正在切换主账号模式...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            String sandboxLs = runRoot("ls /data/data/com.hellotalk/files/htai_* 2>/dev/null");
            boolean sandboxHas = sandboxLs != null && !sandboxLs.trim().isEmpty();

            if (!sandboxHas) {
                runRoot("mkdir -p /data/data/com.hellotalk/files");
                runRoot("chown $(stat -c %u:%g /data/data/com.hellotalk) /data/data/com.hellotalk/files 2>/dev/null");
                runRoot("chmod 777 /data/data/com.hellotalk/files 2>/dev/null");
                runRoot("cp /data/local/tmp/htai_store/htai_* /data/data/com.hellotalk/files/ 2>/dev/null");
                runRoot("chmod 666 /data/data/com.hellotalk/files/htai_* 2>/dev/null");
                runRoot("chown $(stat -c %u:%g /data/data/com.hellotalk) /data/data/com.hellotalk/files/htai_* 2>/dev/null");
            }

            String dbMsg = "";
            if (restoreNativeDb) {
                dbMsg = restoreNativeDbNow()
                        ? "\n官方聊天列表已恢复"
                        : "\n⚠️ 官方聊天列表未恢复：备份版本与当前版本不一致";
            }

            runRoot("echo main > /data/local/tmp/htai_mem_mode.txt && chmod 644 /data/local/tmp/htai_mem_mode.txt");
            runRoot("am force-stop com.hellotalk");

            final String finalDbMsg = dbMsg;
            runOnUiThread(() -> {
                updateMemStatus("main");
                refreshDrawerList();
                Toast.makeText(MainActivity.this, "👑 已切回主账号模式" + finalDbMsg, Toast.LENGTH_LONG).show();
            });
        }).start();
    }

    private void showMemoryFiles() {
        new Thread(() -> {
            String marker = runRoot("cat /data/local/tmp/htai_mem_mode.txt 2>/dev/null");
            String sandbox = runRoot("cp /data/data/com.hellotalk/files/htai_* /data/local/tmp/ 2>/dev/null; ls -la /data/local/tmp/ 2>/dev/null | grep htai");
            String store = runRoot("ls -la /data/local/tmp/htai_store/ 2>/dev/null | grep htai");

            StringBuilder sb = new StringBuilder();
            sb.append("【模式标记】\n").append(marker == null ? "读取失败" : marker.trim())
              .append("\n\n【沙箱】\n").append(sandbox == null ? "读取失败" : sandbox.trim())
              .append("\n\n【保险箱】\n").append(store == null ? "读取失败" : store.trim());

            runOnUiThread(() -> new AlertDialog.Builder(MainActivity.this)
                    .setTitle("记忆文件诊断")
                    .setMessage(sb.toString())
                    .setPositiveButton("知道了", null)
                    .show());
        }).start();
    }

    private void showPopupMenu(View v) {
        PopupMenu popup = new PopupMenu(this, v);
        popup.getMenu().add(0, 1, 0, "⚙️ 设置/API配置");
        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void loadConfigOnce() {
        cachedApiKey = prefs.getString("api_key", "");
        cachedApiUrl = prefs.getString("api_url", "");
        cachedModel = prefs.getString("model", "");
        if (cachedApiKey.isEmpty()) cachedApiKey = readConfig("api_key");
        if (cachedApiUrl.isEmpty()) cachedApiUrl = readConfig("api_url");
        if (cachedModel.isEmpty()) cachedModel = readConfig("model");
    }

    private String readConfig(String key) {
        String content = runRoot("cat /data/local/tmp/htai_config.txt");
        if (content == null) return "";
        for (String l : content.split("\n")) {
            if (l.trim().startsWith(key + "=")) {
                return l.trim().substring(key.length() + 1).trim();
            }
        }
        return "";
    }

    private void updateModelInConfig(String newModel) {
        new Thread(() -> {
            String key = prefs.getString("api_key", "");
            String url = prefs.getString("api_url", "https://api.openai.com/v1/chat/completions");
            String mList = prefs.getString("model_list", "");
            String tempStr = prefs.getString("temperature", "0.7");
            String maxChat = prefs.getString("max_chat_messages", "30");
            String maxT = prefs.getString("max_tokens", "8000"); 
            String banned = prefs.getString("banned_words", "");
            String effort = prefs.getString("reasoning_effort", "default");

            String cfg = "cat > /data/local/tmp/htai_config.txt << 'EOF'\n"
                    + "api_key=" + key + "\n"
                    + "api_url=" + url + "\n"
                    + "model=" + newModel + "\n"
                    + "model_list=" + mList + "\n"
                    + "temperature=" + tempStr + "\n"
                    + "max_chat_messages=" + maxChat + "\n"
                    + "max_tokens=" + maxT + "\n"
                    + "banned_words=" + banned + "\n"
                    + "reasoning_effort=" + effort + "\n"
                    + "EOF\n";
            runRoot(cfg);
            runRoot("chmod 644 /data/local/tmp/htai_config.txt");
        }).start();
    }

    private List<String> loadModelList() {
        List<String> list = new ArrayList<>();
        String saved = prefs.getString("model_list", "");
        if (!saved.isEmpty()) {
            for (String s : saved.split(",")) {
                String t = s.trim();
                if (!t.isEmpty()) list.add(t);
            }
        }
        return list;
    }

    private void showAttachMenu() {
        new AlertDialog.Builder(this)
                .setTitle("选择要注入底层记忆的附件")
                .setItems(new String[]{"🖼️ 相册图片", "📎 文本文档"}, (d, w) -> {
                    if (w == 0) pickImage();
                    else if (w == 1) pickFile();
                })
                .show();
    }

    private void pickImage() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        startActivityForResult(intent, REQUEST_CODE_PICK_IMAGE);
    }

    private void pickFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        startActivityForResult(intent, REQUEST_CODE_PICK_FILE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;
        if (requestCode == REQUEST_CODE_PICK_IMAGE) {
            String b64 = imageUriToBase64(uri);
            if (b64 != null) {
                pendingImageBase64 = b64;
                inputBox.setHint("已选图片: " + getFileName(uri) + "，输入调教描述后发送");
                Toast.makeText(this, "图片已就绪", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQUEST_CODE_PICK_FILE) {
            String fc = readTextFile(uri);
            if (fc != null) {
                sendAttachmentAsMessage("用户上传了前置文档参考：" + getFileName(uri) + "\n内容如下：\n" + fc);
            }
        }
    }

    private void clearImagePreview() {
        pendingImageBase64 = "";
        previewImage.setImageBitmap(null);
        imagePreviewBar.setVisibility(View.GONE);
        inputBox.setHint("输入对 AI 的调教指令...");
    }

    private void showImagePreview(Bitmap thumb, String fileName) {
        previewImage.setImageBitmap(thumb);
        TextView nv = (TextView) imagePreviewBar.findViewWithTag("previewName");
        if (nv != null) nv.setText("已选: " + fileName);
        imagePreviewBar.setVisibility(View.VISIBLE);
    }

    private String imageUriToBase64(Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            if (is == null) return null;
            Bitmap orig = BitmapFactory.decodeStream(is);
            is.close();
            if (orig == null) return null;
            Bitmap thumb = Bitmap.createScaledBitmap(orig, dpToPx(112), dpToPx(112), true);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            orig.compress(Bitmap.CompressFormat.JPEG, 80, baos);
            byte[] bytes = baos.toByteArray();
            orig.recycle();
            showImagePreview(thumb, getFileName(uri));
            return Base64.encodeToString(bytes, Base64.NO_WRAP);
        } catch (Exception e) { return null; }
    }

    private String readTextFile(Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            if (is == null) return null;
            BufferedReader r = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String l;
            while ((l = r.readLine()) != null) sb.append(l).append("\n");
            r.close();
            return sb.toString().trim();
        } catch (Exception e) { return null; }
    }

    private String getFileName(Uri uri) {
        String name = null;
        Cursor c = getContentResolver().query(uri, null, null, null, null);
        if (c != null && c.moveToFirst()) {
            int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
            if (idx >= 0) name = c.getString(idx);
            c.close();
        }
        if (name == null) name = uri.getLastPathSegment();
        return name;
    }

    private void sendAttachmentAsMessage(String content) {
        inputBox.setText(content);
        sendMessage();
    }

    private String runRoot(String cmd) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-M", "-c", cmd});
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String l;
            while ((l = r.readLine()) != null) {
                sb.append(l).append("\n");
            }
            p.waitFor();
            return sb.toString().trim();
        } catch (Exception e) {
            return null;
        }
    }

    private void deleteHTChatRoot(ChatSession s) {
        new Thread(() -> {
            try {
                String histPath = "/data/data/com.hellotalk/files/htai_hist_" + s.id + ".json";
                runRoot("rm " + histPath);

                runRoot("rm /data/data/com.hellotalk/files/htai_profile_" + s.id + ".txt 2>/dev/null");
                runRoot("rm /data/local/tmp/htai_store/htai_hist_" + s.id + ".json /data/local/tmp/htai_store/htai_profile_" + s.id + ".txt 2>/dev/null");

                String friendsPath = "/data/data/com.hellotalk/files/htai_friends.json";
                String jsonStr = runRoot("cp " + friendsPath + " /data/local/tmp/htai_friends.json 2>/dev/null; cat /data/local/tmp/htai_friends.json");
                if (jsonStr != null && !jsonStr.trim().isEmpty()) {
                    JSONObject friends = new JSONObject(jsonStr);
                    if (friends.has(s.id)) {
                        friends.remove(s.id);

                        File tempFile = new File(getCacheDir(), "htai_temp_friends.json");
                        BufferedWriter w = new BufferedWriter(new java.io.FileWriter(tempFile));
                        w.write(friends.toString());
                        w.close();

                        runRoot("cp " + tempFile.getAbsolutePath() + " " + friendsPath);
                        runRoot("chmod 666 " + friendsPath);
                        runRoot("cp " + friendsPath + " /data/local/tmp/htai_store/htai_friends.json 2>/dev/null");
                    }
                }

                mainHandler.post(() -> {
                    if (currentChatId.equals(s.id)) {
                        currentChatId = "";
                        currentChatName = "";
                        ((TextView) drawerLayout.findViewWithTag("chatTitle")).setText("当前遥控: 未选择好友");
                        messageContainer.removeAllViews();
                    }
                    refreshDrawerList();
                    Toast.makeText(MainActivity.this, "已彻底抹除与 [" + s.name + "] 的底层记忆", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                mainHandler.post(() -> Toast.makeText(MainActivity.this, "删除失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void refreshDrawerList() {
        new Thread(() -> {
            final List<ChatSession> htFriends = new ArrayList<>();
            try {
                String jsonStr = runRoot("cp /data/data/com.hellotalk/files/htai_friends.json /data/local/tmp/htai_friends.json 2>/dev/null; cat /data/local/tmp/htai_friends.json");
                if (jsonStr != null && !jsonStr.trim().isEmpty()) {
                    JSONObject friends = new JSONObject(jsonStr);
                    JSONArray names = friends.names();
                    if (names != null) {
                        for (int i = 0; i < names.length(); i++) {
                            String id = names.getString(i);
                            JSONObject info = friends.getJSONObject(id);
                            String name = info.optString("name", id);
                            htFriends.add(new ChatSession(id, name));
                        }
                    }
                }
            } catch (Exception ignored) {}

            runOnUiThread(() -> renderDrawerList(htFriends));
        }).start();
    }

    private void renderDrawerList(List<ChatSession> htFriends) {
        while (drawerContent.getChildCount() > 1) drawerContent.removeViewAt(1);

        if (htFriends.isEmpty()) {
            TextView hint = new TextView(this);
            hint.setText("未检测到好友。\n请先在 HelloTalk 中打开任意聊天界面。");
            hint.setTextSize(14f);
            hint.setTextColor(Color.GRAY);
            hint.setPadding(10, 20, 0, 20);
            drawerContent.addView(hint);
        } else {
            for (ChatSession s : htFriends) {
    // 过滤旧版本把 chatId 当名字存进去的纯数字条目
    if (s.name == null || s.name.trim().isEmpty() || s.name.equals(s.id)) continue;

    TextView tv = new TextView(this);
    tv.setText("👤 " + s.name);
                tv.setTextSize(16f);
                tv.setPadding(20, 26, 20, 26);
                tv.setTextColor(Color.parseColor("#333333"));
                if (s.id.equals(currentChatId)) tv.setBackgroundColor(Color.parseColor("#E3F2FD"));

                tv.setOnClickListener(v -> {
                    currentChatId = s.id;
                    currentChatName = s.name;
                    ((TextView) drawerLayout.findViewWithTag("chatTitle")).setText("当前遥控: " + currentChatName);
                    loadHTMessagesRoot(currentChatId);
                    drawerLayout.closeDrawers();
                    refreshDrawerList();
                });

                tv.setOnLongClickListener(v -> {
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("高能预警")
                            .setMessage("确定要彻底抹除与 [" + s.name + "] 的底层记忆文件吗？")
                            .setPositiveButton("销毁记忆", (dialog, which) -> deleteHTChatRoot(s))
                            .setNegativeButton("取消", null)
                            .show();
                    return true;
                });

                drawerContent.addView(tv);
            }
        }
    }

    private void loadHTMessagesRoot(String chatId) {
        messageContainer.removeAllViews();
        new Thread(() -> {
            final String tmpPath = "/data/local/tmp/htai_hist_copy_" + chatId + ".json";
            final String jsonStr = runRoot("rm -f " + tmpPath + "; cp /data/data/com.hellotalk/files/htai_hist_" + chatId + ".json " + tmpPath + " 2>/dev/null; cat " + tmpPath);
            runOnUiThread(() -> {
                try {
                if (jsonStr != null && !jsonStr.trim().isEmpty()) {
    // 修复旧版历史文件开头可能是 "[," 的损坏问题
    String fixedJson = jsonStr.trim();
    if (fixedJson.startsWith("[,")) {
        fixedJson = "[" + fixedJson.substring(2);
    } else if (fixedJson.startsWith("[")) {
        int p = 1;
        while (p < fixedJson.length()
                && (fixedJson.charAt(p) == ','
                    || fixedJson.charAt(p) == ' '
                    || fixedJson.charAt(p) == '\t'
                    || fixedJson.charAt(p) == '\n'
                    || fixedJson.charAt(p) == '\r')) {
            p++;
        }
        if (p > 1) fixedJson = "[" + fixedJson.substring(p);
    }
    JSONArray history = new JSONArray(fixedJson);
                        for (int i = 0; i < history.length(); i++) {
                            JSONObject obj = history.getJSONObject(i);
                            String role = obj.optString("role", "");
                            String content = obj.optString("content", "");

                           // 过滤旧版本的脏数据：纯数字聊天ID、bean 的 toString 等
if (content == null || content.trim().isEmpty()) {
    // 跳过空内容
} else if (content.trim().matches("\\d{6,}")) {
    // 跳过纯数字聊天ID
} else if (content.trim().startsWith("{") && content.trim().endsWith("}")) {
    // 跳过 {name = xxx} 这类脏数据
} else if ("user".equals(role)) {
    displayMessage("ai", "对方: " + content);
} else if ("assistant".equals(role)) {
    displayMessage("user", content);
} else {
    displayMessage("system", content);
}
                        }
                        messageScrollView.postDelayed(() -> messageScrollView.fullScroll(View.FOCUS_DOWN), 100);
                    } else {
                        displayMessage("system", "暂无与该好友的翻译记录");
                    }
                } catch (Exception e) {
                    displayMessage("system", "⚠️ 读取该好友记录失败");
                }
            });
        }).start();
    }

    private void sendMessage() {
        if (currentChatId.isEmpty()) {
            Toast.makeText(this, "请先在左侧选择要调教的好友", Toast.LENGTH_SHORT).show();
            drawerLayout.openDrawer(Gravity.LEFT);
            return;
        }

        String text = inputBox.getText().toString().trim();
        if (text.isEmpty() && pendingImageBase64.isEmpty()) return;
        if (text.isEmpty() && !pendingImageBase64.isEmpty()) text = "请描述这张图片";

        boolean hasImage = !pendingImageBase64.isEmpty();

        String contentToInject = hasImage ? "[IMAGE_BASE64:" + pendingImageBase64 + "]" + text : text;

        if (hasImage) clearImagePreview();

        String uiDisplay = hasImage ? "【附图调教】 " + text : "【调教指令】 " + text;
        displayMessage("system", uiDisplay);

        inputBox.setText("");

        new Thread(() -> {
            try {
                String path = "/data/data/com.hellotalk/files/htai_hist_" + currentChatId + ".json";
                String jsonStr = runRoot("cat " + path);
                JSONArray history;
                if (jsonStr != null && !jsonStr.trim().isEmpty() && jsonStr.startsWith("[")) {
                    history = new JSONArray(jsonStr);
                } else {
                    history = new JSONArray();
                }

                JSONObject entry = new JSONObject();
                entry.put("role", "system");
                entry.put("content", contentToInject);
                history.put(entry);

                File tempFile = new File(getCacheDir(), "htai_temp.json");
                BufferedWriter w = new BufferedWriter(new java.io.FileWriter(tempFile));
                w.write(history.toString());
                w.close();

                runRoot("mkdir -p /data/data/com.hellotalk/files");
                runRoot("chown $(stat -c %u:%g /data/data/com.hellotalk) /data/data/com.hellotalk/files 2>/dev/null");
                runRoot("chmod 777 /data/data/com.hellotalk/files 2>/dev/null");
                
                runRoot("cp " + tempFile.getAbsolutePath() + " " + path);
                runRoot("chmod 666 " + path);
                runRoot("chown $(stat -c %u:%g /data/data/com.hellotalk) " + path + " 2>/dev/null");

                mainHandler.post(() -> {
                    displayMessage("system", "✅ 指令及附件已静默注入底层！\n切回 HelloTalk 再次点击“译”按钮即可生效。");
                    messageScrollView.post(() -> messageScrollView.fullScroll(View.FOCUS_DOWN));
                });
            } catch (Exception e) {
                mainHandler.post(() -> displayMessage("system", "❌ 注入失败: " + e.getMessage()));
            }
        }).start();
    }

    private void displayMessage(String role, String content) {
        if (content.contains("[IMAGE_BASE64:")) {
            int start = content.indexOf("[IMAGE_BASE64:");
            int end = content.indexOf("]", start);
            if (end != -1) {
                content = content.substring(0, start) + "[图片附件] " + content.substring(end + 1);
            }
        }

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, 12, 0, 12);

        TextView bubble = new TextView(this);
        bubble.setText(content);
        bubble.setTextSize(15f);
        bubble.setPadding(24, 18, 24, 18);
        bubble.setLineSpacing(6f, 1f);
        bubble.setTextIsSelectable(true);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.width = (int)(getResources().getDisplayMetrics().widthPixels * 0.75);

        if ("ai".equals(role)) {
            bubble.setBackgroundColor(Color.parseColor("#E8E8E8"));
            bubble.setTextColor(Color.parseColor("#212529"));
            bubble.setLayoutParams(lp);
            row.addView(bubble);
            row.setGravity(Gravity.START);
        } else if ("user".equals(role)) {
            bubble.setBackgroundColor(Color.parseColor("#DCF8C6"));
            bubble.setTextColor(Color.parseColor("#155724"));
            bubble.setLayoutParams(lp);

            LinearLayout spacer = new LinearLayout(this);
            spacer.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(spacer);
            row.addView(bubble);
            row.setGravity(Gravity.END);
        } else {
            bubble.setBackgroundColor(Color.parseColor("#FFF3CD"));
            bubble.setTextColor(Color.parseColor("#856404"));
            bubble.setTextSize(13f);
            lp.width = (int)(getResources().getDisplayMetrics().widthPixels * 0.85);
            bubble.setLayoutParams(lp);
            row.addView(bubble);
            row.setGravity(Gravity.CENTER);
        }
        messageContainer.addView(row);
    }

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }

    static class ChatSession {
        String id;
        String name;
        ChatSession(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }
}
