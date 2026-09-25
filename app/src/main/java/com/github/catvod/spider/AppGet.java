package com.github.catvod.spider;
import android.content.Context;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import com.github.catvod.bean.Class;
import com.github.catvod.bean.Filter;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;
import com.google.gson.JsonObject;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class AppGet extends Spider {

    // =========================================================
    // 配置
    // =========================================================

    /**
     * API 基础地址
     */
    private String apiBaseUrl;

    /**
     * AES Key
     */
    private String dataKey;

    /**
     * AES IV
     */
    private String dataIv;

    /**
     * 设备 ID
     */
    private String deviceId;

    /**
     * App 版本号
     */
    private String appVersion;

    /**
     * User-Agent
     */
    private String userAgent;

    /**
     * 用户 Token
     */
    private String userToken;

    /**
     * 屏蔽关键词
     */
    private static Map<String, Boolean> keywordsMap = new HashMap<>();


    // =========================================================
    // API 请求
    // =========================================================

    
    private String requestApi(String apiPath, String requestBody) {
        try {
            String verifyTime =
                    String.valueOf(System.currentTimeMillis() / 1000);

            Map<String, String> headers = createHeaders();

            headers.put(
                    "Content-Type",
                    "application/x-www-form-urlencoded"
            );

            headers.put(
                    "app-user-device-id",
                    deviceId
            );

            headers.put(
                    "app-version-code",
                    appVersion
            );

            if (!TextUtils.isEmpty(userToken)) {
                headers.put(
                        "app-user-token",
                        userToken
                );
            }

            headers.put(
                    "app-api-verify-time",
                    verifyTime
            );

            headers.put(
                    "app-ui-mode",
                    "light"
            );

            String url =
                    apiBaseUrl
                            + "/api.php"
                            + apiPath;

            String response =
                    OkHttp.post(
                            url,
                            requestBody,
                            headers
                    ).getBody();

            JSONObject json =
                    new JSONObject(response);

            String encryptedData =
                    json.getString("data");

            return aesDecrypt(
                    encryptedData,
                    dataKey,
                    dataIv
            );

        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }


    /**
     * 创建普通请求 Header。
     */
    private Map<String, String> createHeaders() {
        Map<String, String> headers =
                new HashMap<>();

        headers.put(
                "User-Agent",
                TextUtils.isEmpty(userAgent)
                        ? "okhttp/3.14.9"
                        : userAgent
        );

        return headers;
    }


    // =========================================================
    // Vod Parse
    // =========================================================

    /**
     * 调用 vodParse 接口解析播放地址。
     */
    private String parsePlayUrl(String encodedUrl) {
        try {
            String verifyTime =
                    String.valueOf(
                            System.currentTimeMillis() / 1000
                    );

            /*
             *对当前时间戳进行 AES 加密，
             * 然后 Base64。
             */
            String verifySign =
                    Base64.encodeToString(
                            aesEncrypt(
                                    verifyTime,
                                    dataKey,
                                    dataIv
                            ),
                            Base64.NO_WRAP
                    );

            String apiUrl =
                    apiBaseUrl
                            + "/api.php/getappapi.index/vodParse";

            Map<String, String> headers =
                    new HashMap<>();

            headers.put(
                    "User-Agent",
                    userAgent
            );

            headers.put(
                    "Connection",
                    "Keep-Alive"
            );

            headers.put(
                    "Content-Type",
                    "application/x-www-form-urlencoded"
            );

            headers.put(
                    "app-version-code",
                    appVersion
            );

            if (!TextUtils.isEmpty(userToken)) {
                headers.put(
                        "app-user-token",
                        userToken
                );
            }

            headers.put(
                    "app-ui-mode",
                    "light"
            );

            headers.put(
                    "app-user-device-id",
                    deviceId
            );

            headers.put(
                    "app-api-verify-time",
                    verifyTime
            );

            headers.put(
                    "app-api-verify-sign",
                    verifySign
            );

            String response =
                    OkHttp.post(
                            apiUrl,
                            encodedUrl,
                            headers
                    ).getBody();

            String decrypted =
                    new JSONObject(response)
                            .getString("data");

            String json =
                    aesDecrypt(
                            decrypted,
                            dataKey,
                            dataIv
                    );

           
            JSONObject root =
                    new JSONObject(json);

            JSONObject data =
                    root.optJSONObject("json");

            if (data == null) {
                return "";
            }

            return data.optString("url");

        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }


    // =========================================================
    // Filter
    // =========================================================

    /**
     * 创建分类筛选项。
     */
    private Filter createFilter(
            String key,
            JSONArray values
    ) {

        List<Filter.Value> filterValues =
                new ArrayList<>();

        for (int i = 0; i < values.length(); i++) {
            String value =
                    values.optString(i);

            filterValues.add(
                    new Filter.Value(value)
            );
        }

        String filterKey = key;

       
        if ("sort".equals(filterKey)) {
            filterKey = "by";
        }

        String displayName = "";

        if ("class".equals(key)) {
            displayName = "类型";
        } else if ("lang".equals(key)) {
            displayName = "语言";
        } else if ("area".equals(key)) {
            displayName = "地区";
        } else if ("year".equals(key)) {
            displayName = "年份";
        } else if ("sort".equals(key)) {
            displayName = "排序";
        }

        return new Filter(
                filterKey,
                displayName,
                filterValues
        );
    }


    // =========================================================
    // URL 检查
    // =========================================================

   
    private boolean isUrlValid(String url) {
        try {
            HttpURLConnection connection =
                    (HttpURLConnection)
                            new URL(url).openConnection();

            connection.setInstanceFollowRedirects(true);
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);

            int responseCode =
                    connection.getResponseCode();

            return responseCode == 200
                    || responseCode == 301
                    || responseCode == 302;

        } catch (Exception e) {
            Log.e(
                    "AppGet",
                    "URL validation failed",
                    e
            );

            return false;
        }
    }


    // =========================================================
    // 分类
    // =========================================================

    @Override
    public String categoryContent(
            String typeId,
            String page,
            boolean filter,
            HashMap<String, String> extend
    ) {

        List<Vod> vodList =
                new ArrayList<>();

        try {
            JsonObject request =
                    new JsonObject();

            request.addProperty(
                    "type_id",
                    typeId
            );

            if (extend != null) {

                addFilterParameter(
                        request,
                        extend,
                        "class"
                );

                addFilterParameter(
                        request,
                        extend,
                        "lang"
                );

                addFilterParameter(
                        request,
                        extend,
                        "area"
                );

                addFilterParameter(
                        request,
                        extend,
                        "year"
                );

                /*
                 * TVBox 对外使用 by，
                 * 服务器接口使用 sort。
                 */
                addFilterParameter(
                        request,
                        extend,
                        "by",
                        "sort"
                );
            }

            request.addProperty(
                    "page",
                    page
            );

            String response =
                    requestApi(
                            "/getappapi.index/typeFilterVodList?page="
                                    + page,
                            request.toString()
                    );

            JSONObject json =
                    new JSONObject(response);

            vodList =
                    parseVodList(
                            json.optJSONArray(
                                    "recommend_list"
                            )
                    );

        } catch (Exception e) {
            e.printStackTrace();
        }

        return Result.string(vodList);
    }


    /**
     * 将筛选参数添加到 API 请求。
     */
    private void addFilterParameter(
            JsonObject request,
            HashMap<String, String> extend,
            String key
    ) {
        addFilterParameter(
                request,
                extend,
                key,
                key
        );
    }


    private void addFilterParameter(
            JsonObject request,
            HashMap<String, String> extend,
            String extendKey,
            String apiKey
    ) {

        if (!extend.containsKey(extendKey)) {
            return;
        }

        String value =
                extend.get(extendKey);

        if (!TextUtils.isEmpty(value)) {
            request.addProperty(
                    apiKey,
                    value
            );
        }
    }


    // =========================================================
    // 详情
    // =========================================================

    @Override
    public String detailContent(
            List<String> ids
    ) {

        Vod vod =
                new Vod();

        try {
            String vodId =
                    ids.get(0);

            JsonObject request =
                    new JsonObject();

            request.addProperty(
                    "vod_id",
                    vodId
            );

            String response =
                    requestApi(
                            "/getappapi.index/vodDetail",
                            request.toString()
                    );

            JSONObject root =
                    new JSONObject(response);

            JSONObject vodObject =
                    root.optJSONObject("vod");

            if (vodObject == null) {
                return Result.string(vod);
            }

            /*
             * 基本资料
             */
            vod = new Vod(
                    vodId,
                    vodObject.optString("vod_name"),
                    vodObject.optString("vod_pic"),
                    vodObject.optString("vod_remarks")
            );

            vod.setVodContent(
                    vodObject.optString("vod_content")
            );

            vod.setVodActor(
                    vodObject.optString("vod_actor")
            );

            vod.setVodDirector(
                    vodObject.optString("vod_director")
            );

           // vod.setVodClass(
           //         vodObject.optString("vod_class")
           // );


            /*
             * 播放线路
             */
            JSONArray playList =
                    root.optJSONArray(
                            "vod_play_list"
                    );

            List<String> playFrom =
                    new ArrayList<>();

            List<String> playUrls =
                    new ArrayList<>();

            if (playList != null) {

                for (int i = 0;
                     i < playList.length();
                     i++) {

                    JSONObject playItem =
                            playList.optJSONObject(i);

                    if (playItem == null) {
                        continue;
                    }

                    JSONObject playerInfo =
                            playItem.optJSONObject(
                                    "player_info"
                            );

                    if (playerInfo == null) {
                        continue;
                    }

                    String show =
                            playerInfo.optString(
                                    "show"
                            );

                    String parseApi =
                            playerInfo.optString(
                                    "parse"
                            );

                    JSONArray urls =
                            playItem.optJSONArray(
                                    "urls"
                            );

                    if (urls == null) {
                        continue;
                    }

                    List<String> episodeList =
                            new ArrayList<>();

                    for (int j = 0;
                         j < urls.length();
                         j++) {

                        JSONObject episode =
                                urls.optJSONObject(j);

                        if (episode == null) {
                            continue;
                        }

                        String url =
                                episode.optString(
                                        "url"
                                );

                        String parseApiUrl =
                                episode.optString(
                                        "parse_api_url"
                                );

                        String token =
                                episode.optString(
                                        "token"
                                );

                        String name =
                                episode.optString(
                                        "name"
                                );

                        String nid =
                                episode.optString(
                                        "nid"
                                );

                        String playInfo;

                        /*
                         * parse_api_url 是完整 HTTP URL：
                         *
                         * name$url|vodName|nid
                         */
                        if (parseApiUrl.matches(
                                "^https?://.*"
                        )) {

                            playInfo =
                                    name
                                            + "$"
                                            + parseApiUrl
                                            + "|"
                                            + vodObject.optString(
                                                    "vod_name"
                                            )
                                            + "|"
                                            + nid;

                        } else {

                            /*
                             * 否则：
                             *
                             * parse_api=...
                             * &url=AES(Base64)
                             * &token=...
                             */
                            String encryptedUrl =
                                    Base64.encodeToString(
                                            aesEncrypt(
                                                    url,
                                                    dataKey,
                                                    dataIv
                                            ),
                                            Base64.NO_WRAP
                                    );

                            playInfo =
                                    name
                                            + "$parse_api="
                                            + parseApi
                                            + "&url="
                                            + encryptedUrl
                                            + "&token="
                                            + token
                                            + "|"
                                            + vodObject.optString(
                                                    "vod_name"
                                            )
                                            + "|"
                                            + nid;
                        }

                        episodeList.add(
                                playInfo
                        );
                    }

                    playFrom.add(show);

                    playUrls.add(
                            TextUtils.join(
                                    "#",
                                    episodeList
                            )
                    );
                }
            }

            /*
             * TVBox：
             *
             * vod_play_from:
             * 线路1$$$线路2
             *
             * vod_play_url:
             * 集1$url#集2$url$$$...
             */
            vod.setVodPlayFrom(
                    TextUtils.join(
                            "$$$",
                            playFrom
                    )
            );

            vod.setVodPlayUrl(
                    TextUtils.join(
                            "$$$",
                            playUrls
                    )
            );

        } catch (Exception e) {
            e.printStackTrace();
        }

        return Result.string(vod);
    }


    // =========================================================
    // URL 编码
    // =========================================================

    /**
     * 将：
     *
     * url=xxxx&token
     *
     * 中的 URL 参数进行 URL Encode。
     */
    private String encodeUrlParameter(
            String value
    ) {

        Matcher matcher =
                Pattern.compile(
                        "(url=)(.*?)(?=&token)(&token)"
                ).matcher(value);

        StringBuffer result =
                new StringBuffer();

        while (matcher.find()) {

            try {

                matcher.appendReplacement(
                        result,
                        Matcher.quoteReplacement(
                                matcher.group(1)
                                        + URLEncoder.encode(
                                                matcher.group(2),
                                                "UTF-8"
                                        )
                                        + matcher.group(3)
                        )
                );

            } catch (Exception e) {
                e.printStackTrace();
                return value;
            }
        }

        matcher.appendTail(result);

        return result.toString();
    }


    /**
     * 解密：
     *
     * &url=xxxx&token
     *
     * 中的 URL。
     */
    private String decodeUrlParameter(
            String value
    ) {

        Matcher matcher =
                Pattern.compile(
                        "(&url=)(.*?)(?=&token)(&token)"
                ).matcher(value);

        StringBuffer result =
                new StringBuffer();

        while (matcher.find()) {

            try {

                String decryptedUrl =
                        aesDecrypt(
                                matcher.group(2),
                                dataKey,
                                dataIv
                        );

                matcher.appendReplacement(
                        result,
                        Matcher.quoteReplacement(
                                matcher.group(1)
                                        + decryptedUrl
                                        + matcher.group(3)
                        )
                );

            } catch (Exception e) {
                e.printStackTrace();
                return value;
            }
        }

        matcher.appendTail(result);

        return result.toString();
    }


    // =========================================================
    // 首页
    // =========================================================

    @Override
    public String homeContent(
            boolean filter
    ) {

        List<Class> classes =
                new ArrayList<>();

        List<Vod> vodList =
                new ArrayList<>();

        LinkedHashMap<
                String,
                List<Filter>
                > filters =
                new LinkedHashMap<>();

        try {

            String response =
                    requestApi(
                            "/getappapi.index/initV119",
                            "{}"
                    );

            JSONObject root =
                    new JSONObject(response);

            /*
             * 推荐
             */
            vodList =
                    parseVodList(
                            root.optJSONArray(
                                    "recommend_list"
                            )
                    );

            /*
             * 分类
             */
            JSONArray typeList =
                    root.optJSONArray(
                            "type_list"
                    );

            if (typeList != null) {

                for (int i = 0;
                     i < typeList.length();
                     i++) {

                    JSONObject type =
                            typeList.optJSONObject(i);

                    if (type == null) {
                        continue;
                    }

                    String typeId =
                            type.optString(
                                    "type_id"
                            );

                    String typeName =
                            type.optString(
                                    "type_name"
                            );

                    /*
                     * 过滤掉这些分类。
                     */
                    if (typeName.contains("正版QQ群")
                            || "伦理".equals(typeName)
                            || "福利".equals(typeName)
                            || "小影院".equals(typeName)) {
                        continue;
                    }

                    classes.add(
                            new Class(
                                    typeId,
                                    typeName
                            )
                    );

                    JSONArray filterList =
                            type.optJSONArray(
                                    "filter_type_list"
                            );

                    if (filterList == null) {
                        continue;
                    }

                    List<Filter> typeFilters =
                            new ArrayList<>();

                    for (int j = 0;
                         j < filterList.length();
                         j++) {

                        JSONObject filterObject =
                                filterList.optJSONObject(j);

                        if (filterObject == null) {
                            continue;
                        }

                        String name =
                                filterObject.optString(
                                        "name"
                                );

                        
                        if (!"class".equals(name)
                                && !"area".equals(name)
                                && !"lang".equals(name)
                                && !"year".equals(name)
                                && !"sort".equals(name)) {
                            continue;
                        }

                        JSONArray values =
                                filterObject.optJSONArray(
                                        "list"
                                );

                        if (values != null) {
                            typeFilters.add(
                                    createFilter(
                                            name,
                                            values
                                    )
                            );
                        }
                    }

                    filters.put(
                            typeId,
                            typeFilters
                    );
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return Result.string(
                classes,
                vodList,
                filters
        );
    }


    // =========================================================
    // 初始化
    // =========================================================

    @Override
    public void init(
            Context context,
            String ext
    ) {

        try {

            JSONObject config =
                    new JSONObject(ext);

            /*
             * 优先使用 url。
             */
            String url =
                    config.optString(
                            "url",
                            ""
                    );

            /*
             * 如果没有 url，
             * 则从 site 获取候选地址。
             */
            if (TextUtils.isEmpty(url)) {

                String site =
                        config.optString(
                                "site"
                        );

                String candidates =
                        OkHttp.string(
                                site,
                                new HashMap<>()
                        );

                if (TextUtils.isEmpty(
                        candidates
                )) {

                    Log.e(
                            "AppGet",
                            "Both url and site are invalid!"
                    );

                } else {

                    for (String candidate :
                            candidates.split("\\n")) {

                        candidate =
                                candidate.trim();

                        if (TextUtils.isEmpty(
                                candidate
                        )) {
                            continue;
                        }

                        if (isUrlValid(candidate)) {

                            Log.i(
                                    "AppGet",
                                    "Using valid URL: "
                                            + candidate
                            );

                            url = candidate;
                            break;
                        }
                    }
                }
            }

            /*
             * 保存配置。
             */
            apiBaseUrl = url;

            dataKey =
                    config.optString(
                            "dataKey"
                    );

            dataIv =
                    config.optString(
                            "dataIv"
                    );

            deviceId ="k4h850043nzhwl9ulja5c3zg327nq165";
                    //config.optString(
                    //        "deviceId"
                    //);

            appVersion ="113";
                    //config.optString(
                    //        "version"
                    //);

            userAgent =
                    config.optString(
                            "ua"
                    );

            userToken =
                    config.optString(
                            "token"
                    );

            /*
             * 获取宿主关键词屏蔽表。
             */
            // keywordsMap =
            //        Init.getKeywordsMap();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    // =========================================================
    // Vod 解析
    // =========================================================

    /**
     * 将服务器返回的 Vod 数组转换为 TVBox Vod。
     */
    public List<Vod> parseVodList(JSONArray array) {
    List<Vod> list = new ArrayList<>();

    for (int i = 0; i < array.length(); i++) {
        try {
            JSONObject object = array.getJSONObject(i);

            list.add(new Vod(
                    object.optString("vod_id"),
                    object.optString("vod_name"),
                    object.optString("vod_pic"),
                    object.optString("vod_remarks")
            ));
        } catch (Exception ignored) {
        }
    }

    return list;
}


    // =========================================================
    // 播放
    // =========================================================

    @Override
    public String playerContent(
            String flag,
            String id,
            List<String> vipFlags
    ) {

        try {

            /*
             * 播放 ID 格式：
             *
             * url|vodName|vodIndex
             *
             * 某些情况下为 4 段，
             * 会去掉第二段。
             */
            String[] parts =
                    id.split("\\|");

            if (parts.length == 4) {

                id =
                        parts[0]
                                + "|"
                                + parts[2]
                                + "|"
                                + parts[3];

                parts =
                        id.split("\\|");
            }

            String playUrl =
                    parts[0];

            String danmakuUrl =
                    Proxy.getUrl()
                            + "?do=appdanmu"
                            + "&vodName="
                            + parts[1]
                            + "&vodIndex="
                            + parts[2]
                            + "&vodUrl=";

            /*
             * 播放结果使用的 Header。
             */
            Map<String, String> headers =
                    createHeaders();

            /*
             * 使用 Chrome UA
             * 作为后续播放 Header。
             */
            headers.put(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                            + "AppleWebKit/537.36 "
                            + "(KHTML, like Gecko) "
                            + "Chrome/117.0.0.0 "
                            + "Safari/537.36"
            );


            // -------------------------------------------------
            // 情况 1：
            // http(s)://xxx?url= 或 ?key=
            // -------------------------------------------------

            if (playUrl.matches(
                    "^https?://.*"
            )
                    && (
                    playUrl.contains("?url=")
                            || playUrl.contains("?key=")
            )) {

                String response =
                        OkHttp.string(
                                playUrl,
                                createHeaders()
                        );

                if (response.startsWith("{")) {

                    playUrl =
                            new JSONObject(response)
                                    .optString(
                                            "url"
                                    );

                } else {

                    Matcher matcher =
                            Pattern.compile(
                                    "\"url\"\\s*:\\s*\"([^\"]+)\""
                            ).matcher(response);

                    if (matcher.find()) {
                        playUrl =
                                matcher.group(1);
                    }
                }

                return buildPlayerResult(
                        playUrl,
                        danmakuUrl,
                        headers
                );
            }


            // -------------------------------------------------
            // 情况 2：
            // 已经是 m3u8 / mp4 / mkv
            // -------------------------------------------------

            if (playUrl.matches(
                    ".*(m3u8|mp4|mkv).*"
            )) {

                return buildPlayerResult(
                        playUrl,
                        danmakuUrl,
                        createHeaders()
                );
            }


            // -------------------------------------------------
            // 情况 3：
            // 包含 ?url= / ?key= / html
            // -------------------------------------------------

            if (playUrl.contains("?url=")
                    || playUrl.contains("?key=")
                    || playUrl.contains("html")) {

                playUrl =
                        decodeUrlParameter(
                                playUrl
                        );

                Matcher matcher =
                        Pattern.compile(
                                "(parse_api=)(.*?)(?=&token)(&token)"
                        ).matcher(playUrl);

                if (matcher.find()) {

                    String parseApi =
                            matcher.group(2);

                    String response =
                            OkHttp.string(
                                    parseApi,
                                    null
                            );

                    JSONObject json =
                            new JSONObject(response);

                    JSONObject data =
                            json.optJSONObject(
                                    "data"
                            );

                    String parsedUrl =
                            data == null
                                    ? ""
                                    : data.optString(
                                            "url"
                                    );

                    if (!TextUtils.isEmpty(
                            parsedUrl
                    )) {

                        return buildPlayerResult(
                                parsedUrl,
                                danmakuUrl,
                                headers
                        );
                    }

                   
                    String location =
                            OkHttp.getLocation(
                                    parsedUrl,
                                    createHeaders()
                            );

                    return buildPlayerResult(
                            location,
                            danmakuUrl,
                            headers
                    );
                }
            }


            // -------------------------------------------------
            // 情况 4：
            // 使用 vodParse API
            // -------------------------------------------------

            String parsedUrl =
                    parsePlayUrl(
                            encodeUrlParameter(
                                    playUrl
                            )
                    );

            if (!TextUtils.isEmpty(
                    parsedUrl
            )) {

                return buildPlayerResult(
                        parsedUrl,
                        danmakuUrl,
                        headers
                );
            }


            // -------------------------------------------------
            // 最后尝试获取重定向地址
            // -------------------------------------------------

            String location =
                    OkHttp.getLocation(
                            parsedUrl,
                            createHeaders()
                    );

            return buildPlayerResult(
                    location,
                    danmakuUrl,
                    headers
            );

        } catch (Exception e) {

            e.printStackTrace();
            return "";
        }
    }


    /**
     * 创建播放结果。
     *
     */
    private String buildPlayerResult(
            String url,
            String danmakuUrl,
            Map<String, String> headers
    ) {

        return Result.get()
                .url(url)
                // .danmaku(danmakuUrl)
                .header(headers)
                .string();
    }


    // =========================================================
    // 搜索
    // =========================================================

    @Override
    public String searchContent(
            String keyword,
            boolean quick
    ) {

        List<Vod> result =
                new ArrayList<>();

        try {

            JsonObject request =
                    new JsonObject();

            request.addProperty(
                    "type_id",
                    0
            );

            request.addProperty(
                    "keywords",
                    keyword
            );

            request.addProperty(
                    "page",
                    1
            );

            String response =
                    requestApi(
                            "/getappapi.index/searchList",
                            request.toString()
                    );

            JSONObject json =
                    new JSONObject(response);

            result =
                    parseVodList(
                            json.optJSONArray(
                                    "search_list"
                            )
                    );

        } catch (Exception e) {
            e.printStackTrace();
        }

        return Result.string(result);
    }


    // =========================================================
    // AES
    // =========================================================

    /**
     * AES-CBC 解密。
     *
     * 输入：
     * Base64 密文
     *
     * 输出：
     * UTF-8 明文
     */
    private static String aesDecrypt(
            String encryptedBase64,
            String key,
            String iv
    ) {

        try {

            Cipher cipher =
                    Cipher.getInstance(
                            "AES/CBC/PKCS7Padding"
                    );

            SecretKeySpec keySpec =
                    new SecretKeySpec(
                            key.getBytes(
                                    StandardCharsets.UTF_8
                            ),
                            "AES"
                    );

            IvParameterSpec ivSpec =
                    new IvParameterSpec(
                            iv.getBytes(
                                    StandardCharsets.UTF_8
                            )
                    );

            cipher.init(
                    Cipher.DECRYPT_MODE,
                    keySpec,
                    ivSpec
            );

            byte[] encrypted =
                    Base64.decode(
                            encryptedBase64,
                            Base64.DEFAULT
                    );

            byte[] decrypted =
                    cipher.doFinal(
                            encrypted
                    );

            return new String(
                    decrypted,
                    StandardCharsets.UTF_8
            );

        } catch (Exception e) {

            e.printStackTrace();
            return "";
        }
    }


    /**
     * AES-CBC 加密。
     *
     * 返回原始密文字节。
     */
    private static byte[] aesEncrypt(
            String plainText,
            String key,
            String iv
    ) {

        try {

            Cipher cipher =
                    Cipher.getInstance(
                            "AES/CBC/PKCS5Padding"
                    );

            SecretKeySpec keySpec =
                    new SecretKeySpec(
                            key.getBytes(
                                    StandardCharsets.UTF_8
                            ),
                            "AES"
                    );

            IvParameterSpec ivSpec =
                    new IvParameterSpec(
                            iv.getBytes(
                                    StandardCharsets.UTF_8
                            )
                    );

            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    keySpec,
                    ivSpec
            );

            return cipher.doFinal(
                    plainText.getBytes(
                            StandardCharsets.UTF_8
                    )
            );

        } catch (Exception e) {

            e.printStackTrace();
            return new byte[0];
        }
    }
}
