package com.aihellotalk;

import android.content.ClipData;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.TextWatcher;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileReader;
import java.io.BufferedReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class ChatHook {

    private static final String TAG = "HT_AI";
    private static final String DEFAULT_REPLY_LANG = "en";

    private static volatile String currentChatId = "0";
    // ===== 新版 HelloTalk：当前真实聊天页面对象 =====
// 只给新版实时消息列表读取使用，旧版不会使用。
private static volatile Object currentChatDetailFragment = null;
    private static volatile int currentChatType = 1;
    private static volatile String currentPartnerName = "";
    private static volatile int partnerLang = 1;

    private static volatile String latestNationality = "";
    private static volatile int latestNativeLang = 1;
    private static volatile String latestPartnerName = "";

    private static volatile boolean isTranslatingAPI = false;

    private static final Set<String> translating = ConcurrentHashMap.newKeySet();
    private static final Set<String> recordedMsgIds = ConcurrentHashMap.newKeySet();
    private static final ConcurrentHashMap<String, String> chatLangOverride = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, String> chatRequestMap = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Integer> chatRetryCountMap = new ConcurrentHashMap<>();

    private static final Set<String> reverseTranslatedMsgIds = ConcurrentHashMap.newKeySet();
    private static final ConcurrentHashMap<String, Integer> reverseRetryMap = new ConcurrentHashMap<>();

    private static Method langCodeMethod = null;

    private static final int RECENT_IMAGE_LIMIT = 3;
    private static final ConcurrentHashMap<String, String> imageUrlToPathMap = new ConcurrentHashMap<>();
    private static final List<RenderedImageInfo> recentRenderedImages = Collections.synchronizedList(new ArrayList<>());

    private static volatile String latestRenderedImagePath = null;
    private static volatile long latestRenderedImageTime = 0;

    private static volatile String currentQuotedImagePath = null;
    private static volatile boolean currentQuotedImageMissing = false;

    // ===== 新增：当前回复条选中的消息 =====
    private static volatile String selectedReplyText = null;
    private static volatile String selectedReplyMsgType = null;
    private static volatile boolean selectedReplyIsMine = false;
    private static volatile boolean selectedReplyValid = false;
    private static volatile String selectedReplyMsgId = null;
    private static volatile long selectedReplySendTime = 0;
private static volatile String selectedReplySenderName = null;
private static volatile String selectedReplyChatId = null;
private static volatile ClassLoader hostClassLoader = null;

// ===== 新版 HelloTalk：真正的回复控制器 =====
// m4t = com.hellotalk.talk.detail.controller.TalkDetailReplyController
private static volatile Object newReplyController = null;
private static volatile boolean newReplyControllerDetected = false;
    
// ===== v5.13 回复引用中转变量 =====
private static volatile String pendingSendQuote = null;
    private static volatile String pendingSendChatId = null;
    private static final long SELECTED_REPLY_FALLBACK_WINDOW_MS = 120000L;

    private static final String HT_TEXT_VIEW_CLASS = "com.hellotalk.lib.ui.text.view.HTCompatTextView";
    private static Class<?> htTextViewClass = null;

    private static final Handler uiHandler = new Handler(Looper.getMainLooper());

    private static volatile String pendingSelectedForeign = null;
    private static volatile String lastPickerResult = null;
    private static volatile String lastPickerOrig = null;
    private static volatile String lastPickerPns = null;
    private static volatile boolean lastPickerOneTime = false;
    private static volatile Button versionButton = null;
    private static volatile EditText versionEdit = null;

    private static class RenderedImageInfo {
        final String path, url, compressedUrl;
        final long ts;

        RenderedImageInfo(String p, String u, String c, long t) {
            path = p;
            url = u;
            compressedUrl = c;
            ts = t;
        }
    }

    private static volatile boolean msgMethodsReady = false;
    private static Method mIsSender, mGetChatId, mGetSenderName, mGetMsgType,
            mGetMsgId, mGetSendTime, mGetReplyInfo, mGetMsgContentTyped;
    private static volatile Method mBeanGetText = null;

    private static void ensureMsgMethods(Object msg) {
    if (msgMethodsReady || msg == null) return;
    try {
        Class<?> c = msg.getClass();
        mIsSender = getMethodFallback(c, "isSender", "Y");
        mGetChatId = getMethodFallback(c, "getChatId", "P");
        mGetSenderName = getMethodFallback(c, "getSenderName", "S");
        mGetMsgType = getMethodFallback(c, "getMsgType", "M");
        mGetMsgId = getMethodFallback(c, "getMsgId", "L");
        mGetSendTime = getMethodFallback(c, "getSendTime", "R");
        mGetReplyInfo = getMethodFallback(c, "getReplyInfo", "N");
        try {
            mGetMsgContentTyped = c.getMethod("getMessageContent", Class.class, boolean.class);
        } catch (Throwable t1) {
            try {
                mGetMsgContentTyped = c.getMethod("B", Class.class);
            } catch (Throwable t2) {
                mGetMsgContentTyped = null;
            }
        }
        msgMethodsReady = true;
    } catch (Throwable ignored) {}
}

private static void callSetText(Object bean, String text) {
    try {
        XposedHelpers.callMethod(bean, "setText", text);
    } catch (Throwable t1) {
        try {
            XposedHelpers.callMethod(bean, "J", text);
        } catch (Throwable t2) {
            try {
                XposedHelpers.callMethod(bean, "C", text);
            } catch (Throwable t3) {
                log("setText fail: " + t3.getMessage());
            }
        }
    }
}

private static Method getMethodFallback(Class<?> c, String oldName, String newName) {
    try { return c.getMethod(oldName); }
    catch (Throwable t) {
        try { return c.getMethod(newName); }
        catch (Throwable t2) { return null; }
    }
}

private static Method ensureBeanGetText(Object bean) {
    if (bean == null) return null;

    try {
        Method m = bean.getClass().getMethod("getText");
        m.setAccessible(true);
        return m;
    } catch (Throwable ignored) {}

    // ===== 新版 HelloTalk =====
    // 新版 IMTextBean / IMTranslateBean 的文字 getter
    try {
        Method m = bean.getClass().getMethod("u");
        m.setAccessible(true);
        return m;
    } catch (Throwable ignored) {}

    // ===== 旧版兜底 =====
    try {
        Method m = bean.getClass().getMethod("r");
        m.setAccessible(true);
        return m;
    } catch (Throwable ignored) {}

    try {
        Method m = bean.getClass().getMethod("p");
        m.setAccessible(true);
        return m;
    } catch (Throwable ignored) {}

    return null;
}

    private static final java.util.concurrent.ExecutorService historyExecutor =
            java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "HT_AI_HistWriter");
                t.setPriority(Thread.MIN_PRIORITY + 1);
                return t;
            });

    private static final java.util.concurrent.ExecutorService reverseTranslateExecutor =
            java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "HT_AI_ReverseTL");
                t.setPriority(Thread.MIN_PRIORITY);
                return t;
            });

    public static void install(ClassLoader cl) {
        hostClassLoader = cl;
        log("=== Hook v5.5 精准回复修复版 ===");

        try {
            htTextViewClass = XposedHelpers.findClassIfExists(HT_TEXT_VIEW_CLASS, cl);
        } catch (Throwable ignored) {}

        try {
            Class<?> avClass = XposedHelpers.findClass("av.a", cl);
            langCodeMethod = avClass.getMethod("a", int.class);
        } catch (Throwable ignored) {}

        try { hookTextViewRender(cl); } catch (Throwable t) { log("render hook fail"); }
        try { hookClipboard(cl); } catch (Throwable ignored) {}
        try { hookBubbleFlip(cl); } catch (Throwable ignored) {}
        try { hookStartChat(cl); } catch (Throwable ignored) {}
        try { hookRecv(cl); } catch (Throwable ignored) {}
        try { hookLang(cl); } catch (Throwable ignored) {}
        try { hookBtnOld(cl); } catch (Throwable ignored) {}
        try { hookBtnNew(cl); } catch (Throwable ignored) {}
        try { hookUltimateStealth(cl); } catch (Throwable ignored) {}
        try { hookImageRenderLayer(cl); } catch (Throwable ignored) {}

// ===== 新版 HelloTalk：真正的回复控制器 =====
// 旧版没有 m4t，所以不会影响旧版
try {
    hookNewReplyController(cl);
} catch (Throwable t) {
    log("新版回复控制器 hook fail: " + t.getMessage());
}

// 回复条
try { hookInputReplyBar(cl); } catch (Throwable ignored) {}

// ===== v5.13 新增：hook 拦截发送动作 =====
try { hookOutgoingSetMsg(cl); } catch (Throwable ignored) {}
    }

    private static void log(String msg) {
        XposedBridge.log("HT_AI " + msg);
    }

    private static boolean isPureBracketQuery(String text) {
        if (text == null) return false;
        String s = text.trim();
        return (s.startsWith("(") && s.endsWith(")")) || (s.startsWith("（") && s.endsWith("）"));
    }
    private static boolean shouldSkipHistory(String text) {
        if (text == null) return false;
        String t = text.trim();
        if (t.isEmpty()) return false;
        if (AITranslator.isNoHistoryText(t)) return true;
        return isPureBracketQuery(t);
    }

    private static String safeCallString(Object obj, String methodName) {
        if (obj == null) return null;
        try {
            Object r = XposedHelpers.callMethod(obj, methodName);
            return r == null ? null : String.valueOf(r);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String safeNormalize(String s) {
        if (s == null) return null;
        try {
            String x = s.trim();
            if (x.isEmpty()) return null;
            int q = x.indexOf('?');
            if (q >= 0) x = x.substring(0, q);
            int h = x.indexOf('#');
            if (h >= 0) x = x.substring(0, h);
            try { x = URLDecoder.decode(x, "UTF-8"); } catch (Throwable ignored) {}
            return x.trim();
        } catch (Throwable e) {
            return s;
        }
    }

    private static String fileNameFromUrl(String url) {
        try {
            String s = safeNormalize(url);
            if (s == null || s.isEmpty()) return null;
            int idx = s.lastIndexOf('/');
            if (idx >= 0 && idx < s.length() - 1) return s.substring(idx + 1);
            return s;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static File getHelloTalkImageCacheDir() {
        try {
            return new File("/storage/emulated/0/Android/data/com.hellotalk/cache/hellotalk/images_/");
        } catch (Throwable e) {
            return null;
        }
    }

    private static void putImageMapping(String key, String path) {
        if (key == null || key.trim().isEmpty() || path == null || path.trim().isEmpty()) return;
        imageUrlToPathMap.put(key, path);
    }

    private static void addRenderedImageRecord(String path, String url, String compressedUrl) {
        if (path == null || path.isEmpty()) return;
        long now = System.currentTimeMillis();
        latestRenderedImagePath = path;
        latestRenderedImageTime = now;

        synchronized (recentRenderedImages) {
            recentRenderedImages.add(0, new RenderedImageInfo(path, url, compressedUrl, now));
            Set<String> seen = ConcurrentHashMap.newKeySet();
            List<RenderedImageInfo> dedup = new ArrayList<>();
            for (RenderedImageInfo info : recentRenderedImages) {
                if (info != null && info.path != null && seen.add(info.path)) dedup.add(info);
            }
            recentRenderedImages.clear();
            recentRenderedImages.addAll(dedup);
            while (recentRenderedImages.size() > RECENT_IMAGE_LIMIT) {
                recentRenderedImages.remove(recentRenderedImages.size() - 1);
            }
        }
    }

    private static String bruteFindLocalImagePathFromBean(Object imageBean) {
        if (imageBean == null) return null;

        String url = safeCallString(imageBean, "getUrl");
        String compressedUrl = safeCallString(imageBean, "getCompressedUrl");
        String urlNorm = safeNormalize(url);
        String compressedNorm = safeNormalize(compressedUrl);
        String urlName = fileNameFromUrl(url);
        String compressedName = fileNameFromUrl(compressedUrl);

        String cachedByUrl = imageUrlToPathMap.get(url);
        if (cachedByUrl != null && new File(cachedByUrl).exists()) return cachedByUrl;
        String cachedByCompressed = imageUrlToPathMap.get(compressedUrl);
        if (cachedByCompressed != null && new File(cachedByCompressed).exists()) return cachedByCompressed;

        File dir = getHelloTalkImageCacheDir();
        if (dir != null && dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f == null || !f.exists() || f.length() <= 0) continue;
                    String name = f.getName();
                    if (urlName != null && !urlName.isEmpty() && name.contains(urlName)) return f.getAbsolutePath();
                    if (compressedName != null && !compressedName.isEmpty() && name.contains(compressedName)) return f.getAbsolutePath();
                }
            }
        }

        synchronized (recentRenderedImages) {
            for (RenderedImageInfo info : recentRenderedImages) {
                if (info == null || info.path == null) continue;
                File f = new File(info.path);
                if (!f.exists() || f.length() <= 0) continue;
                String infoUrl = safeNormalize(info.url);
                String infoCompressed = safeNormalize(info.compressedUrl);
                if (urlNorm != null && infoUrl != null
                        && (urlNorm.equals(infoUrl) || infoUrl.contains(urlNorm) || urlNorm.contains(infoUrl))) {
                    return info.path;
                }
                if (compressedNorm != null && infoCompressed != null
                        && (compressedNorm.equals(infoCompressed) || infoCompressed.contains(compressedNorm) || compressedNorm.contains(infoCompressed))) {
                    return info.path;
                }
                String infoName = f.getName();
                if (urlName != null && infoName.contains(urlName)) return info.path;
                if (compressedName != null && infoName.contains(compressedName)) return info.path;
            }
        }
        return null;
    }

// ===== 新版 HelloTalk 精准回复状态 =====
//
// 逆向确认：
// m4t = com.hellotalk.talk.detail.controller.TalkDetailReplyController
// g(HTIMMessage) = updateReplyMode
// f() = obtainReplyMsg
//
// 用户真正点“回复某条消息”时才会进入 g()。
// 点击翻译按钮时再调用 f()，得到输入框当前真正引用的消息。
private static void hookNewReplyController(ClassLoader cl) {
    try {
        Class<?> controller = XposedHelpers.findClassIfExists("m4t", cl);

        if (controller == null) {
            log("新版 TalkDetailReplyController(m4t) 不存在，继续使用旧版回复逻辑");
            return;
        }

        newReplyControllerDetected = true;

        XposedBridge.hookAllMethods(controller, "g", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam p) {
                try {
                    // 保存当前真正工作的回复控制器实例
                    newReplyController = p.thisObject;

                    Object msg =
                            (p.args != null && p.args.length > 0)
                                    ? p.args[0]
                                    : null;

                    if (msg != null) {
                        applySelectedReply(msg);

                        log("新版真实回复目标捕获: mine="
                                + selectedReplyIsMine
                                + " text="
                                + selectedReplyText);
                    } else {
                        resetSelectedReply();
                        log("新版真实回复目标清空");
                    }

                } catch (Throwable t) {
                    log("新版 updateReplyMode 捕获失败: " + t.getMessage());
                }
            }
        });

        log("新版 TalkDetailReplyController(m4t) Hook 注册成功");

    } catch (Throwable t) {
        log("hookNewReplyController fail: " + t.getMessage());
    }
}


