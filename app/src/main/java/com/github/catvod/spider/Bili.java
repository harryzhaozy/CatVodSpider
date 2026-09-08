package com.github.catvod.spider;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Danmaku;
import com.github.catvod.bean.Filter;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.bean.bili.Dash;
import com.github.catvod.bean.bili.Data;
import com.github.catvod.bean.bili.Media;
import com.github.catvod.bean.bili.Page;
import com.github.catvod.bean.bili.Resp;
import com.github.catvod.bean.bili.Wbi;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Json;
import com.github.catvod.utils.Path;
import com.github.catvod.utils.Util;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.github.catvod.utils.QRCode;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.net.URLEncoder;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


/**
 * @author ColaMint & FongMi & 唐三
 */
public class Bili extends Spider {

    private static final String COOKIE = "buvid3=7238A82F-D821-D23A-FAA5-ADC19C9796B458050infoc;bsource=search_baidu;_uuid=628F6D84-DF8D-ED5A-E4BF-66219979424B58395infoc;  buvid_fp=f7761c3c9bde36415f0299493c60b971;bp_t_offset_55423440=1203047221809905664;b_lsid=C1568F05_19E3B3AD0DE";
    private static String cookie;

    private JsonObject extend;
    private boolean login;
    private boolean isVip;
    private Wbi wbi;
    private Context mContext;

    private AlertDialog qrDialog;
    private ScheduledExecutorService pollScheduler;

    private static Map<String, String> getHeader() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Referer", "https://www.bilibili.com/");
        if (cookie != null) headers.put("cookie", cookie);
        headers.put("origin", "https://www.bilibili.com");
        headers.put("User-Agent", Util.CHROME);
        
        
        
