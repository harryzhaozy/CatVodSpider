package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;

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
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * @author ColaMint & FongMi & 唐三
 */
public class Bili extends Spider {

    private static final String DEFAULT_COOKIE = "buvid3=8B57D3BA-607A-1E85-018A-E8C430023CED42659infoc; b_lsid=BEB8EE7F_18742FF8C2E; bsource=search_baidu; _uuid=DE810E367-B52C-AF6E-A612-EDF4C31567F358591infoc; b_nut=100; buvid_fp=711a632b5c876fa8bbcf668c1efba551;";
    private static String cookie = "";

    private JsonObject extend;
    private boolean login;
    private boolean isVip;
    private Wbi wbi;

    private static Map<String, String> getHeader() {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", Util.CHROME);
        headers.put("origin", "https://www.bilibili.com");
        headers.put("Referer", "https://www.bilibili.com/");
        if (!TextUtils.isEmpty(cookie)) {
            headers.put("cookie", cookie);
        }
        return headers;
    }

    private void setCookie() {
        try {
            if (extend != null && extend.has("cookie")) {
                cookie = extend.get("cookie").getAsString();
                if (cookie.startsWith("http")) {
                    cookie = OkHttp.string(cookie).trim();
                }
            }
            if (TextUtils.isEmpty(cookie)) {
                cookie = Path.read(getCache());
            }
            if (TextUtils.isEmpty(cookie)) {
                cookie = DEFAULT_COOKIE;
            }
        } catch (Exception e) {
            SpiderDebug.log("===[Bili setCookie Error] " + e.getMessage());
        }
    }

    private List<Filter> getFilter() {
        List<Filter> items = new ArrayList<>();
        items.add(new Filter("order", "排序", Arrays.asList(
                new Filter.Value("預設", "totalrank"),
                new Filter.Value("最多點擊", "click"),
                new Filter.Value("最新發布", "pubdate"),
                new Filter.Value("最多彈幕", "dm"),
                new Filter.Value("最多收藏", "stow")
        )));
        items.add(new Filter("duration", "時長", Arrays.asList(
                new Filter.Value("全部時長", "0"),
                new Filter.Value("60分鐘以上", "4"),
                new Filter.Value("30~60分鐘", "3"),
                new Filter.Value("10~30分鐘", "2"),
                new Filter.Value("10分鐘以下", "1")
        )));
        return items;
    }

    private File getCache() {
        return Path.tv("bilibili");
    }