// 点击“译”时，不相信 UI 曾经保存的 selectedReply，
// 而是直接问新版 TalkDetailReplyController：
// “输入框现在到底回复的是哪条消息？”
private static boolean refreshSelectedReplyFromNewController() {

    // 没检测到 m4t = 旧版 HelloTalk
    // 什么也不做，原来的旧版逻辑继续运行
    if (!newReplyControllerDetected) {
        return false;
    }

    Object controller = newReplyController;

    // 新版存在，但用户还没有执行过“回复”
    if (controller == null) {
        resetSelectedReply();
        return true;
    }

    try {
        // 新版逆向确认：
        // m4t.f() = obtainReplyMsg
        // 它返回的就是“输入框此刻真正引用的 HTIMMessage”
        Object msg = XposedHelpers.callMethod(controller, "f");

        // 当前已经取消回复 / 根本没有回复对象
        if (msg == null) {
            resetSelectedReply();
            log("新版 obtainReplyMsg: 当前没有回复对象");
            return true;
        }

        // 先沿用原来的通用解析，读取 mine/type/text/msgId 等。
        // 这部分旧版也在使用，所以不要改 applySelectedReply() 本身。
        applySelectedReply(msg);

// ===== 新版专用：过滤 m4t.f() 的伪回复对象 =====
//
// 新版在“没有真正选择回复框”时，某些情况下 m4t.f()
// 仍可能返回一个 HTIMMessage 占位对象。
// 如果解析出的所谓回复文字恰好就是当前 ChatDetailFragment.H3()
// 得到的 chatId，那么它绝不是真正聊天正文。
//
// 只在新版 m4t 路径处理，不影响旧版 kr0.d。
if (selectedReplyValid
        && selectedReplyText != null
        && currentChatId != null
        && selectedReplyText.trim().equals(currentChatId.trim())) {

    log("新版伪回复对象已忽略: text=chatId=" + currentChatId);

    resetSelectedReply();

    return true;
}
        // ===== 新版专用修复 =====
        //
        // 新版回复控制器 m4t.f() 本身已经代表“当前聊天输入框正在回复的消息”。
        // 某些新版 HTIMMessage 临时/引用对象中的 chatId 会是 0、null，
        // 甚至可能不是当前页面真正的会话 id。
        //
        // 旧代码拿这个不可靠的 selectedReplyChatId 去和 currentChatId 比较，
        // 会把已经正确捕获到的回复对象误删掉。
        //
        // 因此这里只在新版 m4t 路径里，把回复对象归属到
        // ChatDetailFragment.H3() 已经确认的当前页面 chatId。
        // 旧版 kr0.d 路径完全不受影响。
        if (currentChatId != null
                && !currentChatId.trim().isEmpty()
                && !"0".equals(currentChatId)
                && !"null".equalsIgnoreCase(currentChatId)) {

            String rawReplyChatId = selectedReplyChatId;
            selectedReplyChatId = currentChatId;

            log("新版回复对象绑定当前会话: rawReplyChatId="
                    + rawReplyChatId
                    + " current="
                    + currentChatId);
        }

        log("新版 obtainReplyMsg 精准读取: mine="
                + selectedReplyIsMine
                + " chatId="
                + selectedReplyChatId
                + " text="
                + selectedReplyText);

        return true;

    } catch (Throwable t) {

        log("新版 obtainReplyMsg 读取失败: " + t.getMessage());

        // 新版路径读取失败时宁可认为没有回复，
        // 也绝不能继续使用被 UI 列表污染的旧 selectedReply
        resetSelectedReply();

        return true;
    }
}

    private static void hookInputReplyBar(ClassLoader cl) {

    // 旧版 5.7.0 兜底：kr0.d
    try {
        Class<?> replyBar = XposedHelpers.findClassIfExists("kr0.d", cl);
        if (replyBar != null) {
            XposedBridge.hookAllMethods(replyBar, "b", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam p) {
                    resetSelectedReply();
                }
                @Override
                protected void afterHookedMethod(MethodHookParam p) {
                    try {
                        Object msg = (p.args != null && p.args.length > 0) ? p.args[0] : null;
                        applySelectedReply(msg);
                    } catch (Throwable t) {
                        log("inputReplyBar.b hook error: " + t.getMessage());
                    }
                }
            });
            XposedBridge.hookAllMethods(replyBar, "d", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam p) {
                    try {
                        if (p.args != null && p.args.length > 0
                                && p.args[0] instanceof Boolean
                                && !((Boolean) p.args[0])) {
                            resetSelectedReply();
                        }
                    } catch (Throwable ignored) {}
                }
            });
        }
    } catch (Throwable t) {
        log("hookInputReplyBar kr0.d fail: " + t.getMessage());
    }

    // ReplyHolderView 的图片处理保持不动
    try {
        Class<?> replyHolder = XposedHelpers.findClassIfExists(
                "com.hellotalk.talk.detail.widget.reply.ReplyHolderView", cl);
        if (replyHolder != null) {
            XposedBridge.hookAllMethods(replyHolder, "setImageMessageImage", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam p) {
                    try {
                        if (p.args == null || p.args.length < 1) return;
                        String lp = bruteFindLocalImagePathFromBean(p.args[0]);
                        if (lp != null && new File(lp).exists()) {
                            currentQuotedImagePath = lp;
                            currentQuotedImageMissing = false;
                        } else if ("image".equals(selectedReplyMsgType)
        || "photo".equals(selectedReplyMsgType)) {
                            currentQuotedImagePath = null;
                            currentQuotedImageMissing = true;
                        }
                    } catch (Throwable ignored) {}
                }
            });
        }
    } catch (Throwable t) {
        log("hookInputReplyBar ReplyHolderView fail: " + t.getMessage());
    }
}

    private static void resetSelectedReply() {
        selectedReplyValid = false;
        selectedReplyText = null;
        selectedReplyMsgType = null;
        selectedReplyIsMine = false;
        selectedReplyMsgId = null;
        selectedReplySendTime = 0;
        selectedReplySenderName = null;
        selectedReplyChatId = null;
        currentQuotedImagePath = null;
        currentQuotedImageMissing = false;
    }