        return headers;
    }

    private void setCookie() {
        // 1. 优先读取本地扫码保存的缓存凭证
        cookie = Path.read(getCache());
        // 2. 如果本地缓存不存在，才去读 extend 配置
        if (TextUtils.isEmpty(cookie)) {
            if (extend != null && extend.has("cookie")) {
                cookie = extend.get("cookie").getAsString();
            }
        }

        // 3. 处理 URL 类型的 Cookie 链接
        if (cookie != null && cookie.startsWith("http")) {
            cookie = OkHttp.string(cookie).trim();
        }
        // 4. 最后兜底：使用默认静态 COOKIE
        if (TextUtils.isEmpty(cookie)) {
            cookie = COOKIE;
        }
    
        SpiderDebug.log("===[Bili setCookie] 最终生效的 Cookie: " + cookie);
    }

    private List<Filter> getFilter() {
        List<Filter> items = new ArrayList<>();
        items.add(new Filter("order", "排序", Arrays.asList(new Filter.Value("預設", "totalrank"), new Filter.Value("最多點擊", "click"), new Filter.Value("最新發布", "pubdate"), new Filter.Value("最多彈幕", "dm"), new Filter.Value("最多收藏", "stow"))));
        items.add(new Filter("duration", "時長", Arrays.asList(new Filter.Value("全部時長", "0"), new Filter.Value("60分鐘以上", "4"), new Filter.Value("30~60分鐘", "3"), new Filter.Value("10~30分鐘", "2"), new Filter.Value("10分鐘以下", "1"))));
        return items;
    }

    private File getCache() {
        return Path.tv("bilibili");
    }

    @Override
    public void init(Context context, String extend) throws Exception {
        this.mContext = context;
        this.extend = Json.safeObject(extend);
        
        try {
            if (java.net.CookieHandler.getDefault() == null) {
                java.net.CookieHandler.setDefault(new java.net.CookieManager(null, java.net.CookiePolicy.ACCEPT_ALL));
                SpiderDebug.log("===[Bili Init] 全局 CookieManager 注册成功");
            }
        } catch (Exception e) {
            SpiderDebug.log("===[Bili Init Error] " + e.getMessage());
        }
        setCookie();
        checkLogin();
    }

    private void checkLogin() {
    try {
        String json = OkHttp.string("https://api.bilibili.com/x/web-interface/nav", getHeader());
        
        // 1. 安全过滤：返回为空，或者包含 412/HTML 拦截页时，跳过解析，保留原有本地 Cookie 登录态
        if (json == null || json.isEmpty() || json.contains("412") || json.contains("JavaScript") || !json.trim().startsWith("{")) {
            SpiderDebug.log("===[Bili Status] 接口返回非 JSON 数据(可能风控拦截)，保留本地登录态: " + json);
            // 本地如果含有 SESSDATA，默认保留登录状态，防止被风控误杀
            if (!TextUtils.isEmpty(this.cookie) && this.cookie.contains("SESSDATA")) {
                this.login = true;
            }
            return;
        }

        // 2. 正常 JSON 解析
        Resp resp = Resp.objectFrom(json);
        if (resp != null && resp.getData() != null) {
            Data data = resp.getData();
            login = data.isLogin();
            isVip = data.isVip();
            wbi = data.getWbi();
            if (login) {
                SpiderDebug.log("===[Bili Status] B站已登录");
            } else {
                SpiderDebug.log("===[Bili Status] 未登录或 Cookie 已失效");
            }
            return;
        }
    } catch (Exception e) {
        SpiderDebug.log("===[Bili Check Login Exception] " + e.getMessage());
    }

    // 3. 只有当确定本地根本没有 SESSDATA 凭证时，才重置为未登录
    if (TextUtils.isEmpty(this.cookie) || !this.cookie.contains("SESSDATA")) {
        login = false;
        isVip = false;
    } else {
        // 本地有 SESSDATA 但解析偶尔失败时，兜底维持登录，防止掉线
        login = true;
    }
}

    
    // ====================== 登录配置与扫码界面控制 ======================

   private void showPeizhiDialog() {
    Activity activity = null;
    try {
        activity = Init.getActivity();
    } catch (Exception ignored) {
    }
    
    if (activity == null && mContext instanceof Activity) {
        activity = (Activity) mContext;
    }

    if (activity == null) {
        SpiderDebug.log("===[Bili Error] 无法获取 Activity，无法弹出登录配置窗口");
        return;
    }

    Activity finalActivity = activity;

    // 1. 【核心】先在当前线程快速装载本地持久化 Cookie
    setCookie();
    
    // 2. 本地快速预判：只要含有 SESSDATA，先置位为 true，避免网络阻塞 UI
    if (!TextUtils.isEmpty(this.cookie) && this.cookie.contains("SESSDATA")) {
        this.login = true;
    }

    // 3. 开启子线程去跑 checkLogin() 网络 API 校验，彻底解决 NetworkOnMainThreadException
    new Thread(() -> {
        try {
            checkLogin(); // 子线程中安心跑 HTTP 网络校验，不引发系统拦截
        } catch (Exception e) {
            SpiderDebug.log("===[Bili CheckLogin Async Error] " + e.getMessage());
        }

        // 4. 网络校验完成后，切回 UI 主线程弹窗展示
        finalActivity.runOnUiThread(() -> {
            try {
                // 如果 Activity 已经销毁，不再弹窗
                if (finalActivity.isFinishing() || finalActivity.isDestroyed()) return;

                String statusTip = login ? "当前状态：B站已登录" : "当前状态：未登录 / Cookie 已失效";

                AlertDialog.Builder builder = new AlertDialog.Builder(finalActivity);
                builder.setTitle("Bilibili 账号配置");
                builder.setMessage(statusTip);

                // 按钮1：扫码登录
                builder.setPositiveButton("扫码登录", (dialog, which) -> {
                    dialog.dismiss();
                    startQrCodeLogin();
                });

                // 按钮2：清除 Cookie
                builder.setNegativeButton("清除 Cookie", (dialog, which) -> {
                    clearCookie();
                    dialog.dismiss();
                    Toast.makeText(finalActivity, "Cookie 已清除", Toast.LENGTH_SHORT).show();
                });

                builder.setNeutralButton("取消", (dialog, which) -> dialog.dismiss());
                builder.create().show();

            } catch (Exception e) {
                SpiderDebug.log("===[Bili Dialog UI Exception] " + e.getMessage());
            }
        });
    }).start();
}
    private void clearCookie() {
    this.cookie = "";
    this.login = false;
    this.isVip = false;
    try {
        
        //  清空 Path 文件缓存
        Path.write(getCache(), "");
        SpiderDebug.log("===[Bili Clear Cookie] 本地凭证已彻底清空");
    } catch (Exception e) {
        SpiderDebug.log("===[Bili Clear Cookie Error] " + e.getMessage());
    }
}

   private void startQrCodeLogin() {
        // 在后台线程发起网络请求和二维码绘制，避免 NetworkOnMainThreadException
        Init.execute(() -> {
            try {
                // 1. 获取 B 站二维码 token
                String api = "https://passport.bilibili.com/x/passport-login/web/qrcode/generate?source=main-mini";
                String json = OkHttp.string(api, getHeader());
                if (TextUtils.isEmpty(json)) return;

                JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
                if (!obj.has("code") || obj.get("code").getAsInt() != 0) return;

                JsonObject data = obj.getAsJsonObject("data");
                String qrUrl = data.get("url").getAsString();
                String qrcodeKey = data.get("qrcode_key").getAsString();

                // 2. 本地/后台线程绘制二维码图片
                Bitmap bitmap = createQRCodeBitmap(qrUrl, 600, 600);
                if (bitmap != null) {
                    // 3. 切回 UI 主线程显示弹窗，并启动轮询
                    showQrDialog(bitmap);
                    startPolling(qrcodeKey);
                } else {
                    SpiderDebug.log("===[Bili QrCode Error] Bitmap 生成失败");
                }
            } catch (Exception e) {
                SpiderDebug.log("===[Bili QrCode Login Exception] " + e.getMessage());
            }
        });
    }

    private void showQrDialog(Bitmap bitmap) {
        Activity activity = null;
        try {
            activity = Init.getActivity();
        } catch (Exception ignored) {}
        if (activity == null && mContext instanceof Activity) {
            activity = (Activity) mContext;
        }
        if (activity == null) return;

        Activity finalActivity = activity;
        finalActivity.runOnUiThread(() -> {
            try {
                LinearLayout layout = new LinearLayout(finalActivity);
                layout.setOrientation(LinearLayout.VERTICAL);
                layout.setPadding(40, 40, 40, 40);
                layout.setGravity(Gravity.CENTER);

                TextView textView = new TextView(finalActivity);
                textView.setText("请使用 Bilibili 手机客户端扫码登录");
                textView.setTextSize(18);
                textView.setTextColor(Color.BLACK);
                textView.setPadding(0, 0, 0, 20);
                textView.setGravity(Gravity.CENTER);

                ImageView imageView = new ImageView(finalActivity);
                imageView.setImageBitmap(bitmap);

                layout.addView(textView);
                layout.addView(imageView);

                AlertDialog.Builder builder = new AlertDialog.Builder(finalActivity);
                builder.setView(layout);
                builder.setNegativeButton("取消扫码", (dialog, which) -> stopPolling());
                builder.setOnDismissListener(dialog -> stopPolling());

                qrDialog = builder.create();
                qrDialog.show();
            } catch (Exception e) {
                SpiderDebug.log("===[Bili Show QR Dialog Exception] " + e.getMessage());
            }
        });
    }

    private Bitmap createQRCodeBitmap(String content, int width, int height) {
        try {
            // 调用纯 Java 的 QRCode 本地工具类生成
            return QRCode.getBitmap(content, width, 0);
        } catch (Exception e) {
            SpiderDebug.log("===[Bili QRCode Generate Exception] " + e.getMessage());
            return null;
        }
    }

 private void startPolling(String qrcodeKey) {
    stopPolling();
    SpiderDebug.log("===[Bili Poll] 开始单接口（带 SSL 兼容）轮询，qrcodeKey: " + qrcodeKey);
    
    // 1. 初始化跳过证书校验的 TrustManager（兼容 Android 6.0 系统根证书过老问题）
    javax.net.ssl.SSLContext sslContext = null;
    javax.net.ssl.SSLSocketFactory sslSocketFactory = null;
    try {
        sslContext = javax.net.ssl.SSLContext.getInstance("TLS");
        sslContext.init(null, new javax.net.ssl.TrustManager[]{
            new javax.net.ssl.X509TrustManager() {
                public java.security.cert.X509Certificate[] getAcceptedIssuers() { return new java.security.cert.X509Certificate[0]; }
                public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
                public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
            }
        }, new java.security.SecureRandom());
        sslSocketFactory = sslContext.getSocketFactory();
    } catch (Exception e) {
        SpiderDebug.log("===[Bili SSL Init Error] " + e.getMessage());
    }

    final javax.net.ssl.SSLSocketFactory finalSslSocketFactory = sslSocketFactory;

    pollScheduler = Executors.newSingleThreadScheduledExecutor();
    pollScheduler.scheduleAtFixedRate(() -> {
        try {
            String pollApi = "https://passport.bilibili.com/x/passport-login/web/qrcode/poll?qrcode_key=" + qrcodeKey + "&source=main-mini";
            
            java.net.HttpURLConnection conn = null;
            String json = "";
            try {
                java.net.URL url = new java.net.URL(pollApi);
                conn = (java.net.HttpURLConnection) url.openConnection();
                
                // 2. 注入 SSL Socket Factory，彻底解决 CertPathValidatorException 报错
                if (conn instanceof javax.net.ssl.HttpsURLConnection && finalSslSocketFactory != null) {
                    ((javax.net.ssl.HttpsURLConnection) conn).setSSLSocketFactory(finalSslSocketFactory);
                    ((javax.net.ssl.HttpsURLConnection) conn).setHostnameVerifier((hostname, session) -> true);
                }

                conn.setRequestMethod("GET");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.setRequestProperty("Referer", "https://www.bilibili.com/");
                if (cookie != null) conn.setRequestProperty("cookie", this.cookie);
                conn.setRequestProperty("origin", "https://www.bilibili.com");
                conn.setRequestProperty("User-Agent", Util.CHROME);
                
                
                
                // 3. 读取响应体
                java.io.InputStream in = conn.getInputStream();
                java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(in, "UTF-8"));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                json = response.toString();
            } catch (Exception e) {
                SpiderDebug.log("===[Bili Poll Http Error] " + e.getMessage());
            } finally {
                if (conn != null) conn.disconnect();
            }

            if (TextUtils.isEmpty(json)) return;

            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            if (!obj.has("data")) return;

            JsonObject data = obj.getAsJsonObject("data");
            int code = data.get("code").getAsInt();
            String message = data.has("message") ? data.get("message").getAsString() : "";

            SpiderDebug.log("===[Bili Poll] 轮询结果 code: " + code + " | msg: " + message);

            if (code == 0) { // 扫码登录成功！
                SpiderDebug.log("===[Bili Poll Success] 扫码成功，单接口自动截获 Set-Cookie，准备提取...");
                stopPolling();

                // 4. 从全局 CookieManager 提取这一次单接口请求下发的所有 Cookie
                java.net.CookieManager cm = (java.net.CookieManager) java.net.CookieHandler.getDefault();
                if (cm != null) {
                    List<java.net.HttpCookie> cookies = cm.getCookieStore().get(java.net.URI.create("https://passport.bilibili.com"));
                    if (cookies.isEmpty()) {
                        cookies = cm.getCookieStore().get(java.net.URI.create("https://bilibili.com"));
                    }

                    StringBuilder sb = new StringBuilder();
                    for (java.net.HttpCookie ck : cookies) {
                        sb.append(ck.getName()).append("=").append(ck.getValue()).append("; ");
                    }

                    if (sb.length() > 0) {
                        cookie = sb.toString().trim();
                        // 持久化保存 Cookie 到本地缓存
                        Path.write(getCache(), cookie);
                        SpiderDebug.log("===[Bili Poll Success] 单接口提取并保存 Cookie 成功: " + cookie);
                    } else {
                        SpiderDebug.log("===[Bili Poll Warning] CookieStore 为空，请检查 init 中 CookieManager 是否正常注册");
                    }
                }

                // 5. 校验登录状态
                checkLogin();

                // 6. 跨线程安全关闭 UI 弹窗
                Init.run(() -> {
                    try {
                        if (qrDialog != null && qrDialog.isShowing()) {
                            qrDialog.dismiss();
                            SpiderDebug.log("===[Bili Poll UI] 二维码 Dialog 弹窗已成功 Dismiss");
                        }
                        Toast.makeText(Init.context(), "B站扫码登录成功！", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        SpiderDebug.log("===[Bili Poll UI Error] " + e.getMessage());
                    }
                });

            } else if (code == 86038) { // 二维码失效
                SpiderDebug.log("===[Bili Poll] 二维码已失效，停止轮询");
                stopPolling();
                Init.run(() -> {
                    try {
                        if (qrDialog != null && qrDialog.isShowing()) {
                            qrDialog.dismiss();
                        }
                        Toast.makeText(Init.context(), "二维码已失效，请重新点击扫码", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        SpiderDebug.log("===[Bili Poll UI Error] " + e.getMessage());
                    }
                });
            }
        } catch (Exception e) {
            SpiderDebug.log("===[Bili Poll Exception] 轮询异常: " + e.getMessage());
        }
    }, 0, 2, TimeUnit.SECONDS);
}

private void stopPolling() {
    if (pollScheduler != null && !pollScheduler.isShutdown()) {
        pollScheduler.shutdownNow();
        pollScheduler = null;
        SpiderDebug.log("===[Bili Poll] 轮询线程池已停止");
    }
}


    // ====================== 分类与业务逻辑 ======================

@Override
public String homeContent(boolean filter) throws Exception {
    List<Class> classes = new ArrayList<>();
    LinkedHashMap<String, List<Filter>> filters = new LinkedHashMap<>();

    // 1. 如果配置了 "json" 路径
    if (extend != null && extend.has("json")) {
        String jsonPath = extend.get("json").getAsString();
        String jsonStr = "";

        if (jsonPath.startsWith("http")) {
            jsonStr = OkHttp.string(jsonPath, getHeader());
        } else {
            // 本地路径读取（纯原生，不依赖 Util.fs）
            try {
                java.io.File file = new java.io.File(jsonPath.replace("./", ""));
                if (file.exists() && file.isFile()) {
                    byte[] bytes = java.nio.file.Files.readAllBytes(file.toPath());
                    jsonStr = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                    SpiderDebug.log("===[Bili Read Local Json Success] ");
                }
            } catch (Throwable t) {
                SpiderDebug.log("===[Bili Read Local Json Fail] " + t.getMessage());
            }
        }

        if (!TextUtils.isEmpty(jsonStr)) {
            try {
                com.google.gson.JsonObject jsonObject = com.google.gson.JsonParser.parseString(jsonStr).getAsJsonObject();
                if (jsonObject.has("class") && jsonObject.get("class").isJsonArray()) {
                    com.google.gson.JsonArray classArray = jsonObject.getAsJsonArray("class");

                    for (com.google.gson.JsonElement element : classArray) {
                        if (!element.isJsonObject()) continue;
                        com.google.gson.JsonObject item = element.getAsJsonObject();

                        String typeId = item.has("type_id") ? item.get("type_id").getAsString() : "";
                        String typeName = item.has("type_name") ? item.get("type_name").getAsString() : "";

                        // 使用有参构造函数实例化 Class(typeId, typeName)
                        classes.add(new Class(typeId, typeName));

                        // 💡 关键拦截：如果是“登录配置”分类，挂载弹窗 Filter 按钮
                        if ("peizhi".equals(typeId) || "login_setting".equals(typeId)) {
                            List<Filter.Value> values = new ArrayList<>();
                            values.add(new Filter.Value("【点击弹窗配置账号】", "action_dialog"));

                            // 使用有参构造函数实例化 Filter(key, name, values)
                            Filter f = new Filter("action", "账号配置", values);

                            List<Filter> peizhiFilters = new ArrayList<>();
                            peizhiFilters.add(f);
                            filters.put(typeId, peizhiFilters);
                        } else {
                            filters.put(typeId, getFilter());
                        }
                    }

                    return Result.string(classes, filters);
                }
            } catch (Exception e) {
                com.github.catvod.crawler.SpiderDebug.log("===[Bili Home Parse Error] " + e.getMessage());
            }
        }
    }

    // 2. 兼容用 "type" 拼接分类的旧逻辑
    if (extend != null && extend.has("type")) {
        String[] types = extend.get("type").getAsString().split("#");
        for (String type : types) {
            classes.add(new Class(type));
            if ("peizhi".equals(type) || "login_setting".equals(type)) {
                List<Filter.Value> values = new ArrayList<>();
                values.add(new Filter.Value("【点击弹窗配置账号】", "action_dialog"));

                // 使用有参构造函数实例化 Filter(key, name, values)
                Filter f = new Filter("action", "账号配置", values);

                List<Filter> peizhiFilters = new ArrayList<>();
                peizhiFilters.add(f);
                filters.put(type, peizhiFilters);
            } else {
                filters.put(type, getFilter());
            }
        }
    }

    return Result.string(classes, filters);
}



    @Override
    public String homeVideoContent() {
        String api = "https://api.bilibili.com/x/web-interface/popular?ps=20";
        String json = OkHttp.string(api, getHeader());
        List<Vod> list = new ArrayList<>();

        if (json != null && !json.trim().isEmpty()) {
            try {
                com.google.gson.JsonObject jsonObject = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
                if (jsonObject.has("data") && !jsonObject.get("data").isJsonNull()) {
                    com.google.gson.JsonObject data = jsonObject.getAsJsonObject("data");
                    if (data.has("list") && data.get("list").isJsonArray()) {
                        com.google.gson.JsonArray listArray = data.getAsJsonArray("list");
                        com.google.gson.Gson gson = new com.google.gson.Gson();

                        for (com.google.gson.JsonElement element : listArray) {
                            if (!element.isJsonObject()) continue;
                            com.google.gson.JsonObject itemObj = element.getAsJsonObject();

                            com.google.gson.JsonObject vodJson = new com.google.gson.JsonObject();

                            String bvid = itemObj.has("bvid") ? itemObj.get("bvid").getAsString() : "";
                            String aid = itemObj.has("aid") ? itemObj.get("aid").getAsString() : "";
                            String vodId = bvid + "@" + aid;

                            String title = itemObj.has("title") ? itemObj.get("title").getAsString() : "";

                            String pic = itemObj.has("pic") ? itemObj.get("pic").getAsString() : "";
                            if (pic.startsWith("//")) {
                                pic = "https:" + pic;
                            }

                            String durationStr = "";
                            if (itemObj.has("duration")) {
                                try {
                                    long duration = itemObj.get("duration").getAsLong();
                                    durationStr = String.format("%02d:%02d", duration / 60, duration % 60);
                                } catch (Exception e) {
                                    durationStr = itemObj.get("duration").getAsString();
                                }
                            }

                            vodJson.addProperty("vod_id", vodId);
                            vodJson.addProperty("vod_name", title);
                            vodJson.addProperty("vod_pic", pic);
                            vodJson.addProperty("vod_remarks", durationStr);

                            Vod vod = gson.fromJson(vodJson, Vod.class);
                            if (vod != null) {
                                list.add(vod);
                            }
                        }
                    }
                }
            } catch (Throwable t) {
                com.github.catvod.crawler.SpiderDebug.log("===[Bili Home Error] " + t.getMessage());
            }
        }

        return Result.string(list);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        // 1. 拦截“登陆配置”栏目 (type_id 为 peizhi)
        if ("peizhi".equals(tid) || "login_setting".equals(tid)) {

        // 1. 捕获 Filter 按钮点击：直接在当前页面弹窗，绝对无背景跳转
        if (extend != null && "action_dialog".equals(extend.get("action"))) {
            SpiderDebug.log("===[Bili Category] 捕获到 Filter 账号配置操作，直接弹窗");
            Init.run(this::showPeizhiDialog);
        }

        // 2. 限制分页请求，防止多卡片
        if (pg != null && !pg.equals("1") && !pg.isEmpty()) {
            return Result.string(new ArrayList<>());
        }

        // 3. 实时刷新 Cookie 与内存登录标志
        setCookie();
        if (!TextUtils.isEmpty(this.cookie) && this.cookie.contains("SESSDATA")) {
            this.login = true;
        } else {
            this.login = false;
        }

        // 4. 构建当前分类页面中央的静态提示卡片
        try {
            org.json.JSONObject json = new org.json.JSONObject();
            json.put("page", 1);
            json.put("pagecount", 1);
            json.put("limit", 1);
            json.put("total", 1);

            org.json.JSONArray array = new org.json.JSONArray();
            org.json.JSONObject vodObj = new org.json.JSONObject();
            vodObj.put("vod_id", "notice_card");
            vodObj.put("vod_name", "【提示】请点击上方「账号配置」按钮弹出登录框");
            vodObj.put("vod_pic", "https://q5.itc.cn/images01/20250512/f6fdbe7b18854e1cad03f190f3280f70.jpeg");
            
            // 实时展示登录状态角标
            vodObj.put("vod_remarks", this.login ? "当前状态：已登录" : "当前状态：未登录");
            
            array.put(vodObj);
            json.put("list", array);
            
            return json.toString();
        } catch (Exception e) {
            return Result.string(new ArrayList<>());
        }
    }

        // 2. 如果是 UP 主空间视频
        if (tid.endsWith("/{pg}")) {
            LinkedHashMap<String, Object> params = new LinkedHashMap<>();
            params.put("mid", tid.split("/")[0]);
            params.put("pn", pg);
            List<Vod> list = new ArrayList<>();

            if (wbi == null) checkLogin();

            String query = (wbi != null) ? wbi.getQuery(params) : "";
            String json = OkHttp.string("https://api.bilibili.com/x/space/wbi/arc/search?" + query, getHeader());
            if (json != null && !json.isEmpty()) {
                json = json.replaceAll("\"//", "\"https://");
                Resp resp = Resp.objectFrom(json);
                if (resp != null && resp.getData() != null && resp.getData().getList() != null) {
                    com.google.gson.JsonElement vlist = resp.getData().getList().getAsJsonObject().get("vlist");
                    if (vlist != null && vlist.isJsonArray()) {
                        for (Resp.Result item : Resp.Result.arrayFrom(vlist)) {
                            if (item != null && item.getVod() != null) {
                                list.add(item.getVod());
                            }
                        }
                    }
                }
            }
            return Result.string(list);
        } else {
            // 3. 关键字/分类搜索
            String order = (extend != null && extend.containsKey("order")) ? extend.get("order") : "totalrank";
            String duration = (extend != null && extend.containsKey("duration")) ? extend.get("duration") : "0";
            if (extend != null && extend.containsKey("tid")) {
                tid = tid + " " + extend.get("tid");
            }

            String encodedTid = URLEncoder.encode(tid, "UTF-8");
            String api = "https://api.bilibili.com/x/web-interface/search/type?search_type=video&keyword=" 
                       + encodedTid + "&order=" + order + "&duration=" + duration + "&page=" + pg;

            String json = OkHttp.string(api, getHeader());
            List<Vod> list = new ArrayList<>();

            if (json != null && !json.trim().isEmpty()) {
                try {
                    com.google.gson.JsonObject jsonObject = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
                    if (jsonObject.has("data") && !jsonObject.get("data").isJsonNull()) {
                        com.google.gson.JsonObject data = jsonObject.getAsJsonObject("data");
                        if (data.has("result") && data.get("result").isJsonArray()) {
                            com.google.gson.JsonArray resultArray = data.getAsJsonArray("result");
                            com.google.gson.Gson gson = new com.google.gson.Gson();

                            for (com.google.gson.JsonElement element : resultArray) {
                                if (!element.isJsonObject()) continue;
                                com.google.gson.JsonObject itemObj = element.getAsJsonObject();

                                if (itemObj.has("type") && "video".equals(itemObj.get("type").getAsString())) {
                                    com.google.gson.JsonObject vodJson = new com.google.gson.JsonObject();

                                    String bvid = itemObj.has("bvid") ? itemObj.get("bvid").getAsString() : "";
                                    String aid = itemObj.has("aid") ? itemObj.get("aid").getAsString() : "";
                                    String vodId = bvid + "@" + aid;

                                    String title = itemObj.has("title") ? itemObj.get("title").getAsString() : "";
                                    if (!title.isEmpty()) {
                                        title = title.replaceAll("<[^>]*>", "")
                                                     .replaceAll("&quot;", "\"")
                                                     .replaceAll("&amp;", "&")
                                                     .replaceAll("&lt;", "<")
                                                     .replaceAll("&gt;", ">")
                                                     .replaceAll("&nbsp;", " ");
                                    }

                                    String pic = itemObj.has("pic") ? itemObj.get("pic").getAsString() : "";
                                    if (pic.startsWith("//")) {
                                        pic = "https:" + pic;
                                    }

                                    String durationStr = itemObj.has("duration") ? itemObj.get("duration").getAsString() : "";

                                    vodJson.addProperty("vod_id", vodId);
                                    vodJson.addProperty("vod_name", title);
                                    vodJson.addProperty("vod_pic", pic);
                                    vodJson.addProperty("vod_remarks", durationStr);

                                    Vod vod = gson.fromJson(vodJson, Vod.class);
                                    if (vod != null) {
                                        list.add(vod);
                                    }
                                }
                            }
                        }
                    }
                } catch (Throwable t) {
                    com.github.catvod.crawler.SpiderDebug.log("===[Bili Error] " + t.getMessage());
                }
            }

            return Result.string(list);
        }
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
       
    
        if (!login) checkLogin();

        String[] split = ids.get(0).split("@");
        String bvid = split[0];
        String aid = split[1];

        String api = "https://api.bilibili.com/x/web-interface/view?aid=" + aid;
        String json = OkHttp.string(api, getHeader());
        Data detail = Resp.objectFrom(json).getData();
        Vod vod = new Vod();
        vod.setVodId(ids.get(0));
        vod.setVodPic(detail.getPic());
        vod.setVodName(detail.getTitle());
        vod.setTypeName(detail.getType());
        vod.setVodContent(detail.getDesc());
        vod.setVodDirector(detail.getOwner().getFormat());
        vod.setVodRemarks(detail.getDuration() / 60 + "分鐘");

        List<String> acceptDesc = new ArrayList<>();
        List<Integer> acceptQuality = new ArrayList<>();
        api = "https://api.bilibili.com/x/player/playurl?avid=" + aid + "&cid=" + detail.getCid() + "&qn=127&fnval=4048&fourk=1";
        json = OkHttp.string(api, getHeader());
        Data play = Resp.objectFrom(json).getData();
        if (play != null && play.getAcceptQuality() != null) {
            for (int i = 0; i < play.getAcceptQuality().size(); i++) {
                int qn = play.getAcceptQuality().get(i);
                if (!login && qn > 32) continue;
                if (!isVip && qn > 80) continue;
                acceptQuality.add(play.getAcceptQuality().get(i));
                acceptDesc.add(play.getAcceptDescription().get(i));
            }
        }

        List<String> episode = new ArrayList<>();
        LinkedHashMap<String, String> flag = new LinkedHashMap<>();
        for (Page page : detail.getPages()) episode.add(page.getPart() + "$" + aid + "+" + page.getCid() + "+" + TextUtils.join(":", acceptQuality) + "+" + TextUtils.join(":", acceptDesc));
        flag.put("B站", TextUtils.join("#", episode));

        episode = new ArrayList<>();
        api = "https://api.bilibili.com/x/web-interface/archive/related?bvid=" + bvid;
        json = OkHttp.string(api, getHeader());
        JsonArray array = Json.parse(json).getAsJsonObject().getAsJsonArray("data");
        if (array != null) {
            for (int i = 0; i < array.size(); i++) {
                JsonObject object = array.get(i).getAsJsonObject();
                episode.add(object.get("title").getAsString() + "$" + object.get("aid").getAsInt() + "+" + object.get("cid").getAsInt() + "+" + TextUtils.join(":", acceptQuality) + "+" + TextUtils.join(":", acceptDesc));
            }
        }
        flag.put("相关", TextUtils.join("#", episode));
        vod.setVodPlayFrom(TextUtils.join("$$$", flag.keySet()));
        vod.setVodPlayUrl(TextUtils.join("$$$", flag.values()));
        return Result.string(vod);
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return categoryContent(key, "1", true, new HashMap<>());
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        return categoryContent(key, pg, true, new HashMap<>());
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        String[] ids = id.split("\\+");
        String aid = ids[0];
        String cid = ids[1];
        String[] acceptDesc = ids[3].split(":");
        String[] acceptQuality = ids[2].split(":");
        List<String> url = new ArrayList<>();
        String dan = "https://api.bilibili.com/x/v1/dm/list.so?oid=".concat(cid);
        for (int i = 0; i < acceptDesc.length; i++) {
            url.add(acceptDesc[i]);
            url.add(Proxy.getUrl() + "?do=bili" + "&aid=" + aid + "&cid=" + cid + "&qn=" + acceptQuality[i] + "&type=mpd");
        }
        return Result.get().url(url).danmaku(Arrays.asList(Danmaku.create().name("B站").url(dan))).dash().header(getHeader()).string();
    }

    public static Object[] proxy(Map<String, String> params) {
        String aid = params.get("aid");
        String cid = params.get("cid");
        String qn = params.get("qn");
        String api = "https://api.bilibili.com/x/player/playurl?avid=" + aid + "&cid=" + cid + "&qn=" + qn + "&fnval=4048&fourk=1";
        String json = OkHttp.string(api, getHeader());
        Resp resp = Resp.objectFrom(json);
        Dash dash = resp.getData().getDash();
        StringBuilder video = new StringBuilder();
        StringBuilder audio = new StringBuilder();
        findAudio(dash, audio);
        findVideo(dash, video, qn);
        String mpd = getMpd(dash, video.toString(), audio.toString());
        Object[] result = new Object[3];
        result[0] = 200;
        result[1] = "application/dash+xml";
        result[2] = new ByteArrayInputStream(mpd.getBytes());
        return result;
    }

    private static HashMap<String, String> getAudioFormat() {
        HashMap<String, String> audios = new HashMap<>();
        audios.put("30280", "192000");
        audios.put("30232", "132000");
        audios.put("30216", "64000");
        return audios;
    }

    private static void findAudio(Dash dash, StringBuilder sb) {
        if (dash == null || dash.getAudio() == null) return;
        for (Media audio : dash.getAudio()) {
            for (String key : getAudioFormat().keySet()) {
                if (audio.getId().equals(key)) {
                    sb.append(getMedia(audio));
                }
            }
        }
    }

    private static void findVideo(Dash dash, StringBuilder sb, String qn) {
        if (dash == null || dash.getVideo() == null) return;
        for (Media video : dash.getVideo()) {
            if (video.getId().equals(qn)) {
                sb.append(getMedia(video));
            }
        }
    }

    private static String getMedia(Media media) {
        if (media.getMimeType().startsWith("video")) {
            return getAdaptationSet(media, String.format(Locale.getDefault(), "height='%s' width='%s' frameRate='%s' sar='%s'", media.getHeight(), media.getWidth(), media.getFrameRate(), media.getSar()));
        } else if (media.getMimeType().startsWith("audio")) {
            return getAdaptationSet(media, String.format("numChannels='2' sampleRate='%s'", getAudioFormat().get(media.getId())));
        } else {
            return "";
        }
    }

    private static String getAdaptationSet(Media media, String params) {
        String id = media.getId() + "_" + media.getCodecId();
        String type = media.getMimeType().split("/")[0];
        String baseUrl = media.getBaseUrl().replace("&", "&amp;");
        return String.format(Locale.getDefault(), "<AdaptationSet>\n" + "<ContentComponent contentType=\"%s\"/>\n" + "<Representation id=\"%s\" bandwidth=\"%s\" codecs=\"%s\" mimeType=\"%s\" %s startWithSAP=\"%s\">\n" + "<BaseURL>%s</BaseURL>\n" + "<SegmentBase indexRange=\"%s\">\n" + "<Initialization range=\"%s\"/>\n" + "</SegmentBase>\n" + "</Representation>\n" + "</AdaptationSet>", type, id, media.getBandWidth(), media.getCodecs(), media.getMimeType(), params, media.getStartWithSap(), baseUrl, media.getSegmentBase().getIndexRange(), media.getSegmentBase().getInitialization());
    }

    private static String getMpd(Dash dash, String videoList, String audioList) {
        return String.format(Locale.getDefault(), "<MPD xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns=\"urn:mpeg:dash:schema:mpd:2011\" xsi:schemaLocation=\"urn:mpeg:dash:schema:mpd:2011 DASH-MPD.xsd\" type=\"static\" mediaPresentationDuration=\"PT%sS\" minBufferTime=\"PT%sS\" profiles=\"urn:mpeg:dash:profile:isoff-on-demand:2011\">\n" + "<Period duration=\"PT%sS\" start=\"PT0S\">\n" + "%s\n" + "%s\n" + "</Period>\n" + "</MPD>", dash.getDuration(), dash.getMinBufferTime(), dash.getDuration(), videoList, audioList);
    }
}
