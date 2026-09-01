package com.aihellotalk;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

public class SettingsActivity extends Activity {

    private EditText etKey, etUrl, etModel, etTemperature, etMaxTokens, etMaxChat, etBannedWords;
    private EditText etLiveContextMax;
    private android.widget.CheckBox cbShowApiSwitchHint;
    private android.widget.Spinner spinnerReasoning;
    private EditText etPromptZH, etPromptEN, etPromptRU, etPromptUK, etPromptKO, etPromptES;
    private EditText etPromptAR, etPromptPT, etPromptFR, etPromptDE, etPromptIT;
    private EditText etPromptTR, etPromptNL, etPromptPL, etPromptKK, etPromptCS;
    private EditText etQuick1Tag, etQuick1Content, etQuick2Tag, etQuick2Content;
    private EditText etQuick3Tag, etQuick3Content, etQuick4Tag, etQuick4Content;
    private EditText etQuick5;
    
    // API 1 字段
    private EditText etWeight1;
    private android.widget.Spinner spinnerDir1;
    
    // API 2-8 字段及自定义别名框
    private android.widget.Spinner spinnerReasoning2, spinnerReasoning3, spinnerReasoning4, spinnerReasoning5;
    private EditText etKey2, etUrl2, etModel2, etWeight2, etAlias2;
    private android.widget.Spinner spinnerDir2;
    private EditText etKey3, etUrl3, etModel3, etWeight3, etAlias3;
    private android.widget.Spinner spinnerDir3;
    private EditText etKey4, etUrl4, etModel4, etWeight4, etAlias4;
    private android.widget.Spinner spinnerDir4;
    private EditText etKey5, etUrl5, etModel5, etWeight5, etAlias5;
    private android.widget.Spinner spinnerDir5;
    private EditText etKey6, etUrl6, etModel6, etWeight6, etAlias6;
    private android.widget.Spinner spinnerDir6, spinnerReasoning6;
    private EditText etKey7, etUrl7, etModel7, etWeight7, etAlias7;
    private android.widget.Spinner spinnerDir7, spinnerReasoning7;
    private EditText etKey8, etUrl8, etModel8, etWeight8, etAlias8;
    private android.widget.Spinner spinnerDir8, spinnerReasoning8;
    
    private android.widget.Switch swHideRead, swHideTyping;
    private EditText etSearchPrompt;
    private Button btnFetch, btnSave, btnTest;
    private Button btnSearchPrompt;

    private ScrollView settingsScroll;
    private LinearLayout settingsContent;
    
    private LinearLayout advContentLayout;
    private TextView advHeaderTitle;
    private LinearLayout promptContentLayout;
    private TextView promptHeaderTitle;

    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        prefs = getSharedPreferences("htai_settings", MODE_PRIVATE);

