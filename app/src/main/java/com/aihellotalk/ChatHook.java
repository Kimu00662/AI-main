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

    // ===== 鏂板锛氬綋鍓嶅洖澶嶆潯閫変腑鐨勬秷鎭� =====
    private static volatile String selectedReplyText = null;
    private static volatile String selectedReplyMsgType = null;
    private static volatile boolean selectedReplyIsMine = false;
    private static volatile boolean selectedReplyValid = false;
    private static volatile String selectedReplyMsgId = null;
    private static volatile long selectedReplySendTime = 0;
    private static volatile String selectedReplySenderName = null;
    private static volatile String selectedReplyChatId = null;
    private static volatile ClassLoader hostClassLoader = null;
    
    // ===== v5.13 鍥炲寮曠敤涓浆鍙橀噺 =====
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
            mIsSender = c.getMethod("isSender");
            mGetChatId = c.getMethod("getChatId");
            mGetSenderName = c.getMethod("getSenderName");
            mGetMsgType = c.getMethod("getMsgType");
            mGetMsgId = c.getMethod("getMsgId");
            mGetSendTime = c.getMethod("getSendTime");
            mGetReplyInfo = c.getMethod("getReplyInfo");
            mGetMsgContentTyped = c.getMethod("getMessageContent", Class.class, boolean.class);
            msgMethodsReady = true;
        } catch (Throwable ignored) {}
    }

    private static Method ensureBeanGetText(Object bean) {
        Method m = mBeanGetText;
        if (m != null) return m;
        if (bean == null) return null;
        try {
            m = bean.getClass().getMethod("getText");
            mBeanGetText = m;
            return m;
        } catch (Throwable t) {
            return null;
        }
    }

    private static Object invokeQuiet(Method m, Object target, Object... args) {
        if (m == null || target == null) return null;
        try {
            return (args == null || args.length == 0) ? m.invoke(target) : m.invoke(target, args);
        } catch (Throwable t) {
            return null;
        }
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
        log("=== Hook v5.5 绮惧噯鍥炲淇鐗� ===");

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

        // 鍏抽敭锛歨ook HelloTalk 杈撳叆妗嗕笂鏂圭殑鈥滃洖澶嶆潯鈥�
        try { hookInputReplyBar(cl); } catch (Throwable ignored) {}
        
        // ===== v5.13 鏂板锛歨ook 鎷︽埅鍙戦€佸姩浣� =====
        try { hookOutgoingSetMsg(cl); } catch (Throwable ignored) {}
        try { hookSendMessage(cl); } catch (Throwable ignored) {}
    }

    private static void log(String msg) {
        XposedBridge.log("HT_AI " + msg);
    }

    private static boolean isPureBracketQuery(String text) {
        if (text == null) return false;
        String s = text.trim();
        return (s.startsWith("(") && s.endsWith(")")) || (s.startsWith("锛�") && s.endsWith("锛�"));
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

    private static void hookInputReplyBar(ClassLoader cl) {
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
                if (bean == null) return null;

                Object t = invokeQuiet(ensureBeanGetText(bean), bean);
                return (t != null) ? String.valueOf(t) : null;
            }

            if ("translate".equals(msgType)) {
                Class<?> transBean = XposedHelpers.findClassIfExists(
                        "com.hellotalk.talk.detail.delegate.translate.IMTranslateBean",
                        hostClassLoader);
                if (transBean == null) return null;

                Object bean = invokeQuiet(mGetMsgContentTyped, msg, transBean, false);
                if (bean == null) return null;

                try {
                    Object t = XposedHelpers.callMethod(bean, "getSrcText");
                    return (t != null) ? String.valueOf(t) : null;
                } catch (Throwable ignored) {
                    return null;
                }
            }
        } catch (Throwable ignored) {}

        return null;
    }

    private static String describeNonTextMessage(String mt, boolean isMine) {
        String who = isMine ? "鎴�" : "瀵规柟";
        if (mt == null) return "[" + who + "鍙戦€佷簡涓€鏉℃秷鎭痌";

        switch (mt) {
            case "image":
            case "photo":
                return "[" + who + "鍙戦€佷簡涓€寮犲浘鐗嘳";
            case "voice":
            case "audio":
                return "[" + who + "鍙戦€佷簡涓€鏉¤闊砞";
            case "video":
                return "[" + who + "鍙戦€佷簡涓€娈佃棰慮";
            case "emoji":
            case "sticker":
                return "[" + who + "鍙戦€佷簡涓€涓〃鎯呭寘]";
            case "location":
                return "[" + who + "鍙戦€佷簡涓€涓綅缃甝";
            case "card":
            case "introduction":
                return "[" + who + "鍙戦€佷簡涓€寮犲悕鐗嘳";
            case "gift":
                return "[" + who + "鍙戦€佷簡涓€涓ぜ鐗";
            default:
                return "[" + who + "鍙戦€佷簡涓€鏉�" + mt + "娑堟伅]";
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
                    if (s.endsWith(" 馃寪") || s.endsWith(" 馃攧")) return;

                    String d = AITranslator.getDraftFuzzy(s);
                    if (d == null) d = AITranslator.getChineseByForeign(s);
                    if (d != null && !d.equals(s)) {
                        SpannableStringBuilder ssb = new SpannableStringBuilder(cs);
                        ssb.append(" 馃寪");
                        param.args[0] = ssb;
                    }
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

        try {
            XposedHelpers.findAndHookMethod("android.widget.TextView", null, "setText",
                    char[].class, int.class, int.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            try {
                                if (param.thisObject instanceof EditText) return;
                                if (!htTextViewClass.isInstance(param.thisObject)) return;

                                char[] chars = (char[]) param.args[0];
                                int start = (int) param.args[1];
                                int len = (int) param.args[2];
                                if (chars == null || len <= 0 || len > 5000) return;

                                String s = new String(chars, start, len);
                                if (s.endsWith(" 馃寪") || s.endsWith(" 馃攧")) return;

                                String d = AITranslator.getDraftFuzzy(s);
                                if (d == null) d = AITranslator.getChineseByForeign(s);
                                if (d != null && !d.equals(s)) {
                                    String ns = s + " 馃寪";
                                    param.args[0] = ns.toCharArray();
                                    param.args[1] = 0;
                                    param.args[2] = ns.length();
                                }
                            } catch (Throwable ignored) {}
                        }
                    });
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
                        if (!ts.endsWith(" 馃寪") && !ts.endsWith(" 馃攧") && !ts.matches(".*[\\u4e00-\\u9fa5]+.*")) {
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
                        if (!s.endsWith(" 馃攧") && !s.endsWith(" 馃寪")) return;

                        Layout lay = tv.getLayout();
                        if (lay == null) return;

                        int line = lay.getLineForVertical((int) ev.getY());
                        int off = lay.getOffsetForHorizontal(line, ev.getX());
                        if (off < s.length() - 2) return;

                        if (ev.getAction() == MotionEvent.ACTION_UP) {
                            String clean = s.substring(0, s.length() - 2).trim();
                            if (s.endsWith(" 馃攧")) {
                                String orig = AITranslator.getForeignByDraftChinese(clean);
                                if (orig == null) orig = AITranslator.getForeignByChinese(clean);
                                if (orig == null) orig = AITranslator.getForeignFuzzy(clean);
                                if (orig != null && !orig.equals(clean)) tv.setText(orig + " 馃寪");
                            } else {
                                String zh = AITranslator.getDraftFuzzy(clean);
                                if (zh == null) zh = AITranslator.getChineseByForeign(clean);
                                if (zh != null && !zh.equals(clean)) tv.setText(zh + " 馃攧");
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

    // ===== 鏃х増 HelloTalk =====
    // 淇濈暀鍘熸潵鐨� startChat 閫昏緫锛屼繚璇佹棫鐗堜笉鍙楀奖鍝�
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
                                Field f = vm.getClass().getDeclaredField("chatUser");
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
        log("鏃х増 startChat 涓嶅瓨鍦紝鍚敤鏂扮増 ChatDetailFragment 鏂规");
    }

    // ===== 鏂扮増 HelloTalk 6.4.0 =====
    // 鏂扮増 ChatDetailViewModel.startChat 宸茬粡涓嶅瓨鍦紝
    // chatId 淇濆瓨鍦� ChatDetailFragment 涓紝鐢� H3() 杩斿洖銆�
    try {
        XposedHelpers.findAndHookMethod(
                "com.hellotalk.talk.detail.fragment.ChatDetailFragment",
                cl,
                "H3",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam p) {
                        try {
                            Object result = p.getResult();
                            if (!(result instanceof Integer)) return;

                            int cid = (Integer) result;

                            // 0 琛ㄧず褰撳墠杩樻病鏈夋湁鏁堜細璇�
                            if (cid <= 0) return;

                            String newChatId = String.valueOf(cid);

                            // 鍙湁鐪熸鍒囨崲鑱婂ぉ鏃舵墠娓呯悊涓婁竴鑱婂ぉ鐨勬暟鎹�
                            if (!newChatId.equals(currentChatId)) {
                                currentChatId = newChatId;
                                latestNationality = "";
                                latestNativeLang = 1;
                                latestPartnerName = "";
                                currentPartnerName = "";
                                currentQuotedImagePath = null;
                                currentQuotedImageMissing = false;
                                resetSelectedReply();

                                log("鏂扮増 ChatDetailFragment.H3() 鑾峰彇 chatId = " + newChatId);
                            } else {
                                currentChatId = newChatId;
                            }

                        } catch (Throwable t) {
                            log("鏂扮増 H3 chatId 鑾峰彇澶辫触: " + t.getMessage());
                        }
                    }
                });

        log("鏂扮増 ChatDetailFragment.H3 Hook 娉ㄥ唽鎴愬姛");

    } catch (Throwable t) {
        log("鏂扮増 ChatDetailFragment.H3 Hook 娉ㄥ唽澶辫触: " + t.getMessage());
    }
}

    private static void updateFromChatUser(Object chatUser) {
        try {
            int nl = (Integer) XposedHelpers.callMethod(chatUser, "getNativeLang");
            String nat = (String) XposedHelpers.callMethod(chatUser, "getNationality");
            String nn = (String) XposedHelpers.callMethod(chatUser, "getNickName");
            String un = (String) XposedHelpers.callMethod(chatUser, "getUserName");

            latestNativeLang = nl;
            latestNationality = nat != null ? nat : "";
            latestPartnerName = (nn != null && !nn.isEmpty()) ? nn : (un != null ? un : "");

            if (!latestPartnerName.isEmpty()) currentPartnerName = latestPartnerName;

            if (!currentChatId.isEmpty() && !"0".equals(currentChatId)) {
                AITranslator.updateFriendNationality(currentChatId, latestNationality);
            }
            log("鍥界睄鍘熸枃: [" + latestNationality + "] 姣嶈鐮�: " + nl);
        } catch (Throwable ignored) {}
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

        XposedHelpers.findAndHookMethod(hm, "getMessageContent", Class.class, boolean.class,
                new XC_MethodHook() {
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
                            
                            // ===== v5.13 淇涓插彿锛堟嫆缁濅娇鐢ㄥ綋鍓嶇獥鍙D锛�=====
                            if ("0".equals(eid) || "null".equals(eid) || eid.trim().isEmpty()) {
                                return;
                            }
                            final String chatId = eid;

                            String sn = null;
                            Object sno = invokeQuiet(mGetSenderName, msg);
                            if (sno != null) sn = String.valueOf(sno);
                            if (sn != null && !sn.isEmpty() && !isMine) {
                                AITranslator.registerFriend(chatId, sn, AITranslator.getFriendLang(chatId), latestNationality);
                            }

                            Method gtm = ensureBeanGetText(bean);
                            Object to = invokeQuiet(gtm, bean);
                            String text = (to != null) ? String.valueOf(to) : null;

                            Object mto = invokeQuiet(mGetMsgType, msg);
                            String mt = (mto != null) ? String.valueOf(mto) : null;

                            if (text == null || text.isEmpty()) {
                                if ("image".equals(mt) || "photo".equals(mt)) {
                                    text = "[瀵规柟鍙戦€佷簡涓€寮犲浘鐗嘳";
                                } else if ("voice".equals(mt) || "audio".equals(mt)) {
                                    text = "[瀵规柟鍙戦€佷簡涓€鏉¤闊砞";
                                } else if ("video".equals(mt)) {
                                    text = "[瀵规柟鍙戦€佷簡涓€娈佃棰慮";
                                } else if ("emoji".equals(mt) || "sticker".equals(mt)) {
                                    text = "[瀵规柟鍙戦€佷簡涓€涓〃鎯呭寘]";
                                } else {
                                    return;
                                }
                            }

                            if (isMine && pendingSelectedForeign != null && pendingSelectedForeign.equals(text)) {
                                pendingSelectedForeign = null;
                                lastPickerResult = null;
                                uiHandler.post(() -> {
                                    if (versionButton != null) versionButton.setVisibility(View.GONE);
                                });
                            }

                            Object mio = invokeQuiet(mGetMsgId, msg);
                            String mid = (mio != null) ? String.valueOf(mio) : null;
                            if (mid == null || mid.isEmpty()) mid = "n_" + text.hashCode();

                            long st = System.currentTimeMillis();
                            Object sto = invokeQuiet(mGetSendTime, msg);
                            if (sto instanceof Long) st = (Long) sto;

                            String quotedText = null;
                            try {
                                Object ri = invokeQuiet(mGetReplyInfo, msg);
                                if (ri != null) {
                                    Object rIs = invokeQuiet(mIsSender, ri);
                                    boolean rIm = (rIs instanceof Boolean) && ((Boolean) rIs);

                                    Object rmt = invokeQuiet(mGetMsgType, ri);
                                    String rmtS = (rmt != null) ? String.valueOf(rmt) : null;

                                    if ("text".equals(rmtS) || "translate".equals(rmtS)) {
                                        String rq = extractMessageTextByType(ri, rmtS);
                                        if (rq != null && !rq.isEmpty()) {
                                            if (rIm) {
                                                String mc = AITranslator.getChineseByForeign(rq);
                                                if (mc == null) mc = AITranslator.getDraftFuzzy(rq);
                                                quotedText = (mc != null) ? mc : rq;
                                            } else {
                                                quotedText = rq;
                                            }
                                        }
                                    } else if (rmtS != null) {
                                        quotedText = "[" + rmtS + "]";
                                    }
                                }
                            } catch (Exception ignored) {}

                            final boolean oneTime = isMine && text != null && AITranslator.consumeSuppressSent(text);

                            final boolean isPureSymbol = !AITranslator.hasAnyLetterOrDigit(text);
                            boolean isNew = recordedMsgIds.add(chatId + "_" + mid);
                            if (isNew && !shouldSkipHistory(text)) {
                                final String fm = mid;
                                final String ft = text;
                                final String fq = quotedText;
                                final long fst = st;
                                final boolean fmn = isMine;
                                historyExecutor.execute(() -> {
                                    if (fmn) {
                                        AITranslator.appendHistory(chatId, fm, "assistant", ft, fst, fq, oneTime);
                                    } else {
                                        AITranslator.appendHistory(chatId, fm, "user", ft, fst, fq, false);
                                    }
                                });
                            }

                            if (isPureSymbol && !isMine) {
                                final String ft3 = text;
                                final Object fb3 = bean;
                                final String fc3 = chatId;
                                new Thread(() -> {
                                    try {
                                        String display = AITranslator.analyzePureSymbol(ft3, fc3);
                                        if (display != null && !display.isEmpty() && !display.equals(ft3)) {
                                            try { XposedHelpers.callMethod(fb3, "setText", display); } catch (Exception ignored) {}
                                        }
                                    } catch (Exception ignored) {}
                                }).start();
                                return;
                            }

                            if (text.startsWith("[")) return;
                            if (AITranslator.containsJapanese(text) || AITranslator.isChineseOnly(text)) return;

                            if (isMine) {
                                String d = AITranslator.getDraftFuzzy(text);
                                if (d == null) d = AITranslator.getChineseByForeign(text);
                                if (d != null && !d.isEmpty()) {
                                    AITranslator.cacheResult(mid, text, d);
                                    final Object fbk = bean;
                                    final String ftk = text;
                                    new Thread(() -> {
                                        try { Thread.sleep(150); } catch (InterruptedException ignored) {}
                                        try { XposedHelpers.callMethod(fbk, "setText", ftk); } catch (Exception ignored) {}
                                    }).start();
                                    return;
                                }

                                final String ft2 = text;
                                final String fc2 = chatId;
                                final String fm2 = mid;
                                final Object fb2 = bean;
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
                                                try { XposedHelpers.callMethod(fb2, "setText", ft2); } catch (Exception ignored) {}
                                            }
                                        } catch (Exception ignored) {}
                                    });
                                }
                                return;
                            }

                            String[] cached = AITranslator.getCached(mid);
                            if (cached != null && cached[0] != null && cached[0].equals(text)) {
                                try {
                                    XposedHelpers.callMethod(bean, "setText",
                                            cached[1].replaceAll("[\\s馃寪馃攧]+$", "") + " 馃攧");
                                } catch (Exception ignored) {}
                                return;
                            }

                            if (!translating.add(mid)) return;

                            final String ft = text;
                            final String fm = mid;
                            final Object fb = bean;

                            new Thread(() -> {
                                try {
                                    String t = null;
                                    try {
                                        t = AITranslator.toChinese(ft, chatId);
                                    } catch (Exception fe) {
                                        String m = fe.getMessage() == null ? "" : fe.getMessage();
                                        if (!m.contains("Key鏈厤缃�") && !m.contains("鏈垵濮嬪寲")) {
                                            try { Thread.sleep(1500); } catch (InterruptedException ignored) {}
                                            t = AITranslator.toChinese(ft, chatId);
                                        }
                                    }

                                    if (t != null && !t.trim().isEmpty() && !t.equals(ft)) {
                                        AITranslator.cacheResult(fm, ft, t);
                                        try {
                                            XposedHelpers.callMethod(fb, "setText",
                                                    t.replaceAll("[\\s馃寪馃攧]+$", "") + " 馃攧");
                                        } catch (Exception ignored) {}
                                    }
                                } catch (Exception ignored) {
                                } finally {
                                    translating.remove(fm);
                                }
                            }).start();

                        } catch (Throwable ignored) {}
                    }
                });
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
        if (ov == null || ov.isEmpty()) btn.setText("璇�");
        else btn.setText("璇懧�" + ov.toUpperCase());
    }

        private static void showLanguagePicker(Button btn, EditText edit) {
        if (btn == null || edit == null) return;
        android.content.Context ctx = edit.getContext();
        final String cid = currentChatId;
        if (cid == null || cid.isEmpty() || "0".equals(cid) || "null".equals(cid)) {
            Toast.makeText(ctx, "鈿狅笍 浼氳瘽灏氭湭灏辩华锛岃閫€鍑洪噸鏂拌繘鍏�", Toast.LENGTH_SHORT).show();
            return;
        }

        android.widget.LinearLayout root = new android.widget.LinearLayout(ctx);
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        root.setPadding(0, 24, 0, 0);

        // ============ 鍏ㄥ眬鍚屾锛氫竴娆℃€у閫夋 ============
        final android.content.SharedPreferences sp = ctx.getSharedPreferences("htai_quick_prefs", android.content.Context.MODE_PRIVATE);
        final android.widget.CheckBox checkBoxOneTime = new android.widget.CheckBox(ctx);
        checkBoxOneTime.setText("浠呮湰娆� (鍏ㄥ眬鍚屾锛屼笉姹℃煋鍘嗗彶璁板繂)");
        checkBoxOneTime.setTextSize(13f);
        checkBoxOneTime.setTextColor(Color.parseColor("#B02A37"));
        checkBoxOneTime.setPadding(48, 16, 48, 16);
        checkBoxOneTime.setChecked(sp.getBoolean("always_one_time", false));
        checkBoxOneTime.setOnCheckedChangeListener((btnView, isChecked) -> {
            sp.edit().putBoolean("always_one_time", isChecked).apply();
            Toast.makeText(ctx, isChecked ? "鉁� 宸插紑鍚粎鏈" : "鉂� 宸插叧闂粎鏈", Toast.LENGTH_SHORT).show();
        });
        root.addView(checkBoxOneTime);

        // ============ 蹇嵎鎸囦护婊戝姩鏉� ============
        TextView tagHeader = new TextView(ctx);
        tagHeader.setText("鈿� 蹇嵎璋冩暀鎸囦护");
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
                if (newPrompt.endsWith("锛�") || newPrompt.endsWith(")")) {
                    newPrompt = newPrompt.replaceAll("[锛圽\(][^锛塡\)]*[锛塡\)]$", "").trim();
                }
                if (tag.contains("鐏姏鍏ㄥ紑") || checkBoxOneTime.isChecked()) {
                    newPrompt = "涓€娆℃€э細" + newPrompt + " 锛�" + tagContent + "锛�";
                } else {
                    newPrompt = newPrompt + " 锛�" + tagContent + "锛�";
                }
                edit.setText(newPrompt);
                edit.setSelection(newPrompt.length());
                if (dialogRef[0] != null) dialogRef[0].dismiss();
                edit.post(() -> btn.performClick());
            });
            quickBar.addView(tagBtn);
        }
        root.addView(hsv);

        // ============ 璇█鍒楄〃鍖� ============
        View div = new View(ctx);
        div.setBackgroundColor(Color.parseColor("#DDDDDD"));
        div.setLayoutParams(new android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 2));
        root.addView(div);

        final String[] codes = {"auto", "en", "es", "ru", "uk", "ko", "ar", "pt", "fr", "de", "it", "tr", "nl", "pl", "kk", "cs"};
        final String[] names = {"馃寪 鑷姩鍒ゆ柇", "鑻辫 English", "瑗跨彮鐗欒 Espa帽ol", "淇勮 袪褍褋褋泻懈泄", "涔屽厠鍏拌 校泻褉邪褩薪褋褜泻邪", "闊╄ 頃滉淡鞏�", "闃挎媺浼 丕賱毓乇亘賷丞", "钁¤悇鐗欒 Portugu锚s", "娉曡 Fran莽ais", "寰疯 Deutsch", "鎰忓ぇ鍒╄ Italiano", "鍦熻€冲叾璇� T眉rk莽e", "鑽峰叞璇� Nederlands", "娉㈠叞璇� Polski", "鍝堣惃鍏嬭 覛邪蟹邪覜褕邪", "鎹峰厠璇� 膶e拧tina"};
        
        android.widget.ListView listView = new android.widget.ListView(ctx);
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(ctx, android.R.layout.simple_list_item_1, names);
        listView.setAdapter(adapter);
        root.addView(listView);

        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(ctx)
                .setTitle("闀挎寜鑿滃崟 (蹇嵎鎸囦护 & 涓存椂璇█)")
                .setView(root)
                .setNegativeButton("鍙栨秷", null)
                .create();
        
        dialogRef[0] = dialog;

        listView.setOnItemClickListener((parent, view, position, id) -> {
            String code = codes[position];
            if ("auto".equals(code)) chatLangOverride.remove(cid);
            else chatLangOverride.put(cid, code);
            updateTranslateBtnText(btn);
            Toast.makeText(ctx, "褰撳墠鑱婂ぉ璇█宸茶涓猴細" + names[position], Toast.LENGTH_SHORT).show();
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
        btn.setText("璇�");
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
        verBtn.setText("鐗堟湰");
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
                Toast.makeText(edit.getContext(), "鏆傛棤鍙€夌増鏈�", Toast.LENGTH_SHORT).show();
            }
        });
        btn.setOnLongClickListener(v -> {
            if (isTranslatingAPI) Toast.makeText(edit.getContext(), "缈昏瘧涓紝璇风◢鍊�", Toast.LENGTH_SHORT).show();
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

            boolean oneTime = text.startsWith("涓€娆℃€э細")
                    || text.startsWith("涓€娆℃€�:")
                    || text.startsWith("[涓€娆℃€");

            if (text.startsWith("涓€娆℃€э細")) {
                text = text.substring("涓€娆℃€э細".length()).trim();
            } else if (text.startsWith("涓€娆℃€�:")) {
                text = text.substring("涓€娆℃€�:".length()).trim();
            } else if (text.startsWith("[涓€娆℃€")) {
                text = text.substring("[涓€娆℃€".length()).trim();
            }

            if (text.isEmpty()) return;

            if (!oneTime) {
                int p1 = text.indexOf("锛�");
                int p2 = text.indexOf("锛�");
                if (p1 >= 0 && p2 > p1 && text.substring(p1, p2 + 1).contains("涓€娆℃€�")) {
                    oneTime = true;
                }
            }

            final boolean oneTimeFinal = oneTime;

            String cid = currentChatId;
            if (cid == null || cid.isEmpty() || "0".equals(cid) || "null".equals(cid)) {
                Toast.makeText(edit.getContext(), "鈿狅笍 浼氳瘽灏氭湭灏辩华锛岃閫€鍑鸿亰澶╅噸鏂拌繘鍏ュ悗鍐嶈瘯", Toast.LENGTH_SHORT).show();
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

            boolean hasSelectedReply = selectedReplyValid;
            boolean selectedReplyMine = selectedReplyIsMine;
            String quote = selectedReplyText;
            final String qis = currentQuotedImagePath;
            final boolean qms = currentQuotedImageMissing;

            boolean pbm = isPureBracketQuery(text);
            String cleanText = text;
            if (pbm) {
                if (cleanText.startsWith("(") && cleanText.endsWith(")")) cleanText = cleanText.substring(1, cleanText.length() - 1).trim();
                else if (cleanText.startsWith("锛�") && cleanText.endsWith("锛�")) cleanText = cleanText.substring(1, cleanText.length() - 1).trim();
            }

            String ttt = cleanText;

            if (hasSelectedReply && quote != null && !quote.trim().isEmpty()) {
                String orig = AITranslator.getForeignFuzzy(quote);
                if (orig != null) quote = orig;
                if (selectedReplyMine) {
                    if (pbm) {
                        ttt = "銆愭垜閫変腑鐨勬垜鑷繁鐨勫巻鍙叉秷鎭€戯細" + quote.trim() + "\n銆愭垜瀵硅繖鏉℃秷鎭殑鐤戦棶/鎻愰棶銆戯細" + cleanText;
                    } else {
                        ttt = "銆愭垜瀵规垜鑷繁涔嬪墠杩欐潯澶栬娑堟伅鐨勮ˉ鍏呫€戯細" + quote.trim() + "\n銆愯ˉ鍏呭唴瀹广€戯細" + cleanText;
                    }
                } else {
                    if (pbm) {
                        ttt = "銆愭垜閫変腑鐨勫鏂瑰師璇濄€戯細" + quote.trim() + "\n銆愭垜鍏充簬杩欏彞璇濈殑鎻愰棶/瑕佹眰銆戯細" + cleanText;
                    } else {
                        ttt = "銆愭垜瑕佸洖澶嶇殑瀵规柟鍘熻瘽銆戯細" + quote.trim() + "\n銆愭垜鐨勫洖澶嶃€戯細" + cleanText;
                    }
                }
            }

            if (qis != null) {
                File qf = new File(qis);
                if (qf.exists() && qf.length() > 0) {
                    ttt += "\n[QUOTED_LOCAL_IMAGE:" + qis + "]";
                }
            } else if (qms) {
                ttt += "\n[QUOTED_IMAGE_BUT_PATH_MISSING]";
            }

            final String ftt = ttt;
            final String rci = text;

            if (qis != null) currentQuotedImagePath = null;

            if (pbm) {
                AITranslator.markNoHistory(rci);
                String inner = rci;
                if (inner.startsWith("(") && inner.endsWith(")")) inner = inner.substring(1, inner.length() - 1).trim();
                else if (inner.startsWith("锛�") && inner.endsWith("锛�")) inner = inner.substring(1, inner.length() - 1).trim();
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
                        String answer = AITranslator.askAiQuestion(ftt, cs);
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
                        if (cts == 1) AITranslator.registerFriend(cs, pns, tl, nats);

                        String lr = chatRequestMap.get(cs);
                        boolean retry = ftt.equals(lr);
                        if (retry) {
                            chatRetryCountMap.put(cs, chatRetryCountMap.getOrDefault(cs, 0) + 1);
                        } else {
                            chatRequestMap.put(cs, ftt);
                            chatRetryCountMap.put(cs, 0);
                        }

                        String result = AITranslator.translateForPicker(ftt, tl, cs, retry);
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
                                "鈿狅笍 澶辫触: " + (e.getMessage() != null ? e.getMessage() : "鏈煡閿欒"),
                                Toast.LENGTH_LONG).show();
                    });
                }
            }).start();
        });
    }

    private static String determineSmartTargetLang(String nat, int nl, String cid) {
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
        if (langCodeMethod != null) {
            try {
                String r = (String) langCodeMethod.invoke(null, nl);
                return r != null ? r.toLowerCase() : null;
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static String mapNationalityToLang(String nat) {
        if (nat == null || nat.isEmpty()) return null;
        switch (nat) {
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
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(ctx).setTitle("AI 鍥炵瓟").setView(sv)
                .setPositiveButton("澶嶅埗", (d, w) -> {
                    try { ((android.content.ClipboardManager) ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("HT_AI_Copy", showText)); Toast.makeText(ctx, "宸插鍒�", Toast.LENGTH_SHORT).show(); } catch (Exception ignored) {}
                })
                .setNegativeButton("鍏抽棴", null).create();
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
                    .setTitle(refused ? "AI 鎷掔粷鎴栬Е鍙戝畨鍏ㄧ瓥鐣�" : "AI 鏈寜鏍煎紡杩斿洖")
                    .setView(sv)
                    .setPositiveButton("閲嶈瘯", (d, w) -> edit.post(() -> btn.performClick()))
                    .setNeutralButton("澶嶅埗鍘熸枃", (d, w) -> {
                        try {
                            ((android.content.ClipboardManager) ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE))
                                    .setPrimaryClip(ClipData.newPlainText("HT_AI_Copy", showText));
                            Toast.makeText(ctx, "宸插鍒�", Toast.LENGTH_SHORT).show();
                        } catch (Exception ignored) {}
                    })
                    .setNegativeButton("鍙栨秷", null)
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
            header.setText("馃搵 鍒嗘瀽");
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
        optHeader.setText("馃挰 閫変竴涓彂閫侊紙鍏�" + items.size() + "涓増鏈級");
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
        String title = (dn != null && !dn.isEmpty()) ? ("閫夌増鏈� - " + dn) : "閫夌増鏈�";

        final android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(ctx)
                .setTitle(title)
                .setView(root)
                .setNegativeButton("鍙栨秷", (d, w) -> {})
                .setPositiveButton("馃攧 鎹竴鎵�", (d, w) -> edit.post(() -> btn.performClick()))
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
                        versionButton.setText("鐗堟湰");
                    }
                });

                dialog.dismiss();
            });

            card.setOnLongClickListener(v2 -> {
                try {
                    ((android.content.ClipboardManager) ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE))
                            .setPrimaryClip(ClipData.newPlainText("HT_AI_Copy", foreign));
                    Toast.makeText(ctx, "鉁� 宸插鍒跺埌鍓创鏉�", Toast.LENGTH_SHORT).show();
                } catch (Exception ignored) {}
                return true;
            });

            cont.addView(card);
        }
         // ================= 鎶樺彔寮忓揩鎹峰井璋� =================
        android.widget.LinearLayout quickHeader = new android.widget.LinearLayout(ctx);
        quickHeader.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        quickHeader.setPadding(36, 14, 36, 14);
        quickHeader.setBackgroundColor(Color.parseColor("#E9ECEF"));
        final TextView quickTitle = new TextView(ctx);
        quickTitle.setText("鈻� 鈿� 蹇嵎寰皟 (鐐瑰嚮灞曞紑)");
        quickTitle.setTextSize(13f);
        quickTitle.setTextColor(Color.parseColor("#0B5ED7"));
        quickTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        quickHeader.addView(quickTitle);
        cont.addView(quickHeader);

        final android.widget.LinearLayout quickBody = new android.widget.LinearLayout(ctx);
        quickBody.setOrientation(android.widget.LinearLayout.VERTICAL);
        quickBody.setVisibility(View.GONE);
        quickBody.setPadding(0, 8, 0, 8);

        // 浣跨敤妯悜婊戝姩灞傦紝闃叉鎸夐挳琚尋鍑哄睆骞�
        android.widget.HorizontalScrollView hsv = new android.widget.HorizontalScrollView(ctx);
        hsv.setHorizontalScrollBarEnabled(false);
        hsv.setBackgroundColor(Color.parseColor("#F5F5F5"));

        android.widget.LinearLayout quickBar = new android.widget.LinearLayout(ctx);
        quickBar.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        quickBar.setPadding(36, 8, 36, 8);
        hsv.addView(quickBar);

        final android.widget.CheckBox checkBoxOneTime = new android.widget.CheckBox(ctx);
        checkBoxOneTime.setText("浠呮湰娆� (涓嶆薄鏌撳巻鍙茶蹇�)");
        checkBoxOneTime.setTextSize(12f);
        checkBoxOneTime.setTextColor(Color.parseColor("#666666"));
        checkBoxOneTime.setPadding(48, 10, 36, 10);
        
        // 浣跨敤 SharedPreferences 姘镐箙璁板繂浣犵殑鍕鹃€夌姸鎬�
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
                if (newPrompt.endsWith("锛�") || newPrompt.endsWith(")")) {
                    newPrompt = newPrompt.replaceAll("[锛圽\(][^锛塡\)]*[锛塡\)]$", "").trim();
                }
                // 鈥滅伀鍔涘叏寮€鈥濅韩鍙楃粷瀵硅眮鍏嶆潈锛屽繀瀹氭墦涓娾€滀竴娆℃€р€濇爣绛�
                if (tag.contains("鐏姏鍏ㄥ紑") || checkBoxOneTime.isChecked()) {
                    newPrompt = "涓€娆℃€э細" + newPrompt + " 锛�" + tagContent + "锛�";
                } else {
                    newPrompt = newPrompt + " 锛�" + tagContent + "锛�";
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
        
        // 寮哄埗鎶婂閫夋鍔犲湪鏈€澶栧眰锛屾案杩滃彲瑙佷笉琚姌鍙�
        cont.addView(checkBoxOneTime);

        quickHeader.setOnClickListener(v2 -> {
            if (quickBody.getVisibility() == View.GONE) {
                quickBody.setVisibility(View.VISIBLE);
                quickTitle.setText("鈻� 鈿� 蹇嵎寰皟 (鐐瑰嚮鎶樺彔)");
            } else {
                quickBody.setVisibility(View.GONE);
                quickTitle.setText("鈻� 鈿� 蹇嵎寰皟 (鐐瑰嚮灞曞紑)");
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
                                            log("寮曠敤鏇挎崲鎴愬姛: 涓枃 鈫� " + originalForeign);
                                        }
                                    }
                                } catch (Throwable t) {
                                    log("寮曠敤鏇挎崲澶辫触: " + t.getMessage());
                                }
                                quote = originalForeign;
                            }

                            pendingSendQuote = quote.trim();
                            pendingSendChatId = currentChatId;
                            log("鎹曡幏鍙戦€佸紩鐢�: " + pendingSendQuote);
                        } catch (Throwable t) {
                            log("sendMessage寮曠敤鎹曡幏澶辫触: " + t.getMessage());
                        }
                    }
                });
    } catch (Throwable t) {
        log("hookSendMessage澶辫触: " + t.getMessage());
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
            if (chatId == null || chatId.isEmpty() || "0".equals(chatId) || "null".equals(chatId)) return;

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
