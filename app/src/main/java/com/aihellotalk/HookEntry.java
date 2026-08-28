package com.aihellotalk;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class HookEntry implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpp) {
        if (!"com.hellotalk".equals(lpp.packageName)) return;

        XposedBridge.log("HT_AI =====================");
        XposedBridge.log("HT_AI 模块启动 v5.1（后台初始化版）");

        // ★ 钩子先装（纯注册，很快），保证所有功能就位
        try {
            ChatHook.install(lpp.classLoader);
        } catch (Throwable t) {
            XposedBridge.log("HT_AI 钩子安装失败: " + t.getMessage());
        }

        // ★ 读配置 + AITranslator.init（文件IO + root调用）全部丢后台线程，
        //   不再占用 HelloTalk 主线程，打开 HelloTalk 不会被 root 调用拖慢
        new Thread(() -> {
            String apiKey = "";
            String apiUrl = "https://api.openai.com/v1/chat/completions";
            String model = "";

            File cfgFile = new File("/data/local/tmp/htai_config.txt");
            if (cfgFile.exists()) {
                try (BufferedReader r = new BufferedReader(new FileReader(cfgFile))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        line = line.trim();
                        if (line.startsWith("api_key=") && line.length() > 8) {
                            apiKey = line.substring(8);
                        } else if (line.startsWith("api_url=") && line.length() > 8) {
                            apiUrl = line.substring(8);
                        } else if (line.startsWith("model=") && line.length() > 6) {
                            model = line.substring(6);
                        }
                    }
                } catch (Exception e) {
                    XposedBridge.log("HT_AI 读配置失败: " + e.getMessage());
                }
            }

            if (apiUrl.isEmpty()) {
                apiUrl = "https://api.openai.com/v1/chat/completions";
            }

            XposedBridge.log("HT_AI Key长度: " + apiKey.length() + " model: " + model);

            AITranslator.init(apiKey, apiUrl, model);
            XposedBridge.log("HT_AI 初始化完成");
        }, "HT_AI_Init").start();
    }
}