// ===== 新版 HelloTalk：直接从新版 HTIMMessage 读取文字 =====
// 只给 buildNewLiveChatContext() 使用。
// 不调用旧版通用消息解析器，避免影响旧版 HelloTalk。
private static String extractNewLiveMessageText(Object msg) {
    if (msg == null) return null;

    try {
        // 新版 dex 已确认：
        // HTIMMessage.M() -> String，返回消息类型
        Object typeObj = XposedHelpers.callMethod(msg, "M");
        String msgType = typeObj != null
                ? String.valueOf(typeObj)
                : "";

        if ("text".equals(msgType)) {

            Class<?> textBeanClass = XposedHelpers.findClassIfExists(
                    "com.hellotalk.talk.detail.delegate.text.IMTextBean",
                    hostClassLoader
            );

            if (textBeanClass == null) {
                return null;
            }

            // 新版 dex：
            // HTIMMessage.B(Class) -> HTIMJsonBean
            Object bean = XposedHelpers.callMethod(
                    msg,
                    "B",
                    textBeanClass
            );

            if (bean == null) {
                return null;
            }

            // 先读未混淆字段。
            Object text = readFieldQuiet(bean, "text");

            if (text == null) {
                text = readFieldQuiet(bean, "reportText");
            }

            // 新版 IMTextBean 中存在 u() -> String。
            // 字段读取失败时用新版 getter 兜底。
            if (text == null) {
                try {
                    text = XposedHelpers.callMethod(bean, "u");
                } catch (Throwable ignored) {}
            }

            if (text == null) {
                return null;
            }

            String result = String.valueOf(text).trim();

            return result.isEmpty() ? null : result;
        }

        if ("translate".equals(msgType)) {

            Class<?> transBeanClass = XposedHelpers.findClassIfExists(
                    "com.hellotalk.talk.detail.delegate.translate.IMTranslateBean",
                    hostClassLoader
            );

            if (transBeanClass == null) {
                return null;
            }

            Object bean = XposedHelpers.callMethod(
                    msg,
                    "B",
                    transBeanClass
            );

            if (bean == null) {
                return null;
            }

            // classes9.dex 已确认 IMTranslateBean.srcText 字段存在。
            Object text = readFieldQuiet(bean, "srcText");

            // classes9.dex 已确认：
            // IMTranslateBean.u() -> srcText
            if (text == null) {
                try {
                    text = XposedHelpers.callMethod(bean, "u");
                } catch (Throwable ignored) {}
            }

            if (text == null) {
                return null;
            }

            String result = String.valueOf(text).trim();

            return result.isEmpty() ? null : result;
        }

    } catch (Throwable ignored) {}

    return null;
}
// ===== 新版 HelloTalk：直接读取当前聊天页面真实消息 =====
//
// 新版不再依赖 hookRecv / recordOutgoing 写入的历史文件。
// 点击“译”时直接从当前 ChatDetailFragment 的 MessageAdapter
// 读取屏幕所在会话里的真实消息。
//
// 旧版没有 currentChatDetailFragment，因此完全不会走这里。
private static String buildNewLiveChatContext(int maxCount) {

    if (!newReplyControllerDetected) {
        return null;
    }

    Object fragment = currentChatDetailFragment;

    if (fragment == null) {
        log("新版实时上下文: currentChatDetailFragment=null");
        return null;
    }

    try {
        // 逆向新版：
        // ChatDetailFragment.L3() -> 当前聊天 MessageAdapter
        Object adapter = XposedHelpers.callMethod(fragment, "L3");

        if (adapter == null) {
            log("新版实时上下文: L3() adapter=null");
            return null;
        }
// ===== 新版 HelloTalk：读取 UI 真正使用的原始消息列表 =====
//
// 重新逆向确认：
// of4.k() 会额外筛选 v84，所以可能返回空列表。
// HelloTalk 自己显示聊天气泡时使用的是：
// of4.j() -> b84.a -> 原始消息 List。
Object helper = XposedHelpers.callMethod(adapter, "j");

if (helper == null) {
    log("新版实时上下文: adapter.j() helper=null");
    return null;
}

Object listObj = readFieldQuiet(helper, "a");

if (!(listObj instanceof java.util.List)) {
    log("新版实时上下文: helper.a 不是 List: "
            + (listObj == null ? "null" : listObj.getClass().getName()));
    return null;
}

java.util.List<?> items = (java.util.List<?>) listObj;

if (items.isEmpty()) {
    log("新版实时上下文: UI原始消息列表为空");
    return null;
}

        int wanted = maxCount;

if (wanted < 0) wanted = 0;
if (wanted > 80) wanted = 80;

// 用户把上下文设置为 0 时，明确告诉后面的新版 AI：
// 本次没有聊天历史。
// 绝不能因此回退到模块旧 history。
if (wanted == 0) {
    return "【程序直接读取的当前 HelloTalk 实时对话】\n"
            + "（用户已将上下文条数设置为0，本次没有提供任何历史聊天消息。）\n";
}
        
       

        int start = Math.max(0, items.size() - wanted);

        StringBuilder sb = new StringBuilder();

        sb.append("【程序直接读取的当前 HelloTalk 实时对话】\n");
        sb.append("说明：以下内容直接来自当前聊天页面的真实消息列表，")
          .append("不是模块缓存，也不是聊天ID。")
          .append("回答时应优先相信这里的内容。\n");

        int added = 0;

        for (int i = start; i < items.size(); i++) {

            Object wrapper = items.get(i);
            if (wrapper == null) continue;

            Object msg;

            try {
                // 新版 v84.a() -> HTIMMessage
                msg = XposedHelpers.callMethod(wrapper, "a");
            } catch (Throwable e) {
                // 极端情况下 adapter.k() 可能直接给 HTIMMessage
                msg = wrapper;
            }

            if (msg == null) continue;

            try {
// ===== 新版专用 =====
// classes9.dex 已确认 HTIMMessage.Y() -> boolean
// 这里直接读取，不使用旧版共用的 mIsSender 缓存。
Object mineObj = null;

try {
    mineObj = XposedHelpers.callMethod(msg, "Y");
} catch (Throwable ignored) {}

boolean mine = mineObj instanceof Boolean
        && ((Boolean) mineObj);

// ===== 新版专用 =====
// 直接按照新版 HTIMMessage / IMTextBean / IMTranslateBean
// 的实际 dex 方法读取文字。
String content = extractNewLiveMessageText(msg);

                // 目前实时上下文优先读取真正文字消息。
                // 图片/语音以后再单独完善，先别影响已有功能。
                if (content == null || content.trim().isEmpty()) {
                    continue;
                }

                content = content.trim();

                // 防止错误字段再次把当前 chatId 当聊天正文。
                if (currentChatId != null
                        && currentChatId.equals(content)) {
                    continue;
                }

                // 不把括号问答本身重新塞进上下文。
                if (AITranslator.isNoHistoryText(content)) {
                    continue;
                }

                sb.append(mine ? "我：" : "对方：")
                  .append(content)
                  .append("\n");

                added++;

            } catch (Throwable one) {
                // 单条消息解析失败直接跳过，不影响整个按钮。
            }
        }

        if (added == 0) {
            log("新版实时上下文: 找到列表但没有可用文字消息");
            return null;
        }

        log("新版实时上下文读取成功: adapterItems="
                + items.size()
                + " textMessages="
                + added);

        return sb.toString();

    } catch (Throwable t) {

        log("新版实时上下文读取失败: "
                + t.getClass().getSimpleName()
                + ": "
                + t.getMessage());

        return null;
    }
}
    private static void applySelectedReply(Object msg) {
        if (msg == null) {
            resetSelectedReply();
            return;
        }

        try {
            ensureMsgMethods(msg);

            Object isMineObj = invokeQuiet(mIsSender, msg);
            boolean isMine = (isMineObj instanceof Boolean) && ((Boolean) isMineObj);

            Object mtObj = invokeQuiet(mGetMsgType, msg);
            String mt = (mtObj != null) ? String.valueOf(mtObj) : "";

            Object idObj = invokeQuiet(mGetMsgId, msg);
            String id = (idObj != null) ? String.valueOf(idObj) : "";

            Object stObj = invokeQuiet(mGetSendTime, msg);
            long st = (stObj instanceof Long) ? ((Long) stObj) : System.currentTimeMillis();

            Object snObj = invokeQuiet(mGetSenderName, msg);
            String sn = (snObj != null) ? String.valueOf(snObj) : "";

            Object cidObj = invokeQuiet(mGetChatId, msg);
            String cid = (cidObj != null) ? String.valueOf(cidObj) : currentChatId;

            String text = extractMessageTextByType(msg, mt);

            selectedReplyMsgType = mt;
            selectedReplyIsMine = isMine;
            selectedReplyMsgId = id;
            selectedReplySendTime = st;
            selectedReplySenderName = sn;
            selectedReplyChatId = cid;
            selectedReplyText = (text != null && !text.isEmpty())
                    ? text
                    : describeNonTextMessage(mt, isMine);
            selectedReplyValid = true;

            if ("image".equals(mt) || "photo".equals(mt)) {
                if (currentQuotedImagePath == null) {
                    currentQuotedImageMissing = true;
                }
            } else {
                currentQuotedImageMissing = false;
            }

            log("selectedReply: mine=" + isMine + " type=" + mt + " text=" + selectedReplyText);
        } catch (Throwable t) {
            resetSelectedReply();
            log("applySelectedReply error: " + t.getMessage());
        }
    }

    private static String extractMessageTextByType(Object msg, String msgType) {
    if (msg == null) return null;
    if (!"text".equals(msgType) && !"translate".equals(msgType)) return null;

    try {
        ensureMsgMethods(msg);

        if ("text".equals(msgType)) {
            Class<?> textBean = XposedHelpers.findClassIfExists(
                    "com.hellotalk.talk.detail.delegate.text.IMTextBean",
                    hostClassLoader);
            if (textBean == null) return null;

            Object bean = invokeQuiet(mGetMsgContentTyped, msg, textBean, false);
            if (bean == null) bean = invokeQuiet(mGetMsgContentTyped, msg, textBean);
            if (bean == null) return null;

            // 直接反射读字段，绕开方法名混淆
            Object t = readFieldQuiet(bean, "text");
            if (t == null) t = readFieldQuiet(bean, "reportText");
            if (t == null) {
                Method m = ensureBeanGetText(bean);
                t = invokeQuiet(m, bean);
            }
            return (t != null) ? String.valueOf(t) : null;
        }

        if ("translate".equals(msgType)) {
            Class<?> transBean = XposedHelpers.findClassIfExists(
                    "com.hellotalk.talk.detail.delegate.translate.IMTranslateBean",
                    hostClassLoader);
            if (transBean == null) return null;

            Object bean = invokeQuiet(mGetMsgContentTyped, msg, transBean, false);
            if (bean == null) bean = invokeQuiet(mGetMsgContentTyped, msg, transBean);
            if (bean == null) return null;

            Object t = readFieldQuiet(bean, "srcText");
            if (t == null) {
                try { t = XposedHelpers.callMethod(bean, "getSrcText"); } catch (Throwable ignored) {}
            }
            return (t != null) ? String.valueOf(t) : null;
        }
    } catch (Throwable ignored) {}

    return null;
}

private static Object readFieldQuiet(Object obj, String fieldName) {
    try {
        Field f = obj.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        return f.get(obj);
    } catch (Throwable t) {
        return null;
    }
}

    private static String describeNonTextMessage(String mt, boolean isMine) {
        String who = isMine ? "我" : "对方";
        if (mt == null) return "[" + who + "发送了一条消息]";

        switch (mt) {
            case "image":
            case "photo":
                return "[" + who + "发送了一张图片]";
            case "voice":
            case "audio":
                return "[" + who + "发送了一条语音]";
            case "video":
                return "[" + who + "发送了一段视频]";
            case "emoji":
            case "sticker":
                return "[" + who + "发送了一个表情包]";
            case "location":
                return "[" + who + "发送了一个位置]";
            case "card":
            case "introduction":
                return "[" + who + "发送了一张名片]";
            case "gift":
                return "[" + who + "发送了一个礼物]";
            default:
                return "[" + who + "发送了一条" + mt + "消息]";
        }
    }

    private static void hookUltimateStealth(ClassLoader cl) {
    boolean hideTyping = readStealthConfig("stealth_hide_typing", true);
    boolean hideRead = readStealthConfig("stealth_hide_read", true);

    if (hideTyping) {
        try {
            Class<?> tc = XposedHelpers.findClassIfExists(
                    "com.hellotalk.talk.detail.controller.title.TalkSingleTitleController", cl);
            if (tc != null) {
                XposedBridge.hookAllMethods(tc, "s0", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam p) {
                        p.setResult(null);
                    }
                });
            }
        } catch (Throwable ignored) {}
    }

    if (!hideRead && !hideTyping) return;

    XC_MethodHook kill = new XC_MethodHook() {
        @Override
        protected void beforeHookedMethod(MethodHookParam p) {
            p.setResult(null);
        }
    };

    if (hideRead) {
        try {
            Class<?> za = XposedHelpers.findClassIfExists("z10.a", cl);
            if (za != null) {
                XposedBridge.hookAllMethods(za, "m", kill);
                XposedBridge.hookAllMethods(za, "c0", kill);
                XposedBridge.hookAllMethods(za, "f0", kill);
            }
            Class<?> yb = XposedHelpers.findClassIfExists("y10.b", cl);
            if (yb != null) {
                XposedBridge.hookAllMethods(yb, "m", kill);
                XposedBridge.hookAllMethods(yb, "c0", kill);
                XposedBridge.hookAllMethods(yb, "f0", kill);
            }
        } catch (Throwable ignored) {}

        try {
            Class<?> be = XposedHelpers.findClassIfExists("b20.e", cl);
            if (be != null) {
                XposedBridge.hookAllMethods(be, "z", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam p) {
                        if (p.args != null && p.args.length > 0 && p.args[0] != null) {
                            String n = p.args[0].getClass().getName();
                            if ("tm.a".equals(n) || "e20.c".equals(n)) p.setResult(null);
                        }
                    }
                });
            }
        } catch (Throwable ignored) {}

        try {
            Class<?> ec = XposedHelpers.findClassIfExists("e20.c", cl);
            if (ec != null) {
                XposedBridge.hookAllMethods(ec, "f", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam p) {
                        p.setResult(new byte[0]);
                    }
                });
            }
        } catch (Throwable ignored) {}
    }
}

private static boolean readStealthConfig(String key, boolean def) {
    try {
        File f = new File("/data/local/tmp/htai_config.txt");
        if (f.exists()) {
            BufferedReader r = new BufferedReader(new FileReader(f));
            String line;
            while ((line = r.readLine()) != null) {
                if (line.trim().startsWith(key + "=")) {
                    String v = line.substring(key.length() + 1).trim();
                    r.close();
                    return "true".equalsIgnoreCase(v);
                }
            }
            r.close();
        }
    } catch (Exception ignored) {}
    return def;
}