        ScrollView sv = new ScrollView(this);
        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.VERTICAL);
        ll.setPadding(40, 40, 40, 40);

        settingsScroll = sv;
        settingsContent = ll;

        ll.addView(tip("HT AI翻译 v5.16 (增强测试直连版)\n接收自动翻译 + 点[文A]按钮选版本"));

        etSearchPrompt = edit("");
        etSearchPrompt.setHint("🔍 搜索语言跳转，例如：阿拉伯、葡萄牙、法语");
        etSearchPrompt.setSingleLine(true);
        ll.addView(etSearchPrompt);

        btnSearchPrompt = btn("跳转到语言");
        btnSearchPrompt.setOnClickListener(v -> {
            String q = etSearchPrompt.getText().toString().trim();
            jumpToLanguage(q);
        });
        ll.addView(btnSearchPrompt);

        ll.addView(div());

        // ================= 常驻显示区：API 基础配置 =================
        ll.addView(lab("API Key:"));
        etKey = edit(prefs.getString("api_key", ""));
        etKey.setHint("输入你的 API Key");
        ll.addView(etKey);

        ll.addView(lab("API URL:"));
        etUrl = edit(prefs.getString("api_url", "https://api.openai.com/v1/chat/completions"));
        ll.addView(etUrl);

        // 核心修改：左右并列的双按钮（获取模型 + 测试API）
        LinearLayout rowBtn1 = new LinearLayout(this); rowBtn1.setOrientation(LinearLayout.HORIZONTAL);
        btnFetch = btn("获取模型"); btnFetch.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)); 
        btnFetch.setOnClickListener(v -> fetchModels()); rowBtn1.addView(btnFetch);
        Button btnTestApi1 = btn("测试通道"); btnTestApi1.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)); 
        btnTestApi1.setOnClickListener(v -> testSingleApi(etKey.getText().toString().trim(), etUrl.getText().toString().trim(), etModel.getText().toString().trim())); rowBtn1.addView(btnTestApi1);
        ll.addView(rowBtn1);

        ll.addView(lab("模型:"));
        etModel = edit(prefs.getString("model", ""));
        etModel.setHint("先获取后选择，或手动输入");
        ll.addView(etModel);

        ll.addView(lab("调用权重 (数字越大用得越多，建议1~10):"));
        etWeight1 = edit(prefs.getString("api_weight", "3"));
        etWeight1.setHint("3");
        ll.addView(etWeight1);

        // API 1 增加防误触锁
        LinearLayout dirRow1 = new LinearLayout(this); dirRow1.setOrientation(LinearLayout.HORIZONTAL); dirRow1.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView lblDir1 = lab("翻译方向 (防误触锁定中): "); dirRow1.addView(lblDir1);
        Button btnUnlock1 = new Button(this); btnUnlock1.setText("🔓解锁修改"); btnUnlock1.setTextSize(12f); btnUnlock1.setPadding(10,0,10,0);
        dirRow1.addView(btnUnlock1);
        ll.addView(dirRow1);

        spinnerDir1 = new android.widget.Spinner(this);
        String[] dirs = {"发送+接收", "仅接收", "仅发送"};
        android.widget.ArrayAdapter<String> dirAdapter1 = new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_item, dirs);
        dirAdapter1.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerDir1.setAdapter(dirAdapter1);
        spinnerDir1.setSelection(prefs.getInt("api_direction", 0));
        spinnerDir1.setEnabled(false); 
        ll.addView(spinnerDir1);

        ll.addView(lab("思考模式:"));
        spinnerReasoning = new android.widget.Spinner(this);
        String[] efforts = {"默认(不干预)", "轻度思考", "中度思考", "深度思考"};
        android.widget.ArrayAdapter<String> effAdapter1 = new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_item, efforts);
        effAdapter1.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerReasoning.setAdapter(effAdapter1);                      
        String savedEff1 = prefs.getString("reasoning_effort", "default");
        int selEff1 = 0;
        if ("low".equals(savedEff1)) selEff1 = 1;
        else if ("medium".equals(savedEff1)) selEff1 = 2;
        else if ("high".equals(savedEff1)) selEff1 = 3;
        spinnerReasoning.setSelection(selEff1);                        
        spinnerReasoning.setTag("reasoning1"); 
        spinnerReasoning.setEnabled(false);                       
        ll.addView(spinnerReasoning);                                  

        btnUnlock1.setOnClickListener(v -> {
            spinnerDir1.setEnabled(true);
            spinnerReasoning.setEnabled(true);
            Toast.makeText(this, "✅ 主 API 下拉框已解锁", Toast.LENGTH_SHORT).show();
            btnUnlock1.setText("已解锁");
            btnUnlock1.setEnabled(false);
        });

        // ================= 折叠区 1：高级与安全设置 =================
        LinearLayout advHeaderLayout = createHeaderLayout();
        advHeaderTitle = new TextView(this);
        boolean isAdvExpanded = prefs.getBoolean("adv_expanded", false);
        styleHeaderTitle(advHeaderTitle, isAdvExpanded ? "▼ ⚙️ 高级与安全设置 (点击折叠)" : "▶ ⚙️ 高级与安全设置 (点击展开)");
        advHeaderLayout.addView(advHeaderTitle);
        ll.addView(advHeaderLayout);

        advContentLayout = new LinearLayout(this);
        advContentLayout.setOrientation(LinearLayout.VERTICAL);
        advContentLayout.setVisibility(isAdvExpanded ? View.VISIBLE : View.GONE);
        advContentLayout.setPadding(20, 10, 0, 10);

        advContentLayout.addView(lab("Temperature (模型发散温度):"));
        etTemperature = edit(prefs.getString("temperature", "0.7"));
        etTemperature.setHint("0.0 到 2.0 之间，推荐 0.7");
        advContentLayout.addView(etTemperature);

        advContentLayout.addView(lab("上下文消息数 (Max Chat Messages):"));
        etMaxChat = edit(prefs.getString("max_chat_messages", "30"));
        etMaxChat.setHint("建议 20~60，越大越慢但记忆越久");
        advContentLayout.addView(etMaxChat);

        advContentLayout.addView(lab("新版实时 UI 上下文条数 (0=关闭, 建议 0~60):"));
        etLiveContextMax = edit(prefs.getString("live_context_max", "30"));
        etLiveContextMax.setHint("0=关闭实时UI上下文，越大越慢但语境越全；仅新版 6.4.0 生效");
        advContentLayout.addView(etLiveContextMax);

        android.widget.LinearLayout hintRow = new android.widget.LinearLayout(this);
        hintRow.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        cbShowApiSwitchHint = new android.widget.CheckBox(this);
        cbShowApiSwitchHint.setText("智能切换 API 时显示提示");
        cbShowApiSwitchHint.setChecked(prefs.getBoolean("show_api_switch_hint", true));
        hintRow.addView(cbShowApiSwitchHint);
        advContentLayout.addView(hintRow);

        advContentLayout.addView(lab("最大输出长度 (Max Tokens):"));
        etMaxTokens = edit(prefs.getString("max_tokens", "8000"));
        etMaxTokens.setHint("建议设置 2000 到 8000，防止回答被截断");
        advContentLayout.addView(etMaxTokens);

        advContentLayout.addView(lab("全局违禁词库 (Banned Words & Symbols):"));
        etBannedWords = bigEdit(prefs.getString("banned_words", ""));
        etBannedWords.setHint("输入千万不能出现的词或标点，如：lol,破折号,;");
        advContentLayout.addView(etBannedWords);

        ll.addView(advContentLayout);
        setupToggle(advHeaderLayout, advHeaderTitle, advContentLayout, "⚙️ 高级与安全设置", "adv_expanded");

        // ================= 折叠区 1.5：多API智能密钥配置 =================
        LinearLayout apiHeaderLayout = createHeaderLayout();
        TextView apiHeaderTitle = new TextView(this);
        boolean isApiExpanded = prefs.getBoolean("api_expanded", false);
        styleHeaderTitle(apiHeaderTitle, isApiExpanded ? "▼ 🔄 多API智能密钥配置（最多8個） (点击折叠)" : "▶ 🔄 多API智能密钥配置（最多8個） (点击展开)");
        apiHeaderLayout.addView(apiHeaderTitle);
        ll.addView(apiHeaderLayout);

        LinearLayout apiContentLayout = new LinearLayout(this);
        apiContentLayout.setOrientation(LinearLayout.VERTICAL);
        apiContentLayout.setVisibility(isApiExpanded ? View.VISIBLE : View.GONE);
        apiContentLayout.setPadding(20, 10, 0, 10);

        // 辅助选项定义
        android.graphics.drawable.GradientDrawable titleBg = new android.graphics.drawable.GradientDrawable();
        titleBg.setColor(Color.parseColor("#E8EAF6"));
        titleBg.setCornerRadius(8f);
        String[] efforts2 = {"默认(不干预)", "轻度思考", "中度思考", "深度思考"};

        // --- API 2 ---
        LinearLayout row2 = new LinearLayout(this); row2.setOrientation(LinearLayout.HORIZONTAL); row2.setBackground(titleBg); row2.setPadding(20, 20, 20, 20); row2.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lpRow2 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); lpRow2.setMargins(0, 30, 0, 10); row2.setLayoutParams(lpRow2);
        TextView t2 = new TextView(this); t2.setText("🟢 备用 API 2 | "); t2.setTextColor(Color.parseColor("#198754")); t2.setTextSize(15f); t2.setTypeface(null, android.graphics.Typeface.BOLD); row2.addView(t2);
        etAlias2 = new EditText(this); etAlias2.setText(prefs.getString("api_alias_2", "")); etAlias2.setHint("点右侧解锁"); etAlias2.setHintTextColor(Color.parseColor("#80198754")); etAlias2.setTextColor(Color.parseColor("#198754")); etAlias2.setTextSize(14f); etAlias2.setTypeface(null, android.graphics.Typeface.BOLD); etAlias2.setBackgroundColor(Color.TRANSPARENT); etAlias2.setEnabled(false); etAlias2.setPadding(0,0,0,0);
        LinearLayout.LayoutParams lpAlias2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f); etAlias2.setLayoutParams(lpAlias2); row2.addView(etAlias2);
        Button btnEdit2 = new Button(this); btnEdit2.setText("🔓解锁"); btnEdit2.setTextSize(12f); btnEdit2.setBackgroundColor(Color.TRANSPARENT); btnEdit2.setPadding(0,0,0,0);
        btnEdit2.setOnClickListener(v -> { etAlias2.setEnabled(true); etAlias2.setFocusableInTouchMode(true); etAlias2.requestFocus(); spinnerDir2.setEnabled(true); spinnerReasoning2.setEnabled(true); Toast.makeText(this, "API 2 已解锁", Toast.LENGTH_SHORT).show(); btnEdit2.setText("已解"); btnEdit2.setEnabled(false); });
        row2.addView(btnEdit2); apiContentLayout.addView(row2);
        apiContentLayout.addView(lab("API Key 2:")); etKey2 = edit(prefs.getString("api_key_2", "")); apiContentLayout.addView(etKey2);
        apiContentLayout.addView(lab("API URL 2:")); etUrl2 = edit(prefs.getString("api_url_2", "")); apiContentLayout.addView(etUrl2);
        apiContentLayout.addView(lab("模型 2:")); etModel2 = edit(prefs.getString("model_2", "")); apiContentLayout.addView(etModel2);
        LinearLayout rowBtn2 = new LinearLayout(this); rowBtn2.setOrientation(LinearLayout.HORIZONTAL);
        Button btnFetch2 = btn("获取模型"); btnFetch2.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)); btnFetch2.setOnClickListener(v -> fetchModelsForApi(etKey2.getText().toString().trim(), etUrl2.getText().toString().trim(), etModel2)); rowBtn2.addView(btnFetch2);
        Button btnTestApi2 = btn("测试通道"); btnTestApi2.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)); btnTestApi2.setOnClickListener(v -> testSingleApi(etKey2.getText().toString().trim(), etUrl2.getText().toString().trim(), etModel2.getText().toString().trim())); rowBtn2.addView(btnTestApi2);
        apiContentLayout.addView(rowBtn2);
        apiContentLayout.addView(lab("调用权重 2:")); etWeight2 = edit(prefs.getString("api_weight_2", "3")); apiContentLayout.addView(etWeight2);
        apiContentLayout.addView(lab("翻译方向 2:")); spinnerDir2 = new android.widget.Spinner(this); android.widget.ArrayAdapter<String> dirAdapter2 = new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_item, dirs); dirAdapter2.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); spinnerDir2.setAdapter(dirAdapter2); spinnerDir2.setSelection(prefs.getInt("api_direction_2", 0)); spinnerDir2.setEnabled(false); apiContentLayout.addView(spinnerDir2);
        apiContentLayout.addView(lab("思考模式 2:")); spinnerReasoning2 = new android.widget.Spinner(this); android.widget.ArrayAdapter<String> effAdapter2 = new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_item, efforts2); effAdapter2.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); spinnerReasoning2.setAdapter(effAdapter2); String savedEff2 = prefs.getString("reasoning_effort_2", "default"); spinnerReasoning2.setSelection("low".equals(savedEff2) ? 1 : ("medium".equals(savedEff2) ? 2 : ("high".equals(savedEff2) ? 3 : 0))); spinnerReasoning2.setEnabled(false); apiContentLayout.addView(spinnerReasoning2);

        // --- API 3 ---
        LinearLayout row3 = new LinearLayout(this); row3.setOrientation(LinearLayout.HORIZONTAL); row3.setBackground(titleBg); row3.setPadding(20, 20, 20, 20); row3.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lpRow3 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); lpRow3.setMargins(0, 40, 0, 10); row3.setLayoutParams(lpRow3);
        TextView t3 = new TextView(this); t3.setText("🟠 备用 API 3 | "); t3.setTextColor(Color.parseColor("#D97706")); t3.setTextSize(15f); t3.setTypeface(null, android.graphics.Typeface.BOLD); row3.addView(t3);
        etAlias3 = new EditText(this); etAlias3.setText(prefs.getString("api_alias_3", "")); etAlias3.setHint("点右侧解锁"); etAlias3.setHintTextColor(Color.parseColor("#80D97706")); etAlias3.setTextColor(Color.parseColor("#D97706")); etAlias3.setTextSize(14f); etAlias3.setTypeface(null, android.graphics.Typeface.BOLD); etAlias3.setBackgroundColor(Color.TRANSPARENT); etAlias3.setEnabled(false); etAlias3.setPadding(0,0,0,0);
        etAlias3.setLayoutParams(lpAlias2); row3.addView(etAlias3);
        Button btnEdit3 = new Button(this); btnEdit3.setText("🔓解锁"); btnEdit3.setTextSize(12f); btnEdit3.setBackgroundColor(Color.TRANSPARENT); btnEdit3.setPadding(0,0,0,0);
        btnEdit3.setOnClickListener(v -> { etAlias3.setEnabled(true); etAlias3.setFocusableInTouchMode(true); etAlias3.requestFocus(); spinnerDir3.setEnabled(true); spinnerReasoning3.setEnabled(true); Toast.makeText(this, "API 3 已解锁", Toast.LENGTH_SHORT).show(); btnEdit3.setText("已解"); btnEdit3.setEnabled(false); });
        row3.addView(btnEdit3); apiContentLayout.addView(row3);
        apiContentLayout.addView(lab("API Key 3:")); etKey3 = edit(prefs.getString("api_key_3", "")); apiContentLayout.addView(etKey3);
        apiContentLayout.addView(lab("API URL 3:")); etUrl3 = edit(prefs.getString("api_url_3", "")); apiContentLayout.addView(etUrl3);
        apiContentLayout.addView(lab("模型 3:")); etModel3 = edit(prefs.getString("model_3", "")); apiContentLayout.addView(etModel3);
        LinearLayout rowBtn3 = new LinearLayout(this); rowBtn3.setOrientation(LinearLayout.HORIZONTAL);
        Button btnFetch3 = btn("获取模型"); btnFetch3.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)); btnFetch3.setOnClickListener(v -> fetchModelsForApi(etKey3.getText().toString().trim(), etUrl3.getText().toString().trim(), etModel3)); rowBtn3.addView(btnFetch3);
        Button btnTestApi3 = btn("测试通道"); btnTestApi3.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)); btnTestApi3.setOnClickListener(v -> testSingleApi(etKey3.getText().toString().trim(), etUrl3.getText().toString().trim(), etModel3.getText().toString().trim())); rowBtn3.addView(btnTestApi3);
        apiContentLayout.addView(rowBtn3);
        apiContentLayout.addView(lab("调用权重 3:")); etWeight3 = edit(prefs.getString("api_weight_3", "3")); apiContentLayout.addView(etWeight3);
        apiContentLayout.addView(lab("翻译方向 3:")); spinnerDir3 = new android.widget.Spinner(this); android.widget.ArrayAdapter<String> dirAdapter3 = new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_item, dirs); dirAdapter3.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); spinnerDir3.setAdapter(dirAdapter3); spinnerDir3.setSelection(prefs.getInt("api_direction_3", 0)); spinnerDir3.setEnabled(false); apiContentLayout.addView(spinnerDir3);
        apiContentLayout.addView(lab("思考模式 3:")); spinnerReasoning3 = new android.widget.Spinner(this); android.widget.ArrayAdapter<String> effAdapter3 = new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_item, efforts2); effAdapter3.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); spinnerReasoning3.setAdapter(effAdapter3); String savedEff3 = prefs.getString("reasoning_effort_3", "default"); spinnerReasoning3.setSelection("low".equals(savedEff3) ? 1 : ("medium".equals(savedEff3) ? 2 : ("high".equals(savedEff3) ? 3 : 0))); spinnerReasoning3.setEnabled(false); apiContentLayout.addView(spinnerReasoning3);

        // --- API 4 ---
        LinearLayout row4 = new LinearLayout(this); row4.setOrientation(LinearLayout.HORIZONTAL); row4.setBackground(titleBg); row4.setPadding(20, 20, 20, 20); row4.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lpRow4 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); lpRow4.setMargins(0, 40, 0, 10); row4.setLayoutParams(lpRow4);
        TextView t4 = new TextView(this); t4.setText("🟣 备用 API 4 | "); t4.setTextColor(Color.parseColor("#7C3AED")); t4.setTextSize(15f); t4.setTypeface(null, android.graphics.Typeface.BOLD); row4.addView(t4);
        etAlias4 = new EditText(this); etAlias4.setText(prefs.getString("api_alias_4", "")); etAlias4.setHint("点右侧解锁"); etAlias4.setHintTextColor(Color.parseColor("#807C3AED")); etAlias4.setTextColor(Color.parseColor("#7C3AED")); etAlias4.setTextSize(14f); etAlias4.setTypeface(null, android.graphics.Typeface.BOLD); etAlias4.setBackgroundColor(Color.TRANSPARENT); etAlias4.setEnabled(false); etAlias4.setPadding(0,0,0,0);
        etAlias4.setLayoutParams(lpAlias2); row4.addView(etAlias4);
        Button btnEdit4 = new Button(this); btnEdit4.setText("🔓解锁"); btnEdit4.setTextSize(12f); btnEdit4.setBackgroundColor(Color.TRANSPARENT); btnEdit4.setPadding(0,0,0,0);
        btnEdit4.setOnClickListener(v -> { etAlias4.setEnabled(true); etAlias4.setFocusableInTouchMode(true); etAlias4.requestFocus(); spinnerDir4.setEnabled(true); spinnerReasoning4.setEnabled(true); Toast.makeText(this, "API 4 已解锁", Toast.LENGTH_SHORT).show(); btnEdit4.setText("已解"); btnEdit4.setEnabled(false); });
        row4.addView(btnEdit4); apiContentLayout.addView(row4);
        apiContentLayout.addView(lab("API Key 4:")); etKey4 = edit(prefs.getString("api_key_4", "")); apiContentLayout.addView(etKey4);
        apiContentLayout.addView(lab("API URL 4:")); etUrl4 = edit(prefs.getString("api_url_4", "")); apiContentLayout.addView(etUrl4);
        apiContentLayout.addView(lab("模型 4:")); etModel4 = edit(prefs.getString("model_4", "")); apiContentLayout.addView(etModel4);
        LinearLayout rowBtn4 = new LinearLayout(this); rowBtn4.setOrientation(LinearLayout.HORIZONTAL);
        Button btnFetch4 = btn("获取模型"); btnFetch4.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)); btnFetch4.setOnClickListener(v -> fetchModelsForApi(etKey4.getText().toString().trim(), etUrl4.getText().toString().trim(), etModel4)); rowBtn4.addView(btnFetch4);
        Button btnTestApi4 = btn("测试通道"); btnTestApi4.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)); btnTestApi4.setOnClickListener(v -> testSingleApi(etKey4.getText().toString().trim(), etUrl4.getText().toString().trim(), etModel4.getText().toString().trim())); rowBtn4.addView(btnTestApi4);
        apiContentLayout.addView(rowBtn4);
        apiContentLayout.addView(lab("调用权重 4:")); etWeight4 = edit(prefs.getString("api_weight_4", "3")); apiContentLayout.addView(etWeight4);
        apiContentLayout.addView(lab("翻译方向 4:")); spinnerDir4 = new android.widget.Spinner(this); android.widget.ArrayAdapter<String> dirAdapter4 = new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_item, dirs); dirAdapter4.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); spinnerDir4.setAdapter(dirAdapter4); spinnerDir4.setSelection(prefs.getInt("api_direction_4", 0)); spinnerDir4.setEnabled(false); apiContentLayout.addView(spinnerDir4);
        apiContentLayout.addView(lab("思考模式 4:")); spinnerReasoning4 = new android.widget.Spinner(this); android.widget.ArrayAdapter<String> effAdapter4 = new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_item, efforts2); effAdapter4.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); spinnerReasoning4.setAdapter(effAdapter4); String savedEff4 = prefs.getString("reasoning_effort_4", "default"); spinnerReasoning4.setSelection("low".equals(savedEff4) ? 1 : ("medium".equals(savedEff4) ? 2 : ("high".equals(savedEff4) ? 3 : 0))); spinnerReasoning4.setEnabled(false); apiContentLayout.addView(spinnerReasoning4);

        // --- API 5 ---
        LinearLayout row5 = new LinearLayout(this); row5.setOrientation(LinearLayout.HORIZONTAL); row5.setBackground(titleBg); row5.setPadding(20, 20, 20, 20); row5.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lpRow5 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); lpRow5.setMargins(0, 40, 0, 10); row5.setLayoutParams(lpRow5);
        TextView t5 = new TextView(this); t5.setText("🟤 备用 API 5 | "); t5.setTextColor(Color.parseColor("#92400E")); t5.setTextSize(15f); t5.setTypeface(null, android.graphics.Typeface.BOLD); row5.addView(t5);
        etAlias5 = new EditText(this); etAlias5.setText(prefs.getString("api_alias_5", "")); etAlias5.setHint("点右侧解锁"); etAlias5.setHintTextColor(Color.parseColor("#8092400E")); etAlias5.setTextColor(Color.parseColor("#92400E")); etAlias5.setTextSize(14f); etAlias5.setTypeface(null, android.graphics.Typeface.BOLD); etAlias5.setBackgroundColor(Color.TRANSPARENT); etAlias5.setEnabled(false); etAlias5.setPadding(0,0,0,0);
        etAlias5.setLayoutParams(lpAlias2); row5.addView(etAlias5);
        Button btnEdit5 = new Button(this); btnEdit5.setText("🔓解锁"); btnEdit5.setTextSize(12f); btnEdit5.setBackgroundColor(Color.TRANSPARENT); btnEdit5.setPadding(0,0,0,0);
        btnEdit5.setOnClickListener(v -> { etAlias5.setEnabled(true); etAlias5.setFocusableInTouchMode(true); etAlias5.requestFocus(); spinnerDir5.setEnabled(true); spinnerReasoning5.setEnabled(true); Toast.makeText(this, "API 5 已解锁", Toast.LENGTH_SHORT).show(); btnEdit5.setText("已解"); btnEdit5.setEnabled(false); });
        row5.addView(btnEdit5); apiContentLayout.addView(row5);
        apiContentLayout.addView(lab("API Key 5:")); etKey5 = edit(prefs.getString("api_key_5", "")); apiContentLayout.addView(etKey5);
        apiContentLayout.addView(lab("API URL 5:")); etUrl5 = edit(prefs.getString("api_url_5", "")); apiContentLayout.addView(etUrl5);
        apiContentLayout.addView(lab("模型 5:")); etModel5 = edit(prefs.getString("model_5", "")); apiContentLayout.addView(etModel5);
        LinearLayout rowBtn5 = new LinearLayout(this); rowBtn5.setOrientation(LinearLayout.HORIZONTAL);
        Button btnFetch5 = btn("获取模型"); btnFetch5.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)); btnFetch5.setOnClickListener(v -> fetchModelsForApi(etKey5.getText().toString().trim(), etUrl5.getText().toString().trim(), etModel5)); rowBtn5.addView(btnFetch5);
        Button btnTestApi5 = btn("测试通道"); btnTestApi5.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)); btnTestApi5.setOnClickListener(v -> testSingleApi(etKey5.getText().toString().trim(), etUrl5.getText().toString().trim(), etModel5.getText().toString().trim())); rowBtn5.addView(btnTestApi5);
        apiContentLayout.addView(rowBtn5);
        apiContentLayout.addView(lab("调用权重 5:")); etWeight5 = edit(prefs.getString("api_weight_5", "3")); apiContentLayout.addView(etWeight5);
        apiContentLayout.addView(lab("翻译方向 5:")); spinnerDir5 = new android.widget.Spinner(this); android.widget.ArrayAdapter<String> dirAdapter5 = new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_item, dirs); dirAdapter5.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); spinnerDir5.setAdapter(dirAdapter5); spinnerDir5.setSelection(prefs.getInt("api_direction_5", 0)); spinnerDir5.setEnabled(false); apiContentLayout.addView(spinnerDir5);
        apiContentLayout.addView(lab("思考模式 5:")); spinnerReasoning5 = new android.widget.Spinner(this); android.widget.ArrayAdapter<String> effAdapter5 = new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_item, efforts2); effAdapter5.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); spinnerReasoning5.setAdapter(effAdapter5); String savedEff5 = prefs.getString("reasoning_effort_5", "default"); spinnerReasoning5.setSelection("low".equals(savedEff5) ? 1 : ("medium".equals(savedEff5) ? 2 : ("high".equals(savedEff5) ? 3 : 0))); spinnerReasoning5.setEnabled(false); apiContentLayout.addView(spinnerReasoning5);

        // --- API 6 ---
        LinearLayout row6 = new LinearLayout(this); row6.setOrientation(LinearLayout.HORIZONTAL); row6.setBackground(titleBg); row6.setPadding(20, 20, 20, 20); row6.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lpRow6 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); lpRow6.setMargins(0, 40, 0, 10); row6.setLayoutParams(lpRow6);
        TextView t6 = new TextView(this); t6.setText("🔵 备用 API 6 | "); t6.setTextColor(Color.parseColor("#0284C7")); t6.setTextSize(15f); t6.setTypeface(null, android.graphics.Typeface.BOLD); row6.addView(t6);
        etAlias6 = new EditText(this); etAlias6.setText(prefs.getString("api_alias_6", "")); etAlias6.setHint("点右侧解锁"); etAlias6.setHintTextColor(Color.parseColor("#800284C7")); etAlias6.setTextColor(Color.parseColor("#0284C7")); etAlias6.setTextSize(14f); etAlias6.setTypeface(null, android.graphics.Typeface.BOLD); etAlias6.setBackgroundColor(Color.TRANSPARENT); etAlias6.setEnabled(false); etAlias6.setPadding(0,0,0,0);
        LinearLayout.LayoutParams lpAlias6 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f); etAlias6.setLayoutParams(lpAlias6); row6.addView(etAlias6);
        Button btnEdit6 = new Button(this); btnEdit6.setText("🔓解锁"); btnEdit6.setTextSize(12f); btnEdit6.setBackgroundColor(Color.TRANSPARENT); btnEdit6.setPadding(0,0,0,0);
        btnEdit6.setOnClickListener(v -> { etAlias6.setEnabled(true); etAlias6.setFocusableInTouchMode(true); etAlias6.requestFocus(); spinnerDir6.setEnabled(true); spinnerReasoning6.setEnabled(true); Toast.makeText(this, "API 6 已解锁", Toast.LENGTH_SHORT).show(); btnEdit6.setText("已解"); btnEdit6.setEnabled(false); });
        row6.addView(btnEdit6); apiContentLayout.addView(row6);
        apiContentLayout.addView(lab("API Key 6:")); etKey6 = edit(prefs.getString("api_key_6", "")); apiContentLayout.addView(etKey6);
        apiContentLayout.addView(lab("API URL 6:")); etUrl6 = edit(prefs.getString("api_url_6", "")); apiContentLayout.addView(etUrl6);
        apiContentLayout.addView(lab("模型 6:")); etModel6 = edit(prefs.getString("model_6", "")); apiContentLayout.addView(etModel6);
        LinearLayout rowBtn6 = new LinearLayout(this); rowBtn6.setOrientation(LinearLayout.HORIZONTAL);
        Button btnFetch6 = btn("获取模型"); btnFetch6.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)); btnFetch6.setOnClickListener(v -> fetchModelsForApi(etKey6.getText().toString().trim(), etUrl6.getText().toString().trim(), etModel6)); rowBtn6.addView(btnFetch6);
        Button btnTestApi6 = btn("测试通道"); btnTestApi6.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)); btnTestApi6.setOnClickListener(v -> testSingleApi(etKey6.getText().toString().trim(), etUrl6.getText().toString().trim(), etModel6.getText().toString().trim())); rowBtn6.addView(btnTestApi6);
        apiContentLayout.addView(rowBtn6);
        apiContentLayout.addView(lab("调用权重 6:")); etWeight6 = edit(prefs.getString("api_weight_6", "3")); apiContentLayout.addView(etWeight6);
        apiContentLayout.addView(lab("翻译方向 6:")); spinnerDir6 = new android.widget.Spinner(this); android.widget.ArrayAdapter<String> dirAdapter6 = new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_item, dirs); dirAdapter6.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); spinnerDir6.setAdapter(dirAdapter6); spinnerDir6.setSelection(prefs.getInt("api_direction_6", 0)); spinnerDir6.setEnabled(false); apiContentLayout.addView(spinnerDir6);
        apiContentLayout.addView(lab("思考模式 6:")); spinnerReasoning6 = new android.widget.Spinner(this); android.widget.ArrayAdapter<String> effAdapter6 = new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_item, efforts2); effAdapter6.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); spinnerReasoning6.setAdapter(effAdapter6); String savedEff6 = prefs.getString("reasoning_effort_6", "default"); spinnerReasoning6.setSelection("low".equals(savedEff6) ? 1 : ("medium".equals(savedEff6) ? 2 : ("high".equals(savedEff6) ? 3 : 0))); spinnerReasoning6.setEnabled(false); apiContentLayout.addView(spinnerReasoning6);

        // --- API 7 ---
        LinearLayout row7 = new LinearLayout(this); row7.setOrientation(LinearLayout.HORIZONTAL); row7.setBackground(titleBg); row7.setPadding(20, 20, 20, 20); row7.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lpRow7 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); lpRow7.setMargins(0, 40, 0, 10); row7.setLayoutParams(lpRow7);
        TextView t7 = new TextView(this); t7.setText("🔴 备用 API 7 | "); t7.setTextColor(Color.parseColor("#BE185D")); t7.setTextSize(15f); t7.setTypeface(null, android.graphics.Typeface.BOLD); row7.addView(t7);
        etAlias7 = new EditText(this); etAlias7.setText(prefs.getString("api_alias_7", "")); etAlias7.setHint("点右侧解锁"); etAlias7.setHintTextColor(Color.parseColor("#80BE185D")); etAlias7.setTextColor(Color.parseColor("#BE185D")); etAlias7.setTextSize(14f); etAlias7.setTypeface(null, android.graphics.Typeface.BOLD); etAlias7.setBackgroundColor(Color.TRANSPARENT); etAlias7.setEnabled(false); etAlias7.setPadding(0,0,0,0);
        LinearLayout.LayoutParams lpAlias7 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f); etAlias7.setLayoutParams(lpAlias7); row7.addView(etAlias7);
        Button btnEdit7 = new Button(this); btnEdit7.setText("🔓解锁"); btnEdit7.setTextSize(12f); btnEdit7.setBackgroundColor(Color.TRANSPARENT); btnEdit7.setPadding(0,0,0,0);
        btnEdit7.setOnClickListener(v -> { etAlias7.setEnabled(true); etAlias7.setFocusableInTouchMode(true); etAlias7.requestFocus(); spinnerDir7.setEnabled(true); spinnerReasoning7.setEnabled(true); Toast.makeText(this, "API 7 已解锁", Toast.LENGTH_SHORT).show(); btnEdit7.setText("已解"); btnEdit7.setEnabled(false); });
        row7.addView(btnEdit7); apiContentLayout.addView(row7);
        apiContentLayout.addView(lab("API Key 7:")); etKey7 = edit(prefs.getString("api_key_7", "")); apiContentLayout.addView(etKey7);
        apiContentLayout.addView(lab("API URL 7:")); etUrl7 = edit(prefs.getString("api_url_7", "")); apiContentLayout.addView(etUrl7);
        apiContentLayout.addView(lab("模型 7:")); etModel7 = edit(prefs.getString("model_7", "")); apiContentLayout.addView(etModel7);
        LinearLayout rowBtn7 = new LinearLayout(this); rowBtn7.setOrientation(LinearLayout.HORIZONTAL);
        Button btnFetch7 = btn("获取模型"); btnFetch7.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)); btnFetch7.setOnClickListener(v -> fetchModelsForApi(etKey7.getText().toString().trim(), etUrl7.getText().toString().trim(), etModel7)); rowBtn7.addView(btnFetch7);
        Button btnTestApi7 = btn("测试通道"); btnTestApi7.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)); btnTestApi7.setOnClickListener(v -> testSingleApi(etKey7.getText().toString().trim(), etUrl7.getText().toString().trim(), etModel7.getText().toString().trim())); rowBtn7.addView(btnTestApi7);
        apiContentLayout.addView(rowBtn7);
        apiContentLayout.addView(lab("调用权重 7:")); etWeight7 = edit(prefs.getString("api_weight_7", "3")); apiContentLayout.addView(etWeight7);
        apiContentLayout.addView(lab("翻译方向 7:")); spinnerDir7 = new android.widget.Spinner(this); android.widget.ArrayAdapter<String> dirAdapter7 = new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_item, dirs); dirAdapter7.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); spinnerDir7.setAdapter(dirAdapter7); spinnerDir7.setSelection(prefs.getInt("api_direction_7", 0)); spinnerDir7.setEnabled(false); apiContentLayout.addView(spinnerDir7);
        apiContentLayout.addView(lab("思考模式 7:")); spinnerReasoning7 = new android.widget.Spinner(this); android.widget.ArrayAdapter<String> effAdapter7 = new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_item, efforts2); effAdapter7.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); spinnerReasoning7.setAdapter(effAdapter7); String savedEff7 = prefs.getString("reasoning_effort_7", "default"); spinnerReasoning7.setSelection("low".equals(savedEff7) ? 1 : ("medium".equals(savedEff7) ? 2 : ("high".equals(savedEff7) ? 3 : 0))); spinnerReasoning7.setEnabled(false); apiContentLayout.addView(spinnerReasoning7);

        // --- API 8 ---
        LinearLayout row8 = new LinearLayout(this); row8.setOrientation(LinearLayout.HORIZONTAL); row8.setBackground(titleBg); row8.setPadding(20, 20, 20, 20); row8.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lpRow8 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); lpRow8.setMargins(0, 40, 0, 10); row8.setLayoutParams(lpRow8);
        TextView t8 = new TextView(this); t8.setText("⚫ 备用 API 8 | "); t8.setTextColor(Color.parseColor("#475569")); t8.setTextSize(15f); t8.setTypeface(null, android.graphics.Typeface.BOLD); row8.addView(t8);
        etAlias8 = new EditText(this); etAlias8.setText(prefs.getString("api_alias_8", "")); etAlias8.setHint("点右侧解锁"); etAlias8.setHintTextColor(Color.parseColor("#80475569")); etAlias8.setTextColor(Color.parseColor("#475569")); etAlias8.setTextSize(14f); etAlias8.setTypeface(null, android.graphics.Typeface.BOLD); etAlias8.setBackgroundColor(Color.TRANSPARENT); etAlias8.setEnabled(false); etAlias8.setPadding(0,0,0,0);
        LinearLayout.LayoutParams lpAlias8 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f); etAlias8.setLayoutParams(lpAlias8); row8.addView(etAlias8);
        Button btnEdit8 = new Button(this); btnEdit8.setText("🔓解锁"); btnEdit8.setTextSize(12f); btnEdit8.setBackgroundColor(Color.TRANSPARENT); btnEdit8.setPadding(0,0,0,0);
        btnEdit8.setOnClickListener(v -> { etAlias8.setEnabled(true); etAlias8.setFocusableInTouchMode(true); etAlias8.requestFocus(); spinnerDir8.setEnabled(true); spinnerReasoning8.setEnabled(true); Toast.makeText(this, "API 8 已解锁", Toast.LENGTH_SHORT).show(); btnEdit8.setText("已解"); btnEdit8.setEnabled(false); });
        row8.addView(btnEdit8); apiContentLayout.addView(row8);
        apiContentLayout.addView(lab("API Key 8:")); etKey8 = edit(prefs.getString("api_key_8", "")); apiContentLayout.addView(etKey8);
        apiContentLayout.addView(lab("API URL 8:")); etUrl8 = edit(prefs.getString("api_url_8", "")); apiContentLayout.addView(etUrl8);
        apiContentLayout.addView(lab("模型 8:")); etModel8 = edit(prefs.getString("model_8", "")); apiContentLayout.addView(etModel8);
        LinearLayout rowBtn8 = new LinearLayout(this); rowBtn8.setOrientation(LinearLayout.HORIZONTAL);
        Button btnFetch8 = btn("获取模型"); btnFetch8.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)); btnFetch8.setOnClickListener(v -> fetchModelsForApi(etKey8.getText().toString().trim(), etUrl8.getText().toString().trim(), etModel8)); rowBtn8.addView(btnFetch8);
        Button btnTestApi8 = btn("测试通道"); btnTestApi8.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)); btnTestApi8.setOnClickListener(v -> testSingleApi(etKey8.getText().toString().trim(), etUrl8.getText().toString().trim(), etModel8.getText().toString().trim())); rowBtn8.addView(btnTestApi8);
        apiContentLayout.addView(rowBtn8);
        apiContentLayout.addView(lab("调用权重 8:")); etWeight8 = edit(prefs.getString("api_weight_8", "3")); apiContentLayout.addView(etWeight8);
        apiContentLayout.addView(lab("翻译方向 8:")); spinnerDir8 = new android.widget.Spinner(this); android.widget.ArrayAdapter<String> dirAdapter8 = new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_item, dirs); dirAdapter8.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); spinnerDir8.setAdapter(dirAdapter8); spinnerDir8.setSelection(prefs.getInt("api_direction_8", 0)); spinnerDir8.setEnabled(false); apiContentLayout.addView(spinnerDir8);
        apiContentLayout.addView(lab("思考模式 8:")); spinnerReasoning8 = new android.widget.Spinner(this); android.widget.ArrayAdapter<String> effAdapter8 = new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_item, efforts2); effAdapter8.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); spinnerReasoning8.setAdapter(effAdapter8); String savedEff8 = prefs.getString("reasoning_effort_8", "default"); spinnerReasoning8.setSelection("low".equals(savedEff8) ? 1 : ("medium".equals(savedEff8) ? 2 : ("high".equals(savedEff8) ? 3 : 0))); spinnerReasoning8.setEnabled(false); apiContentLayout.addView(spinnerReasoning8);

        ll.addView(apiContentLayout);
        // ================= 隐身开关 =================
