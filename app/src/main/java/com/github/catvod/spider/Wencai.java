package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;
import com.github.catvod.crawler.Spider;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;

import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class Wencai extends Spider {
    private static final String HOST = "https://www.hkybqufgh.com";
    private static final String KEY = "cb808529bae6b6be45ecfab29a4889bc";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";
    private OkHttpClient client;

    @Override
    public void init(Context context, String extend) throws Exception {
        try{
        super.init(context, extend);
        client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build();
        } catch (Exception e)
            {
             e.printStackTrace();
            }
    }

    private String md5(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(s.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private String sha1(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(s.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private String toQueryString(LinkedHashMap<String, String> params) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                if (sb.length() > 0) sb.append("&");
                sb.append(entry.getKey()).append("=").append(entry.getValue());
            }
        }
        return sb.toString();
    }

    private HashMap<String, String> getHeaders(LinkedHashMap<String, String> params) {
        String t = String.valueOf(System.currentTimeMillis());
        // 保证参数拼装顺序不变，按照 JS 的 { ...params, key, t }
        LinkedHashMap<String, String> signParams = new LinkedHashMap<>(params);
        signParams.put("key", KEY);
        signParams.put("t", t);

        String sign = sha1(md5(toQueryString(signParams)));

        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", USER_AGENT);
        headers.put("Accept", "application/json, text/plain, */*");
        headers.put("sign", sign);
        headers.put("t", t);
        headers.put("deviceid", UUID.randomUUID().toString());
        return headers;
    }

    private String normalizeFieldName(String k) {
        String l = k.toLowerCase();
        if (l.startsWith("vod") && l.length() > 3) return "vod_" + l.substring(3);
        if (l.startsWith("type") && l.length() > 4) return "type_" + l.substring(4);
        return l;
    }

    private JSONArray normalizeVodList(JSONArray list) throws Exception {
        JSONArray result = new JSONArray();
        if (list == null) return result;
        for (int i = 0; i < list.length(); i++) {
            JSONObject item = list.optJSONObject(i);
            if (item == null) continue;
            JSONObject resItem = new JSONObject();
            Iterator<String> keys = item.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                Object v = item.opt(k);
                if (v != null && !JSONObject.NULL.equals(v)) {
                    resItem.put(normalizeFieldName(k), v);
                }
            }
            result.put(resItem);
        }
        return result;
    }

    private JSONObject reqSafe(String url, HashMap<String, String> headers) {
        try {
            Request.Builder builder = new Request.Builder().url(url).get();
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                builder.addHeader(entry.getKey(), entry.getValue());
            }
            Response response = client.newCall(builder.build()).execute();
            if (response.isSuccessful() && response.body() != null) {
                return new JSONObject(response.body().string());
            }
        } catch (Exception e) {
            // Ignored as per JS catch
        }
        return new JSONObject();
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        JSONObject cRes = reqSafe(HOST + "/api/mw-movie/anonymous/get/filer/type", getHeaders(new LinkedHashMap<>()));
        JSONObject fRes = reqSafe(HOST + "/api/mw-movie/anonymous/v1/get/filer/list", getHeaders(new LinkedHashMap<>()));

        JSONArray cData = cRes.optJSONArray("data");
        JSONArray classes = new JSONArray();
        if (cData != null) {
            for (int i = 0; i < cData.length(); i++) {
                JSONObject k = cData.optJSONObject(i);
                JSONObject c = new JSONObject();
                c.put("type_name", k.optString("typeName"));
                c.put("type_id", k.optString("typeId"));
                classes.put(c);
            }
        }

        JSONObject fData = fRes.optJSONObject("data");
        JSONObject filters = new JSONObject();

        JSONArray baseSort = new JSONArray();
        baseSort.put(new JSONObject().put("n", "最近更新").put("v", "2"));
        baseSort.put(new JSONObject().put("n", "人气高低").put("v", "3"));
        baseSort.put(new JSONObject().put("n", "评分高低").put("v", "4"));

        JSONArray baseSortSlice = new JSONArray();
        baseSortSlice.put(new JSONObject().put("n", "人气高低").put("v", "3"));
        baseSortSlice.put(new JSONObject().put("n", "评分高低").put("v", "4"));

        if (fData != null) {
            Iterator<String> keys = fData.keys();
            while (keys.hasNext()) {
                String tid = keys.next();
                JSONObject d = fData.optJSONObject(tid);
                if (d == null) continue;

                JSONArray currentSortValues = "1".equals(tid) ? baseSortSlice : baseSort;
                JSONArray arr = new JSONArray();

                if (d.has("typeList")) {
                    JSONArray typeList = d.optJSONArray("typeList");
                    JSONObject f = new JSONObject().put("key", "type").put("name", "类型");
                    JSONArray vals = new JSONArray();
                    for (int i = 0; typeList != null && i < typeList.length(); i++)
                        vals.put(new JSONObject().put("n", typeList.optJSONObject(i).optString("itemText")).put("v", typeList.optJSONObject(i).optString("itemValue")));
                    arr.put(f.put("value", vals));
                }

                if (d.has("plotList") && d.optJSONArray("plotList").length() > 0) {
                    JSONArray plotList = d.optJSONArray("plotList");
                    JSONObject f = new JSONObject().put("key", "v_class").put("name", "剧情");
                    JSONArray vals = new JSONArray();
                    for (int i = 0; i < plotList.length(); i++)
                        vals.put(new JSONObject().put("n", plotList.optJSONObject(i).optString("itemText")).put("v", plotList.optJSONObject(i).optString("itemText")));
                    arr.put(f.put("value", vals));
                }

                if (d.has("districtList")) {
                    JSONArray districtList = d.optJSONArray("districtList");
                    JSONObject f = new JSONObject().put("key", "area").put("name", "地区");
                    JSONArray vals = new JSONArray();
                    for (int i = 0; districtList != null && i < districtList.length(); i++)
                        vals.put(new JSONObject().put("n", districtList.optJSONObject(i).optString("itemText")).put("v", districtList.optJSONObject(i).optString("itemText")));
                    arr.put(f.put("value", vals));
                }

                if (d.has("yearList")) {
                    JSONArray yearList = d.optJSONArray("yearList");
                    JSONObject f = new JSONObject().put("key", "year").put("name", "年份");
                    JSONArray vals = new JSONArray();
                    for (int i = 0; yearList != null && i < yearList.length(); i++)
                        vals.put(new JSONObject().put("n", yearList.optJSONObject(i).optString("itemText")).put("v", yearList.optJSONObject(i).optString("itemText")));
                    arr.put(f.put("value", vals));
                }

                if (d.has("languageList")) {
                    JSONArray languageList = d.optJSONArray("languageList");
                    JSONObject f = new JSONObject().put("key", "lang").put("name", "语言");
                    JSONArray vals = new JSONArray();
                    for (int i = 0; languageList != null && i < languageList.length(); i++)
                        vals.put(new JSONObject().put("n", languageList.optJSONObject(i).optString("itemText")).put("v", languageList.optJSONObject(i).optString("itemText")));
                    arr.put(f.put("value", vals));
                }

                arr.put(new JSONObject().put("key", "sort").put("name", "排序").put("value", currentSortValues));
                filters.put(tid, arr);
            }
        }

        JSONObject result = new JSONObject();
        result.put("class", classes);
        result.put("filters", filters);
        return result.toString();
    }

    @Override
    public String homeVideoContent() throws Exception {
        JSONObject r1 = reqSafe(HOST + "/api/mw-movie/anonymous/v1/home/all/list", getHeaders(new LinkedHashMap<>()));
        JSONObject r2 = reqSafe(HOST + "/api/mw-movie/anonymous/home/hotSearch", getHeaders(new LinkedHashMap<>()));

        JSONArray list = new JSONArray();
        JSONObject data1 = r1.optJSONObject("data");
        if (data1 != null) {
            Iterator<String> keys = data1.keys();
            while (keys.hasNext()) {
                JSONObject obj = data1.optJSONObject(keys.next());
                if (obj != null && obj.has("list")) {
                    JSONArray gList = obj.optJSONArray("list");
                    for (int i = 0; gList != null && i < gList.length(); i++) list.put(gList.optJSONObject(i));
                }
            }
        }

        JSONArray data2 = r2.optJSONArray("data");
        if (data2 != null) {
            for (int i = 0; i < data2.length(); i++) list.put(data2.optJSONObject(i));
        }

        JSONObject result = new JSONObject();
        result.put("list", normalizeVodList(list));
        return result.toString();
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        LinkedHashMap<String, String> params = new LinkedHashMap<>();
        params.put("area", extend != null && extend.containsKey("area") ? extend.get("area") : "");
        params.put("filterStatus", "1");
        params.put("lang", extend != null && extend.containsKey("lang") ? extend.get("lang") : "");
        params.put("pageNum", pg);
        params.put("pageSize", "30");
        params.put("sort", extend != null && extend.containsKey("sort") ? extend.get("sort") : "1");
        params.put("sortBy", "1");
        params.put("type", extend != null && extend.containsKey("type") ? extend.get("type") : "");
        params.put("type1", tid);
        params.put("v_class", extend != null && extend.containsKey("v_class") ? extend.get("v_class") : "");
        params.put("year", extend != null && extend.containsKey("year") ? extend.get("year") : "");

        String url = HOST + "/api/mw-movie/anonymous/video/list?" + toQueryString(params);
        JSONObject res = reqSafe(url, getHeaders(params));
        
        JSONObject data = res.optJSONObject("data");
        JSONArray vodList = normalizeVodList(data != null ? data.optJSONArray("list") : new JSONArray());

        JSONObject result = new JSONObject();
        result.put("list", vodList);
        result.put("page", Integer.parseInt(pg));
        result.put("pagecount", 9999);
        result.put("limit", 90);
        result.put("total", 999999);
        return result.toString();
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String id = ids.get(0);
        LinkedHashMap<String, String> params = new LinkedHashMap<>();
        params.put("id", id);

        String url = HOST + "/api/mw-movie/anonymous/video/detail?id=" + id;
        JSONObject res = reqSafe(url, getHeaders(params));
        
        JSONObject data = res.optJSONObject("data");
        JSONArray wrapper = new JSONArray();
        if (data != null) wrapper.put(data);

        JSONArray normalized = normalizeVodList(wrapper);
        if (normalized.length() == 0) {
            JSONObject err = new JSONObject();
            err.put("vod_id", id);
            err.put("vod_name", "加载失败");
            err.put("vod_play_url", "");
            return new JSONObject().put("list", new JSONArray().put(err)).toString();
        }

        JSONObject vod = normalized.optJSONObject(0);
        vod.put("vod_play_from", "多多APP");

        JSONArray episodelist = vod.optJSONArray("episodelist"); // Normalize转换为全小写了
        if (episodelist != null && episodelist.length() > 0) {
            List<String> eps = new ArrayList<>();
            for (int i = 0; i < episodelist.length(); i++) {
                JSONObject ep = episodelist.optJSONObject(i);
                String name = ep.optString("name");
                if (name.length() == 1) name = "0" + name; // padding
                String nid = ep.optString("nid");
                eps.add(name + "$" + id + "-" + nid);
            }
            vod.put("vod_play_url", TextUtils.join("#", eps));
            vod.remove("episodelist");
        }

        return new JSONObject().put("list", new JSONArray().put(vod)).toString();
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        String[] parts = id.split("-");
        String vid = parts[0];
        String nid = parts[1];

        LinkedHashMap<String, String> params = new LinkedHashMap<>();
        params.put("clientType", "1");
        params.put("id", vid);
        params.put("nid", nid);

        String url = HOST + "/api/mw-movie/anonymous/v2/video/episode/url?clientType=1&id=" + vid + "&nid=" + nid;
        JSONObject res = reqSafe(url, getHeaders(params));

        JSONObject data = res.optJSONObject("data");
        JSONArray list = data != null ? data.optJSONArray("list") : new JSONArray();

        JSONArray urls = new JSONArray();
        if (list != null) {
            for (int i = 0; i < list.length(); i++) {
                JSONObject item = list.optJSONObject(i);
                urls.put(item.optString("resolutionName"));
                urls.put(item.optString("url"));
            }
        }

        JSONObject header = new JSONObject();
        header.put("User-Agent", USER_AGENT);
        header.put("sec-ch-ua-platform", "\"Windows\"");
        header.put("DNT", "1");
        header.put("sec-ch-ua", "\"Not/A)Brand\";v=\"8\", \"Chromium\";v=\"131\", \"Google Chrome\";v=\"131\"");
        header.put("sec-ch-ua-mobile", "?0");
        header.put("Origin", HOST);
        header.put("Referer", HOST + "/");

        JSONObject result = new JSONObject();
        result.put("parse", 0);
        // 如果环境支持多线路可以直接传 JSONArray，否则兜底回退为提取第一个线路的纯字符串格式
        result.put("url", urls.length() > 0 ? urls : ""); 
        result.put("header", header);
        return result.toString();
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        LinkedHashMap<String, String> params = new LinkedHashMap<>();
        params.put("keyword", key);
        params.put("pageNum", "1");
        params.put("pageSize", "8");
        params.put("sourceCode", "1");

        String url = HOST + "/api/mw-movie/anonymous/video/searchByWord?" + toQueryString(params);
        JSONObject res = reqSafe(url, getHeaders(params));

        JSONObject data = res.optJSONObject("data");
        JSONObject resultData = data != null ? data.optJSONObject("result") : null;
        JSONArray list = resultData != null ? resultData.optJSONArray("list") : new JSONArray();

        JSONObject result = new JSONObject();
        result.put("list", normalizeVodList(list));
        result.put("page", 1);
        return result.toString();
    }
}