    @Override
    public void init(Context context, String extend) throws Exception {
        this.extend = Json.safeObject(extend);
        setCookie();
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        if (extend.has("json")) return OkHttp.string(extend.get("json").getAsString());
        List<Class> classes = new ArrayList<>();
        LinkedHashMap<String, List<Filter>> filters = new LinkedHashMap<>();
        String[] types = extend.get("type").getAsString().split("#");
        for (String type : types) {
            classes.add(new Class(type));
            filters.put(type, getFilter());
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
                JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
                if (jsonObject.has("data") && !jsonObject.get("data").isJsonNull()) {
                    JsonObject data = jsonObject.getAsJsonObject("data");
                    if (data.has("list") && data.get("list").isJsonArray()) {
                        JsonArray listArray = data.getAsJsonArray("list");
                        Gson gson = new Gson();

                        for (JsonElement element : listArray) {
                            if (!element.isJsonObject()) continue;
                            JsonObject itemObj = element.getAsJsonObject();
                            JsonObject vodJson = new JsonObject();

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
                                    durationStr = String.format(Locale.getDefault(), "%02d:%02d", duration / 60, duration % 60);
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
                SpiderDebug.log("===[Bili Home Error] " + t.getMessage());
            }
        }
        return Result.string(list);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        // 修复问题一：解决登录配置生成无限多个二维码的问题
        if ("peizhi".equals(tid)) {
            if ("1".equals(pg)) {
                return getQrCodeVodList();
            } else {
                // 加载第二页及以后直接返回空，避免重复生成二维码
                return Result.string(new ArrayList<Vod>());
            }
        }

        if (tid.endsWith("/{pg}")) {
            LinkedHashMap<String, Object> params = new LinkedHashMap<>();
            params.put("mid", tid.split("/")[0]);
            params.put("pn", pg);
            List<Vod> list = new ArrayList<>();

            String json = OkHttp.string("https://api.bilibili.com/x/space/wbi/arc/search?" + wbi.getQuery(params), getHeader());
            if (json != null && !json.isEmpty()) {
                json = json.replaceAll("\"//", "\"https://");
                Resp resp = Resp.objectFrom(json);
                if (resp != null && resp.getData() != null && resp.getData().getList() != null) {
                    JsonElement vlist = resp.getData().getList().getAsJsonObject().get("vlist");
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
                    JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
                    if (jsonObject.has("data") && !jsonObject.get("data").isJsonNull()) {
                        JsonObject data = jsonObject.getAsJsonObject("data");
                        if (data.has("result") && data.get("result").isJsonArray()) {
                            JsonArray resultArray = data.getAsJsonArray("result");
                            Gson gson = new Gson();

                            for (JsonElement element : resultArray) {
                                if (!element.isJsonObject()) continue;
                                JsonObject itemObj = element.getAsJsonObject();

                                if (itemObj.has("type") && "video".equals(itemObj.get("type").getAsString())) {
                                    JsonObject vodJson = new JsonObject();

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
                    SpiderDebug.log("===[Bili Category Error] " + t.getMessage());
                }
            }

            return Result.string(list);
        }
    }

    /**
     * 获取 B站 登录二维码卡片
     */
    private String getQrCodeVodList() {
        List<Vod> list = new ArrayList<>();
        try {
            String api = "https://passport.bilibili.com/x/passport-login/web/qrcode/generate";
            String json = OkHttp.string(api, getHeader());

            if (!TextUtils.isEmpty(json)) {
                JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
                if (jsonObject.has("data")) {
                    JsonObject data = jsonObject.getAsJsonObject("data");
                    String url = data.get("url").getAsString();
                    String qrcodeKey = data.get("qrcode_key").getAsString();

                    String qrImgUrl = "https://api.qrserver.com/v1/create-qr-code/?size=300x300&data=" + URLEncoder.encode(url, "UTF-8");

                    Gson gson = new Gson();
                    JsonObject vodJson = new JsonObject();
                    vodJson.addProperty("vod_id", "qrcode@" + qrcodeKey);
                    vodJson.addProperty("vod_name", "【哔哩哔哩扫码登录】点击确认登录状态");
                    vodJson.addProperty("vod_pic", qrImgUrl);
                    vodJson.addProperty("vod_remarks", "请使用 B站 App 扫码后点击此处");

                    Vod vod = gson.fromJson(vodJson, Vod.class);
                    if (vod != null) {
                        list.add(vod);
                    }
                }
            }
        } catch (Exception e) {
            SpiderDebug.log("===[Bili QR Generate Error] " + e.getMessage());
        }
        return Result.string(list);
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String id = ids.get(0);

        if (id.startsWith("qrcode@")) {
            String qrcodeKey = id.split("@")[1];
            return checkQrCodeStatus(qrcodeKey);
        }

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
        for (Page page : detail.getPages()) {
            episode.add(page.getPart() + "$" + aid + "+" + page.getCid() + "+" + TextUtils.join(":", acceptQuality) + "+" + TextUtils.join(":", acceptDesc));
        }
        flag.put("B站", TextUtils.join("#", episode));

        episode = new ArrayList<>();
        api = "https://api.bilibili.com/x/web-interface/archive/related?bvid=" + bvid;
        json = OkHttp.string(api, getHeader());
        try {
            JsonArray array = Json.parse(json).getAsJsonObject().getAsJsonArray("data");
            if (array != null) {
                for (int i = 0; i < array.size(); i++) {
                    JsonObject object = array.get(i).getAsJsonObject();
                    episode.add(object.get("title").getAsString() + "$" + object.get("aid").getAsInt() + "+" + object.get("cid").getAsInt() + "+" + TextUtils.join(":", acceptQuality) + "+" + TextUtils.join(":", acceptDesc));
                }
            }
        } catch (Exception ignored) {
        }
        flag.put("相关", TextUtils.join("#", episode));
        vod.setVodPlayFrom(TextUtils.join("$$$", flag.keySet()));
        vod.setVodPlayUrl(TextUtils.join("$$$", flag.values()));
        return Result.string(vod);
    }

    /**
     * 轮询 B站 二维码状态并写出 Cookie
     */
    private String checkQrCodeStatus(String qrcodeKey) {
        Vod vod = new Vod();
        try {
            if ("success".equals(qrcodeKey)) {
                vod.setVodId("qrcode@success");
                vod.setVodName("当前已处于登录成功状态");
                vod.setVodRemarks("请返回上级页面继续正常浏览");
                return Result.string(vod);
            }

            String api = "https://passport.bilibili.com/x/passport-login/web/qrcode/poll?qrcode_key=" + qrcodeKey;
            String json = OkHttp.string(api, getHeader());

            if (!TextUtils.isEmpty(json)) {
                JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
                if (jsonObject.has("data")) {
                    JsonObject data = jsonObject.getAsJsonObject("data");
                    int code = data.get("code").getAsInt();
                    String message = data.get("message").getAsString();

                    if (code == 0) {
                        if (data.has("url")) {
                            String redirectUrl = data.get("url").getAsString();
                            StringBuilder newCookie = new StringBuilder();

                            if (redirectUrl.contains("?")) {
                                String queryString = redirectUrl.substring(redirectUrl.indexOf("?") + 1);
                                String[] params = queryString.split("&");
                                for (String param : params) {
                                    String[] kv = param.split("=", 2);
                                    if (kv.length == 2) {
                                        newCookie.append(kv[0]).append("=").append(kv[1]).append("; ");
                                    }
                                }
                            }

                            if (newCookie.length() > 0) {
                                cookie = newCookie.toString().trim();
                                Path.write(getCache(), cookie);
                                login = true;
                                SpiderDebug.log("===[Bili Login Success] Cookie saved: " + cookie);
                            }
                        }

                        vod.setVodId("qrcode@success");
                        vod.setVodName("登录成功！Cookie 已保存");
                        vod.setVodRemarks("请按返回键返回列表，开始播放更高画质内容");
                        vod.setVodContent("保存的 Cookie：" + cookie);
                        return Result.string(vod);
                    } else {
                        vod.setVodId("qrcode@" + qrcodeKey);
                        vod.setVodName("扫码状态：" + message);
                        vod.setVodRemarks("请在手机上确认登录后，在此重新点击尝试");
                        return Result.string(vod);
                    }
                }
            }
        } catch (Exception e) {
            SpiderDebug.log("===[Bili Poll QR Error] " + e.getMessage());
        }
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

    // 修复问题二：修正播放代理映射与参数拼装
    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        String[] ids = id.split("\\+");
        String aid = ids[0];
        String cid = ids[1];
        String[] acceptQuality = ids[2].split(":");
        String[] acceptDesc = ids[3].split(":");

        List<String> url = new ArrayList<>();
        String dan = "https://api.bilibili.com/x/v1/dm/list.so?oid=".concat(cid);

        for (int i = 0; i < acceptDesc.length; i++) {
            url.add(acceptDesc[i]);
            url.add(Proxy.getUrl() + "?do=bili" + "&aid=" + aid + "&cid=" + cid + "&qn=" + acceptQuality[i] + "&type=mpd");
        }

        return Result.get().url(url).danmaku(Arrays.asList(Danmaku.create().name("B站").url(dan))).dash().header(getHeader()).string();
    }

    // 静态本地代理服务回调
    public static Object[] proxy(Map<String, String> params) {
        try {
            String aid = params.get("aid");
            String cid = params.get("cid");
            String qn = params.get("qn");
            String api = "https://api.bilibili.com/x/player/playurl?avid=" + aid + "&cid=" + cid + "&qn=" + qn + "&fnval=4048&fourk=1";

            String json = OkHttp.string(api, getHeader());
            Resp resp = Resp.objectFrom(json);

            if (resp != null && resp.getData() != null && resp.getData().getDash() != null) {
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
        } catch (Exception e) {
            SpiderDebug.log("===[Bili Proxy Error] " + e.getMessage());
        }
        return null;
    }

    private static HashMap<String, String> getAudioFormat() {
        HashMap<String, String> audios = new HashMap<>();
        audios.put("30280", "192000");
        audios.put("30232", "132000");
        audios.put("30216", "64000");
        return audios;
    }

    private static void findAudio(Dash dash, StringBuilder sb) {
        if (dash.getAudio() == null) return;
        for (Media audio : dash.getAudio()) {
            for (String key : getAudioFormat().keySet()) {
                if (audio.getId().equals(key)) {
                    sb.append(getMedia(audio));
                }
            }
        }
    }

    private static void findVideo(Dash dash, StringBuilder sb, String qn) {
        if (dash.getVideo() == null) return;
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
            return getAdaptationSet(media, String.format(Locale.getDefault(), "numChannels='2' sampleRate='%s'", getAudioFormat().get(media.getId())));
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

    private void checkLogin() {
        try {
            String json = OkHttp.string("https://api.bilibili.com/x/web-interface/nav", getHeader());
            Resp resp = Resp.objectFrom(json);
            if (resp != null && resp.getData() != null) {
                Data data = resp.getData();
                login = data.isLogin();
                isVip = data.isVip();
                wbi = data.getWbi();
            }
        } catch (Exception e) {
            SpiderDebug.log("===[Bili checkLogin Error] " + e.getMessage());
        }
    }
}