LinearLayout stealthHeaderLayout = createHeaderLayout();
TextView stealthHeaderTitle = new TextView(this);
boolean isStealthExpanded = prefs.getBoolean("stealth_expanded", false);
styleHeaderTitle(stealthHeaderTitle, isStealthExpanded ? "▼ 🕵️ 隐身与反检测 (点击折叠)" : "▶ 🕵️ 隐身与反检测 (点击展开)");
stealthHeaderLayout.addView(stealthHeaderTitle);
ll.addView(stealthHeaderLayout);

LinearLayout stealthContentLayout = new LinearLayout(this);
stealthContentLayout.setOrientation(LinearLayout.VERTICAL);
stealthContentLayout.setVisibility(isStealthExpanded ? View.VISIBLE : View.GONE);
stealthContentLayout.setPadding(20, 10, 0, 10);

// 隐藏已读
LinearLayout rowHideRead = new LinearLayout(this);
rowHideRead.setOrientation(LinearLayout.HORIZONTAL);
rowHideRead.setGravity(android.view.Gravity.CENTER_VERTICAL);
rowHideRead.setPadding(0, 10, 0, 10);
TextView lblHideRead = new TextView(this);
lblHideRead.setText("隐藏已读状态（对方看不到你已读）");
lblHideRead.setTextSize(14f);
lblHideRead.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
rowHideRead.addView(lblHideRead);
swHideRead = new android.widget.Switch(this);
swHideRead.setChecked(prefs.getBoolean("stealth_hide_read", true));
rowHideRead.addView(swHideRead);
stealthContentLayout.addView(rowHideRead);

