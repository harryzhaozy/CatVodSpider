package com.github.catvod.spider;

import android.content.Context;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Wencai extends Spider {

    private static final String HOST = "https://www.hkybqufgh.com";
    private static final String KEY = "cb808529bae6b6be45ecfab29a4889bc";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";
    private static final String DEVICE_ID = UUID.randomUUID().toString();

    private String currentHost = HOST;

    @Override
    public void init(Context context, String extend) {
        try {
            super.init(context, extend);
             } catch (Exception e) {
            return "";
        }
        this.currentHost = HOST;
    }

    // ==================== 加密与辅助工具 ====================

    private String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private String sha1(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private String toQueryString(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            String val = entry.getValue();
            if (val != null && !val.trim().isEmpty()) {
                if (!first) sb.append("&");
                sb.append(entry.getKey()).append("=").append(val);
                first = false;
            }
        }
        return sb.toString();
    }

    private Map<String, String> getHeaders(Map<String, String> params) {
        if (params == null) params = new HashMap<>();
        String t = String.valueOf(System.currentTimeMillis());

        Map<String, String> signParams = new HashMap<>(params);
        signParams.put("key", KEY);
        signParams.put("t", t);

        String queryString = toQueryString(signParams);
        String sign = sha1(md5(queryString));

        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", USER_AGENT);
        headers.put("Accept", "application/json, text/plain, */*");
        headers.put("sign", sign);
        headers.put("t", t);
        headers.put("deviceid", DEVICE_ID);
        return headers;
    }

    private String normalizeFieldName(String key) {
        String l = key.toLowerCase();
        if (l.startsWith("vod") && l.length() > 3) return "vod_" + l.substring(3);
        if (l.startsWith("type") && l.length() > 4) return "type_" + l.substring(4);
        return l;
    }

    private JSONObject normalizeVodItem(JSONObject item) {
        JSONObject res = new JSONObject();
        if (item == null) return res;
        try {
            Iterator<String> keys = item.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                if (!item.isNull(k)) {
                    res.put(normalizeFieldName(k), item.get(k));
                }
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return res;
    }

    private JSONArray normalizeVodList(JSONArray list) {
        JSONArray res = new JSONArray();
        if (list == null) return res;
        try {
            for (int i = 0; i < list.length(); i++) {
                JSONObject item = list.optJSONObject(i);
                if (item != null) {
                    res.put(normalizeVodItem(item));
                }
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return res;
    }

    private JSONObject reqSafe(String url, Map<String, String> params) {
        try {
            Map<String, String> headers = getHeaders(params);
            String content = OkHttp.string(url, headers);
            return new JSONObject(content);
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    // ==================== 业务接口实现 ====================

    @Override
    public String homeContent(boolean filter) {
        try {
            // 1. 获取分类
            String typeUrl = currentHost + "/api/mw-movie/anonymous/get/filer/type";
            JSONObject cRes = reqSafe(typeUrl, null);
            JSONArray cData = cRes.optJSONArray("data");

            JSONArray classes = new JSONArray();
            if (cData != null) {
                for (int i = 0; i < cData.length(); i++) {
                    JSONObject k = cData.getJSONObject(i);
                    JSONObject cls = new JSONObject();
                    cls.put("type_name", k.optString("typeName"));
                    cls.put("type_id", k.optString("typeId"));
                    classes.put(cls);
                }
            }

            // 2. 获取筛选列表
            String filterUrl = currentHost + "/api/mw-movie/anonymous/v1/get/filer/list";
            JSONObject fRes = reqSafe(filterUrl, null);
            JSONObject fData = fRes.optJSONObject("data");

            JSONObject filters = new JSONObject();
            if (fData != null) {
                JSONArray baseSort = new JSONArray();
                baseSort.put(new JSONObject().put("n", "最近更新").put("v", "2"));
                baseSort.put(new JSONObject().put("n", "人气高低").put("v", "3"));
                baseSort.put(new JSONObject().put("n", "评分高低").put("v", "4"));

                Iterator<String> tids = fData.keys();
                while (tids.hasNext()) {
                    String tid = tids.next();
                    JSONObject d = fData.optJSONObject(tid);
                    if (d == null) continue;

                    JSONArray currentSortValues = new JSONArray();
                    int startIdx = "1".equals(tid) ? 1 : 0;
                    for (int i = startIdx; i < baseSort.length(); i++) {
                        currentSortValues.put(baseSort.get(i));
                    }

                    JSONArray arr = new JSONArray();

                    // 类型
                    JSONArray typeList = d.optJSONArray("typeList");
                    if (typeList != null) {
                        JSONArray typeArr = new JSONArray();
                        for (int i = 0; i < typeList.length(); i++) {
                            JSONObject item = typeList.getJSONObject(i);
                            typeArr.put(new JSONObject().put("n", item.optString("itemText")).put("v", item.optString("itemValue")));
                        }
                        arr.put(new JSONObject().put("key", "type").put("name", "类型").put("value", typeArr));
                    }

                    // 剧情
                    JSONArray plotList = d.optJSONArray("plotList");
                    if (plotList != null && plotList.length() > 0) {
                        JSONArray plotArr = new JSONArray();
                        for (int i = 0; i < plotList.length(); i++) {
                            JSONObject item = plotList.getJSONObject(i);
                            plotArr.put(new JSONObject().put("n", item.optString("itemText")).put("v", item.optString("itemText")));
                        }
                        arr.put(new JSONObject().put("key", "v_class").put("name", "剧情").put("value", plotArr));
                    }

                    // 地区
                    JSONArray districtList = d.optJSONArray("districtList");
                    if (districtList != null) {
                        JSONArray distArr = new JSONArray();
                        for (int i = 0; i < districtList.length(); i++) {
                            JSONObject item = districtList.getJSONObject(i);
                            distArr.put(new JSONObject().put("n", item.optString("itemText")).put("v", item.optString("itemText")));
                        }
                        arr.put(new JSONObject().put("key", "area").put("name", "地区").put("value", distArr));
                    }

                    // 年份
                    JSONArray yearList = d.optJSONArray("yearList");
                    if (yearList != null) {
                        JSONArray yearArr = new JSONArray();
                        for (int i = 0; i < yearList.length(); i++) {
                            JSONObject item = yearList.getJSONObject(i);
                            yearArr.put(new JSONObject().put("n", item.optString("itemText")).put("v", item.optString("itemText")));
                        }
                        arr.put(new JSONObject().put("key", "year").put("name", "年份").put("value", yearArr));
                    }

                    // 语言
                    JSONArray languageList = d.optJSONArray("languageList");
                    if (languageList != null) {
                        JSONArray langArr = new JSONArray();
                        for (int i = 0; i < languageList.length(); i++) {
                            JSONObject item = languageList.getJSONObject(i);
                            langArr.put(new JSONObject().put("n", item.optString("itemText")).put("v", item.optString("itemText")));
                        }
                        arr.put(new JSONObject().put("key", "lang").put("name", "语言").put("value", langArr));
                    }

                    // 排序
                    arr.put(new JSONObject().put("key", "sort").put("name", "排序").put("value", currentSortValues));

                    filters.put(tid, arr);
                }
            }

            // 3. 首页推荐列表 (对应 JS 中的 homeVod)
            JSONArray rawList = new JSONArray();
            JSONObject r1 = reqSafe(currentHost + "/api/mw-movie/anonymous/v1/home/all/list", null);
            JSONObject data1 = r1.optJSONObject("data");
            if (data1 != null) {
                Iterator<String> keys = data1.keys();
                while (keys.hasNext()) {
                    JSONObject obj = data1.optJSONObject(keys.next());
                    if (obj != null && obj.has("list")) {
                        JSONArray subList = obj.optJSONArray("list");
                        if (subList != null) {
                            for (int i = 0; i < subList.length(); i++) {
                                rawList.put(subList.get(i));
                            }
                        }
                    }
                }
            }

            JSONObject r2 = reqSafe(currentHost + "/api/mw-movie/anonymous/home/hotSearch", null);
            JSONArray data2 = r2.optJSONArray("data");
            if (data2 != null) {
                for (int i = 0; i < data2.length(); i++) {
                    rawList.put(data2.get(i));
                }
            }

            JSONObject result = new JSONObject();
            result.put("class", classes);
            result.put("filters", filters);
            result.put("list", normalizeVodList(rawList));
            return result.toString();
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return "";
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        try {
            if (extend == null) extend = new HashMap<>();

            Map<String, String> params = new HashMap<>();
            params.put("area", extend.get("area") != null ? extend.get("area") : "");
            params.put("filterStatus", "1");
            params.put("lang", extend.get("lang") != null ? extend.get("lang") : "");
            params.put("pageNum", pg);
            params.put("pageSize", "30");
            params.put("sort", extend.get("sort") != null ? extend.get("sort") : "1");
            params.put("sortBy", "1");
            params.put("type", extend.get("type") != null ? extend.get("type") : "");
            params.put("type1", tid);
            params.put("v_class", extend.get("v_class") != null ? extend.get("v_class") : "");
            params.put("year", extend.get("year") != null ? extend.get("year") : "");

            String url = currentHost + "/api/mw-movie/anonymous/video/list?" + toQueryString(params);
            JSONObject res = reqSafe(url, params);

            JSONArray rawList = null;
            JSONObject data = res.optJSONObject("data");
            if (data != null) rawList = data.optJSONArray("list");

            JSONObject result = new JSONObject();
            result.put("list", normalizeVodList(rawList));
            result.put("page", Integer.parseInt(pg));
            result.put("pagecount", 9999);
            result.put("limit", 90);
            result.put("total", 999999);
            return result.toString();
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return "";
    }

    @Override
    public String detailContent(List<String> ids) {
        try {
            String id = ids.get(0);
            Map<String, String> params = new HashMap<>();
            params.put("id", id);

            String url = currentHost + "/api/mw-movie/anonymous/video/detail?id=" + id;
            JSONObject res = reqSafe(url, params);

            JSONObject rawVod = res.optJSONObject("data");
            if (rawVod == null) {
                JSONObject failVod = new JSONObject();
                failVod.put("vod_id", id);
                failVod.put("vod_name", "加载失败");
                failVod.put("vod_play_url", "");

                JSONObject result = new JSONObject();
                result.put("list", new JSONArray().put(failVod));
                return result.toString();
            }

            JSONObject vod = normalizeVodItem(rawVod);
            vod.put("vod_play_from", "多多APP");

            JSONArray episodelist = rawVod.optJSONArray("episodelist");
            if (episodelist != null && episodelist.length() > 0) {
                StringBuilder playUrlSb = new StringBuilder();
                for (int i = 0; i < episodelist.length(); i++) {
                    JSONObject ep = episodelist.getJSONObject(i);
                    String name = ep.optString("name");
                    String nid = ep.optString("nid");

                    // JS 逻辑: ep.name.padStart(2, '0')
                    if (name.length() < 2) name = "0" + name;

                    if (i > 0) playUrlSb.append("#");
                    playUrlSb.append(name).append("$").append(id).append("-").append(nid);
                }
                vod.put("vod_play_url", playUrlSb.toString());
            }

            JSONObject result = new JSONObject();
            result.put("list", new JSONArray().put(vod));
            return result.toString();
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return "";
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        try {
            String[] parts = id.split("-");
            String vid = parts[0];
            String nid = parts.length > 1 ? parts[1] : "";

            Map<String, String> params = new HashMap<>();
            params.put("clientType", "1");
            params.put("id", vid);
            params.put("nid", nid);

            String url = currentHost + "/api/mw-movie/anonymous/v2/video/episode/url?clientType=1&id=" + vid + "&nid=" + nid;
            JSONObject res = reqSafe(url, params);

            JSONObject data = res.optJSONObject("data");
            JSONArray rawList = data != null ? data.optJSONArray("list") : null;

            JSONArray urls = new JSONArray();
            if (rawList != null) {
                for (int i = 0; i < rawList.length(); i++) {
                    JSONObject item = rawList.getJSONObject(i);
                    urls.put(item.optString("resolutionName"));
                    urls.put(item.optString("url"));
                }
            }

            JSONObject headers = new JSONObject();
            headers.put("User-Agent", USER_AGENT);
            headers.put("sec-ch-ua-platform", "\"Windows\"");
            headers.put("DNT", "1");
            headers.put("sec-ch-ua", "\"Not/A)Brand\";v=\"8\", \"Chromium\";v=\"131\", \"Google Chrome\";v=\"131\"");
            headers.put("sec-ch-ua-mobile", "?0");
            headers.put("Origin", currentHost);
            headers.put("Referer", currentHost + "/");

            JSONObject result = new JSONObject();
            result.put("parse", 0);
            result.put("url", urls);
            result.put("header", headers);
            return result.toString();
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return "";
    }

    @Override
    public String searchContent(String key, boolean quick) {
        return searchContent(key, quick, "1");
    }

    public String searchContent(String key, boolean quick, String pg) {
        try {
            Map<String, String> params = new HashMap<>();
            params.put("keyword", key);
            params.put("pageNum", pg);
            params.put("pageSize", "8");
            params.put("sourceCode", "1");

            String url = currentHost + "/api/mw-movie/anonymous/video/searchByWord?keyword="
                    + URLEncoder.encode(key, "UTF-8")
                    + "&pageNum=" + pg
                    + "&pageSize=8&sourceCode=1";

            JSONObject res = reqSafe(url, params);

            JSONArray rawList = null;
            JSONObject data = res.optJSONObject("data");
            if (data != null) {
                JSONObject resultObj = data.optJSONObject("result");
                if (resultObj != null) {
                    rawList = resultObj.optJSONArray("list");
                }
            }

            JSONObject result = new JSONObject();
            result.put("list", normalizeVodList(rawList));
            result.put("page", Integer.parseInt(pg));
            return result.toString();
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return "";
    }
}