private static void hookTextViewRender(ClassLoader cl) {
    if (htTextViewClass == null) return;

    XC_MethodHook renderLogic = new XC_MethodHook() {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) {
            try {
                if (param.thisObject instanceof EditText) return;
                if (!htTextViewClass.isInstance(param.thisObject)) return;

                CharSequence cs = (CharSequence) param.args[0];
                if (cs == null) return;

                String s = cs.toString();
                if (s.isEmpty() || s.length() > 5000) return;
                if (s.endsWith(" 🌐") || s.endsWith(" 🔄")) return;

                // 主线程只做轻量判断：必须有外语字母
                if (!AITranslator.hasAnyLetterOrDigit(s)) return;
                if (AITranslator.containsJapanese(s)) return;

                // 自己发的：只查 mySentDrafts，命中只加 🌐
String d = AITranslator.mySentDrafts.get(s);
if (d != null && !d.equals(s)) {
    param.args[0] = new SpannableStringBuilder(cs).append(" 🌐");
    return;
}
// 对方发的：查 foreignToChinese，命中替换为中文 + 🔄
d = AITranslator.foreignToChinese.get(s);
if (d != null && !d.equals(s)) {
    param.args[0] = d + " 🔄";
    return;
}

                // 缓存没命中，丢后台翻译
                final String ft = s;
                final String key = "tv_" + ft;
                final TextView tv = (TextView) param.thisObject;
                if (!translating.add(key)) return;
                reverseTranslateExecutor.execute(() -> {
                    try {
                        String t = AITranslator.toChinese(ft, currentChatId);
                        if (t != null && !t.trim().isEmpty() && !t.equals(ft)) {
                            AITranslator.cacheResult(key, ft, t);
                            tv.post(() -> {
                                try { tv.setText(t + " 🔄"); } catch (Throwable ignored) {}
                            });
                        }
                    } catch (Throwable ignored) {
                    } finally {
                        translating.remove(key);
                    }
                });
            } catch (Throwable ignored) {}
        }
    };

    try {
        XposedHelpers.findAndHookMethod("android.widget.TextView", null, "setText",
                CharSequence.class, TextView.BufferType.class, renderLogic);
    } catch (Throwable t) {}

    try {
        XposedHelpers.findAndHookMethod("android.widget.TextView", null, "setText",
                CharSequence.class, renderLogic);
    } catch (Throwable t) {}
}

    private static void hookClipboard(ClassLoader cl) {
        XC_MethodHook h = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam p) throws Throwable {
                ClipData cd = (ClipData) p.args[0];
                if (cd != null && cd.getItemCount() > 0) {
                    CharSequence t = cd.getItemAt(0).getText();
                    if (t != null) {
                        String ts = t.toString();
                        if (cd.getDescription() != null && "HT_AI_Copy".equals(cd.getDescription().getLabel())) {
                            return;
                        }
                        if (!ts.endsWith(" 🌐") && !ts.endsWith(" 🔄") && !ts.matches(".*[\\u4e00-\\u9fa5]+.*")) {
                            return;
                        }
                        try {
                            String orig = AITranslator.getForeignFuzzy(ts);
                            if (orig != null && !orig.trim().isEmpty() && !orig.equals(ts)) {
                                p.args[0] = ClipData.newPlainText("HT_AI", orig);
                            }
                        } catch (Throwable ignored) {}
                    }
                }
            }
        };

        try {
            XposedHelpers.findAndHookMethod("android.content.ClipboardManager", cl,
                    "setPrimaryClip", ClipData.class, h);
        } catch (Throwable ignored) {}
    }

    private static void hookBubbleFlip(ClassLoader cl) throws Exception {
        XposedHelpers.findAndHookMethod(HT_TEXT_VIEW_CLASS, cl, "onTouchEvent", MotionEvent.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam p) throws Throwable {
                        TextView tv = (TextView) p.thisObject;
                        MotionEvent ev = (MotionEvent) p.args[0];
                        if (ev == null) return;

                        CharSequence cs = tv.getText();
                        if (cs == null) return;

                        String s = cs.toString();
                        if (!s.endsWith(" 🔄") && !s.endsWith(" 🌐")) return;

                        Layout lay = tv.getLayout();
                        if (lay == null) return;

                        int line = lay.getLineForVertical((int) ev.getY());
                        int off = lay.getOffsetForHorizontal(line, ev.getX());
                        if (off < s.length() - 2) return;

                        if (ev.getAction() == MotionEvent.ACTION_UP) {
                            String clean = s.substring(0, s.length() - 2).trim();
                            if (s.endsWith(" 🔄")) {
                                String orig = AITranslator.getForeignByDraftChinese(clean);
                                if (orig == null) orig = AITranslator.getForeignByChinese(clean);
                                if (orig == null) orig = AITranslator.getForeignFuzzy(clean);
                                if (orig != null && !orig.equals(clean)) tv.setText(orig + " 🌐");
                            } else {
                                String zh = AITranslator.getDraftFuzzy(clean);
                                if (zh == null) zh = AITranslator.getChineseByForeign(clean);
                                if (zh != null && !zh.equals(clean)) tv.setText(zh + " 🔄");
                            }
                            p.setResult(true);
                        }
 else if (ev.getAction() == MotionEvent.ACTION_DOWN) {
                            p.setResult(true);
                        }
                    }
                });
    }

    private static void hookStartChat(ClassLoader cl) throws Exception {

    // ===== 旧版 HelloTalk =====
    // 保留原来的 startChat 逻辑，保证旧版不受影响
    try {
        XposedHelpers.findAndHookMethod(
                "com.hellotalk.talk.detail.data.source.ChatDetailViewModel", cl,
                "startChat", int.class, int.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam p) {
                        currentChatId = String.valueOf(p.args[0]);
                        currentChatType = (int) p.args[1];
                        latestNationality = "";
                        latestNativeLang = 1;
                        latestPartnerName = "";
                        currentPartnerName = "";
                        currentQuotedImagePath = null;
                        currentQuotedImageMissing = false;
                        resetSelectedReply();

                        final Object vm = p.thisObject;
new Thread(() -> {
    try {
        Field f;
        try {
            f = vm.getClass().getDeclaredField("chatUser");
        } catch (NoSuchFieldException e) {
            f = vm.getClass().getDeclaredField("o");
        }
        f.setAccessible(true);
        for (int i = 0; i < 6; i++) {
            Object cu = f.get(vm);
            if (cu != null) {
                updateFromChatUser(cu);
                return;
            }
            Thread.sleep(500);
        }
    } catch (Exception ignored) {}
}).start();
                    }
                });
    } catch (Throwable t) {
        log("旧版 startChat 不存在，启用新版 ChatDetailFragment 方案");
    }

    // ===== 新版 HelloTalk 6.4.0 =====
    // 新版 ChatDetailViewModel.startChat 已经不存在，
    // chatId 保存在 ChatDetailFragment 中，由 H3() 返回。
    try {
        XposedHelpers.findAndHookMethod(
                "com.hellotalk.talk.detail.fragment.ChatDetailFragment",
                cl,
                "H3",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam p) {
                        try {
                        // 新版：保存当前聊天页面本身。
// 后面点击“译”时直接从这个页面读取真实消息列表。
currentChatDetailFragment = p.thisObject;

                            Object result = p.getResult();
                            if (!(result instanceof Integer)) return;

                            int cid = (Integer) result;

                            // 0 表示当前还没有有效会话
                            if (cid <= 0) return;

                            String newChatId = String.valueOf(cid);

                            // 只有真正切换聊天时才清理上一聊天的数据
                            if (!newChatId.equals(currentChatId)) {
                                currentChatId = newChatId;
                                latestNationality = "";
                                latestNativeLang = 1;
                                latestPartnerName = "";
                                currentPartnerName = "";
                                currentQuotedImagePath = null;
                                currentQuotedImageMissing = false;
                                resetSelectedReply();

                                log("新版 ChatDetailFragment.H3() 获取 chatId = " + newChatId);
                                // 获取对方国籍/母语/昵称
final Object fragment = p.thisObject;
new Thread(() -> {
    try {
        for (int i = 0; i < 8; i++) {
            Object vm = XposedHelpers.callMethod(fragment, "Q3");
            if (vm != null) {
                Object cu = XposedHelpers.callMethod(vm, "getChatUser");
                if (cu != null) {
                    updateFromChatUser(cu);
                    return;
                }
            }
            Thread.sleep(400);
        }
    } catch (Exception ignored) {}
}).start();
                            } else {
                                currentChatId = newChatId;
                            }

                        } catch (Throwable t) {
                            log("新版 H3 chatId 获取失败: " + t.getMessage());
                        }
                    }
                });

        log("新版 ChatDetailFragment.H3 Hook 注册成功");

    } catch (Throwable t) {
        log("新版 ChatDetailFragment.H3 Hook 注册失败: " + t.getMessage());
    }
}

    private static void updateFromChatUser(Object chatUser) {
    try {
        int nl = (Integer) callObjMethod(chatUser, "getNativeLang", "T");
        String nat = (String) callObjMethod(chatUser, "getNationality", "S");
        String nn = (String) callObjMethod(chatUser, "getNickName", "R");
        String un = (String) callObjMethod(chatUser, "getUserName", "l0");

        latestNativeLang = nl;
        latestNationality = nat != null ? nat : "";
        latestPartnerName = (nn != null && !nn.isEmpty()) ? nn : (un != null ? un : "");

        if (!latestPartnerName.isEmpty()) currentPartnerName = latestPartnerName;

        
        log("国籍原文: [" + latestNationality + "] 母语码: " + nl);
    } catch (Throwable ignored) {}
}

private static Object callObjMethod(Object obj, String oldName, String newName) {
    try { return XposedHelpers.callMethod(obj, oldName); }
    catch (Throwable t) { return XposedHelpers.callMethod(obj, newName); }
}

    private static void hookLang(ClassLoader cl) throws Exception {
        Class<?> vm = XposedHelpers.findClass("com.hellotalk.talk.detail.data.source.ChatDetailViewModel", cl);
        Field uf = vm.getDeclaredField("chatUser");
        uf.setAccessible(true);

        Class<?> hm = cl.loadClass("com.hellotalk.lib.im.entity.HTIMMessage");

        XposedHelpers.findAndHookMethod(vm, "generateChatMessage", hm, boolean.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam p) {
                        try {
                            Object u = uf.get(p.thisObject);
                            if (u != null) {
                                partnerLang = (Integer) XposedHelpers.callMethod(u, "getNativeLang");
                            }
                        } catch (Throwable ignored) {}
                    }
                });
    }

    private static void hookImageRenderLayer(ClassLoader cl) {
        try {
            Class<?> imc = XposedHelpers.findClassIfExists(
                    "com.hellotalk.talk.detail.widget.msgcard.ImageMsgCard", cl);
            if (imc != null) {
                XposedBridge.hookAllMethods(imc, "c", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam p) throws Throwable {
                        if (p.args == null || p.args.length < 2) return;

                        Object ib = p.args[0];
                        Object fpo = p.args[1];
                        if (!(fpo instanceof String)) return;

                        String fp = (String) fpo;
                        File img = new File(fp);
                        if (!img.exists() || img.length() <= 0) return;

                        String url = null;
                        String cu = null;
                        try { url = (String) XposedHelpers.callMethod(ib, "getUrl"); } catch (Throwable ignored) {}
                        try { cu = (String) XposedHelpers.callMethod(ib, "getCompressedUrl"); } catch (Throwable ignored) {}

                        putImageMapping(url, fp);
                        putImageMapping(cu, fp);
                        putImageMapping(safeNormalize(url), fp);
                        putImageMapping(safeNormalize(cu), fp);

                        String un = fileNameFromUrl(url);
                        String cn = fileNameFromUrl(cu);
                        if (un != null) putImageMapping("fname:" + un, fp);
                        if (cn != null) putImageMapping("fname:" + cn, fp);

                        addRenderedImageRecord(fp, url, cu);
                    }
                });
            }
        } catch (Throwable ignored) {}
    }

        private static void hookRecv(ClassLoader cl) throws Exception {
    Class<?> hm = cl.loadClass("com.hellotalk.lib.im.entity.HTIMMessage");

    XC_MethodHook recvHook = new XC_MethodHook() {
        @Override
        protected void afterHookedMethod(MethodHookParam p) {
            try {
                Object msg = p.thisObject;
                ensureMsgMethods(msg);

                Object iso = invokeQuiet(mIsSender, msg);
                if (!(iso instanceof Boolean)) return;
                boolean isMine = (Boolean) iso;

                Object bean = p.getResult();
                if (bean == null) return;

                String eid = "0";
Object cidO = invokeQuiet(mGetChatId, msg);
if (cidO != null) eid = String.valueOf(cidO);

// ===== 新版 HelloTalk 历史记录修复 =====
// 新版部分 HTIMMessage 的 chatId getter 会得到 0/null。
// 旧版保持原行为；只有检测到新版 m4t 时，才用当前聊天页 H3()
// 已确认的 currentChatId 作为兜底。
boolean eidInvalid = eid == null
        || eid.trim().isEmpty()
        || "0".equals(eid)
        || "null".equalsIgnoreCase(eid);

if (eidInvalid && newReplyControllerDetected
        && currentChatId != null
        && !currentChatId.trim().isEmpty()
        && !"0".equals(currentChatId)
        && !"null".equalsIgnoreCase(currentChatId)) {

    log("新版接收历史 chatId 兜底: raw=" + eid
            + " current=" + currentChatId);

    eid = currentChatId;
    eidInvalid = false;
}

if (eidInvalid) return;

final String chatId = eid;

                String sn = null;
                Object sno = invokeQuiet(mGetSenderName, msg);
                if (sno != null) sn = String.valueOf(sno);
                if (sn != null && !sn.isEmpty() && !isMine) {

    // 旧版保持原样；新版收到对方消息不创建遥控好友
    if (!newReplyControllerDetected) {
        AITranslator.registerFriend(
                chatId,
                sn,
                AITranslator.getFriendLang(chatId),
                latestNationality
        );
    }
}
                

                Method gtm = ensureBeanGetText(bean);
                Object to = invokeQuiet(gtm, bean);
                String text = (to != null) ? String.valueOf(to) : null;

                Object mto = invokeQuiet(mGetMsgType, msg);
                String mt = (mto != null) ? String.valueOf(mto) : null;

                if (text == null || text.isEmpty()) {
                    if ("image".equals(mt) || "photo".equals(mt)) text = "[对方发送了一张图片]";
                    else if ("voice".equals(mt) || "audio".equals(mt)) text = "[对方发送了一条语音]";
                    else if ("video".equals(mt)) text = "[对方发送了一段视频]";
                    else if ("emoji".equals(mt) || "sticker".equals(mt)) text = "[对方发送了一个表情包]";
                    else return;
                }

                if (isMine
        && pendingSelectedForeign != null
        && pendingSelectedForeign.equals(text)) {

    // ===== 新版：只有翻译结果真正发送出去以后才创建遥控好友 =====
    if (newReplyControllerDetected
            && chatId != null
            && !chatId.trim().isEmpty()
            && !"0".equals(chatId)
            && !"null".equalsIgnoreCase(chatId)) {

        String friendName = currentPartnerName;

        if (friendName == null || friendName.trim().isEmpty()) {
            friendName = latestPartnerName;
        }

        if (friendName == null) {
            friendName = "";
        }

        String manualLang = chatLangOverride.get(chatId);

        String targetLang =
                (manualLang != null && !manualLang.isEmpty())
                        ? manualLang
                        : determineSmartTargetLang(
                                latestNationality,
                                latestNativeLang,
                                chatId
                        );

        AITranslator.registerFriend(
                chatId,
                friendName,
                targetLang,
                latestNationality
        );

        log("新版真实发送翻译消息，创建HT遥控好友: chatId="
                + chatId
                + " name="
                + friendName
                + " lang="
                + targetLang);
    }

    pendingSelectedForeign = null;
    lastPickerResult = null;

    uiHandler.post(() -> {
        if (versionButton != null) {
            versionButton.setVisibility(View.GONE);
        }
    });
}
                Object mio = invokeQuiet(mGetMsgId, msg);
                String mid = (mio != null) ? String.valueOf(mio) : ("n_" + text.hashCode());

                long st = System.currentTimeMillis();
                Object sto = invokeQuiet(mGetSendTime, msg);
                if (sto instanceof Long) st = (Long) sto;

                boolean isNew = recordedMsgIds.add(chatId + "_" + mid);
                if (isNew && !shouldSkipHistory(text)) {
                    final String fm = mid; final String ft = text; final long fst = st; final boolean fmn = isMine;
                    historyExecutor.execute(() -> {
                        if (fmn) AITranslator.appendHistory(chatId, fm, "assistant", ft, fst, null, false);
                        else AITranslator.appendHistory(chatId, fm, "user", ft, fst, null, false);
                    });
                }

                if (text.startsWith("[")) return;
                if (AITranslator.containsJapanese(text) || AITranslator.isChineseOnly(text)) return;

                if (isMine) {
                    // 反向翻译：只查本地缓存，不调API
                    String d = AITranslator.getDraftFuzzy(text);
                    if (d == null) d = AITranslator.getChineseByForeign(text);
                    if (d != null && !d.isEmpty()) {
                        AITranslator.cacheResult(mid, text, d);
                        final Object fbk = bean; final String ftk = text;
                        reverseTranslateExecutor.execute(() -> {
                            try { Thread.sleep(150); } catch (InterruptedException ignored) {}
                            try { setBeanField(fbk, ftk); } catch (Exception ignored) {}
                        });
                        return;
                    }
                    // 缓存没有，启动反向翻译 API
                    final String ft2 = text; final String fc2 = chatId; final String fm2 = mid; final Object fb2 = bean;
                    if (reverseTranslatedMsgIds.add(fm2)) {
                        reverseTranslateExecutor.execute(() -> {
                            try {
                                String existDraft = AITranslator.getDraftFuzzy(ft2);
                                if (existDraft != null && !existDraft.isEmpty()) {
                                    AITranslator.cacheResult(fm2, ft2, existDraft);
                                    return;
                                }
                                String zh = AITranslator.reverseTranslateMyForeign(ft2, fc2);
                                if (zh != null && !zh.isEmpty()) {
                                    AITranslator.cacheResult(fm2, ft2, zh);
                                    AITranslator.rememberDraftIfAbsent(ft2, zh);
                                    reverseRetryMap.remove(fm2);
                                    try { setBeanField(fb2, ft2); } catch (Exception ignored) {}
                                }
                            } catch (Exception ignored) {}
                        });
                    }
                    return;
                }

                // 对方消息：只查缓存，不调API（翻译由 hookTextViewRender 负责）
                String[] cached = AITranslator.getCached(mid);
                if (cached != null && cached[0] != null && cached[0].equals(text)) {
                    try {
                        setBeanField(bean, cached[1].replaceAll("[\\s🌐🔄]+$", "") + " 🔄");
                    } catch (Exception ignored) {}
                }

            } catch (Throwable ignored) {}
        }
    };

    try {
        XposedHelpers.findAndHookMethod(hm, "getMessageContent", Class.class, boolean.class, recvHook);
    } catch (Throwable t) {
        log("hookRecv old fail: " + t.getMessage());
    }

    try {
        XposedHelpers.findAndHookMethod(hm, "B", Class.class, recvHook);
    } catch (Throwable t) {
        log("hookRecv new B fail: " + t.getMessage());
    }
}

