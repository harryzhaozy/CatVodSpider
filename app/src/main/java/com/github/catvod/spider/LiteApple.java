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


public class LiteApple extends Spider {
    private static final String siteUrl = "http://ht.grelighting.cn/api.php";

    private HashMap<String, String> getHeaders(String url, String data) {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "okhttp/4.9.1 Lvaf/58.12.100");
        return headers;
    }

    @Override
    public void init(Context context, String extend) {
        super.init(context, extend);
    }

    @Override
    public String homeContent(boolean filter) {
        try {
            String url = siteUrl + "/v2.vod/androidtypes";
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
                    String url = siteUrl + "/v2.main/androidhome";
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
            String url = siteUrl + "/v2.vod/androidfilter?page=" + pg + "&type=" + tid;
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
            String url = siteUrl + "/v2.vod/androiddetail?vod_id=" + ids.get(0);
            String content = OkHttp.string(url, getHeaders(url, ids.get(0)));
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
                playUrls.add(u.getString("key") + "$" + u.getString("url"));
            }

            vodAtom.put("vod_play_from", "LiteApple");
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
        try {
            JSONObject result = new JSONObject();
            result.put("parse", 0);
            result.put("header", new JSONObject(getHeaders(id, null)).toString());
            result.put("playUrl", "");
            result.put("url", id);
            return result.toString();
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return "";
    }

    @Override
    public String searchContent(String key, boolean quick) {
        try {
            String url = siteUrl + "/v2.vod/androidsearch?page=1&wd=" + URLEncoder.encode(key);
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
