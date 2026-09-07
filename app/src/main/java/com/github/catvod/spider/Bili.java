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

    private static final String COOKIE = "buvid3=8B57D3BA-607A-1E85-018A-E8C430023CED42659infoc; b_lsid=BEB8EE7F_18742FF8C2E; bsource=search_baidu; _uuid=DE810E367-B52C-AF6E-A612-EDF4C31567F358591infoc; b_nut=100; buvid_fp=711a632b5c876fa8bbcf668c1efba551;";
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
        headers.put("User-Agent", Util.CHROME);
        headers.put("origin", "https://www.bilibili.com");
        headers.put("Referer", "https://www.bilibili.com/");
        if (cookie != null) headers.put("cookie", cookie);
        return headers;
    }

    private void setCookie() {
        if (extend != null && extend.has("cookie")) {
            cookie = extend.get("cookie").getAsString();
        }
        if (cookie != null && cookie.startsWith("http")) cookie = OkHttp.string(cookie).trim();
        if (TextUtils.isEmpty(cookie)) cookie = Path.read(getCache());
        if (TextUtils.isEmpty(cookie)) cookie = COOKIE;
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
        setCookie();
        checkLogin();
    }

    private void checkLogin() {
        try {
            String json = OkHttp.string("https://api.bilibili.com/x/web-interface/nav", getHeader());
            if (json != null && !json.isEmpty()) {
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
            }
        } catch (Exception e) {
            SpiderDebug.log("===[Bili Check Login Exception] " + e.getMessage());
        }
        login = false;
        isVip = false;
    }

    // ====================== 登录配置与扫码界面控制 ======================

    private void showPeizhiDialog() {
        if (!(mContext instanceof Activity)) return;
        Activity activity = (Activity) mContext;
        activity.runOnUiThread(() -> {
            checkLogin();
            String statusTip = login ? "当前状态：已登录" : "当前状态：未登录 / Cookie 已失效";

            AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            builder.setTitle("Bilibili 账号配置");
            builder.setMessage(statusTip);

            // 按钮1：弹出扫码
            builder.setPositiveButton("扫码登录", (dialog, which) -> {
                dialog.dismiss();
                startQrCodeLogin();
            });

            // 按钮2：清除 Cookie
            builder.setNegativeButton("清除 Cookie", (dialog, which) -> {
                clearCookie();
                dialog.dismiss();
            });

            builder.setNeutralButton("取消", (dialog, which) -> dialog.dismiss());
            builder.create().show();
        });
    }

    private void clearCookie() {
        try {
            cookie = COOKIE; // 恢复为默认无登录 Cookie
            File file = getCache();
            if (file.exists()) {
                file.delete();
            }
            login = false;
            isVip = false;
            if (mContext != null) {
                Init.run(() -> Toast.makeText(mContext, "Cookie 已清除！", Toast.LENGTH_SHORT).show());
            }
        } catch (Exception e) {
            SpiderDebug.log("===[Bili Clear Cookie Error] " + e.getMessage());
        }
    }

    private void startQrCodeLogin() {
        try {
            String api = "https://passport.bilibili.com/x/passport-login/web/qrcode/generate?source=main-mini";
            String json = OkHttp.string(api, getHeader());
            if (TextUtils.isEmpty(json)) return;

            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            if (!obj.has("code") || obj.get("code").getAsInt() != 0) return;

            JsonObject data = obj.getAsJsonObject("data");
            String qrUrl = data.get("url").getAsString();
            String qrcodeKey = data.get("qrcode_key").getAsString();

            Bitmap bitmap = createQRCodeBitmap(qrUrl, 600, 600);
            if (bitmap != null) {
                showQrDialog(bitmap);
                startPolling(qrcodeKey);
            }
        } catch (Exception e) {
            SpiderDebug.log("===[Bili QrCode Login Exception] " + e.getMessage());
        }
    }

    private Bitmap createQRCodeBitmap(String content, int width, int height) {
        try {
            // 直接调用 CatVod 内置的 QRCode 工具类
            return QRCode.getBitmap(content, width, 0);
        } catch (Exception e) {
            SpiderDebug.log("===[Bili QRCode Generate Error] " + e.getMessage());
            return null;
        }
    }

    private void showQrDialog(Bitmap bitmap) {
        if (!(mContext instanceof Activity)) return;
        Activity activity = (Activity) mContext;
        activity.runOnUiThread(() -> {
            LinearLayout layout = new LinearLayout(activity);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setPadding(40, 40, 40, 40);
            layout.setGravity(Gravity.CENTER);

            TextView textView = new TextView(activity);
            textView.setText("请使用 Bilibili 手机客户端扫码登录");
            textView.setTextSize(18);
            textView.setTextColor(Color.BLACK);
            textView.setPadding(0, 0, 0, 20);
            textView.setGravity(Gravity.CENTER);

            ImageView imageView = new ImageView(activity);
            imageView.setImageBitmap(bitmap);

            layout.addView(textView);
            layout.addView(imageView);

            AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            builder.setView(layout);
            builder.setNegativeButton("取消扫码", (dialog, which) -> stopPolling());
            builder.setOnDismissListener(dialog -> stopPolling());

            qrDialog = builder.create();
            qrDialog.show();
        });
    }

    private void startPolling(String qrcodeKey) {
        stopPolling();
        pollScheduler = Executors.newSingleThreadScheduledExecutor();
        pollScheduler.scheduleAtFixedRate(() -> {
            try {
                String pollApi = "https://passport.bilibili.com/x/passport-login/web/qrcode/poll?qrcode_key=" + qrcodeKey + "&source=main-mini";
                
                // 完全改用 OkHttp.string()
                String json = OkHttp.string(pollApi, getHeader());
                if (TextUtils.isEmpty(json)) return;

                JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
                if (!obj.has("data")) return;

                JsonObject data = obj.getAsJsonObject("data");
                int code = data.get("code").getAsInt();

                if (code == 0) { // 登录成功
                    stopPolling();
                    
                    // 1. 如果返回体 data 里包含 url (通常带有 refresh_token 或 SESSDATA 凭证)
                    if (data.has("url") && !data.get("url").getAsString().isEmpty()) {
                        String redirectUrl = data.get("url").getAsString();
                        // 访问一次跳转 URL 以便获取最终的完整 Cookie
                        OkHttp.string(redirectUrl, getHeader());
                    }

                    // 2. 从系统默认 CookieManager 获取刚才请求写入的 Cookie
                    java.net.CookieManager cookieManager = (java.net.CookieManager) java.net.CookieHandler.getDefault();
                    if (cookieManager != null) {
                        List<java.net.HttpCookie> cookies = cookieManager.getCookieStore().get(java.net.URI.create("https://bilibili.com"));
                        StringBuilder sb = new StringBuilder();
                        for (java.net.HttpCookie ck : cookies) {
                            sb.append(ck.getName()).append("=").append(ck.getValue()).append("; ");
                        }
                        if (sb.length() > 0) {
                            cookie = sb.toString().trim();
                            Path.write(getCache(), cookie);
                        }
                    }

                    // 重新校验登录状态
                    checkLogin();

                    if (mContext instanceof Activity) {
                        ((Activity) mContext).runOnUiThread(() -> {
                            if (qrDialog != null && qrDialog.isShowing()) {
                                qrDialog.dismiss();
                            }
                            Toast.makeText(mContext, "B站扫码登录成功！Cookie 已保存", Toast.LENGTH_SHORT).show();
                        });
                    }
                } else if (code == 86038) { // 二维码失效
                    stopPolling();
                    if (mContext instanceof Activity) {
                        ((Activity) mContext).runOnUiThread(() -> {
                            if (qrDialog != null && qrDialog.isShowing()) qrDialog.dismiss();
                            Toast.makeText(mContext, "二维码已失效，请重新点击扫码", Toast.LENGTH_SHORT).show();
                        });
                    }
                }
            } catch (Exception e) {
                SpiderDebug.log("===[Bili Poll Exception] " + e.getMessage());
            }
        }, 0, 2, TimeUnit.SECONDS);
    }

    private void stopPolling() {
        if (pollScheduler != null && !pollScheduler.isShutdown()) {
            pollScheduler.shutdownNow();
            pollScheduler = null;
        }
    }

    // ====================== 分类与业务逻辑 ======================

    @Override
    public String homeContent(boolean filter) throws Exception {
        if (extend != null && extend.has("json")) return OkHttp.string(extend.get("json").getAsString());
        List<Class> classes = new ArrayList<>();
        LinkedHashMap<String, List<Filter>> filters = new LinkedHashMap<>();
        if (extend != null && extend.has("type")) {
            String[] types = extend.get("type").getAsString().split("#");
            for (String type : types) {
                classes.add(new Class(type));
                filters.put(type, getFilter());
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
        if ("peizhi".equals(tid)) {
            Init.run(this::showPeizhiDialog);
            return Result.string(new ArrayList<>());
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