// 隐藏正在输入
LinearLayout rowHideTyping = new LinearLayout(this);
rowHideTyping.setOrientation(LinearLayout.HORIZONTAL);
rowHideTyping.setGravity(android.view.Gravity.CENTER_VERTICAL);
rowHideTyping.setPadding(0, 10, 0, 10);
TextView lblHideTyping = new TextView(this);
lblHideTyping.setText("隐藏正在输入（对方看不到你打字）");
lblHideTyping.setTextSize(14f);
lblHideTyping.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
rowHideTyping.addView(lblHideTyping);
swHideTyping = new android.widget.Switch(this);
swHideTyping.setChecked(prefs.getBoolean("stealth_hide_typing", true));
rowHideTyping.addView(swHideTyping);
stealthContentLayout.addView(rowHideTyping);

ll.addView(stealthContentLayout);
setupToggle(stealthHeaderLayout, stealthHeaderTitle, stealthContentLayout, "🕵️ 隐身与反检测", "stealth_expanded");
        setupToggle(apiHeaderLayout, apiHeaderTitle, apiContentLayout, "🔄 多API智能密钥配置", "api_expanded");

        // ================= 折叠区 2：语言专属指令 =================
        LinearLayout promptHeaderLayout = createHeaderLayout();
        promptHeaderTitle = new TextView(this);
        boolean isPromptExpanded = prefs.getBoolean("prompt_expanded", false);
        styleHeaderTitle(promptHeaderTitle, isPromptExpanded ? "▼ 🌐 语言专属指令设置 (点击折叠)" : "▶ 🌐 语言专属指令设置 (点击展开)");
        promptHeaderLayout.addView(promptHeaderTitle);
        ll.addView(promptHeaderLayout);

        promptContentLayout = new LinearLayout(this);
        promptContentLayout.setOrientation(LinearLayout.VERTICAL);
        promptContentLayout.setVisibility(isPromptExpanded ? View.VISIBLE : View.GONE);
        promptContentLayout.setPadding(20, 10, 0, 10);

        promptContentLayout.addView(lab("接收翻译 Prompt (外语→中文):"));
        etPromptZH = bigEdit(prefs.getString("prompt_zh", ""));
        promptContentLayout.addView(etPromptZH);

        promptContentLayout.addView(lab("英语 Prompt (发送):"));
        etPromptEN = bigEdit(prefs.getString("prompt_en", ""));
        promptContentLayout.addView(etPromptEN);

        promptContentLayout.addView(lab("俄语 Prompt (发送):"));
        etPromptRU = bigEdit(prefs.getString("prompt_ru", ""));
        promptContentLayout.addView(etPromptRU);

        promptContentLayout.addView(lab("乌克兰语 Prompt (发送):"));
        etPromptUK = bigEdit(prefs.getString("prompt_uk", ""));
        promptContentLayout.addView(etPromptUK);

        promptContentLayout.addView(lab("韩语 Prompt (发送):"));
        etPromptKO = bigEdit(prefs.getString("prompt_ko", ""));
        promptContentLayout.addView(etPromptKO);

        promptContentLayout.addView(lab("西班牙语 Prompt (发送):"));
        etPromptES = bigEdit(prefs.getString("prompt_es", ""));
        promptContentLayout.addView(etPromptES);

        promptContentLayout.addView(div());

        promptContentLayout.addView(lab("阿拉伯语 Prompt (发送):"));
        etPromptAR = bigEdit(prefs.getString("prompt_ar", ""));
        promptContentLayout.addView(etPromptAR);

        promptContentLayout.addView(lab("葡萄牙语 Prompt (发送):"));
        etPromptPT = bigEdit(prefs.getString("prompt_pt", ""));
        promptContentLayout.addView(etPromptPT);

        promptContentLayout.addView(lab("法语 Prompt (发送):"));
        etPromptFR = bigEdit(prefs.getString("prompt_fr", ""));
        promptContentLayout.addView(etPromptFR);

        promptContentLayout.addView(lab("德语 Prompt (发送):"));
        etPromptDE = bigEdit(prefs.getString("prompt_de", ""));
        promptContentLayout.addView(etPromptDE);

        promptContentLayout.addView(lab("意大利语 Prompt (发送):"));
        etPromptIT = bigEdit(prefs.getString("prompt_it", ""));
        promptContentLayout.addView(etPromptIT);

        promptContentLayout.addView(lab("土耳其语 Prompt (发送):"));
        etPromptTR = bigEdit(prefs.getString("prompt_tr", ""));
        promptContentLayout.addView(etPromptTR);

        promptContentLayout.addView(lab("荷兰语 Prompt (发送):"));
        etPromptNL = bigEdit(prefs.getString("prompt_nl", ""));
        promptContentLayout.addView(etPromptNL);

        promptContentLayout.addView(lab("波兰语 Prompt (发送):"));
        etPromptPL = bigEdit(prefs.getString("prompt_pl", ""));
        promptContentLayout.addView(etPromptPL);

        promptContentLayout.addView(lab("哈萨克语 Prompt (发送):"));
        etPromptKK = bigEdit(prefs.getString("prompt_kk", ""));
        promptContentLayout.addView(etPromptKK);

        promptContentLayout.addView(lab("捷克语 Prompt (发送):"));
        etPromptCS = bigEdit(prefs.getString("prompt_cs", ""));
        promptContentLayout.addView(etPromptCS);

        ll.addView(promptContentLayout);
        setupToggle(promptHeaderLayout, promptHeaderTitle, promptContentLayout, "🌐 语言专属指令设置", "prompt_expanded");

        // ================= 折叠区 3：弹窗快捷选项 =================
        LinearLayout quickHeaderLayout = createHeaderLayout();
        TextView quickHeaderTitle = new TextView(this);
        boolean isQuickExpanded = prefs.getBoolean("quick_expanded", false);
        styleHeaderTitle(quickHeaderTitle, isQuickExpanded ? "▼ ⚡ 弹窗快捷选项 (点击折叠)" : "▶ ⚡ 弹窗快捷选项 (点击展开)");
        quickHeaderLayout.addView(quickHeaderTitle);
        ll.addView(quickHeaderLayout);

        LinearLayout quickContentLayout = new LinearLayout(this);
        quickContentLayout.setOrientation(LinearLayout.VERTICAL);
        quickContentLayout.setVisibility(isQuickExpanded ? View.VISIBLE : View.GONE);
        quickContentLayout.setPadding(20, 10, 0, 10);

        // 选项1
        quickContentLayout.addView(lab("【快捷选项 1】"));
        String[] q1Arr = prefs.getString("quick_1", "更随意|更随意").split("\\|", 2);
        etQuick1Tag = edit(q1Arr.length > 0 ? q1Arr[0] : "更随意"); etQuick1Tag.setHint("这里填按钮显示的名字 (如: 更随意)");
        etQuick1Content = bigEdit(q1Arr.length > 1 ? q1Arr[1] : (q1Arr.length > 0 ? q1Arr[0] : "")); etQuick1Content.setHint("这里填对AI的具体指令");
        quickContentLayout.addView(etQuick1Tag); quickContentLayout.addView(etQuick1Content);

        // 选项2
        quickContentLayout.addView(lab("【快捷选项 2】"));
        String[] q2Arr = prefs.getString("quick_2", "更正式|更正式").split("\\|", 2);
        etQuick2Tag = edit(q2Arr.length > 0 ? q2Arr[0] : "更正式"); etQuick2Tag.setHint("这里填按钮显示的名字");
        etQuick2Content = bigEdit(q2Arr.length > 1 ? q2Arr[1] : (q2Arr.length > 0 ? q2Arr[0] : "")); etQuick2Content.setHint("这里填对AI的具体指令");
        quickContentLayout.addView(etQuick2Tag); quickContentLayout.addView(etQuick2Content);

        // 选项3
        quickContentLayout.addView(lab("【快捷选项 3】"));
        String[] q3Arr = prefs.getString("quick_3", "更简洁|更简洁").split("\\|", 2);
        etQuick3Tag = edit(q3Arr.length > 0 ? q3Arr[0] : "更简洁"); etQuick3Tag.setHint("这里填按钮显示的名字");
        etQuick3Content = bigEdit(q3Arr.length > 1 ? q3Arr[1] : (q3Arr.length > 0 ? q3Arr[0] : "")); etQuick3Content.setHint("这里填对AI的具体指令");
        quickContentLayout.addView(etQuick3Tag); quickContentLayout.addView(etQuick3Content);

        // 选项4
        quickContentLayout.addView(lab("【快捷选项 4】"));
        String[] q4Arr = prefs.getString("quick_4", "更像母语者|更像母语者").split("\\|", 2);
        etQuick4Tag = edit(q4Arr.length > 0 ? q4Arr[0] : "更像母语者"); etQuick4Tag.setHint("这里填按钮显示的名字");
        etQuick4Content = bigEdit(q4Arr.length > 1 ? q4Arr[1] : (q4Arr.length > 0 ? q4Arr[0] : "")); etQuick4Content.setHint("这里填对AI的具体指令");
        quickContentLayout.addView(etQuick4Tag); quickContentLayout.addView(etQuick4Content);

        // 选项5：核心防呆锁死，用户只能编辑指令内容
        quickContentLayout.addView(lab("第5个【⚠️ 火力全开】(名字已锁死，永久一次性，只需输入指令)："));
        String savedQ5 = prefs.getString("quick_5", "⚠️ 火力全开|因为是熟人。如果当前中文中含有毒舌、粗鲁、调情、性词汇中的一种或多种，允许本次照常翻译，无需客气。用最传神的方式，翻译成对方语言中最地道、同等杀伤力的词汇。");
        String q5Content = savedQ5.contains("|") ? savedQ5.substring(savedQ5.indexOf("|") + 1) : savedQ5;
        etQuick5 = bigEdit(q5Content);
        quickContentLayout.addView(etQuick5);

        ll.addView(quickContentLayout);
        setupToggle(quickHeaderLayout, quickHeaderTitle, quickContentLayout, "⚡ 弹窗快捷选项", "quick_expanded");

        ll.addView(div());

        btnSave = btn("保存全部配置");
        btnSave.setOnClickListener(v -> saveAll());
        ll.addView(btnSave);

        btnTest = btn("一键测试大盘全链路 (测试底层的切换器)");
        btnTest.setOnClickListener(v -> testTranslate());
        ll.addView(btnTest);

        sv.addView(ll);
        setContentView(sv);
    }

    // ================= 辅助 UI 方法 =================

    private LinearLayout createHeaderLayout() {
        LinearLayout layout = new LinearLayout(this);
        layout.setBackgroundColor(Color.parseColor("#E9ECEF"));
        layout.setPadding(30, 30, 30, 30);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 20, 0, 10);
        layout.setLayoutParams(lp);
        return layout;
    }

    private void styleHeaderTitle(TextView tv, String text) {
        tv.setText(text);
        tv.setTextSize(15f);
        tv.setTextColor(Color.parseColor("#0B5ED7"));
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
    }

    private void setupToggle(View headerLayout, TextView headerText, View contentLayout, String title, String prefKey) {
        headerLayout.setOnClickListener(v -> {
            if (contentLayout.getVisibility() == View.GONE) {
                contentLayout.setVisibility(View.VISIBLE);
                headerText.setText("▼ " + title + " (点击折叠)");
                prefs.edit().putBoolean(prefKey, true).apply(); 
            } else {
                contentLayout.setVisibility(View.GONE);
                headerText.setText("▶ " + title + " (点击展开)");
                prefs.edit().putBoolean(prefKey, false).apply(); 
            }
        });
    }

    private TextView lab(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setPadding(0, 20, 0, 5);
        return tv;
    }

    private TextView tip(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(13f);
        tv.setPadding(0, 0, 0, 20);
        return tv;
    }

    private EditText edit(String text) {
        EditText et = new EditText(this);
        et.setText(text);
        return et;
    }

    private EditText bigEdit(String text) {
        EditText et = new EditText(this);
        et.setText(text);
        et.setMinLines(3);
        et.setMaxLines(8);
        et.setVerticalScrollBarEnabled(true);
        return et;
    }

    private Button btn(String text) {
        Button b = new Button(this);
        b.setText(text);
        return b;
    }

    private View div() {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 2));
        v.setBackgroundColor(Color.parseColor("#33000000"));
        v.setPadding(0, 20, 0, 20);
        return v;
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private void jumpToLanguage(String q) {
        if (q == null || q.trim().isEmpty()) {
            toast("请先输入语言名");
            return;
        }

        String s = q.trim().toLowerCase();

        if (promptContentLayout != null && promptContentLayout.getVisibility() == View.GONE) {
            promptContentLayout.setVisibility(View.VISIBLE);
            if (promptHeaderTitle != null) promptHeaderTitle.setText("▼ 🌐 语言专属指令设置 (点击折叠)");
            prefs.edit().putBoolean("prompt_expanded", true).apply(); 
        }

        if (s.contains("接收") || s.contains("中文")) { scrollToView(etPromptZH); return; }
        if (s.contains("英语") || s.contains("英文") || s.contains("en")) { scrollToView(etPromptEN); return; }
        if (s.contains("俄语") || s.contains("俄罗斯") || s.contains("ru")) { scrollToView(etPromptRU); return; }
        if (s.contains("乌克兰") || s.contains("uk")) { scrollToView(etPromptUK); return; }
        if (s.contains("韩语") || s.contains("韩国") || s.contains("ko")) { scrollToView(etPromptKO); return; }
        if (s.contains("西班牙") || s.contains("es")) { scrollToView(etPromptES); return; }
        if (s.contains("阿拉伯") || s.contains("ar")) { scrollToView(etPromptAR); return; }
        if (s.contains("葡萄牙") || s.contains("pt")) { scrollToView(etPromptPT); return; }
        if (s.contains("法语") || s.contains("法国") || s.contains("fr")) { scrollToView(etPromptFR); return; }
        if (s.contains("德语") || s.contains("德国") || s.contains("de")) { scrollToView(etPromptDE); return; }
        if (s.contains("意大利") || s.contains("it")) { scrollToView(etPromptIT); return; }
        if (s.contains("土耳其") || s.contains("tr")) { scrollToView(etPromptTR); return; }
        if (s.contains("荷兰") || s.contains("nl")) { scrollToView(etPromptNL); return; }
        if (s.contains("波兰") || s.contains("pl")) { scrollToView(etPromptPL); return; }
        if (s.contains("哈萨克") || s.contains("kk")) { scrollToView(etPromptKK); return; }
        if (s.contains("捷克") || s.contains("cs")) { scrollToView(etPromptCS); return; }

        toast("没找到匹配语言");
    }

    private void scrollToView(View target) {
        if (target == null || settingsScroll == null) return;
        target.post(() -> {
            int y = target.getTop();
            settingsScroll.smoothScrollTo(0, Math.max(0, y - 40));
        });
    }

    private String runRoot(String cmd) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
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
    
    private void fetchModelsForApi(String keyStr, String urlStr, EditText targetModelEdit) {
        if (keyStr.isEmpty()) {
            toast("请先填写该 API 的 Key");
            return;
        }
        String baseUrl = urlStr.isEmpty() ? "https://api.openai.com/v1/chat/completions" : urlStr;
        new Thread(() -> {
            try {
                List<String> models = autoFetchModels(keyStr, baseUrl);
                runOnUiThread(() -> {
                    if (models.isEmpty()) {
                        toast("该 API 获取模型列表失败，请手动输入模型名");
                    } else {
                        showModelPickerForApi(models, targetModelEdit);
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> toast("获取失败: " + e.getMessage()));
            }
        }).start();
    }

    private void showModelPickerForApi(List<String> models, EditText targetEdit) {
        String[] items = models.toArray(new String[0]);
        new AlertDialog.Builder(this)
            .setTitle("选择模型")
            .setItems(items, (d, w) -> {
                targetEdit.setText(items[w]);
                toast("已选择: " + items[w]);
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void fetchModels() {
        String key = etKey.getText().toString().trim();
        if (key.isEmpty()) {
            toast("先填 Key");
            return;
        }
        btnFetch.setEnabled(false);
        btnFetch.setText("获取中...");
        String baseUrl = etUrl.getText().toString().trim();

        new Thread(() -> {
            try {
                List<String> models = autoFetchModels(key, baseUrl);
                runOnUiThread(() -> {
                    btnFetch.setEnabled(true);
                    btnFetch.setText("获取模型列表");
                    if (models.isEmpty()) {
                        new AlertDialog.Builder(SettingsActivity.this)
                                .setTitle("获取失败")
                                .setMessage("自动尝试了多种地址均无法获取模型列表。\n请检查 API Key 和 URL 是否正确，或手动输入模型名。")
                                .setPositiveButton("知道了", null)
                                .show();
                    } else {
                        showModelPicker(models);
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    btnFetch.setEnabled(true);
                    btnFetch.setText("获取模型列表");
                    new AlertDialog.Builder(SettingsActivity.this)
                            .setTitle("获取失败")
                            .setMessage("错误: " + e.getMessage() + "\n\n你可以手动输入模型名。")
                            .setPositiveButton("手动输入", (dialog, which) -> {
                                etModel.requestFocus();
                            })
                            .setNegativeButton("取消", null)
                            .show();
                });
            }
        }).start();
    }

    private List<String> autoFetchModels(String key, String baseUrl) throws Exception {
        String base = baseUrl;
        if (base.endsWith("/chat/completions")) {
            base = base.substring(0, base.length() - "/chat/completions".length());
        }
        if (base.endsWith("/v1")) {
            base = base.substring(0, base.length() - 3);
        } else if (base.endsWith("/v1/")) {
            base = base.substring(0, base.length() - 4);
        }
        if (!base.endsWith("/")) base += "/";

        String[] urlsToTry = {
                base + "v1/models",
                base + "models",
                base + "api/models"
        };

        List<String> lastErrors = new ArrayList<>();
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();

        for (String url : urlsToTry) {
            try {
                Request req = new Request.Builder()
                        .url(url)
                        .header("Authorization", "Bearer " + key)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                        .get()
                        .build();

                try (Response resp = client.newCall(req).execute()) {
                    if (resp.isSuccessful()) {
                        String s = resp.body().string();
                        JSONObject json = new JSONObject(s);
                        JSONArray data = json.getJSONArray("data");
                        List<String> models = new ArrayList<>();
                        for (int i = 0; i < data.length(); i++) {
                            models.add(data.getJSONObject(i).getString("id"));
                        }
                        return models;
                    } else {
                        lastErrors.add(url + " -> HTTP " + resp.code());
                    }
                }
            } catch (Exception e) {
                lastErrors.add(url + " -> " + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }

        throw new Exception("尝试了以下地址均失败:\n" + String.join("\n", lastErrors));
    }

    private void showModelPicker(List<String> models) {
        String[] items = models.toArray(new String[0]);
        boolean[] checked = new boolean[items.length];

        String savedModels = prefs.getString("model_list", "");
        if (!savedModels.isEmpty()) {
            String[] savedArr = savedModels.split(",");
            for (int i = 0; i < items.length; i++) {
                for (String saved : savedArr) {
                    if (items[i].equals(saved.trim())) {
                        checked[i] = true;
                        break;
                    }
                }
            }
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("选择模型（最多4个）");

        builder.setMultiChoiceItems(items, checked, (dialog, which, isChecked) -> {
            checked[which] = isChecked;
        });

        builder.setPositiveButton("确定", (dialog, which) -> {
            List<String> selected = new ArrayList<>();
            for (int i = 0; i < items.length; i++) {
                if (checked[i]) {
                    selected.add(items[i]);
                }
            }

            if (selected.isEmpty()) {
                toast("请至少选择一个模型");
                return;
            }

            if (selected.size() > 4) {
                toast("最多只能选择4个模型");
                return;
            }

            String selectedStr = String.join(",", selected);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString("model_list", selectedStr);
            editor.putString("model", selected.get(0));
            editor.apply();

            etModel.setText(selected.get(0));

            StringBuilder sb = new StringBuilder("已选择：");
            for (String s : selected) {
                sb.append("\n• ").append(s);
            }
            toast(sb.toString());
        });

        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void saveAll() {
        btnSave.setEnabled(false);
        btnSave.setText("保存中...");

        String key = etKey.getText().toString().trim();
        String url = etUrl.getText().toString().trim();
        String mdl = etModel.getText().toString().trim();

        String zh = etPromptZH.getText().toString().trim();
        String en = etPromptEN.getText().toString().trim();
        String ru = etPromptRU.getText().toString().trim();
        String uk = etPromptUK.getText().toString().trim();
        String ko = etPromptKO.getText().toString().trim();
        String es = etPromptES.getText().toString().trim();

        String ar = etPromptAR.getText().toString().trim();
        String pt = etPromptPT.getText().toString().trim();
        String fr = etPromptFR.getText().toString().trim();
        String de = etPromptDE.getText().toString().trim();
        String it = etPromptIT.getText().toString().trim();
        String tr = etPromptTR.getText().toString().trim();
        String nl = etPromptNL.getText().toString().trim();
        String pl = etPromptPL.getText().toString().trim();
        String kk = etPromptKK.getText().toString().trim();
        String cs = etPromptCS.getText().toString().trim();

        String tempStr = etTemperature.getText().toString().trim();
        String maxChatStr = etMaxChat.getText().toString().trim();
        if (maxChatStr.isEmpty()) maxChatStr = "30";
        if (tempStr.isEmpty()) tempStr = "0.7";
        try { Double.parseDouble(tempStr); } catch (NumberFormatException e) { tempStr = "0.7"; }

        String maxTokensStr = etMaxTokens.getText().toString().trim();
        String bannedStr = etBannedWords.getText().toString().trim();
        if (maxTokensStr.isEmpty()) maxTokensStr = "8000";
        try { Integer.parseInt(maxTokensStr); } catch (NumberFormatException e) { maxTokensStr = "8000"; }

        int selectedPos = spinnerReasoning.getSelectedItemPosition();
        String effortStr = "default";
        if (selectedPos == 1) effortStr = "low";
        else if (selectedPos == 2) effortStr = "medium";
        else if (selectedPos == 3) effortStr = "high";
        
        String q1 = etQuick1Tag.getText().toString().trim() + "|" + etQuick1Content.getText().toString().trim();
        String q2 = etQuick2Tag.getText().toString().trim() + "|" + etQuick2Content.getText().toString().trim();
        String q3 = etQuick3Tag.getText().toString().trim() + "|" + etQuick3Content.getText().toString().trim();
        String q4 = etQuick4Tag.getText().toString().trim() + "|" + etQuick4Content.getText().toString().trim();
        String q5 = "⚠️ 火力全开|" + etQuick5.getText().toString().trim();

        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("reasoning_effort", effortStr);
        editor.putString("api_key", key);
        editor.putString("api_url", url);
        editor.putString("api_weight", etWeight1.getText().toString().trim());
        editor.putInt("api_direction", spinnerDir1.getSelectedItemPosition());
        editor.putString("model", mdl);
        editor.putString("temperature", tempStr);
        editor.putString("max_chat_messages", maxChatStr);
        String liveContextMaxStr = etLiveContextMax.getText().toString().trim();
        if (liveContextMaxStr.isEmpty()) liveContextMaxStr = "30";
        editor.putString("live_context_max", liveContextMaxStr);
        editor.putBoolean("show_api_switch_hint", cbShowApiSwitchHint.isChecked());
        editor.putString("max_tokens", maxTokensStr);
        editor.putString("banned_words", bannedStr);
        editor.putString("prompt_zh", zh);
        editor.putString("prompt_en", en);
        editor.putString("prompt_ru", ru);
        editor.putString("prompt_uk", uk);
        editor.putString("prompt_ko", ko);
        editor.putString("prompt_es", es);
        editor.putString("prompt_ar", ar);
        editor.putString("prompt_pt", pt);
        editor.putString("prompt_fr", fr);
        editor.putString("prompt_de", de);
        editor.putString("prompt_it", it);
        editor.putString("prompt_tr", tr);
        editor.putString("prompt_nl", nl);
        editor.putString("prompt_pl", pl);
        editor.putString("prompt_kk", kk);
        editor.putString("prompt_cs", cs);
        editor.putString("quick_1", q1);
        editor.putString("quick_2", q2);
        editor.putString("quick_3", q3);
        editor.putString("quick_4", q4);
        
        editor.putString("api_alias_2", etAlias2.getText().toString().trim());
        editor.putString("api_key_2", etKey2.getText().toString().trim());
        editor.putString("api_url_2", etUrl2.getText().toString().trim());
        editor.putString("model_2", etModel2.getText().toString().trim());
        editor.putString("api_weight_2", etWeight2.getText().toString().trim());
        editor.putInt("api_direction_2", spinnerDir2.getSelectedItemPosition());
        
        editor.putString("api_alias_3", etAlias3.getText().toString().trim());
        editor.putString("api_key_3", etKey3.getText().toString().trim());
        editor.putString("api_url_3", etUrl3.getText().toString().trim());
        editor.putString("model_3", etModel3.getText().toString().trim());
        editor.putString("api_weight_3", etWeight3.getText().toString().trim());
        editor.putInt("api_direction_3", spinnerDir3.getSelectedItemPosition());
        
        editor.putString("api_alias_4", etAlias4.getText().toString().trim());
        editor.putString("api_key_4", etKey4.getText().toString().trim());
        editor.putString("api_url_4", etUrl4.getText().toString().trim());
        editor.putString("model_4", etModel4.getText().toString().trim());
        editor.putString("api_weight_4", etWeight4.getText().toString().trim());
        editor.putInt("api_direction_4", spinnerDir4.getSelectedItemPosition());
        
        editor.putString("api_alias_5", etAlias5.getText().toString().trim());
        editor.putString("api_key_5", etKey5.getText().toString().trim());
        editor.putString("api_url_5", etUrl5.getText().toString().trim());
        editor.putString("model_5", etModel5.getText().toString().trim());
        editor.putString("api_weight_5", etWeight5.getText().toString().trim());
        editor.putInt("api_direction_5", spinnerDir5.getSelectedItemPosition());

        editor.putString("api_alias_6", etAlias6.getText().toString().trim());
        editor.putString("api_key_6", etKey6.getText().toString().trim());
        editor.putString("api_url_6", etUrl6.getText().toString().trim());
        editor.putString("model_6", etModel6.getText().toString().trim());
        editor.putString("api_weight_6", etWeight6.getText().toString().trim());
        editor.putInt("api_direction_6", spinnerDir6.getSelectedItemPosition());

        editor.putString("api_alias_7", etAlias7.getText().toString().trim());
        editor.putString("api_key_7", etKey7.getText().toString().trim());
        editor.putString("api_url_7", etUrl7.getText().toString().trim());
        editor.putString("model_7", etModel7.getText().toString().trim());
        editor.putString("api_weight_7", etWeight7.getText().toString().trim());
        editor.putInt("api_direction_7", spinnerDir7.getSelectedItemPosition());

        editor.putString("api_alias_8", etAlias8.getText().toString().trim());
        editor.putString("api_key_8", etKey8.getText().toString().trim());
        editor.putString("api_url_8", etUrl8.getText().toString().trim());
        editor.putString("model_8", etModel8.getText().toString().trim());
        editor.putString("api_weight_8", etWeight8.getText().toString().trim());
        editor.putInt("api_direction_8", spinnerDir8.getSelectedItemPosition());
        
        String[] effortValues = {"default", "low", "medium", "high"};
        editor.putString("reasoning_effort_2", effortValues[spinnerReasoning2.getSelectedItemPosition()]);
        editor.putString("reasoning_effort_3", effortValues[spinnerReasoning3.getSelectedItemPosition()]);
        editor.putString("reasoning_effort_4", effortValues[spinnerReasoning4.getSelectedItemPosition()]);
        editor.putString("reasoning_effort_5", effortValues[spinnerReasoning5.getSelectedItemPosition()]);
        editor.putString("reasoning_effort_6", effortValues[spinnerReasoning6.getSelectedItemPosition()]);
        editor.putString("reasoning_effort_7", effortValues[spinnerReasoning7.getSelectedItemPosition()]);
        editor.putString("reasoning_effort_8", effortValues[spinnerReasoning8.getSelectedItemPosition()]);
        editor.putString("quick_5", q5);
        editor.putBoolean("stealth_hide_read", swHideRead.isChecked());
editor.putBoolean("stealth_hide_typing", swHideTyping.isChecked());
        editor.apply();

        final String finalTempStr = tempStr;
        final String finalMaxTokensStr = maxTokensStr;
        final String finalEffortStr = effortStr;
        final String finalMaxChatStr = maxChatStr;
        final String finalLiveContextMaxStr = liveContextMaxStr;
        final boolean finalShowApiSwitchHint = cbShowApiSwitchHint.isChecked();
        final String finalBannedStr = bannedStr;
        final String fq1 = q1, fq2 = q2, fq3 = q3, fq4 = q4, fq5 = q5;
        
        new Thread(() -> {
            try {
                String modelList = prefs.getString("model_list", "");
                String cfg = "cat > /data/local/tmp/htai_config.txt << 'EOF'\n"
                        + "api_key=" + key + "\n"
                        + "api_url=" + url + "\n"
                        + "model=" + mdl + "\n"
                        + "model_list=" + modelList + "\n"
                        + "api_weight=" + prefs.getString("api_weight", "3") + "\n"
                        + "api_direction=" + prefs.getInt("api_direction", 0) + "\n"
                        + "temperature=" + finalTempStr + "\n"
                        + "max_chat_messages=" + finalMaxChatStr + "\n"
                        + "max_tokens=" + finalMaxTokensStr + "\n"
                        + "banned_words=" + finalBannedStr + "\n"
                        + "reasoning_effort=" + finalEffortStr + "\n"
                        + "quick_1=" + fq1 + "\n"
                        + "quick_2=" + fq2 + "\n"
                        + "quick_3=" + fq3 + "\n"
                        + "quick_4=" + fq4 + "\n"
                        + "quick_5=" + fq5 + "\n"
                        + "api_key_2=" + prefs.getString("api_key_2", "") + "\n"
                        + "api_url_2=" + prefs.getString("api_url_2", "") + "\n"
                        + "model_2=" + prefs.getString("model_2", "") + "\n"
                        + "api_weight_2=" + prefs.getString("api_weight_2", "3") + "\n"
                        + "api_direction_2=" + prefs.getInt("api_direction_2", 0) + "\n"
                        + "api_key_3=" + prefs.getString("api_key_3", "") + "\n"
                        + "api_url_3=" + prefs.getString("api_url_3", "") + "\n"
                        + "model_3=" + prefs.getString("model_3", "") + "\n"
                        + "api_weight_3=" + prefs.getString("api_weight_3", "3") + "\n"
                        + "api_direction_3=" + prefs.getInt("api_direction_3", 0) + "\n"
                        + "api_key_4=" + prefs.getString("api_key_4", "") + "\n"
                        + "api_url_4=" + prefs.getString("api_url_4", "") + "\n"
                        + "model_4=" + prefs.getString("model_4", "") + "\n"
                        + "api_weight_4=" + prefs.getString("api_weight_4", "3") + "\n"
                        + "api_direction_4=" + prefs.getInt("api_direction_4", 0) + "\n"
                        + "api_key_5=" + prefs.getString("api_key_5", "") + "\n"
                        + "api_url_5=" + prefs.getString("api_url_5", "") + "\n"
                        + "model_5=" + prefs.getString("model_5", "") + "\n"
                        + "api_weight_5=" + prefs.getString("api_weight_5", "3") + "\n"
                        + "api_direction_5=" + prefs.getInt("api_direction_5", 0) + "\n"
                        + "api_key_6=" + prefs.getString("api_key_6", "") + "\n"
                        + "api_url_6=" + prefs.getString("api_url_6", "") + "\n"
                        + "model_6=" + prefs.getString("model_6", "") + "\n"
                        + "api_weight_6=" + prefs.getString("api_weight_6", "3") + "\n"
                        + "api_direction_6=" + prefs.getInt("api_direction_6", 0) + "\n"
                        + "api_key_7=" + prefs.getString("api_key_7", "") + "\n"
                        + "api_url_7=" + prefs.getString("api_url_7", "") + "\n"
                        + "model_7=" + prefs.getString("model_7", "") + "\n"
                        + "api_weight_7=" + prefs.getString("api_weight_7", "3") + "\n"
                        + "api_direction_7=" + prefs.getInt("api_direction_7", 0) + "\n"
                        + "api_key_8=" + prefs.getString("api_key_8", "") + "\n"
                        + "api_url_8=" + prefs.getString("api_url_8", "") + "\n"
                        + "model_8=" + prefs.getString("model_8", "") + "\n"
                        + "api_weight_8=" + prefs.getString("api_weight_8", "3") + "\n"
                        + "api_direction_8=" + prefs.getInt("api_direction_8", 0) + "\n"
                        + "reasoning_effort_2=" + prefs.getString("reasoning_effort_2", "default") + "\n"
                        + "reasoning_effort_3=" + prefs.getString("reasoning_effort_3", "default") + "\n"
                        + "reasoning_effort_4=" + prefs.getString("reasoning_effort_4", "default") + "\n"
                        + "reasoning_effort_5=" + prefs.getString("reasoning_effort_5", "default") + "\n"
                        + "reasoning_effort_6=" + prefs.getString("reasoning_effort_6", "default") + "\n"
                        + "reasoning_effort_7=" + prefs.getString("reasoning_effort_7", "default") + "\n"
+ "reasoning_effort_8=" + prefs.getString("reasoning_effort_8", "default") + "\n"
+ "stealth_hide_read=" + prefs.getBoolean("stealth_hide_read", true) + "\n"
+ "stealth_hide_typing=" + prefs.getBoolean("stealth_hide_typing", true) + "\n"
+ "live_context_max=" + finalLiveContextMaxStr + "\n"
+ "show_api_switch_hint=" + finalShowApiSwitchHint + "\n"
+ "EOF\n";
                runRoot(cfg);

                String prompts = "cat > /data/local/tmp/htai_prompts.txt << 'EOF'\n"
                        + "###ZH###\n" + zh + "\n"
                        + "###EN###\n" + en + "\n"
                        + "###RU###\n" + ru + "\n"
                        + "###UK###\n" + uk + "\n"
                        + "###KO###\n" + ko + "\n"
                        + "###ES###\n" + es + "\n"
                        + "###AR###\n" + ar + "\n"
                        + "###PT###\n" + pt + "\n"
                        + "###FR###\n" + fr + "\n"
                        + "###DE###\n" + de + "\n"
                        + "###IT###\n" + it + "\n"
                        + "###TR###\n" + tr + "\n"
                        + "###NL###\n" + nl + "\n"
                        + "###PL###\n" + pl + "\n"
                        + "###KK###\n" + kk + "\n"
                        + "###CS###\n" + cs + "\n"
                        + "EOF\n";
                runRoot(prompts);

                runRoot("chmod 644 /data/local/tmp/htai_config.txt /data/local/tmp/htai_prompts.txt");

                try {
                    java.lang.reflect.Method m = AITranslator.class.getMethod(
                            "savePrompts",
                            String.class, String.class, String.class, String.class,
                            String.class, String.class, String.class, String.class,
                            String.class, String.class, String.class, String.class,
                            String.class, String.class, String.class, String.class);
                    m.invoke(null, zh, en, ru, uk, ko, es, ar, pt, fr, de, it, tr, nl, pl, kk, cs);
                } catch (NoSuchMethodException ignored) {
                }

                runOnUiThread(() -> {
                    btnSave.setEnabled(true);
                    btnSave.setText("保存全部配置");
                    toast("配置已保存，请强制停止 HelloTalk 后重开生效");
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    btnSave.setEnabled(true);
                    btnSave.setText("保存全部配置");
                    toast("保存失败: " + e.getMessage());
                });
            }
        }).start();
    }

    // ================= 测试大盘用的数据结构 =================
private static class ApiSlot {
    int index;
    String alias;
    String key;
    String url;
    String model;
    boolean tested = false;
    boolean success = false;
    String message = "";

    ApiSlot(int index, String alias, String key, String url, String model) {
        this.index = index;
        this.alias = (alias != null && !alias.trim().isEmpty()) ? alias.trim() : ("API " + index);
        this.key = key;
        this.url = url;
        this.model = model;
    }

    void test() {
        tested = true;
        String baseUrl = url.isEmpty() ? "https://api.openai.com/v1/chat/completions" : url.trim();
        if (!baseUrl.endsWith("/chat/completions")) {
            if (!baseUrl.endsWith("/")) baseUrl += "/";
            if (!baseUrl.contains("generativelanguage.googleapis.com")) {
                if (!baseUrl.contains("/v1/")) {
                    baseUrl += "v1/";
                } else {
                    int idx = baseUrl.indexOf("/v1/");
                    baseUrl = baseUrl.substring(0, idx + 4);
                }
            }
            baseUrl += "chat/completions";
        }

        try {
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(8, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build();

            JSONObject bodyObj = new JSONObject();
            bodyObj.put("model", model);
            bodyObj.put("max_tokens", 10);
            JSONArray msgs = new JSONArray();
            JSONObject m = new JSONObject();
            m.put("role", "user");
            m.put("content", "hello");
            msgs.put(m);
            bodyObj.put("messages", msgs);

            okhttp3.RequestBody reqBody = okhttp3.RequestBody.create(
                    bodyObj.toString(),
                    okhttp3.MediaType.get("application/json; charset=utf-8"));

            Request req = new Request.Builder()
                    .url(baseUrl)
                    .header("Authorization", "Bearer " + key)
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .post(reqBody)
                    .build();

            try (Response resp = client.newCall(req).execute()) {
                String respStr = resp.body() != null ? resp.body().string() : "";
                if (resp.isSuccessful()) {
                    success = true;
                    message = "✅ 畅通";
                } else {
                    success = false;
                    try {
                        JSONObject errJson = new JSONObject(respStr);
                        JSONObject err = errJson.optJSONObject("error");
                        if (err != null) {
                            message = "❌ HTTP " + resp.code() + " - " + err.optString("message", respStr);
                        } else {
                            message = "❌ HTTP " + resp.code() + " - " + respStr;
                        }
                    } catch (Exception e) {
                        message = "❌ HTTP " + resp.code();
                    }
                    if (message.length() > 200) message = message.substring(0, 200);
                }
            }
        } catch (Exception e) {
            success = false;
            message = "❌ " + e.getClass().getSimpleName() + ": " + (e.getMessage() != null ? e.getMessage() : "");
            if (message.length() > 200) message = message.substring(0, 200);
        }
    }
}

private void showTestResultsToast(List<ApiSlot> slots) {
    int successCount = 0, failCount = 0;
    StringBuilder failNames = new StringBuilder();
    for (ApiSlot s : slots) {
        if (s.success) successCount++;
        else { failCount++; if (failNames.length() > 0) failNames.append("、"); failNames.append(s.alias); }
    }
    String msg;
    if (failCount == 0) msg = "✅ 全部 " + slots.size() + " 个通道畅通！";
    else if (successCount == 0) msg = "🚫 全部 " + slots.size() + " 个通道不可用！";
    else { msg = "⚠️ " + successCount + " 通 / " + failCount + " 败"; if (failNames.length() <= 60) msg += "\n失败: " + failNames; }
    Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
}

private void testTranslate() {
    List<ApiSlot> slots = new ArrayList<>();

    String k1 = etKey.getText().toString().trim();
    if (!k1.isEmpty()) slots.add(new ApiSlot(1, "主 API", k1, etUrl.getText().toString().trim(), etModel.getText().toString().trim()));

    String k2 = etKey2.getText().toString().trim();
    if (!k2.isEmpty()) slots.add(new ApiSlot(2, etAlias2.getText().toString().trim(), k2, etUrl2.getText().toString().trim(), etModel2.getText().toString().trim()));

    String k3 = etKey3.getText().toString().trim();
    if (!k3.isEmpty()) slots.add(new ApiSlot(3, etAlias3.getText().toString().trim(), k3, etUrl3.getText().toString().trim(), etModel3.getText().toString().trim()));

    String k4 = etKey4.getText().toString().trim();
    if (!k4.isEmpty()) slots.add(new ApiSlot(4, etAlias4.getText().toString().trim(), k4, etUrl4.getText().toString().trim(), etModel4.getText().toString().trim()));

    String k5 = etKey5.getText().toString().trim();
    if (!k5.isEmpty()) slots.add(new ApiSlot(5, etAlias5.getText().toString().trim(), k5, etUrl5.getText().toString().trim(), etModel5.getText().toString().trim()));

    String k6 = etKey6.getText().toString().trim();
    if (!k6.isEmpty()) slots.add(new ApiSlot(6, etAlias6.getText().toString().trim(), k6, etUrl6.getText().toString().trim(), etModel6.getText().toString().trim()));

    String k7 = etKey7.getText().toString().trim();
    if (!k7.isEmpty()) slots.add(new ApiSlot(7, etAlias7.getText().toString().trim(), k7, etUrl7.getText().toString().trim(), etModel7.getText().toString().trim()));

    String k8 = etKey8.getText().toString().trim();
    if (!k8.isEmpty()) slots.add(new ApiSlot(8, etAlias8.getText().toString().trim(), k8, etUrl8.getText().toString().trim(), etModel8.getText().toString().trim()));

    if (slots.isEmpty()) {
        toast("请至少在任意一个 API 配置中填写 Key");
        return;
    }

    btnTest.setEnabled(false);
    btnTest.setText("测试中 (" + slots.size() + "个通道)...");

    new Thread(() -> {
        for (ApiSlot slot : slots) {
            slot.test();
        }

        runOnUiThread(() -> {
            btnTest.setEnabled(true);
            btnTest.setText("一键测试大盘全链路");
            showTestResultsToast(slots);
        });
    }).start();
}

    // 独立测试通道方法，绕开调度限制
        // 独立测试通道方法，绕开调度限制并修复 /v1/v1/ 重叠BUG
    private void testSingleApi(String key, String url, String model) {
        if (key.isEmpty() || model.isEmpty()) {
            toast("请先填写 Key 和 模型");
            return;
        }
        
        // 采用与主程序完全一致的严谨URL解析逻辑
        String baseUrl = url.isEmpty() ? "https://api.openai.com/v1/chat/completions" : url.trim();
        if (!baseUrl.endsWith("/chat/completions")) {
            if (!baseUrl.endsWith("/")) baseUrl += "/";
            if (!baseUrl.contains("generativelanguage.googleapis.com")) {
                if (!baseUrl.contains("/v1/")) {
                    baseUrl += "v1/";
                } else {
                    int idx = baseUrl.indexOf("/v1/");
                    baseUrl = baseUrl.substring(0, idx + 4);
                }
            }
            baseUrl += "chat/completions";
        }
        
        Toast.makeText(this, "正在直接测试该通道...", Toast.LENGTH_SHORT).show();
        
        String finalUrl = baseUrl;
        new Thread(() -> {
            try {
                OkHttpClient client = new OkHttpClient.Builder()
                        .connectTimeout(8, TimeUnit.SECONDS)
                        .readTimeout(15, TimeUnit.SECONDS)
                        .build();
                        
                JSONObject bodyObj = new JSONObject();
                bodyObj.put("model", model);
                bodyObj.put("max_tokens", 10); 
                JSONArray msgs = new JSONArray();
                JSONObject m = new JSONObject();
                m.put("role", "user");
                m.put("content", "hello");
                msgs.put(m);
                bodyObj.put("messages", msgs);
                
                okhttp3.RequestBody reqBody = okhttp3.RequestBody.create(bodyObj.toString(), okhttp3.MediaType.get("application/json; charset=utf-8"));
                Request req = new Request.Builder()
                        .url(finalUrl)
                        .header("Authorization", "Bearer " + key)
                        .header("Content-Type", "application/json")
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)") // 伪装成浏览器硬解所有防火墙
                        .post(reqBody)
                        .build();
                        
                try (Response resp = client.newCall(req).execute()) {
                    String respStr = resp.body() != null ? resp.body().string() : "";
                    if (resp.isSuccessful()) {
                        runOnUiThread(() -> toast("✅ 测试成功！该 API 通道正常畅通。"));
                    } else {
                        runOnUiThread(() -> {
                            new AlertDialog.Builder(SettingsActivity.this)
                                .setTitle("❌ 测试失败")
                                .setMessage("HTTP " + resp.code() + "\n" + respStr)
                                .setPositiveButton("关闭", null)
                                .show();
                        });
                    }
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    new AlertDialog.Builder(SettingsActivity.this)
                        .setTitle("❌ 请求异常")
                        .setMessage(e.getMessage())
                        .setPositiveButton("关闭", null)
                        .show();
                });
            }
        }).start();
    }

}
