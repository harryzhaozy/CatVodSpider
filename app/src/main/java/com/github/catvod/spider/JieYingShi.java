package com.github.catvod.spider;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.github.catvod.crawler.Spider;

public class JieYingShi extends Spider {

    private static final String HOME_URL =
            "https://www.hkybqufgh.com";

    private static final String ERROR_URL =
            "https://json.doube.eu.org/error/4gtv/index.m3u8";

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                    + "AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/131.0.0.0 Safari/537.36";

    private static final String KEY =
            "cb808529bae6b6be45ecfab29a4889bc";


    @Override
    public String getName() {
        return "JieYingShi";
    }


    @Override
    public void init(Context context, String extend) {
        // Python 中这里只是初始化 home_url、error_url、headers
    }


    @Override
    public String homeContent(boolean filter) {

        try {
            JSONObject result = new JSONObject();

            JSONArray classes = new JSONArray();

            JSONObject item1 = new JSONObject();
            item1.put("type_id", "1");
            item1.put("type_name", "电影");
            classes.put(item1);

            JSONObject item2 = new JSONObject();
            item2.put("type_id", "2");
            item2.put("type_name", "电视剧");
            classes.put(item2);

            JSONObject item3 = new JSONObject();
            item3.put("type_id", "4");
            item3.put("type_name", "动漫");
            classes.put(item3);

            JSONObject item4 = new JSONObject();
            item4.put("type_id", "3");
            item4.put("type_name", "综艺");
            classes.put(item4);

            result.put("class", classes);

            return result.toString();

        } catch (Exception e) {
            return "{\"class\":[]}";
        }
    }


    @Override
    public String homeVideoContent() {

        JSONArray data = getData(HOME_URL);

        try {
            JSONObject result = new JSONObject();

            result.put("list", data);
            result.put("parse", 0);
            result.put("jx", 0);

            return result.toString();

        } catch (Exception e) {
            return "{\"list\":[],\"parse\":0,\"jx\":0}";
        }
    }


    @Override
    public String categoryContent(
            String tid,
            String pg,
            boolean filter,
            HashMap<String, String> extend) {

        String url =
                HOME_URL
                        + "/vod/show/id/"
                        + tid
                        + "/page/"
                        + pg;

        JSONArray data = getData(url);

        try {
            JSONObject result = new JSONObject();

            result.put("list", data);
            result.put("parse", 0);
            result.put("jx", 0);

            return result.toString();

        } catch (Exception e) {
            return "{\"list\":[],\"parse\":0,\"jx\":0}";
        }
    }


    @Override
    public String detailContent(List<String> ids) {

        if (ids == null || ids.size() == 0) {
            return "{\"list\":[],\"parse\":0,\"jx\":0}";
        }

        String id = ids.get(0);

        JSONArray data = getDetailData(id);

        try {
            JSONObject result = new JSONObject();

            result.put("list", data);
            result.put("parse", 0);
            result.put("jx", 0);

            return result.toString();

        } catch (Exception e) {
            return "{\"list\":[],\"parse\":0,\"jx\":0}";
        }
    }


    @Override
    public String searchContent(
            String key,
            boolean quick) {

        /*
         * Python:
         *
         * def searchContent(self, key, quick, page='1'):
         *     if int(page) > 1:
         *         return {'list': [], 'parse': 0, 'jx': 0}
         *
         * 标准 TVBox 的 searchContent 通常没有 page 参数，
         * 所以这里天然只处理第一页。
         */

        try {

            String url =
                    HOME_URL
                            + "/vod/search/"
                            + URLEncoder.encode(
                            key,
                            "UTF-8"
                    );

            JSONArray data = getData(url);

            JSONObject result = new JSONObject();

            result.put("list", data);
            result.put("parse", 0);
            result.put("jx", 0);

            return result.toString();

        } catch (Exception e) {
            return "{\"list\":[],\"parse\":0,\"jx\":0}";
        }
    }


