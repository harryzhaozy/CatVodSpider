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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class Wencai extends Spider {

    private static final String SITE_URL = "https://www.hkybqufgh.com";
    private static final String DEVICE_ID = "c684e808-4922-45e9-b158-da5c48765415";
    private static String wafCookie = "";

    @Override
    public void init(Context context, String extend) {
        try {
            super.init(context, extend);
        } catch (Exception ignored) {
        }
        try {
            // 预热请求获取 WAF Cookie
            homeContent(false);
        } catch (Throwable t) {
            SpiderDebug.log(t);
        }
    }

    /**
     * 嵌套哈希签名算法：SHA-1(MD5(e))
     */
    public static String sign(String e) {

    try {

        MessageDigest md5 = MessageDigest.getInstance("MD5");

        byte[] md5Bytes = md5.digest(e.getBytes("UTF-8"));



        StringBuilder md5Hex = new StringBuilder();

        for (byte b : md5Bytes) {

            md5Hex.append(String.format("%02x", b & 0xff));

        }



        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");

        byte[] sha1Bytes = sha1.digest(md5Hex.toString().getBytes("UTF-8"));



        StringBuilder result = new StringBuilder();

        for (byte b : sha1Bytes) {

            result.append(String.format("%02x", b & 0xff));

        }



        return result.toString();

    } catch (Exception e) {

        return "";

    }

}

    

    /**
     * HTTP 请求封装：精简 Header + 嵌套 Hash 签名
     */
    private String fetch(String url, String paramStr) {
        try {
            String timestamp = String.valueOf(System.currentTimeMillis());
            

            // 1. 拼接待签名明文串
            String rawParams;
            if (paramStr == null || paramStr.trim().isEmpty()) {
                rawParams = "deviceid=" + DEVICE_ID + "&t=" + timestamp;
            } else {
                rawParams = paramStr + "&deviceid=" + DEVICE_ID + "&t=" + timestamp;
            }

            // 2. 排序参数串
           

            // 3. 计算 SHA-1(MD5(e))
            //String signVal = sign(sortedParams);
            String signVal =sign(rawParams);
            // 4. 构建与抓包一致的请求头
            HashMap<String, String> headers = new HashMap<>();
            headers.put("Host", "www.hkybqufgh.com");
            headers.put("t", timestamp);
            headers.put("sign", signVal);
            headers.put("deviceid", DEVICE_ID);
            headers.put("accept-encoding", "gzip");
            headers.put("user-agent", "okhttp/3.12.13");

            if (!wafCookie.isEmpty()) {
                headers.put("Cookie", wafCookie);
            }

            return OkHttp.string(url, headers);
        } catch (Exception ex) {
            SpiderDebug.log(ex);
        }
        return "";
    }

    @Override
    public String homeContent(boolean filter) {
        try {
            String url = SITE_URL + "/api/mw-movie/anonymous/home/hotSearch";
            String jsonStr = fetch(url, "");
            
            JSONObject response = new JSONObject(jsonStr);
            JSONArray list = response.optJSONArray("data");

            List<JSONObject> vodList = new ArrayList<>();
            if (list != null) {
                for (int i = 0; i < list.length(); i++) {
                    JSONObject item = list.getJSONObject(i);
                    JSONObject vod = new JSONObject();
                    vod.put("vod_id", item.optString("vodId"));
                    vod.put("vod_name", item.optString("vodName"));
                    vod.put("vod_pic", item.optString("vodPic"));
                    vod.put("vod_remarks", item.optString("vodRemarks"));
                    vodList.add(vod);
                }
            }

            JSONObject result = new JSONObject();
            result.put("list", new JSONArray(vodList));
            return result.toString();
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return "";
    }

    @Override
    public String searchContent(String key, boolean quick) {
        try {
            String paramStr = "keyword=" + URLEncoder.encode(key, "UTF-8");
            String url = SITE_URL + "/api/mw-movie/anonymous/video/search?" + paramStr;
            String jsonStr = fetch(url, paramStr);

            JSONObject response = new JSONObject(jsonStr);
            JSONArray list = response.optJSONArray("data");

            List<JSONObject> vodList = new ArrayList<>();
            if (list != null) {
                for (int i = 0; i < list.length(); i++) {
                    JSONObject item = list.getJSONObject(i);
                    JSONObject vod = new JSONObject();
                    vod.put("vod_id", item.optString("vodId"));
                    vod.put("vod_name", item.optString("vodName"));
                    vod.put("vod_pic", item.optString("vodPic"));
                    vod.put("vod_remarks", item.optString("vodRemarks"));
                    vodList.add(vod);
                }
            }

            JSONObject result = new JSONObject();
            result.put("list", new JSONArray(vodList));
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
            String paramStr = "id=" + id;
            String url = SITE_URL + "/api/mw-movie/anonymous/video/detail?" + paramStr;
            String jsonStr = fetch(url, paramStr);

            JSONObject response = new JSONObject(jsonStr);
            JSONObject data = response.optJSONObject("data");

            if (data != null) {
                JSONObject vod = new JSONObject();
                vod.put("vod_id", data.optString("vodId"));
                vod.put("vod_name", data.optString("vodName"));
                vod.put("vod_pic", data.optString("vodPic"));
                vod.put("vod_remarks", data.optString("vodRemarks"));
                vod.put("vod_content", data.optString("vodContent"));

                JSONObject result = new JSONObject();
                List<JSONObject> list = new ArrayList<>();
                list.add(vod);
                result.put("list", new JSONArray(list));
                return result.toString();
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return "";
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        try {
            String paramStr = "id=" + id;
            String url = SITE_URL + "/api/mw-movie/anonymous/video/playUrl?" + paramStr;
            String jsonStr = fetch(url, paramStr);

            JSONObject response = new JSONObject(jsonStr);
            JSONObject data = response.optJSONObject("data");

            if (data != null) {
                String playUrl = data.optString("playUrl");
                JSONObject result = new JSONObject();
                result.put("parse", 0);
                result.put("playUrl", "");
                result.put("url", playUrl);
                return result.toString();
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return "";
    }
}
