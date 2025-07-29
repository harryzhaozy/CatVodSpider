package com.github.catvod.spider;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.text.TextUtils;
import android.util.Base64;

import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;

import com.github.catvod.net.OkHttp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.Set;


import java.nio.charset.StandardCharsets;

import com.github.catvod.utils.LogUtils;

public class LiteApple extends Spider {
    private static final String siteUrl = "http://item.xpgtv.com/";
    private final String playHost = "http://c.xpgtv.net/m3u8/";
    
    private HashMap<String, String> getHeaders(String url, String data) {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "okhttp/3.12.11 Lvaf/58.12.100");
        return headers;
    }
    public static String getSimpleHash(String timestamp) {
        return md5(timestamp).substring(0, 4);
    }

    public static String md5(String input) {
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
    
    private HashMap<String, String> getCustomHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("user_id", "XPGBOX");
        headers.put("token2", "enxerhSl0jk2TGhbZCygMdwoKqOmyxsk/Kw8tVy4dsRBE1o1xBhWhoFbh98=");
        headers.put("Range", "bytes=0-");
        headers.put("version", "XPGBOX com.phoenix.tv1.3.3");
        headers.put("Icy-MetaData", "1");
        
        headers.put("screenx", "1280");

        // 生成当前 Unix 时间戳（单位：秒）
        //String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        headers.put("hash", "60b2");
        headers.put("timestamp", "1753708324");

        headers.put("token", "RXQbgQKl3QkFZkIPGwGvH5kofvCokkkn/a893wC2IId7HQFmy0Eh24osz555X12xGVFxQLTaGuBqU/Y7KU4lStp4UjR7giPxdwoTOsU6R3oc4yZZTQc/yTKh1mH3ckZhx6VsQCEoFf6q");
        headers.put("screeny", "720");
        headers.put("User-Agent", "Lvaf/58.12.100");
        headers.put("Host", "c.xpgtv.net");
        headers.put("Connection", "Keep-Alive");
        return headers;
    }

    @Override
    public void init(Context context, String extend) {
        try {
            super.init(context, extend);
        } catch (Exception e) {
            e.printStackTrace(); // 或者记录日志、报错处理
        }
    }


    @Override
    public String homeContent(boolean filter) {
        try {
            String url = siteUrl + "api.php/v2.vod/androidtypes";
            String content = OkHttp.string(url, getHeaders(url, null));
            JSONObject jsonObject = new JSONObject(content);
            JSONArray jsonArray = jsonObject.getJSONArray("data");

            JSONObject filterConfig = new JSONObject();
            JSONArray classes = new JSONArray();
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jObj = jsonArray.getJSONObject(i);
                String typeName = jObj.getString("type_name");
                String typeId = jObj.getString("type_id");
                JSONObject newCls = new JSONObject();
                newCls.put("type_id", typeId);
                newCls.put("type_name", typeName);
                classes.put(newCls);

                JSONArray clses = jObj.getJSONArray("classes");
                JSONArray areas = jObj.getJSONArray("areas");
                JSONArray years = jObj.getJSONArray("years");

                JSONArray extendsAll = new JSONArray();
                // 类型
                JSONObject newTypeExtend;
                JSONArray newTypeExtendKV;
                JSONObject kv;
                newTypeExtend = new JSONObject();
                newTypeExtend.put("key", "class");
                newTypeExtend.put("name", "类型");
                newTypeExtendKV = new JSONArray();
                for (int j = 0; j < clses.length(); j++) {
                    String v = clses.getString(j);
                    kv = new JSONObject();
                    kv.put("n", v);
                    kv.put("v", v);
                    newTypeExtendKV.put(kv);
                }
                newTypeExtend.put("value", newTypeExtendKV);
                extendsAll.put(newTypeExtend);
                // 地区
                newTypeExtend = new JSONObject();
                newTypeExtend.put("key", "area");
                newTypeExtend.put("name", "地区");
                newTypeExtendKV = new JSONArray();
                kv = new JSONObject();
                kv.put("n", "全部");
                kv.put("v", "");
                newTypeExtendKV.put(kv);
                for (int j = 0; j < areas.length(); j++) {
                    String area = areas.getString(j);
                    kv = new JSONObject();
                    kv.put("n", area);
                    kv.put("v", area);
                    newTypeExtendKV.put(kv);
                }
                newTypeExtend.put("value", newTypeExtendKV);
                extendsAll.put(newTypeExtend);
                // 年份
                newTypeExtend = new JSONObject();
                newTypeExtend.put("key", "year");
                newTypeExtend.put("name", "年份");
                newTypeExtendKV = new JSONArray();
                kv = new JSONObject();
                kv.put("n", "全部");
                kv.put("v", "");
                newTypeExtendKV.put(kv);
                for (int j = 0; j < years.length(); j++) {
                    String year = years.getString(j);
                    kv = new JSONObject();
                    kv.put("n", year);
                    kv.put("v", year);
                    newTypeExtendKV.put(kv);
                }
                newTypeExtend.put("value", newTypeExtendKV);
                extendsAll.put(newTypeExtend);
                filterConfig.put(typeId, extendsAll);
                // 排序
                newTypeExtend = new JSONObject();
                newTypeExtend.put("key", "sortby");
                newTypeExtend.put("name", "排序");
                newTypeExtendKV = new JSONArray();
                kv = new JSONObject();
                kv.put("n", "时间");
                kv.put("v", "updatetime");
                newTypeExtendKV.put(kv);
                kv = new JSONObject();
                kv.put("n", "人气");
                kv.put("v", "hits");
                newTypeExtendKV.put(kv);
                kv = new JSONObject();
                kv.put("n", "评分");
                kv.put("v", "score");
                newTypeExtendKV.put(kv);
                newTypeExtend.put("value", newTypeExtendKV);
                extendsAll.put(newTypeExtend);
            }

            JSONObject result = new JSONObject();
            result.put("class", classes);
            if (filter) {
                result.put("filters", filterConfig);
            }
            return result.toString();
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return "";
    }

    @Override
    public String homeVideoContent() {
        try {
            JSONArray videos = new JSONArray();
            for (int id = 1; id < 5; id++) {
                if (videos.length() > 30)
                    break;
                try {
                    String url = siteUrl + "api.php/v2.main/androidhome";
                    String content = OkHttp.string(url, getHeaders(url, null));
                    JSONObject jsonObject = new JSONObject(content);
                    JSONArray jsonArray = jsonObject.getJSONObject("data").getJSONArray("list");
                    for (int i = 0; i < jsonArray.length(); i++) {
                        JSONArray jsonArraySub = jsonArray.getJSONObject(i).getJSONArray("list");
                        for (int j = 0; j < jsonArraySub.length() && j < 4; j++) {
                            JSONObject vObj = jsonArraySub.getJSONObject(j);
                            JSONObject v = new JSONObject();
                            v.put("vod_id", vObj.getString("id"));
                            v.put("vod_name", vObj.getString("name"));
                            v.put("vod_pic", vObj.getString("pic"));
                            v.put("vod_remarks", vObj.getString("updateInfo"));
                            videos.put(v);
                        }
                    }
                } catch (Exception e) {

                }
            }
            JSONObject result = new JSONObject();
            result.put("list", videos);
            return result.toString();
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return "";
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        try {
            String area = extend.containsKey("area") ? extend.get("area") : "";
            String year = extend.containsKey("year") ? extend.get("year") : "";
            String url = siteUrl + "api.php/v2.vod/androidfilter?page=" + pg + "&type=" + tid;
            Set<String> keys = extend.keySet();
            for (String key : keys) {
                String val = extend.get(key).trim();
                if (val.length() == 0)
                    continue;
                url += "&" + key + "=" + URLEncoder.encode(val);
            }
            String content = OkHttp.string(url, getHeaders(url, area + year));
            JSONObject dataObject = new JSONObject(content);
            JSONArray jsonArray = dataObject.getJSONArray("data");
            JSONArray videos = new JSONArray();
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject vObj = jsonArray.getJSONObject(i);
                JSONObject v = new JSONObject();
                v.put("vod_id", vObj.getString("id"));
                v.put("vod_name", vObj.getString("name"));
                v.put("vod_pic", vObj.getString("pic"));
                v.put("vod_remarks", vObj.getString("updateInfo"));
                videos.put(v);
            }
            JSONObject result = new JSONObject();
            int limit = 20;
            int page = Integer.parseInt(pg);
            result.put("page", page);
            int pageCount = videos.length() == limit ? page + 1 : page;
            result.put("pagecount", pageCount);
            result.put("limit", limit);
            result.put("total", Integer.MAX_VALUE);
            result.put("list", videos);
            return result.toString();
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return "";
    }

    @Override
    public String detailContent(List<String> ids) {
        try {
            String url = siteUrl + "api.php/v3.vod/androiddetail2?vod_id=" + ids.get(0);
            //String PlayHost="http://c.xpgtv.net/m3u8/";
            String content = OkHttp.string(url, getHeaders(url, ids.get(0)));
            //String content = OkHttp.string(url, getCustomHeaders());
            JSONObject dataObject = new JSONObject(content);
            JSONObject vObj = dataObject.getJSONObject("data");
            JSONObject result = new JSONObject();
            JSONArray list = new JSONArray();
            JSONObject vodAtom = new JSONObject();
            vodAtom.put("vod_id", vObj.getString("id"));
            vodAtom.put("vod_name", vObj.getString("name"));
            vodAtom.put("vod_pic", vObj.getString("pic"));
            vodAtom.put("type_name", vObj.getString("className"));
            vodAtom.put("vod_year", vObj.getString("year"));
            vodAtom.put("vod_area", vObj.getString("area"));
            vodAtom.put("vod_remarks", vObj.getString("updateInfo"));
            vodAtom.put("vod_actor", vObj.getString("actor"));
            vodAtom.put("vod_content", vObj.getString("content").trim());

            ArrayList<String> playUrls = new ArrayList<>();

            JSONArray urls = vObj.getJSONArray("urls");
            for (int i = 0; i < urls.length(); i++) {
                JSONObject u = urls.getJSONObject(i);
                playUrls.add(u.getString("key") + "$" +PlayHost+u.getString("url"));
            }
            
            LogUtils.e("LiteApple.java >>> detailContent playUrls = " + playUrls);
            
            vodAtom.put("vod_play_from", "小苹果");
            vodAtom.put("vod_play_url", TextUtils.join("#", playUrls));
            list.put(vodAtom);
            result.put("list", list);
            return result.toString();
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return "";
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
       /*
        try {
            JSONObject result = new JSONObject();
            result.put("parse", 0);
            //result.put("header", new JSONObject(getHeaders(id, null)).toString());
            result.put("header", new JSONObject(getCustomHeaders()).toString());
            result.put("playUrl", "");
            result.put("url", id);
            return result.toString();
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return "";
        */
        try {
            String url = id;
            if (!url.endsWith(".m3u8")) url += ".m3u8";

            Map<String, String> headers = new HashMap<>();
            headers.put("User-Agent", userAgent);
            headers.put("Connection", "Keep-Alive");
            headers.put("Accept-Language", "zh-CN,zh;q=0.8");
            headers.put("user_id", "XPGBOX");
            headers.put("token2", "XFxIummRrngadHB4TCzeUaleebTX10Vl/ftCvGLPeI5tN2Y/liZ5tY5e4t8=");
            headers.put("version", "XPGBOX com.phoenix.tv1.3.3");
            headers.put("hash", "0d51");
            headers.put("screenx", "2331");
            headers.put("token", "SH4EsXSBhi1ybXp3XQypB5lsfLfbzSpim+hOlmv7IIZ9Kkwoykkh1Y0r9dAKGx/0Smx2VqjAKdYKQuImbjN/Vuc2GWY/wnqwKk1McYhZES5fuT4fGlR0n2ii1nKqbBk8ketLdT0CXrXr8kcZVTdW77fUVG8S5jaTrSrsN/HnCiT4XT1GEkdnV0pqcr5wQL7NV2HHkG/e");
            headers.put("timestamp", "1731848468");
            headers.put("screeny", "1121");

            String m3u8 = OkHttp.string(url, headers);
            if (m3u8.contains("key")) {
                String[] lines = m3u8.split("\n");
                String prefix = url.substring(0, url.indexOf("m3u8"));
                for (int i = 3; i < lines.length; i++) {
                    if (lines[i].contains("key")) {
                        lines[i] = lines[i].replace("/m3u8key", prefix + "m3u8key");
                    }
                }
                m3u8 = String.join("\n", lines);
            }

            JSONObject result = new JSONObject();
            result.put("parse", 0);
            result.put("type", "m3u8");
            result.put("playUrl", "");
            result.put("url", m3u8);
            return result.toString();

            } catch (Exception e) {
                SpiderDebug.log(e);
            return "";
        
    }

    @Override
    public String searchContent(String key, boolean quick) {
        try {
            String url = siteUrl + "api.php/v2.vod/androidsearch10086?page=1&wd=" + URLEncoder.encode(key);
            String content = OkHttp.string(url, getHeaders(url, key));
            JSONObject dataObject = new JSONObject(content);
            JSONArray jsonArray = dataObject.getJSONArray("data");
            JSONArray videos = new JSONArray();
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject vObj = jsonArray.getJSONObject(i);
                String title = vObj.getString("name");
                if (!title.contains(key))
                    continue;
                JSONObject v = new JSONObject();
                v.put("vod_id", vObj.getString("id"));
                v.put("vod_name", title);
                v.put("vod_pic", vObj.getString("pic"));
                v.put("vod_remarks", vObj.getString("updateInfo"));
                videos.put(v);
            }
            JSONObject result = new JSONObject();
            result.put("list", videos);
            return result.toString();
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return "";
    }
}