    @Override
    public String playerContent(
            String flag,
            String id,
            List<String> vipFlags) {

        String url = getPlayData(id);

        try {

            JSONObject result = new JSONObject();

            result.put("url", url);

            /*
             * 注意：
             *
             * Python 原代码这里使用的是 self.headers，
             * 只有 User-Agent。
             *
             * 并不是 get_headers() 生成的 sign Header。
             */
            JSONObject header = new JSONObject();

            header.put(
                    "User-Agent",
                    USER_AGENT
            );

            result.put("header", header);

            result.put("parse", 1);
            result.put("jx", 0);

            return result.toString();

        } catch (Exception e) {
            return "{\"url\":\""
                    + escapeJson(url)
                    + "\",\"parse\":1,\"jx\":0}";
        }
    }


   


   @Override
    public void destroy() {
    }


    /**
     * ============================================================
     * 获取首页 / 分类 / 搜索数据
     * ============================================================
     *
     * Python:
     *
     * def get_data(self, url):
     *     data = []
     *     res = requests.get(url, headers=self.headers)
     *
     *     vod_id_s = re.findall(
     *         r'\\"vodId\\":(.*?),',
     *         res.text
     *     )
     *
     *     vod_name_s = re.findall(
     *         r'\\"vodName\\":\\"(.*?)\\"',
     *         res.text
     *     )
     *
     *     ...
     */
    private JSONArray getData(String url) {

        JSONArray data = new JSONArray();

        try {

            HashMap<String, String> headers =
                    new HashMap<>();

            headers.put(
                    "User-Agent",
                    USER_AGENT
            );

            String text =
                    httpGet(url, headers);

            if (text == null || text.length() == 0) {
                return data;
            }

            /*
             * 保持 Python 原来的正则逻辑。
             */
            List<String> vodIds =
                    findAll(
                            text,
                            "\\\\\"vodId\\\":(.*?),"
                    );

            List<String> vodNames =
                    findAll(
                            text,
                            "\\\\\"vodName\\\":\\\\\"(.*?)\\\\\""
                    );

            List<String> vodPics =
                    findAll(
                            text,
                            "\\\\\"vodPic\\\":\\\\\"(.*?)\\\\\""
                    );

            List<String> vodRemarks =
                    findAll(
                            text,
                            "\\\\\"vodRemarks\\\":\\\\\"(.*?)\\\\\""
                    );


            /*
             * Python 中：
             *
             * for i in range(len(vod_id_s)):
             *
             * 这里也按照最短数组处理，
             * 防止 Java 数组越界。
             */
            int size =
                    Math.min(
                            Math.min(
                                    vodIds.size(),
                                    vodNames.size()
                            ),
                            Math.min(
                                    vodPics.size(),
                                    vodRemarks.size()
                            )
                    );


            for (int i = 0; i < size; i++) {

                JSONObject item =
                        new JSONObject();

                item.put(
                        "vod_id",
                        vodIds.get(i)
                );

                item.put(
                        "vod_name",
                        vodNames.get(i)
                );

                item.put(
                        "vod_pic",
                        vodPics.get(i)
                );

                item.put(
                        "vod_remarks",
                        vodRemarks.get(i)
                );

                data.put(item);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return data;
    }


    /**
     * ============================================================
     * 获取详情
     * ============================================================
     */
    private JSONArray getDetailData(String ids) {

        JSONArray result =
                new JSONArray();

        try {

            String url =
                    HOME_URL
                            + "/api/mw-movie/anonymous/video/detail?id="
                            + URLEncoder.encode(
                            ids,
                            "UTF-8"
                    );


            String t =
                    String.valueOf(
                            System.currentTimeMillis()
                    );


            String e =
                    "id="
                            + ids
                            + "&key="
                            + KEY
                            + "&t="
                            + t;


            HashMap<String, String> headers =
                    getHeaders(t, e);


            String response =
                    httpGet(
                            url,
                            headers
                    );


            if (response == null
                    || response.length() == 0) {

                return result;
            }


            JSONObject root =
                    new JSONObject(response);


            JSONObject data =
                    root.getJSONObject("data");


            JSONArray episodeList =
                    data.optJSONArray(
                            "episodeList"
                    );


            StringBuilder playUrls =
                    new StringBuilder();


            if (episodeList != null) {

                for (
                        int i = 0;
                        i < episodeList.length();
                        i++
                ) {

                    JSONObject episode =
                            episodeList.getJSONObject(i);


                    String name =
                            episode.optString(
                                    "name"
                            );


                    String nid =
                            episode.optString(
                                    "nid"
                            );


                    if (playUrls.length() > 0) {
                        playUrls.append("#");
                    }


                    /*
                     * Python:
                     *
                     * urls.append(
                     *     f'{name}${ids}-{url}'
                     * )
                     */
                    playUrls
                            .append(name)
                            .append("$")
                            .append(ids)
                            .append("-")
                            .append(nid);
                }
            }


            JSONObject item =
                    new JSONObject();


            item.put(
                    "type_name",
                    data.optString(
                            "vodClass"
                    )
            );


            item.put(
                    "vod_id",
                    data.optString(
                            "vodId"
                    )
            );


            item.put(
                    "vod_name",
                    data.optString(
                            "vodName"
                    )
            );


            item.put(
                    "vod_remarks",
                    data.optString(
                            "vodRemarks"
                    )
            );


            item.put(
                    "vod_year",
                    data.optString(
                            "vodYear"
                    )
            );


            item.put(
                    "vod_area",
                    data.optString(
                            "vodArea"
                    )
            );


            item.put(
                    "vod_actor",
                    data.optString(
                            "vodActor"
                    )
            );


            item.put(
                    "vod_director",
                    data.optString(
                            "vodDirector"
                    )
            );


            item.put(
                    "vod_content",
                    data.optString(
                            "vodContent"
                    )
            );


            item.put(
                    "vod_play_from",
                    "免费分享"
            );


            item.put(
                    "vod_play_url",
                    playUrls.toString()
            );


            result.put(item);

        } catch (Exception e) {
            e.printStackTrace();
        }

        return result;
    }


    /**
     * ============================================================
     * 获取播放地址
     * ============================================================
     */
    private String getPlayData(String play) {

        try {

            /*
             * Python:
             *
             * info = play.split('-')
             * _id = info[0]
             * _pid = info[1]
             *
             * 这里使用 limit=2，
             * 与原逻辑更加安全。
             */
            String[] info =
                    play.split(
                            "-",
                            2
                    );


            if (info.length < 2) {
                return ERROR_URL;
            }


            String id =
                    info[0];

            String pid =
                    info[1];


            String url =
                    HOME_URL
                            + "/api/mw-movie/anonymous/v2/video/episode/url"
                            + "?id="
                            + URLEncoder.encode(
                            id,
                            "UTF-8"
                    )
                            + "&nid="
                            + URLEncoder.encode(
                            pid,
                            "UTF-8"
                    );


            String t =
                    String.valueOf(
                            System.currentTimeMillis()
                    );


            String e =
                    "id="
                            + id
                            + "&nid="
                            + pid
                            + "&key="
                            + KEY
                            + "&t="
                            + t;


            HashMap<String, String> headers =
                    getHeaders(
                            t,
                            e
                    );


            String response =
                    httpGet(
                            url,
                            headers
                    );


            if (response == null
                    || response.length() == 0) {

                return ERROR_URL;
            }


            JSONObject root =
                    new JSONObject(response);


            JSONObject data =
                    root.optJSONObject(
                            "data"
                    );


            if (data == null) {
                return ERROR_URL;
            }


            JSONArray list =
                    data.optJSONArray(
                            "list"
                    );


            if (list == null
                    || list.length() == 0) {

                return ERROR_URL;
            }


            JSONObject first =
                    list.getJSONObject(0);


            String videoUrl =
                    first.optString(
                            "url"
                    );


            if (videoUrl == null
                    || videoUrl.length() == 0) {

                return ERROR_URL;
            }


            return videoUrl;

        } catch (Exception e) {

            e.printStackTrace();

            return ERROR_URL;
        }
    }


    /**
     * ============================================================
     * 生成请求 Header
     * ============================================================
     *
     * Python:
     *
     * @staticmethod
     * def get_headers(t, e):
     *
     *     sign = hashlib.sha1(
     *         hashlib.md5(
     *             e.encode()
     *         ).hexdigest().encode()
     *     ).hexdigest()
     */
    private static HashMap<String, String> getHeaders(
            String t,
            String e) {

        HashMap<String, String> headers =
                new HashMap<>();


        headers.put(
                "User-Agent",
                USER_AGENT
        );


        headers.put(
                "Accept",
                "application/json, text/plain, */*"
        );


        headers.put(
                "sign",
                sign(e)
        );


        headers.put(
                "sec-ch-ua",
                "\"Google Chrome\";v=\"131\", "
                        + "\"Chromium\";v=\"131\", "
                        + "\"Not_A Brand\";v=\"24\""
        );


        headers.put(
                "t",
                t
        );


        headers.put(
                "referer",
                HOME_URL + "/"
        );


        return headers;
    }


    /**
     * ============================================================
     * sign
     * ============================================================
     *
     * MD5(e)
     *      ↓
     * 32位小写 Hex
     *      ↓
     * SHA1
     *      ↓
     * 40位小写 Hex
     */
    public static String sign(String e) {

        try {

            MessageDigest md5 =
                    MessageDigest.getInstance(
                            "MD5"
                    );


            byte[] md5Bytes =
                    md5.digest(
                            e.getBytes(
                                    StandardCharsets.UTF_8
                            )
                    );


            StringBuilder md5Hex =
                    new StringBuilder();


            for (byte b : md5Bytes) {

                md5Hex.append(
                        String.format(
                                "%02x",
                                b & 0xff
                        )
                );
            }


            MessageDigest sha1 =
                    MessageDigest.getInstance(
                            "SHA-1"
                    );


            byte[] sha1Bytes =
                    sha1.digest(
                            md5Hex.toString()
                                    .getBytes(
                                            StandardCharsets.UTF_8
                                    )
                    );


            StringBuilder sha1Hex =
                    new StringBuilder();


            for (byte b : sha1Bytes) {

                sha1Hex.append(
                        String.format(
                                "%02x",
                                b & 0xff
                        )
                );
            }


            return sha1Hex.toString();

        } catch (Exception ex) {

            ex.printStackTrace();

            return "";
        }
    }


    /**
     * ============================================================
     * HTTP GET
     * ============================================================
     */
    private static String httpGet(
            String urlString,
            Map<String, String> headers) {

        HttpURLConnection connection = null;

        try {

            URL url =
                    new URL(urlString);


            connection =
                    (HttpURLConnection)
                            url.openConnection();


            connection.setRequestMethod(
                    "GET"
            );


            connection.setConnectTimeout(
                    10000
            );


            connection.setReadTimeout(
                    15000
            );


            connection.setInstanceFollowRedirects(
                    true
            );


            if (headers != null) {

                for (
                        Map.Entry<String, String> entry
                                : headers.entrySet()
                ) {

                    connection.setRequestProperty(
                            entry.getKey(),
                            entry.getValue()
                    );
                }
            }


            int code =
                    connection.getResponseCode();


            if (code != HttpURLConnection.HTTP_OK) {
                return "";
            }


            InputStream input =
                    connection.getInputStream();


            BufferedReader reader =
                    new BufferedReader(
                            new InputStreamReader(
                                    input,
                                    StandardCharsets.UTF_8
                            )
                    );


            StringBuilder result =
                    new StringBuilder();


            String line;


            while (
                    (line = reader.readLine())
                            != null
            ) {

                result.append(line)
                        .append("\n");
            }


            reader.close();


            return result.toString();

        } catch (Exception e) {

            e.printStackTrace();

            return "";

        } finally {

            if (connection != null) {
                connection.disconnect();
            }
        }
    }


    /**
     * ============================================================
     * Python re.findall() 对应实现
     * ============================================================
     */
    private static List<String> findAll(
            String text,
            String regex) {

        List<String> result =
                new ArrayList<>();


        Pattern pattern =
                Pattern.compile(
                        regex
                );


        Matcher matcher =
                pattern.matcher(
                        text
                );


        while (matcher.find()) {

            if (matcher.groupCount() >= 1) {

                result.add(
                        matcher.group(1)
                );
            }
        }


        return result;
    }


    /**
     * JSON 字符串转义
     */
    private static String escapeJson(
            String value) {

        if (value == null) {
            return "";
        }

        return value
                .replace(
                        "\\",
                        "\\\\"
                )
                .replace(
                        "\"",
                        "\\\""
                );
    }
}