// 直接反射写字段，绕过所有方法名混淆
private static void setBeanField(Object bean, String text) {
    try { XposedHelpers.callMethod(bean, "setText", text); return; } catch (Throwable t1) {}
    try { XposedHelpers.callMethod(bean, "C", text); } catch (Throwable t2) {}
    try { XposedHelpers.callMethod(bean, "J", text); } catch (Throwable t3) {}
    try {
        Field f = bean.getClass().getDeclaredField("reportText");
        f.setAccessible(true);
        f.set(bean, text);
    } catch (Throwable t4) {}
    try {
        Field f = bean.getClass().getDeclaredField("text");
        f.setAccessible(true);
        f.set(bean, text);
    } catch (Throwable t5) {}
}

    private static void hookBtnOld(ClassLoader cl) throws Exception {
        Class<?> bc = XposedHelpers.findClass("com.hellotalk.chat.ui.ChatInputBoxView", cl);
        XposedBridge.hookAllConstructors(bc, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam p) {
                ((View) p.thisObject).postDelayed(() -> tryAddBtn((View) p.thisObject), 2000);
            }
        });
    }

    private static void hookBtnNew(ClassLoader cl) throws Exception {
        Class<?> oc = XposedHelpers.findClass(
                "com.hellotalk.talk.detail.widget.input.ChatInputUIOperate", cl);
        XposedBridge.hookAllConstructors(oc, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam p) {
                ((View) p.thisObject).postDelayed(() -> tryAddBtn((View) p.thisObject), 2500);
            }
        });
    }

    private static void tryAddBtn(View box) {
        EditText edit = findEditIn(box);
        if (edit != null) addTranslateBtn((ViewGroup) box, edit);
    }

    private static EditText findEditIn(View v) {
        try {
            for (Field f : v.getClass().getDeclaredFields()) {
                f.setAccessible(true);
                Object val = f.get(v);
                if (val instanceof EditText) return (EditText) val;
            }
        } catch (Exception ignored) {}

        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                View c = g.getChildAt(i);
                if (c instanceof EditText) return (EditText) c;
                EditText found = findEditIn(c);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void updateTranslateBtnText(Button btn) {
        if (btn == null) return;
        String cid = currentChatId;
        String ov = (cid == null) ? null : chatLangOverride.get(cid);
        if (ov == null || ov.isEmpty()) btn.setText("译");
        else btn.setText("译·" + ov.toUpperCase());
    }

        private static void showLanguagePicker(Button btn, EditText edit) {
        if (btn == null || edit == null) return;
        android.content.Context ctx = edit.getContext();
        final String cid = currentChatId;
        if (cid == null || cid.isEmpty() || "0".equals(cid) || "null".equals(cid)) {
            Toast.makeText(ctx, "⚠️ 会话尚未就绪，请退出重新进入", Toast.LENGTH_SHORT).show();
            return;
        }

        android.widget.LinearLayout root = new android.widget.LinearLayout(ctx);
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        root.setPadding(0, 24, 0, 0);

        // ============ 全局同步：一次性复选框 ============
        final android.content.SharedPreferences sp = ctx.getSharedPreferences("htai_quick_prefs", android.content.Context.MODE_PRIVATE);
        final android.widget.CheckBox checkBoxOneTime = new android.widget.CheckBox(ctx);
        checkBoxOneTime.setText("仅本次 (全局同步，不污染历史记忆)");
        checkBoxOneTime.setTextSize(13f);
        checkBoxOneTime.setTextColor(Color.parseColor("#B02A37"));
        checkBoxOneTime.setPadding(48, 16, 48, 16);
        checkBoxOneTime.setChecked(sp.getBoolean("always_one_time", false));
        checkBoxOneTime.setOnCheckedChangeListener((btnView, isChecked) -> {
            sp.edit().putBoolean("always_one_time", isChecked).apply();
            Toast.makeText(ctx, isChecked ? "✅ 已开启仅本次" : "❌ 已关闭仅本次", Toast.LENGTH_SHORT).show();
        });
        root.addView(checkBoxOneTime);

        // ============ 快捷指令滑动条 ============
        TextView tagHeader = new TextView(ctx);
        tagHeader.setText("⚡ 快捷调教指令");
        tagHeader.setTextSize(12f);
        tagHeader.setTextColor(Color.GRAY);
        tagHeader.setPadding(48, 16, 48, 8);
        root.addView(tagHeader);

        android.widget.HorizontalScrollView hsv = new android.widget.HorizontalScrollView(ctx);
        hsv.setHorizontalScrollBarEnabled(false);
        android.widget.LinearLayout quickBar = new android.widget.LinearLayout(ctx);
        quickBar.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        quickBar.setPadding(36, 0, 36, 16);
        hsv.addView(quickBar);

        final android.app.AlertDialog[] dialogRef = new android.app.AlertDialog[1];

        for (int i = 1; i <= 5; i++) {
            String raw = AITranslator.getQuickOption(i);
            if (raw.isEmpty()) continue;
            String[] parts = raw.split("\\|", 2);
            final String tag = parts.length > 0 ? parts[0].trim() : "";
            final String tagContent = parts.length > 1 ? parts[1].trim() : tag;
            if (tag.isEmpty()) continue;

            Button tagBtn = new Button(ctx);
            tagBtn.setText(tag);
            tagBtn.setTextSize(12f);
            tagBtn.setAllCaps(false);
            tagBtn.setPadding(24, 8, 24, 8);
            GradientDrawable tbg = new GradientDrawable();
            tbg.setColor(Color.parseColor("#E8E8E8"));
            tbg.setCornerRadius(16f);
            tagBtn.setBackground(tbg);
            tagBtn.setTextColor(Color.parseColor("#333333"));
            android.widget.LinearLayout.LayoutParams tlp = new android.widget.LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tlp.setMargins(0, 0, 16, 0);
            tagBtn.setLayoutParams(tlp);

            tagBtn.setOnClickListener(v2 -> {
                String origChinese = edit.getText().toString();
                String newPrompt = origChinese;
                if (newPrompt.endsWith("）") || newPrompt.endsWith(")")) {
                    newPrompt = newPrompt.replaceAll("[（\\(][^）\\)]*[）\\)]$", "").trim();
                }
                if (tag.contains("火力全开") || checkBoxOneTime.isChecked()) {
                    newPrompt = "一次性：" + newPrompt + " （" + tagContent + "）";
                } else {
                    newPrompt = newPrompt + " （" + tagContent + "）";
                }
                edit.setText(newPrompt);
                edit.setSelection(newPrompt.length());
                if (dialogRef[0] != null) dialogRef[0].dismiss();
                edit.post(() -> btn.performClick());
            });
            quickBar.addView(tagBtn);
        }
        root.addView(hsv);

        // ============ 语言列表区 ============
        View div = new View(ctx);
        div.setBackgroundColor(Color.parseColor("#DDDDDD"));
        div.setLayoutParams(new android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 2));
        root.addView(div);

        final String[] codes = {"auto", "en", "es", "ru", "uk", "ko", "ar", "pt", "fr", "de", "it", "tr", "nl", "pl", "kk", "cs"};
        final String[] names = {"🌐 自动判断", "英语 English", "西班牙语 Español", "俄语 Русский", "乌克兰语 Українська", "韩语 한국어", "阿拉伯语 العربية", "葡萄牙语 Português", "法语 Français", "德语 Deutsch", "意大利语 Italiano", "土耳其语 Türkçe", "荷兰语 Nederlands", "波兰语 Polski", "哈萨克语 Қазақша", "捷克语 Čeština"};
        
        android.widget.ListView listView = new android.widget.ListView(ctx);
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(ctx, android.R.layout.simple_list_item_1, names);
        listView.setAdapter(adapter);
        root.addView(listView);

        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(ctx)
                .setTitle("长按菜单 (快捷指令 & 临时语言)")
                .setView(root)
                .setNegativeButton("取消", null)
                .create();
        
        dialogRef[0] = dialog;

        listView.setOnItemClickListener((parent, view, position, id) -> {
            String code = codes[position];
            if ("auto".equals(code)) chatLangOverride.remove(cid);
            else chatLangOverride.put(cid, code);
            updateTranslateBtnText(btn);
            Toast.makeText(ctx, "当前聊天语言已设为：" + names[position], Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        dialog.show();
    }


    private static void addTranslateBtn(ViewGroup layout, EditText edit) {
        try {
            edit.setLongClickable(true);
            edit.setTextIsSelectable(true);
            edit.setFocusable(true);
            edit.setFocusableInTouchMode(true);
        } catch (Throwable ignored) {}

        if ("HT_AI_BTN".equals(String.valueOf(layout.getTag()))) return;

        Button btn = new Button(layout.getContext());
        btn.setText("译");
        btn.setTextSize(12f);
        btn.setAllCaps(false);
        btn.setPadding(12, 4, 12, 4);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#CC333333"));
        bg.setCornerRadius(8f);
        btn.setBackground(bg);
        btn.setTextColor(Color.parseColor("#FFFFFFFF"));
        btn.setAlpha(0.95f);
        btn.setVisibility(View.GONE);
        layout.addView(btn, 0);
        layout.setTag("HT_AI_BTN");

        Button verBtn = new Button(layout.getContext());
        verBtn.setText("版本");
        verBtn.setTextSize(12f);
        verBtn.setAllCaps(false);
        verBtn.setPadding(12, 4, 12, 4);

        GradientDrawable vbg = new GradientDrawable();
        vbg.setColor(Color.parseColor("#0B5ED7"));
        vbg.setCornerRadius(8f);
        verBtn.setBackground(vbg);
        verBtn.setTextColor(Color.parseColor("#FFFFFFFF"));
        verBtn.setAlpha(0.95f);
        verBtn.setVisibility(View.GONE);
        layout.addView(verBtn, 0);

        versionButton = verBtn;
        versionEdit = edit;

        verBtn.setOnClickListener(v -> {
            if (lastPickerResult != null) {
                showPicker(edit, btn, lastPickerResult, lastPickerOrig, lastPickerPns, lastPickerOneTime);
            } else {
                Toast.makeText(edit.getContext(), "暂无可选版本", Toast.LENGTH_SHORT).show();
            }
        });
        btn.setOnLongClickListener(v -> {
            if (isTranslatingAPI) Toast.makeText(edit.getContext(), "翻译中，请稍候", Toast.LENGTH_SHORT).show();
            else showLanguagePicker(btn, edit);
            return true;
        });

        final View[] nsb = new View[1];

        Runnable ev = new Runnable() {
            @Override
            public void run() {
                if (nsb[0] == null) nsb[0] = findNativeSendBtn(layout);

                String ct = edit.getText().toString().replace("@", "");
                if (!ct.trim().isEmpty() && AITranslator.isChineseOnly(ct)) {
                    if (!isTranslatingAPI) {
                        btn.setVisibility(View.VISIBLE);
                        btn.setEnabled(true);
                        updateTranslateBtnText(btn);
                        btn.setAlpha(0.93f);
                    }
                    if (nsb[0] != null) nsb[0].setVisibility(View.GONE);
                } else {
                    if (!isTranslatingAPI) btn.setVisibility(View.GONE);
                    if (nsb[0] != null && !ct.trim().isEmpty()) nsb[0].setVisibility(View.VISIBLE);
                }
            }
        };

        edit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}

            @Override
            public void afterTextChanged(Editable s) {
                edit.post(ev);

                String now = s == null ? "" : s.toString();
                if (pendingSelectedForeign != null && now.trim().isEmpty()) {
                    pendingSelectedForeign = null;
                    lastPickerResult = null;
                    uiHandler.post(() -> {
                        if (versionButton != null) versionButton.setVisibility(View.GONE);
                    });
                }
            }

            @Override
            public void onTextChanged(CharSequence s, int st, int b, int c) {
                if (s != null && isTranslatingAPI && s.toString().contains("@")) {
                    AITranslator.cancelOngoingTranslation();
                    String cl = s.toString().replace("@", "");
                    edit.removeTextChangedListener(this);
                    edit.setText(cl);
                    edit.setSelection(cl.length());
                    edit.addTextChangedListener(this);
                }
            }
        });

        edit.postDelayed(ev, 100);
        edit.postDelayed(ev, 500);

        btn.setOnClickListener(v -> {
            String text = edit.getText().toString().trim();
            if (text.isEmpty() || !AITranslator.isChineseOnly(text)) return;

            if (verBtn.getVisibility() == View.VISIBLE) {
                verBtn.setVisibility(View.GONE);
            }

            pendingSelectedForeign = null;
            lastPickerResult = null;

            boolean oneTime = text.startsWith("一次性：")
                    || text.startsWith("一次性:")
                    || text.startsWith("[一次性]");

            if (text.startsWith("一次性：")) {
                text = text.substring("一次性：".length()).trim();
            } else if (text.startsWith("一次性:")) {
                text = text.substring("一次性:".length()).trim();
            } else if (text.startsWith("[一次性]")) {
                text = text.substring("[一次性]".length()).trim();
            }

            if (text.isEmpty()) return;

            if (!oneTime) {
                int p1 = text.indexOf("（");
                int p2 = text.indexOf("）");
                if (p1 >= 0 && p2 > p1 && text.substring(p1, p2 + 1).contains("一次性")) {
                    oneTime = true;
                }
            }

            final boolean oneTimeFinal = oneTime;

            String cid = currentChatId;
            if (cid == null || cid.isEmpty() || "0".equals(cid) || "null".equals(cid)) {
                Toast.makeText(edit.getContext(), "⚠️ 会话尚未就绪，请退出聊天重新进入后再试", Toast.LENGTH_SHORT).show();
                return;
            }

            isTranslatingAPI = true;
            btn.setEnabled(false);
            btn.setText("...");
            btn.setAlpha(1.0f);

            final String cs = cid;
            final int cts = currentChatType;
            final String pns = currentPartnerName;
            final String nats = latestNationality;
            final int nls = latestNativeLang;

            
           
            // 新版 HelloTalk：在点击“译”的这一刻重新读取真正的回复对象。
// 旧版没有 m4t，所以旧版什么都不会改变。
refreshSelectedReplyFromNewController();

boolean hasSelectedReply = selectedReplyValid;
boolean selectedReplyMine = selectedReplyIsMine;
String quote = selectedReplyText;
            final String qis = currentQuotedImagePath;
            final boolean qms = currentQuotedImageMissing;

            boolean pbm = isPureBracketQuery(text);
            String cleanText = text;
            if (pbm) {
                if (cleanText.startsWith("(") && cleanText.endsWith(")")) cleanText = cleanText.substring(1, cleanText.length() - 1).trim();
                else if (cleanText.startsWith("（") && cleanText.endsWith("）")) cleanText = cleanText.substring(1, cleanText.length() - 1).trim();
            }

            String ttt = cleanText;
// ===== 新版：括号问答直接使用 HelloTalk 当前真实消息列表 =====
//
// 只处理：
// 1. 新版 HelloTalk
// 2. 括号问答模式
// 3. 没有显式选择回复对象
//
// 已经正常工作的：普通翻译 / 回复框翻译 / 旧版 HelloTalk
// 都不会进入这里。
if (pbm
        && newReplyControllerDetected
        && !hasSelectedReply) {

    String liveContext = buildNewLiveChatContext(
            AITranslator.getMaxChatMessagesForHook()
    );

    if (liveContext != null && !liveContext.trim().isEmpty()) {

        ttt =
                "【新版实时上下文优先规则】\n"
                + "下面的 HelloTalk 实时对话由程序直接从当前聊天页面读取，"
                + "真实性高于模块旧历史文件。"
                + "如果旧历史与这里冲突，请忽略旧历史。"
                + "纯数字聊天ID不是聊天内容，不得把聊天ID当成任何一方说过的话。\n\n"
                + liveContext
                + "\n【我的问题】\n"
                + cleanText;

        log("新版括号问答已使用实时聊天列表");

    } else {

        log("新版括号问答实时聊天列表读取失败，继续旧方式");
    }
}
            if (hasSelectedReply && quote != null && !quote.trim().isEmpty()) {
                String orig = AITranslator.getForeignFuzzy(quote);
                if (orig != null) quote = orig;
                if (selectedReplyMine) {
                    if (pbm) {
                        ttt = "【我选中的我自己的历史消息】：" + quote.trim() + "\n【我对这条消息的疑问/提问】：" + cleanText;
                    } else {
                        ttt = "【我对我自己之前这条外语消息的补充】：" + quote.trim() + "\n【补充内容】：" + cleanText;
                    }
                } else {
                    if (pbm) {
                        ttt = "【我选中的对方原话】：" + quote.trim() + "\n【我关于这句话的提问/要求】：" + cleanText;
                    } else {
                        ttt = "【我要回复的对方原话】：" + quote.trim() + "\n【我的回复】：" + cleanText;
                    }
                }
            }

// ===== 新版括号问答增强 =====
// 如果新版回复框已经明确选中一条消息，就告诉 AI：
// 这是程序直接从 m4t.f() 取得的真实引用，不允许再说“不知道对方说了什么”。
// 旧版没有 newReplyControllerDetected，因此完全不受影响。
if (pbm && newReplyControllerDetected
        && hasSelectedReply
        && quote != null
        && !quote.trim().isEmpty()) {

    ttt = "【程序已确认当前选中消息】以下被选原话来自 HelloTalk 当前回复框，"
            + "是真实且明确的上下文。"
            + "回答时必须直接依据这条原话，不得声称不知道对方说了什么或上下文不足。\n"
            + ttt;
}

// 新版括号问答调试：确认实际送进 askAiQuestion() 的文本。
if (pbm && newReplyControllerDetected) {
    log("新版括号问答实际输入: "
            + ttt.replace("\n", " | "));
}
            if (qis != null) {
                File qf = new File(qis);
                if (qf.exists() && qf.length() > 0) {
                    ttt += "\n[QUOTED_LOCAL_IMAGE:" + qis + "]";
                }
            } else if (qms) {
                ttt += "\n[QUOTED_IMAGE_BUT_PATH_MISSING]";
            }

// ===== 新版普通翻译：读取当前页面真实聊天上下文 =====
//
// 括号问答前面已经单独处理。
// 这里给新版普通翻译和新版回复框翻译准备真实上下文，
// 用于替代已经确认有问题的模块旧 history。
String liveTranslateContext = null;

if (!pbm && newReplyControllerDetected) {

    liveTranslateContext = buildNewLiveChatContext(
            AITranslator.getMaxChatMessagesForHook()
    );
}
            final String ftt = ttt;
            final String rci = text;
            final String flive = liveTranslateContext;
            if (qis != null) currentQuotedImagePath = null;

            if (pbm) {
                AITranslator.markNoHistory(rci);
                String inner = rci;
                if (inner.startsWith("(") && inner.endsWith(")")) inner = inner.substring(1, inner.length() - 1).trim();
                else if (inner.startsWith("（") && inner.endsWith("）")) inner = inner.substring(1, inner.length() - 1).trim();
                AITranslator.markNoHistory(inner);
            }

            if (qis != null) {
                final String noteChat = cs;
                final String notePath = qis;
                final boolean noteMine = selectedReplyMine;
                new Thread(() -> AITranslator.rememberImageNote(noteChat, notePath, noteMine)).start();
            }

            new Thread(() -> {
                try {
                    if (pbm) {
                        String answer;

// ===== 新版 HelloTalk =====
// 新版括号问答绝不再读取模块旧 history。
// 哪怕当前实时上下文只有 0 条、1 条、2 条消息，
// 没有就回答没有，绝不能拿 chatId 冒充聊天内容。
if (newReplyControllerDetected) {

    answer = AITranslator.askAiQuestionLive(
            ftt,
            cs
    );

} else {

    // 旧版 HelloTalk 保持原样。
    answer = AITranslator.askAiQuestion(
            ftt,
            cs
    );
}
                        isTranslatingAPI = false;
                        edit.post(() -> {
                            btn.setEnabled(true);
                            updateTranslateBtnText(btn);
                            btn.setAlpha(0.92f);
                            showAnswerDialog(edit, answer);
                        });
                    } else {
                        String manualLang = chatLangOverride.get(cs);
                        String tl = (manualLang != null && !manualLang.isEmpty()) ? manualLang : determineSmartTargetLang(nats, nls, cs);
                        if (!newReplyControllerDetected && cts == 1) {
    AITranslator.registerFriend(cs, pns, tl, nats);
}

                        String lr = chatRequestMap.get(cs);
                        boolean retry = ftt.equals(lr);
                        if (retry) {
                            chatRetryCountMap.put(cs, chatRetryCountMap.getOrDefault(cs, 0) + 1);
                        } else {
                            chatRequestMap.put(cs, ftt);
                            chatRetryCountMap.put(cs, 0);
                        }

                        String result;

// ===== 新版 HelloTalk =====
// 已经成功读取到当前页面真实消息时，
// 普通翻译和回复框翻译都使用真实 UI 上下文，
// 完全不读取错误的 loadHistory()。
if (newReplyControllerDetected
        && flive != null
        && !flive.trim().isEmpty()) {

    result = AITranslator.translateForPickerLive(
            ftt,
            tl,
            cs,
            retry,
            flive
    );

} else {

    // 旧版 HelloTalk 或实时列表读取失败：
    // 完全保持原来的旧逻辑。
    result = AITranslator.translateForPicker(
            ftt,
            tl,
            cs,
            retry
    );
}
                        isTranslatingAPI = false;
                        String fr = result;

                        edit.post(() -> {
                            btn.setEnabled(true);
                            updateTranslateBtnText(btn);
                            btn.setAlpha(0.92f);
                            showPicker(edit, btn, fr, rci, pns, oneTimeFinal);
                        });
                    }
                } catch (Exception e) {
                    isTranslatingAPI = false;
                    chatRequestMap.remove(cs);
                    chatRetryCountMap.put(cs, 0);

                    edit.post(() -> {
                        btn.setEnabled(true);
                        updateTranslateBtnText(btn);
                        btn.setAlpha(0.88f);
                        Toast.makeText(edit.getContext(),
                                "⚠️ 失败: " + (e.getMessage() != null ? e.getMessage() : "未知错误"),
                                Toast.LENGTH_LONG).show();
                    });
                }
            }).start();
        });
    }

    private static String determineSmartTargetLang(String nat, int nl, String cid) { log("SmartTargetLang: nat=" + nat + " nl=" + nl + " cid=" + cid);
        String n = nat == null ? "" : nat.toLowerCase();
        if (!n.isEmpty()) {
            String ml = mapNationalityToLang(n);
            if (ml != null) return ml;
        }

        String lc = getDynamicLangCode(nl);
        if (lc != null && !lc.isEmpty() && !"en".equals(lc)) return lc;

        String fl = AITranslator.getFriendLang(cid);
        if (fl != null && !fl.isEmpty()) {
            if (fl.equalsIgnoreCase("zh") || fl.startsWith("zh")) return DEFAULT_REPLY_LANG;
            return fl;
        }
        return DEFAULT_REPLY_LANG;
    }

    private static String getDynamicLangCode(int nl) {
    // 新版 av.a 可能不存在，直接用数字映射
    switch (nl) {
        case 1: return "en";
        case 2: return "ja";
        case 3: return "ko";
        case 4: return "zh";
        case 5: return "ru";
        case 6: return "fr";
        case 7: return "es";
        case 8: return "de";
        case 9: return "it";
        case 10: return "pt";
        case 11: return "ar";
        case 12: return "tr";
        case 13: return "nl";
        case 14: return "pl";
        case 15: return "th";
        case 16: return "vi";
        case 17: return "id";
        case 18: return "hi";
        case 19: return "uk";
        default: return "en";
    }
}

    private static String mapNationalityToLang(String nat) {
    if (nat == null || nat.isEmpty()) return null;
    switch (nat) {
        // 全名
        case "china": case "taiwan": case "hong kong": case "macau": case "singapore": return "zh";
        case "russia": case "belarus": case "kazakhstan": case "kyrgyzstan": return "ru";
        case "japan": return "ja";
        case "korea": case "south korea": return "ko";
        case "france": case "belgium": case "switzerland": case "canada": return "fr";
        case "germany": case "austria": return "de";
        case "spain": case "mexico": case "argentina": case "colombia": case "peru":
        case "chile": case "venezuela": case "ecuador": case "bolivia": case "paraguay":
        case "uruguay": case "costa rica": case "panama": case "nicaragua": case "honduras":
        case "el salvador": case "guatemala": case "cuba": case "dominican republic": case "puerto rico": return "es";
        case "italy": return "it";
        case "portugal": case "brazil": return "pt";
        case "arabia": case "egypt": case "saudi arabia": case "united arab emirates":
        case "morocco": case "algeria": case "tunisia": case "jordan": case "lebanon":
        case "iraq": case "kuwait": case "qatar": case "oman": case "bahrain": return "ar";
        case "turkey": return "tr";
        case "netherlands": return "nl";
        case "poland": return "pl";
        case "vietnam": return "vi";
        case "thailand": return "th";
        case "indonesia": return "id";
        case "india": return "hi";
        case "ukraine": return "uk";
        // ISO 简码（新版 HelloTalk 返回的是这些）
        case "cn": case "tw": case "hk": case "mo": case "sg": return "zh";
        case "ru": case "by": case "kz": case "kg": return "ru";
        case "jp": return "ja";
        case "kr": case "kp": return "ko";
        case "fr": case "be": case "ch": case "ca": return "fr";
        case "de": case "at": return "de";
        case "es": case "mx": case "ar": case "co": case "pe":
        case "cl": case "ve": case "ec": case "bo": case "py":
        case "uy": case "cr": case "pa": case "ni": case "hn":
        case "sv": case "gt": case "cu": case "do": case "pr": return "es";
        case "it": return "it";
        case "pt": case "br": return "pt";
        case "sa": case "eg": case "ae": case "ma": case "dz":
        case "tn": case "jo": case "lb": case "iq": case "kw":
        case "qa": case "om": case "bh": return "ar";
        case "tr": return "tr";
        case "nl": return "nl";
        case "pl": return "pl";
        case "vn": return "vi";
        case "th": return "th";
        case "id": return "id";
        case "in": return "hi";
        case "ua": return "uk";
        default: return null;
    }
}

    private static void showAnswerDialog(EditText edit, String answer) {
        android.content.Context ctx = edit.getContext();
        final String showText = (answer == null) ? "" : answer.trim().replaceAll("\\*+", "");
        android.widget.ScrollView sv = new android.widget.ScrollView(ctx);
        TextView rawTv = new TextView(ctx);
        rawTv.setText(showText);
        rawTv.setTextIsSelectable(true);
        rawTv.setTextSize(14f);
        rawTv.setTextColor(Color.BLACK);
        rawTv.setPadding(32, 24, 32, 24);
        rawTv.setLineSpacing(4f, 1.1f);
        sv.addView(rawTv, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(ctx).setTitle("AI 回答").setView(sv)
                .setPositiveButton("复制", (d, w) -> {
                    try { ((android.content.ClipboardManager) ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("HT_AI_Copy", showText)); Toast.makeText(ctx, "已复制", Toast.LENGTH_SHORT).show(); } catch (Exception ignored) {}
                })
                .setNegativeButton("关闭", null).create();
        dialog.show();
        if (dialog.getWindow() != null) {
            android.util.DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
            dialog.getWindow().setLayout((int)(dm.widthPixels * 0.92), (int)(dm.heightPixels * 0.75));
        }
    }

    private static void showPicker(EditText edit, Button btn, String result, String origChinese, String pn, boolean oneTime) {
        android.content.Context ctx = edit.getContext();

        String at = AITranslator.extractAnalysis(result);
        List<String[]> items = AITranslator.parseTranslateOptions(result);

        if (items.isEmpty()) {
            AITranslator.dumpDebug("picker_fail", result);

            boolean refused = AITranslator.isRefusalResponse(result);
            String showText = result == null ? "" : result.trim();

            android.widget.ScrollView sv = new android.widget.ScrollView(ctx);
            TextView rawTv = new TextView(ctx);
            rawTv.setText(showText);
            rawTv.setTextIsSelectable(true);
            rawTv.setTextSize(13f);
            rawTv.setTextColor(Color.BLACK);
            rawTv.setPadding(32, 24, 32, 24);
            rawTv.setLineSpacing(4f, 1.1f);
            sv.addView(rawTv, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));

            android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(ctx)
                    .setTitle(refused ? "AI 拒绝或触发安全策略" : "AI 未按格式返回")
                    .setView(sv)
                    .setPositiveButton("重试", (d, w) -> edit.post(() -> btn.performClick()))
                    .setNeutralButton("复制原文", (d, w) -> {
                        try {
                            ((android.content.ClipboardManager) ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE))
                                    .setPrimaryClip(ClipData.newPlainText("HT_AI_Copy", showText));
                            Toast.makeText(ctx, "已复制", Toast.LENGTH_SHORT).show();
                        } catch (Exception ignored) {}
                    })
                    .setNegativeButton("取消", null)
                    .create();

            dialog.show();
            if (dialog.getWindow() != null) {
                android.util.DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
                dialog.getWindow().setLayout(
                        (int) (dm.widthPixels * 0.92),
                        (int) (dm.heightPixels * 0.75));
            }
            return;
        }

        android.widget.LinearLayout root = new android.widget.LinearLayout(ctx);
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        root.setPadding(0, 12, 0, 12);

        if (at != null && !at.isEmpty()) {
            TextView header = new TextView(ctx);
            header.setText("📋 分析");
            header.setTextSize(12f);
            header.setTextColor(Color.parseColor("#999999"));
            header.setPadding(48, 12, 48, 4);
            root.addView(header);

            android.widget.ScrollView ts = new android.widget.ScrollView(ctx);
            android.widget.LinearLayout.LayoutParams tsLp = new android.widget.LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
            tsLp.setMargins(0, 0, 0, 16);
            ts.setLayoutParams(tsLp);

            TextView ta = new TextView(ctx);
            ta.setText(at);
            ta.setTextColor(Color.parseColor("#555555"));
            ta.setTextSize(13f);
            ta.setLineSpacing(4f, 1.1f);
            ta.setTextIsSelectable(true);
            ta.setPadding(48, 0, 48, 12);
            ts.addView(ta);
            root.addView(ts);
        }

        TextView optHeader = new TextView(ctx);
        optHeader.setText("💬 选一个发送（共" + items.size() + "个版本）");
        optHeader.setTextSize(12f);
        optHeader.setTextColor(Color.parseColor("#999999"));
        optHeader.setPadding(48, 0, 48, 8);
        root.addView(optHeader);

        android.widget.ScrollView bs = new android.widget.ScrollView(ctx);
        android.widget.LinearLayout.LayoutParams bsLp = new android.widget.LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                (at != null && !at.isEmpty()) ? 2.5f : 1.0f);
        bs.setLayoutParams(bsLp);
        bs.setFillViewport(true);

        android.widget.LinearLayout cont = new android.widget.LinearLayout(ctx);
        cont.setOrientation(android.widget.LinearLayout.VERTICAL);
        cont.setPadding(36, 8, 36, 24);
        bs.addView(cont);
        root.addView(bs);

        String dn = (pn != null && !pn.isEmpty()) ? pn : currentPartnerName;
        String title = (dn != null && !dn.isEmpty()) ? ("选版本 - " + dn) : "选版本";

        final android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(ctx)
                .setTitle(title)
                .setView(root)
                .setNegativeButton("取消", (d, w) -> {})
                .setPositiveButton("🔄 换一批", (d, w) -> edit.post(() -> btn.performClick()))
                .create();

        for (int idx = 0; idx < items.size(); idx++) {
            String[] item = items.get(idx);
            final String foreign = item[0];
            String ch = item[1];
            String lb = item[2];

            android.widget.LinearLayout card = new android.widget.LinearLayout(ctx);
            card.setOrientation(android.widget.LinearLayout.VERTICAL);
            card.setPadding(32, 24, 32, 24);

            android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 8, 0, 12);
            card.setLayoutParams(lp);

            GradientDrawable cbg = new GradientDrawable();
            cbg.setColor(Color.parseColor("#F8F9FA"));
            cbg.setCornerRadius(14f);
            cbg.setStroke(2, Color.parseColor("#DEE2E6"));
            card.setBackground(cbg);

            TextView tf = new TextView(ctx);
            tf.setText((idx + 1) + ". " + foreign);
            tf.setTextColor(Color.parseColor("#212529"));
            tf.setTextSize(15f);
            tf.setTypeface(null, android.graphics.Typeface.BOLD);
            tf.setLineSpacing(3f, 1.1f);
            card.addView(tf);

            if ((ch != null && !ch.isEmpty()) || (lb != null && !lb.isEmpty())) {
                TextView tc = new TextView(ctx);
                String st = (ch != null) ? ch : "";
                if (lb != null && !lb.isEmpty()) st += "  [" + lb + "]";
                tc.setText(st);
                tc.setTextColor(Color.parseColor("#6C757D"));
                tc.setTextSize(12f);
                tc.setPadding(0, 8, 0, 0);
                card.addView(tc);
            }

            card.setOnClickListener(v2 -> {
                String cleanChinese = AITranslator.stripMetaInstructions(origChinese);

                if (cleanChinese != null && !cleanChinese.isEmpty()) {
                    AITranslator.rememberDraft(foreign, cleanChinese);
                }
                
                boolean isAlwaysOneTime = ctx.getSharedPreferences("htai_quick_prefs", android.content.Context.MODE_PRIVATE).getBoolean("always_one_time", false);
                if (oneTime || isAlwaysOneTime) {
                    AITranslator.suppressSentForeign(foreign);
                }

                edit.setText(foreign);
                edit.setSelection(foreign.length());

                try {
                    ((android.content.ClipboardManager) ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE))
                            .setPrimaryClip(ClipData.newPlainText("HT_AI_Copy", foreign));
                } catch (Exception ignored) {}

                pendingSelectedForeign = foreign;
                lastPickerResult = result;
                lastPickerOrig = origChinese;
                lastPickerPns = pn;
                lastPickerOneTime = oneTime;

                uiHandler.post(() -> {
                    if (versionButton != null) {
                        versionButton.setVisibility(View.VISIBLE);
                        versionButton.setText("版本");
                    }
                });

                dialog.dismiss();
            });

            card.setOnLongClickListener(v2 -> {
                try {
                    ((android.content.ClipboardManager) ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE))
                            .setPrimaryClip(ClipData.newPlainText("HT_AI_Copy", foreign));
                    Toast.makeText(ctx, "✅ 已复制到剪贴板", Toast.LENGTH_SHORT).show();
                } catch (Exception ignored) {}
                return true;
            });

            cont.addView(card);
        }
         // ================= 折叠式快捷微调 =================
        android.widget.LinearLayout quickHeader = new android.widget.LinearLayout(ctx);
        quickHeader.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        quickHeader.setPadding(36, 14, 36, 14);
        quickHeader.setBackgroundColor(Color.parseColor("#E9ECEF"));
        final TextView quickTitle = new TextView(ctx);
        quickTitle.setText("▶ ⚡ 快捷微调 (点击展开)");
        quickTitle.setTextSize(13f);
        quickTitle.setTextColor(Color.parseColor("#0B5ED7"));
        quickTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        quickHeader.addView(quickTitle);
        cont.addView(quickHeader);

        final android.widget.LinearLayout quickBody = new android.widget.LinearLayout(ctx);
        quickBody.setOrientation(android.widget.LinearLayout.VERTICAL);
        quickBody.setVisibility(View.GONE);
        quickBody.setPadding(0, 8, 0, 8);

        // 使用横向滑动层，防止按钮被挤出屏幕
        android.widget.HorizontalScrollView hsv = new android.widget.HorizontalScrollView(ctx);
        hsv.setHorizontalScrollBarEnabled(false);
        hsv.setBackgroundColor(Color.parseColor("#F5F5F5"));

        android.widget.LinearLayout quickBar = new android.widget.LinearLayout(ctx);
        quickBar.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        quickBar.setPadding(36, 8, 36, 8);
        hsv.addView(quickBar);

        final android.widget.CheckBox checkBoxOneTime = new android.widget.CheckBox(ctx);
        checkBoxOneTime.setText("仅本次 (不污染历史记忆)");
        checkBoxOneTime.setTextSize(12f);
        checkBoxOneTime.setTextColor(Color.parseColor("#666666"));
        checkBoxOneTime.setPadding(48, 10, 36, 10);
        
        // 使用 SharedPreferences 永久记忆你的勾选状态
        final android.content.SharedPreferences sp = ctx.getSharedPreferences("htai_quick_prefs", android.content.Context.MODE_PRIVATE);
        checkBoxOneTime.setChecked(oneTime || sp.getBoolean("always_one_time", false));

        checkBoxOneTime.setOnCheckedChangeListener((btnView, isChecked) -> {
            sp.edit().putBoolean("always_one_time", isChecked).apply();
        });

        for (int i = 1; i <= 5; i++) {
            String raw = AITranslator.getQuickOption(i);
            if (raw.isEmpty()) continue;
            String[] parts = raw.split("\\|", 2);
            final String tag = parts.length > 0 ? parts[0].trim() : "";
            final String tagContent = parts.length > 1 ? parts[1].trim() : tag;
            if (tag.isEmpty()) continue;

            Button tagBtn = new Button(ctx);
            tagBtn.setText(tag);
            tagBtn.setTextSize(11f);
            tagBtn.setAllCaps(false);
            tagBtn.setPadding(12, 6, 12, 6);
            GradientDrawable tbg = new GradientDrawable();
            tbg.setColor(Color.parseColor("#E8E8E8"));
            tbg.setCornerRadius(20f);
            tagBtn.setBackground(tbg);
            tagBtn.setTextColor(Color.parseColor("#333333"));
            android.widget.LinearLayout.LayoutParams tlp = new android.widget.LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tlp.setMargins(0, 0, 8, 0);
            tagBtn.setLayoutParams(tlp);

            tagBtn.setOnClickListener(v2 -> {
                String newPrompt = origChinese;
                if (newPrompt.endsWith("）") || newPrompt.endsWith(")")) {
                    newPrompt = newPrompt.replaceAll("[（\\(][^）\\)]*[）\\)]$", "").trim();
                }
                // “火力全开”享受绝对豁免权，必定打上“一次性”标签
                if (tag.contains("火力全开") || checkBoxOneTime.isChecked()) {
                    newPrompt = "一次性：" + newPrompt + " （" + tagContent + "）";
                } else {
                    newPrompt = newPrompt + " （" + tagContent + "）";
                }
                edit.setText(newPrompt);
                edit.setSelection(newPrompt.length());
                dialog.dismiss();
                edit.post(() -> btn.performClick());
            });

            quickBar.addView(tagBtn);
        }
        quickBody.addView(hsv);
        cont.addView(quickBody);
        
        // 强制把复选框加在最外层，永远可见不被折叠
        cont.addView(checkBoxOneTime);

        quickHeader.setOnClickListener(v2 -> {
            if (quickBody.getVisibility() == View.GONE) {
                quickBody.setVisibility(View.VISIBLE);
                quickTitle.setText("▼ ⚡ 快捷微调 (点击折叠)");
            } else {
                quickBody.setVisibility(View.GONE);
                quickTitle.setText("▶ ⚡ 快捷微调 (点击展开)");
            }
        });


        dialog.show();
        if (dialog.getWindow() != null) {
            android.util.DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
            int h = (int) (dm.heightPixels * 0.88);
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, h);
        }
    }

    private static String extractQuoteForHistory(Object msg, String chatId, boolean isMine) {
        try {
            Object ri = invokeQuiet(mGetReplyInfo, msg);
            if (ri != null) {
                Object rIs = invokeQuiet(mIsSender, ri);
                boolean replyIsMine = (rIs instanceof Boolean) && ((Boolean) rIs);

                Object rmt = invokeQuiet(mGetMsgType, ri);
                String rmtS = (rmt != null) ? String.valueOf(rmt) : null;

                if ("text".equals(rmtS) || "translate".equals(rmtS)) {
                    String rq = extractMessageTextByType(ri, rmtS);
                    if (rq != null && !rq.trim().isEmpty()) {
                        if (replyIsMine) {
                            String mc = AITranslator.getChineseByForeign(rq);
                            if (mc == null) mc = AITranslator.getDraftFuzzy(rq);
                            if (mc != null && !mc.trim().isEmpty()) return mc.trim();
                        }
                        return rq.trim();
                    }
                }

                if (rmtS != null && !rmtS.isEmpty()) {
                    return "[" + rmtS + "]";
                }
            }

            if (isMine
                    && chatId != null
                    && selectedReplyValid
                    && selectedReplyChatId != null
                    && selectedReplyChatId.equals(chatId)
                    && selectedReplyText != null
                    && !selectedReplyText.trim().isEmpty()
                    && selectedReplySendTime > 0
                    && System.currentTimeMillis() - selectedReplySendTime <= SELECTED_REPLY_FALLBACK_WINDOW_MS) {
                return selectedReplyText.trim();
            }
        } catch (Throwable ignored) {}

        return null;
    }

