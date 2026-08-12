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
import java.util.HashMap;
import java.util.List;

/**
 * 完整适配 WAF Cookie 与动态 SHA-1 强签名的 Wencai 爬虫类
 */
public class Wencai extends Spider {

    private static final String HOST = "https://www.hkybqufgh.com";
    private static final String DEVICE_ID = "c684e808-4922-45e9-b158-da5c48765415";

    // 动态存储防护 Cookie
    private static String wafCookie = "";

    @Override
    public void init(Context context, String extend) {
        try {
            super.init(context, extend);
        } catch (Exception ignored) {
        }
        try {
            homeContent(false);
        } catch (Throwable t) {
            SpiderDebug.log(t);
        }
    }

    /**
     * SHA-1 签名计算 (强制小写)
     */
    public static String sha1(String str) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hashBytes = digest.digest(str.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(b & 0xFF);
                if (hex.length() == 1) {
                    sb.append('0');
                }
                sb.append(hex);
            }
            return sb.toString().toLowerCase();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    /**
     * 严格按 ASCII 字典序重排所有 key=value 键值对
     */
    private String sortQueryParams(String queryStr) {
        if (queryStr == null || queryStr.trim().isEmpty()) return "";
        String[] pairs = queryStr.split("&");
        java.util.Arrays.sort(pairs);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pairs.length; i++) {
            if (i > 0) sb.append("&");
            sb.append(pairs[i]);
        }
        return sb.toString();
    }

    /**
     * 自定义 HTTP 请求方法：保证 timestamp 强一致性与正确签名
     */
    private String fetch(String url, String paramStr) {
        try {
            // 核心：统一全局唯一时间戳
            String timestamp = String.valueOf(System.currentTimeMillis());

            // 构建待签名的所有 key-value 组合
            String rawParams;
            if (paramStr == null || paramStr.trim().isEmpty()) {
                rawParams = "deviceid=" + DEVICE_ID + "&t=" + timestamp;
            } else {
                rawParams = paramStr + "&deviceid=" + DEVICE_ID + "&t=" + timestamp;
            }

            // 按 ASCII 字母字典序排序 (例如: deviceid=...&keyword=...&pageNum=...&pageSize=...&t=...)
            String sortedSignStr = sortQueryParams(rawParams);

            // 计算 SHA-1
            String sign = sha1(sortedSignStr);

            HashMap<String, String> headers = new HashMap<>();
            headers.put("User-Agent", "okhttp/3.12.13");
            headers.put("Host", "www.hkybqufgh.com");
            headers.put("Accept", "application/json, text/plain, */*");
            headers.put("Accept-Language", "zh-CN,zh;q=0.9");
            headers.put("t", timestamp);
            headers.put("sign", sign);
            headers.put("deviceid", DEVICE_ID);
            headers.put("Accept-Encoding", "gzip");

            if (!wafCookie.isEmpty()) {
                headers.put("Cookie", wafCookie);
            }

            return OkHttp.string(url, headers);
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return "";
    }

    /**
     * 1. 首页推荐 / 热门搜索 (homeContent)
     */
    @Override
    public String homeContent(boolean filter) throws Exception {
        try {
            String url = HOST + "/api/mw-movie/anonymous/home/hotSearch";
            String jsonStr = fetch(url, "");

            JSONObject responseJson = new JSONObject(jsonStr);
            JSONArray vodList = new JSONArray();

            if (responseJson.optInt("code") == 200) {
                JSONArray data = responseJson.optJSONArray("data");
                if (data != null) {
                    for (int i = 0; i < data.length(); i++) {
                        JSONObject item = data.getJSONObject(i);
                        JSONObject vod = new JSONObject();
                        vod.put("vod_id", item.optString("vodId"));
                        vod.put("vod_name", item.optString("vodName"));
                        vod.put("vod_pic", item.optString("vodPic"));
                        vod.put("vod_remarks", item.optString("vodRemarks"));
                        vodList.put(vod);
                    }
                }
            }

            JSONObject result = new JSONObject();
            result.put("list", vodList);
            return result.toString();

        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return "";
    }

    /**
     * 2. 分类列表 (categoryContent)
     */
    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        try {
            String area = extend != null && extend.containsKey("area") ? extend.get("area") : "";
            String year = extend != null && extend.containsKey("year") ? extend.get("year") : "";

            String typeKey = "type1";
            if (extend != null && extend.containsKey("type") && !extend.get("type").isEmpty()) {
                typeKey = "type";
            }

            String queryStr = typeKey + "=" + tid + "&pageNum=" + pg + "&area=" + URLEncoder.encode(area, "UTF-8") + "&year=" + URLEncoder.encode(year, "UTF-8");
            String url = HOST + "/api/mw-movie/anonymous/video/list?" + queryStr;

            String paramStr = "area=" + area + "&pageNum=" + pg + "&" + typeKey + "=" + tid + "&year=" + year;

            String jsonStr = fetch(url, paramStr);
            JSONObject responseJson = new JSONObject(jsonStr);

            JSONObject result = new JSONObject();
            JSONArray vodList = new JSONArray();

            if (responseJson.optInt("code") == 200) {
                JSONObject dataObj = responseJson.optJSONObject("data");
                if (dataObj != null) {
                    result.put("page", dataObj.optInt("pageNum", 1));
                    result.put("pagecount", dataObj.optInt("totalPage", 1));
                    result.put("limit", dataObj.optInt("pageSize", 48));
                    result.put("total", dataObj.optInt("totalCount", 0));

                    JSONArray list = dataObj.optJSONArray("list");
                    if (list != null) {
                        for (int i = 0; i < list.length(); i++) {
                            JSONObject item = list.getJSONObject(i);
                            JSONObject vod = new JSONObject();
                            vod.put("vod_id", item.optString("vodId"));

                            String vodName = item.optString("vodName");
                            vod.put("vod_name", vodName.isEmpty() ? "未命名视频" : vodName);
                            vod.put("vod_pic", item.optString("vodPic"));

                            String remarks = item.optString("vodRemarks");
                            if (remarks.isEmpty()) {
                                remarks = item.optString("vodVersion");
                            }
                            vod.put("vod_remarks", remarks);

                            vodList.put(vod);
                        }
                    }
                }
            }
            result.put("list", vodList);
            return result.toString();

        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return "";
    }

    /**
     * 3. 详情页 (detailContent)
     */
    @Override
    public String detailContent(List<String> ids) throws Exception {
        try {
            if (ids == null || ids.isEmpty()) return "";
            String vodId = ids.get(0);

            String url = HOST + "/api/mw-movie/anonymous/video/detail?id=" + vodId;
            String paramStr = "id=" + vodId;

            String jsonStr = fetch(url, paramStr);
            JSONObject responseJson = new JSONObject(jsonStr);
            JSONObject result = new JSONObject();

            if (responseJson.optInt("code") == 200) {
                JSONObject detail = responseJson.optJSONObject("data");
                if (detail != null) {
                    JSONObject vodDetail = new JSONObject();
                    vodDetail.put("vod_id", detail.optString("vodId"));
                    vodDetail.put("vod_name", detail.optString("vodName"));
                    vodDetail.put("vod_pic", detail.optString("vodPic"));
                    vodDetail.put("type_name", detail.optString("typeName"));
                    vodDetail.put("vod_year", detail.optString("vodYear"));
                    vodDetail.put("vod_area", detail.optString("vodArea"));
                    vodDetail.put("vod_remarks", detail.optString("vodRemarks"));
                    vodDetail.put("vod_actor", detail.optString("vodActor"));
                    vodDetail.put("vod_director", detail.optString("vodDirector"));

                    String content = detail.optString("vodContent").replaceAll("<[^>]*>", "");
                    vodDetail.put("vod_content", content);

                    JSONArray episodeList = detail.optJSONArray("episodeList");
                    if (episodeList != null && episodeList.length() > 0) {
                        StringBuilder playUrlSb = new StringBuilder();
                        for (int i = 0; i < episodeList.length(); i++) {
                            JSONObject ep = episodeList.getJSONObject(i);
                            String name = ep.optString("name", "播放");
                            String nid = ep.optString("nid");

                            if (i > 0) playUrlSb.append("#");
                            playUrlSb.append(name).append("$").append(vodId).append("#").append(nid);
                        }
                        vodDetail.put("vod_play_from", "默认线路");
                        vodDetail.put("vod_play_url", playUrlSb.toString());
                    }

                    JSONArray resultList = new JSONArray();
                    resultList.put(vodDetail);
                    result.put("list", resultList);
                }
            }
            return result.toString();

        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return "";
    }

    /**
     * 4. 搜索 (searchContent)
     */
    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        try {
            String pageSize = "8";

            String queryStr = "keyword=" + URLEncoder.encode(key, "UTF-8") + "&pageNum=" + pg + "&pageSize=" + pageSize;
            String url = HOST + "/api/mw-movie/anonymous/video/searchByWord?" + queryStr;

            String paramStr = "keyword=" + key + "&pageNum=" + pg + "&pageSize=" + pageSize;

            String jsonStr = fetch(url, paramStr);
            JSONObject responseJson = new JSONObject(jsonStr);

            JSONObject result = new JSONObject();
            JSONArray vodList = new JSONArray();

            if (responseJson.optInt("code") == 200) {
                JSONObject dataObj = responseJson.optJSONObject("data");
                if (dataObj != null) {
                    JSONObject resultObj = dataObj.optJSONObject("result");
                    if (resultObj != null) {
                        result.put("page", resultObj.optInt("pageNum", 1));
                        result.put("pagecount", resultObj.optInt("totalPage", 1));
                        result.put("limit", resultObj.optInt("pageSize", 8));
                        result.put("total", resultObj.optInt("totalCount", 0));

                        JSONArray list = resultObj.optJSONArray("list");
                        if (list != null) {
                            for (int i = 0; i < list.length(); i++) {
                                JSONObject item = list.getJSONObject(i);
                                JSONObject vod = new JSONObject();
                                vod.put("vod_id", item.optString("vodId"));
                                vod.put("vod_name", item.optString("vodName"));
                                vod.put("vod_pic", item.optString("vodPic"));

                                String remarks = item.optString("vodRemarks");
                                if (remarks.isEmpty()) {
                                    remarks = item.optString("vodVersion");
                                }
                                vod.put("vod_remarks", remarks);

                                vodList.put(vod);
                            }
                        }
                    }
                }
            }
            result.put("list", vodList);
            return result.toString();

        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return "";
    }

    /**
     * 5. 播放解析 (playerContent)
     */
    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        try {
            String[] parts = id.split("#");
            if (parts.length < 2) return "";

            String vodId = parts[0];
            String nid = parts[1];

            String queryStr = "clientType=3&id=" + vodId + "&nid=" + nid;
            String url = HOST + "/api/mw-movie/anonymous/v2/video/episode/url?" + queryStr;

            String paramStr = "clientType=3&id=" + vodId + "&nid=" + nid;

            String jsonStr = fetch(url, paramStr);
            JSONObject responseJson = new JSONObject(jsonStr);

            if (responseJson.optInt("code") == 200) {
                JSONObject dataObj = responseJson.optJSONObject("data");
                if (dataObj != null) {
                    JSONArray list = dataObj.optJSONArray("list");
                    if (list != null && list.length() > 0) {
                        String targetUrl = "";

                        for (int i = 0; i < list.length(); i++) {
                            JSONObject stream = list.getJSONObject(i);
                            if (stream.optBoolean("flag", false)) {
                                targetUrl = stream.optString("url");
                                break;
                            }
                        }

                        if (targetUrl.isEmpty()) {
                            targetUrl = list.getJSONObject(0).optString("url");
                        }

                        JSONObject result = new JSONObject();
                        result.put("parse", 0);
                        result.put("url", targetUrl);

                        JSONObject playHeaders = new JSONObject();
                        playHeaders.put("User-Agent", "Mozilla/5.0 (Linux; Android 9; TV-BOX Build/PQ3A.190705.08211809; wv) AppleWebKit/537.36");
                        playHeaders.put("Origin", "https://www.ghw9zwp5.com");
                        playHeaders.put("Referer", "https://www.ghw9zwp5.com/");

                        result.put("header", playHeaders.toString());

                        return result.toString();
                    }
                }
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return "";
    }
}
