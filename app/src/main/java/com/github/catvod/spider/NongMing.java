package com.github.catvod.spider;

import android.text.TextUtils;

import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp; 

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;



/**
 * 农民影视（域名改为旺旺有效域名）
 */
public class NongMing extends Spider {

    // ★ 唯一改动：站点域名
    private final String siteUrl = "https://vip.wwgz.cn:5200";

     

    private final String userAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 14_6 like Mac OS X) "
            + "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/14.1.1 Mobile/15E148 Safari/604.1";

    private String req(String url, Map<String, String> header) {
        return OkHttp.string(url, header);
    }

    private Map<String, String> getHeader() {
        Map<String, String> header = new HashMap<>();
        header.put("User-Agent", userAgent);
        return header;
    }

    private String getRemark(String html) {
        return find(Pattern.compile("状态:&nbsp;(.*?)</div"), html)
                .replaceAll("</?[^>]+>", "");
    }

    private String getDirector(String html) {
        return find(Pattern.compile("导演:&nbsp;(.*?)</div"), html)
                .replaceAll("</?[^>]+>", "")
                .replaceAll("&nbsp;&nbsp;", "")
                .replaceAll("&nbsp;", ",");
    }

    private String getActor(String html) {
        return find(Pattern.compile("主演:&nbsp;(.*?)</div"), html)
                .replaceAll("</?[^>]+>", "")
                .replaceAll("&nbsp;&nbsp;", "")
                .replaceAll("&nbsp;", ",");
    }

    private String find(Pattern pattern, String html) {
        Matcher matcher = pattern.matcher(html);
        return matcher.find() ? matcher.group(1) : "";
    }

    private String getYear(String html) {
        return find(Pattern.compile("年代:.*?<a.*?>(.*?)</a>"), html);
    }

    private JSONObject buildFilters() throws Exception {
    // 最外层：Filters 对象 {}
    JSONObject filters = new JSONObject();

    // ==========================================
    // 1. 构建【电影】分类的筛选 (ID 为 "1")
    // ==========================================
    JSONArray filmFilters = new JSONArray(); // 电影分类下的筛选组 []

    // 1.1 电影 - 类型筛选
    JSONObject filmType = new JSONObject();
    filmType.put("key", "cateId");
    filmType.put("name", "类型");
    
    JSONArray filmTypeVals = new JSONArray();
    filmTypeVals.put(new JSONObject().put("n", "全部").put("v", "1"));
    filmTypeVals.put(new JSONObject().put("n", "动作片").put("v", "5"));
    filmTypeVals.put(new JSONObject().put("n", "喜剧片").put("v", "6"));
    filmTypeVals.put(new JSONObject().put("n", "爱情片").put("v", "7"));
    filmTypeVals.put(new JSONObject().put("n", "科幻片").put("v", "8"));
    filmType.put("value", filmTypeVals);
    
    filmFilters.put(filmType); // 将"类型"加入电影筛选组

    // 1.2 电影 - 地区筛选
    JSONObject filmArea = new JSONObject();
    filmArea.put("key", "area");
    filmArea.put("name", "地区");
    
    JSONArray filmAreaVals = new JSONArray();
    filmAreaVals.put(new JSONObject().put("n", "全部").put("v", ""));
    filmAreaVals.put(new JSONObject().put("n", "大陆").put("v", "大陆"));
    filmAreaVals.put(new JSONObject().put("n", "香港").put("v", "香港"));
    filmAreaVals.put(new JSONObject().put("n", "台湾").put("v", "台湾"));
    filmAreaVals.put(new JSONObject().put("n", "美国").put("v", "美国"));
    filmArea.put("value", filmAreaVals);
    
    filmFilters.put(filmArea); // 将"地区"加入电影筛选组

    // 1.3 电影 - 排序筛选
    JSONObject filmSort = new JSONObject();
    filmSort.put("key", "by");
    filmSort.put("name", "排序");
    
    JSONArray filmSortVals = new JSONArray();
    filmSortVals.put(new JSONObject().put("n", "时间").put("v", "time"));
    filmSortVals.put(new JSONObject().put("n", "人气").put("v", "hits"));
    filmSortVals.put(new JSONObject().put("n", "评分").put("v", "score"));
    filmSort.put("value", filmSortVals);
    
    filmFilters.put(filmSort); // 将"排序"加入电影筛选组

    // 将电影筛选挂载到 filters 字典，Key 必须对应 class 中的 type_id ("1")
    filters.put("1", filmFilters);


    // ==========================================
    // 2. 构建【电视剧】分类的筛选 (ID 为 "2")
    // ==========================================
    JSONArray tvFilters = new JSONArray(); // 电视剧分类下的筛选组 []

    // 2.1 电视剧 - 类型筛选
    JSONObject tvType = new JSONObject();
    tvType.put("key", "cateId");
    tvType.put("name", "类型");
    
    JSONArray tvTypeVals = new JSONArray();
    tvTypeVals.put(new JSONObject().put("n", "全部").put("v", "2"));
    tvTypeVals.put(new JSONObject().put("n", "国产剧").put("v", "12"));
    tvTypeVals.put(new JSONObject().put("n", "港台泰").put("v", "13"));
    tvTypeVals.put(new JSONObject().put("n", "日韩剧").put("v", "14"));
    tvTypeVals.put(new JSONObject().put("n", "欧美剧").put("v", "15"));
    tvType.put("value", tvTypeVals);
    
    tvFilters.put(tvType);

    // 将电视剧筛选挂载到 filters 字典，Key 对应 type_id ("2")
    filters.put("2", tvFilters);

    return filters;
}
    
    @Override
    public String homeContent(boolean filter) throws Exception {
        JSONObject result = new JSONObject();

        // 1. 组装基础分类列表
        JSONArray classes = new JSONArray();
        List<String> typeIds = Arrays.asList("1", "2", "3", "4");
        List<String> typeNames = Arrays.asList("电影", "电视剧", "综艺", "动漫");
    
        for (int i = 0; i < typeIds.size(); i++) {
            JSONObject c = new JSONObject();
            c.put("type_id", typeIds.get(i));
            c.put("type_name", typeNames.get(i));
            classes.put(c);
        }
        result.put("class", classes);

        // 2. 如果客户端请求 filter，则动态构建并添加 filters 字段
        if (filter) {
            result.put("filters", buildFilters());
        }

        return result.toString();
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        String area = extend.get("area") == null ? "" : extend.get("area");
        String year = extend.get("year") == null ? "" : extend.get("year");
        String by = extend.get("by") == null ? "" : extend.get("by");
        String classType = extend.get("class") == null ? tid : extend.get("class");

        String cateUrl = siteUrl + String.format("/vod-list-id-%s-pg-%s-order--by-%s-class-0-year-%s-letter--area-%s-lang-.html", classType, pg, by, year, area);
        String html = req(cateUrl, getHeader());
        JSONArray videos = new JSONArray();
        Elements items = Jsoup.parse(html).select("[class=globalPicList] li > a");
        for (Element item : items) {
            String vodId = item.attr("href");
            String name = item.attr("title");
            String pic = item.select("img").attr("src");
            List<TextNode> textNodes = item.select(".sBottom span").textNodes();
            String remark = textNodes.size() > 0 ? textNodes.get(0).text() : "";
            JSONObject vod = new JSONObject();
            vod.put("vod_id", vodId);
            vod.put("vod_name", name);
            vod.put("vod_pic", pic);
            vod.put("vod_remarks", remark);
            videos.put(vod);
        }
        int page = Integer.parseInt(pg), count = Integer.MAX_VALUE, limit = 30, total = Integer.MAX_VALUE;
        JSONObject result = new JSONObject();
        result.put("page", page);
        result.put("pagecount", count);
        result.put("limit", limit);
        result.put("total", total);
        result.put("list", videos);
        return result.toString();
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String vodId = ids.get(0);
        String detailUrl = siteUrl + vodId;
        String html = req(detailUrl, getHeader());
        Document doc = Jsoup.parse(html);
        String name = doc.select(".page-hd a").attr("title");
        String pic = doc.select(".page-hd img").attr("src");
        String typeName = doc.select(".type-title").text();
        String year = getYear(html);
        String remark = getRemark(html);
        String actor = getActor(html);
        String director = getDirector(html);
        String description = doc.select(".detail-con p").text().replaceAll("简 介：", "");

        Elements sourceList = doc.select("[class=numList]");
        Elements circuits = doc.select("#leftTabBox > .hd a");
        Map<String, String> playMap = new LinkedHashMap<>();
        for (int i = 0; i < sourceList.size(); i++) {
            String circuitName = circuits.get(i).text();
            ArrayList<String> vodItems = new ArrayList<>();
            Elements aList = sourceList.get(i).select("a");
            for (int j = aList.size() - 1; j >= 0; j--) {
                String episodeUrl = siteUrl + aList.get(j).attr("href");
                String episodeName = aList.get(j).text();
                vodItems.add(episodeName + "$" + episodeUrl);
            }
            if (vodItems.size() > 0) playMap.put(circuitName, TextUtils.join("#", vodItems));
        }

        JSONObject vod = new JSONObject();
        vod.put("vod_id", ids.get(0));
        vod.put("vod_name", name);
        vod.put("vod_pic", pic);
        vod.put("type_name", typeName);
        vod.put("vod_year", year);
        vod.put("vod_area", "");
        vod.put("vod_remarks", remark);
        vod.put("vod_actor", actor);
        vod.put("vod_director", director);
        vod.put("vod_content", description);
        if (playMap.size() > 0) {
            vod.put("vod_play_from", TextUtils.join("$$$", playMap.keySet()));
            vod.put("vod_play_url", TextUtils.join("$$$", playMap.values()));
        }
        JSONArray jsonArray = new JSONArray().put(vod);
        JSONObject result = new JSONObject().put("list", jsonArray);
        return result.toString();
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        String searchUrl = siteUrl + "/index.php?m=vod-search&wd=" + URLEncoder.encode(key);
        String html = req(searchUrl, getHeader());
        JSONArray videos = new JSONArray();
        Elements items = Jsoup.parse(html).select("[id=data_list] li");
        for (Element item : items) {
            Elements a = item.select(".pic > a");
            String vodId = a.attr("href");
            String name = item.select(".sTit").text();
            String pic = a.select("img").attr("data-src");
            String remark = item.select(".sStyle").text();
            JSONObject vod = new JSONObject();
            vod.put("vod_id", vodId);
            vod.put("vod_name", name);
            vod.put("vod_pic", pic);
            vod.put("vod_remarks", remark);
            videos.put(vod);
        }
        JSONObject result = new JSONObject();
        result.put("list", videos);
        return result.toString();
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        JSONObject result = new JSONObject();
        result.put("parse", 1);
        result.put("header", getHeader().toString());
        result.put("playUrl", "");
        result.put("url", id);
        return result.toString();
    }
}