private static void hookOutgoingSetMsg(ClassLoader cl) {
    try {
        Class<?> hm = cl.loadClass("com.hellotalk.lib.im.entity.HTIMMessage");
        XposedBridge.hookAllMethods(hm, "setMsgContent", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam p) {
                try {
                    Object msg = p.thisObject;
                    Object bean = (p.args != null && p.args.length > 0) ? p.args[0] : null;
                    recordOutgoingIfNeeded(msg, bean);
                } catch (Throwable ignored) {}
            }
        });
    } catch (Throwable ignored) {}
}

private static void hookSendMessage(ClassLoader cl) {
    try {
        Class<?> vm = XposedHelpers.findClass(
                "com.hellotalk.talk.detail.data.source.ChatDetailViewModel", cl);
        Class<?> messageClass = XposedHelpers.findClass(
                "com.hellotalk.lib.im.entity.HTIMMessage", cl);

        XposedHelpers.findAndHookMethod(vm, "sendMessage",
                String.class, Object.class, org.json.JSONArray.class, messageClass,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam p) {
                        try {
                            if (p.args == null || p.args.length < 4) return;
                            Object replyInfo = p.args[3];
                            if (replyInfo == null) return;

                            String msgType = (String) XposedHelpers.callMethod(replyInfo, "getMsgType");
                            String quote = null;

                            if ("text".equals(msgType) || "translate".equals(msgType)) {
                                try {
                                    Class<?> textBeanClass = XposedHelpers.findClassIfExists(
                                            "com.hellotalk.talk.detail.delegate.text.IMTextBean", hostClassLoader);
                                    if (textBeanClass != null) {
                                        Object textBean = XposedHelpers.callMethod(replyInfo,
                                                "getMessageContent", textBeanClass, false);
                                        if (textBean != null) {
                                            quote = (String) XposedHelpers.callMethod(textBean, "getText");
                                        }
                                    }
                                } catch (Throwable ignored) {}
                            }

                            if (quote == null) {
                                quote = extractSelectedReplyText(replyInfo);
                            }
                            if (quote == null || quote.trim().isEmpty()) return;

                            String originalForeign = AITranslator.getForeignByChinese(quote);
                            if (originalForeign == null) {
                                originalForeign = AITranslator.getForeignFuzzy(quote);
                            }

                            if (originalForeign != null && !originalForeign.equals(quote)) {
                                try {
                                    Class<?> textBeanClass2 = XposedHelpers.findClassIfExists(
                                            "com.hellotalk.talk.detail.delegate.text.IMTextBean", hostClassLoader);
                                    if (textBeanClass2 != null) {
                                        Object textBean2 = XposedHelpers.callMethod(replyInfo,
                                                "getMessageContent", textBeanClass2, false);
                                        if (textBean2 != null) {
                                            XposedHelpers.callMethod(textBean2, "setText", originalForeign);
                                            XposedHelpers.callMethod(replyInfo, "setMsgContent", textBean2);
                                            log("引用替换成功: 中文 → " + originalForeign);
                                        }
                                    }
                                } catch (Throwable t) {
                                    log("引用替换失败: " + t.getMessage());
                                }
                                quote = originalForeign;
                            }

                            pendingSendQuote = quote.trim();
                            pendingSendChatId = currentChatId;
                            log("捕获发送引用: " + pendingSendQuote);
                        } catch (Throwable t) {
                            log("sendMessage引用捕获失败: " + t.getMessage());
                        }
                    }
                });
    } catch (Throwable t) {
        log("hookSendMessage失败: " + t.getMessage());
    }
}
    private static String extractSelectedReplyText(Object replyInfo) {
        if (replyInfo == null) return null;

        try {
            Object typeObj = invokeQuiet(mGetMsgType, replyInfo);
            String type = typeObj == null ? "" : String.valueOf(typeObj);

            if ("text".equals(type) || "translate".equals(type)) {
                String text = extractMessageTextByType(replyInfo, type);
                if (text != null && !text.trim().isEmpty()) {
                    return text.trim();
                }
            }

            if (type != null && !type.isEmpty()) {
                return describeNonTextMessage(
                        type,
                        Boolean.TRUE.equals(invokeQuiet(mIsSender, replyInfo))
                );
            }
        } catch (Throwable ignored) {}

        return null;
    }

    private static void recordOutgoingIfNeeded(Object msg, Object bean) {
        try {
            if (msg == null || bean == null) return;
            ensureMsgMethods(msg);

            Object iso = invokeQuiet(mIsSender, msg);
            if (!(iso instanceof Boolean) || !((Boolean) iso)) return;

            Object cidO = invokeQuiet(mGetChatId, msg);
String chatId = (cidO != null) ? String.valueOf(cidO) : null;

// ===== 新版 HelloTalk 发出消息历史记录修复 =====
// 只在新版 m4t 已检测到时使用当前页面 chatId 兜底；旧版逻辑不变。
boolean chatIdInvalid = chatId == null
        || chatId.trim().isEmpty()
        || "0".equals(chatId)
        || "null".equalsIgnoreCase(chatId);

if (chatIdInvalid && newReplyControllerDetected
        && currentChatId != null
        && !currentChatId.trim().isEmpty()
        && !"0".equals(currentChatId)
        && !"null".equalsIgnoreCase(currentChatId)) {

    log("新版发送历史 chatId 兜底: raw=" + chatId
            + " current=" + currentChatId);

    chatId = currentChatId;
    chatIdInvalid = false;
}

if (chatIdInvalid) return;

            Object mto = invokeQuiet(mGetMsgType, msg);
            String mt = (mto != null) ? String.valueOf(mto) : null;

            String text = null;
            if (bean instanceof String) {
                text = (String) bean;
            } else {
                Method gtm = ensureBeanGetText(bean);
                Object to = invokeQuiet(gtm, bean);
                if (to != null) text = String.valueOf(to);
                if (text == null) text = extractMessageTextByType(msg, mt);
            }

            if (text == null || text.trim().isEmpty()) return;
            text = text.trim();
            if (text.startsWith("[") || AITranslator.isChineseOnly(text)) return;

            Object mio = invokeQuiet(mGetMsgId, msg);
            String mid = (mio != null) ? String.valueOf(mio) : null;
            if (mid == null || mid.isEmpty()) return;

            long st = System.currentTimeMillis();
            Object sto = invokeQuiet(mGetSendTime, msg);
            if (sto instanceof Long) st = (Long) sto;
            if (st > 0 && st < 10000000000L) st = st * 1000L;

            if (st <= 0) {
                try {
                    Object ts2 = msg.getClass().getMethod("getSenderTs").invoke(msg);
                    if (ts2 instanceof Long && (Long) ts2 > 0) {
                        st = (Long) ts2;
                        if (st < 10000000000L) st = st * 1000L;
                    }
                } catch (Throwable ignored) {}
            }

            if (st <= 0) st = System.currentTimeMillis();

            final String fc = chatId;
            final String fm = mid;
            final String ft = text;
            final long fst = st;
            String capturedQuote = null;

            if (pendingSendChatId != null && pendingSendChatId.equals(chatId)) {
                capturedQuote = pendingSendQuote;
                pendingSendQuote = null;
                pendingSendChatId = null;
            }

            final String fq = capturedQuote != null
                    ? capturedQuote
                    : extractQuoteForHistory(msg, chatId, true);

            boolean isNew = recordedMsgIds.add(fc + "_" + fm);
            if (isNew && !shouldSkipHistory(text)) {
                historyExecutor.execute(() ->
                        AITranslator.appendHistory(fc, fm, "assistant", ft, fst, fq, false)
                );
            }
        } catch (Throwable ignored) {}
    }
private static View findNativeSendBtn(ViewGroup root) {
    if (root == null) return null;
    ArrayList<View> views = new ArrayList<>();
    views.add(root);
    for (int i = 0; i < views.size(); i++) {
        View cur = views.get(i);
        try {
            if (cur.getId() != View.NO_ID
                    && cur.getResources().getResourceEntryName(cur.getId()).toLowerCase().contains("send")) {
                return cur;
            }
        } catch (Exception ignored) {}
        if (cur instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) cur;
            for (int j = 0; j < vg.getChildCount(); j++) views.add(vg.getChildAt(j));
        }
    }
    return null;
}
}
