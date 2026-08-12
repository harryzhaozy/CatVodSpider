package com.github.catvod.spider;

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
 * 类名：Wencai
 */
public class Wencai extends Spider {

    private static final String HOST = "https://www.hkybqufgh.com";
    private static final String DEVICE_ID = "c684e808-4922-45e9-b158-da5c48765415";

    /**
     * 基础 HTTP Request Headers
     */
    protected HashMap<String, String> getHeaders(String timestamp, String sign) {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "okhttp/3.12.13");
        headers.put("Host", "www.hkybqufgh.com");
        headers.put("t", timestamp);
        headers.put("sign", sign);
        headers.put("deviceid", DEVICE_ID);
        headers.put("Accept-Encoding", "gzip");
        return headers;
    }

    /**
     * SHA-1 签名计算
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
            return sb.toString();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    /**
     * 首页推荐 / 热门搜索 (homeContent)
     */
    @Override
    public String homeContent(boolean filter) throws Exception {
        try {
            String time = String.valueOf(System.currentTimeMillis());
            String signStr = "deviceid=" + DEVICE_ID + "&t=" + time;
            String sign = sha1(signStr);

            String url = HOST + "/api/mw-movie/anonymous/home/hotSearch";
            String jsonStr = OkHttp.string(url, getHeaders(time, sign));

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
     * 分类列表 (categoryContent)
     */
    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        try {
            String area = extend != null && extend.containsKey("area") ? extend.get("area") : "";
            String year = extend != null && extend.containsKey("year") ? extend.get("year") : "";
            
            String typeParam = "type1=" + tid;
            if (extend != null && extend.containsKey("type") && !extend.get("type").isEmpty()) {
                typeParam = "type=" + extend.get("type");
            }

            String time = String.valueOf(System.currentTimeMillis());
            String queryStr = typeParam + "&pageNum=" + pg + "&area=" + URLEncoder.encode(area, "UTF-8") + "&year=" + URLEncoder.encode(year, "UTF-8");
            String url = HOST + "/api/mw-movie/anonymous/video/list?" + queryStr;

            String signStr = "area=" + area + "&deviceid=" + DEVICE_ID + "&pageNum=" + pg + "&t=" + time + "&" + typeParam + "&year=" + year;
            String sign = sha1(signStr);

            String jsonStr = OkHttp.string(url, getHeaders(time, sign));
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
     * 详情页 (detailContent)
     */
    @Override
    public String detailContent(List<String> ids) throws Exception {
        try {
            if (ids == null || ids.isEmpty()) return "";
            String vodId = ids.get(0);

            String time = String.valueOf(System.currentTimeMillis());
            String sign = sha1("deviceid=" + DEVICE_ID + "&id=" + vodId + "&t=" + time);

            String url = HOST + "/api/mw-movie/anonymous/video/detail?id=" + vodId;
            String jsonStr = OkHttp.string(url, getHeaders(time, sign));

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
                            // 拼装成 name$vodId#nid 格式传给 playerContent
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
     * 播放解析 (playerContent)
     */
   /**
     * 播放解析 (playerContent)
     */
    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        try {
            String[] parts = id.split("#");
            if (parts.length < 2) return "";

            String vodId = parts[0];
            String nid = parts[1];

            String time = String.valueOf(System.currentTimeMillis());
            String queryStr = "clientType=3&id=" + vodId + "&nid=" + nid;
            String url = HOST + "/api/mw-movie/anonymous/v2/video/episode/url?" + queryStr;

            String signStr = "clientType=3&deviceid=" + DEVICE_ID + "&id=" + vodId + "&nid=" + nid + "&t=" + time;
            String sign = sha1(signStr);

            // 获取 API Headers
            HashMap<String, String> headers = getHeaders(time, sign);
            headers.put("User-Agent", "Mozilla/5.0 (Linux; Android 9; TV-BOX Build/PQ3A.190705.08211809; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/91.0.4472.114 Mobile Safari/537.36");

            String jsonStr = OkHttp.string(url, headers);
            JSONObject responseJson = new JSONObject(jsonStr);

            if (responseJson.optInt("code") == 200) {
                JSONObject dataObj = responseJson.optJSONObject("data");
                if (dataObj != null) {
                    JSONArray list = dataObj.optJSONArray("list");
                    if (list != null && list.length() > 0) {
                        String targetUrl = "";

                        // 策略优化：
                        // 1. 优先寻找 flag 为 true 的画质 (服务端推荐/免校验标识)
                        for (int i = 0; i < list.length(); i++) {
                            JSONObject stream = list.getJSONObject(i);
                            if (stream.optBoolean("flag", false)) {
                                targetUrl = stream.optString("url");
                                break;
                            }
                        }

                        // 2. 如果没有 flag=true，默认取第一条最高画质 (1080P)
                        if (targetUrl.isEmpty()) {
                            targetUrl = list.getJSONObject(0).optString("url");
                        }

                        JSONObject result = new JSONObject();
                        result.put("parse", 0); // 直链
                        result.put("url", targetUrl);

                        // 根据抓包补全播放器请求防盗链 Header
                        JSONObject playHeaders = new JSONObject();
                        playHeaders.put("User-Agent", headers.get("User-Agent"));
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
