package com.github.catvod.bean.bili;

import android.text.TextUtils;

import com.github.catvod.bean.Vod;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import org.jsoup.Jsoup;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class Resp {

    @SerializedName("code")
    private Integer code;
    @SerializedName("message")
    private String message;
    @SerializedName("data")
    private Data data; 

    public static Resp objectFrom(String str) {
        try {
            return new Gson().fromJson(str, Resp.class);
        } catch (Exception e) {
            return new Resp();
        }
    }

    public Data getData() {
        return data == null ? new Data() : data; // 绝不返回 null
    }

    public static class Result {

        @SerializedName("bvid")
        private String bvid;
        @SerializedName("aid")
        private String aid;
        @SerializedName("title")
        private String title;
        @SerializedName("pic")
        private String pic;
        @SerializedName("duration")
        private String duration;
        @SerializedName("length")
        private String length;

       
        public static List<Result> arrayFrom(JsonElement str) {
            List<Result> list = new ArrayList<>();
            if (str == null || !str.isJsonArray()) {
                return list;
            }
            try {
                Type listType = new TypeToken<List<Result>>() {}.getType();
                List<Result> resultList = new Gson().fromJson(str, listType);
                return resultList == null ? list : resultList;
            } catch (Exception e) {
                return list;
            }
        }

        public String getBvId() {
            return TextUtils.isEmpty(bvid) ? "" : bvid;
        }

        public String getAid() {
            return TextUtils.isEmpty(aid) ? "" : aid;
        }

        public String getTitle() {
            return TextUtils.isEmpty(title) ? "" : title;
        }

        /**
         * 安全的时间格式转换（拦截带 ':' 的字符串，捕获数字转换异常）
         */
        public String getDuration() {
            String dur = TextUtils.isEmpty(duration) ? getLength() : duration;
            if (TextUtils.isEmpty(dur)) return "";
            if (dur.contains(":")) return dur;
            try {
                int seconds = Integer.parseInt(dur);
                if (seconds < 60) return seconds + "秒";
                return (seconds / 60) + "分鐘";
            } catch (Exception e) {
                return dur;
            }
        }

        public String getLength() {
            return TextUtils.isEmpty(length) ? "" : length;
        }

        public String getPic() {
            return TextUtils.isEmpty(pic) ? "" : pic;
        }

        /**
         * 兼容单 BV 号和双 ID，避免拼接尾巴上的多余 '@' 符号
         */
        public Vod getVod() {
            Vod vod = new Vod();
            String bv = getBvId();
            String av = getAid();
            
            if (!bv.isEmpty() && !av.isEmpty()) {
                vod.setVodId(bv + "@" + av);
            } else if (!bv.isEmpty()) {
                vod.setVodId(bv);
            } else if (!av.isEmpty()) {
                vod.setVodId("AV" + av);
            } else {
                vod.setVodId("");
            }

            vod.setVodName(Jsoup.parse(getTitle()).text());
            vod.setVodPic(getPic().startsWith("//") ? "https:" + getPic() : getPic());
            vod.setVodRemarks(getDuration());
            return vod;
        }
    }
}
