package com.github.catvod.bean.bili;

import android.text.TextUtils;

import com.github.catvod.bean.Vod;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.Arrays;
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
        return data == null ? new Data() : data;
    }

    public static class Result {

        @SerializedName("bvid")
        private String bvid;
        
        // 使用 JsonElement 兼容数字和字符串类型的 aid，防止大数值溢出/解析失败
        @SerializedName("aid")
        private JsonElement aid;
        
        @SerializedName("title")
        private String title;
        @SerializedName("pic")
        private String pic;
        @SerializedName("duration")
        private String duration;
        @SerializedName("length")
        private String length;

        /**
         * 替换 TypeToken 避免低版本 Android 触发 ThreadLocal/脱糖 API 崩溃
         */
        public static List<Result> arrayFrom(JsonElement str) {
            List<Result> list = new ArrayList<>();
            if (str == null || !str.isJsonArray()) {
                return list;
            }
            try {
                // 使用数组 Class 替代 TypeToken 匿名内部类
                Result[] array = new Gson().fromJson(str, Result[].class);
                return array == null ? list : Arrays.asList(array);
            } catch (Exception e) {
                return list;
            }
        }

        public String getBvId() {
            return TextUtils.isEmpty(bvid) ? "" : bvid;
        }

        public String getAid() {
            if (aid == null || aid.isJsonNull()) return "";
            try {
                return aid.getAsString();
            } catch (Exception e) {
                return "";
            }
        }

        public String getTitle() {
            return TextUtils.isEmpty(title) ? "" : title;
        }

        public String getDuration() {
            String dur = TextUtils.isEmpty(duration) ? getLength() : duration;
            if (TextUtils.isEmpty(dur)) return "";
            if (dur.contains(":")) return dur;
            try {
                int seconds = Integer.parseInt(dur);
                if (seconds < 60) return seconds + "秒";
                return (seconds / 60) + "分钟";
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

            // 正则替换比 Jsoup 性能高数倍，避免低配盒子挂起
            String cleanTitle = getTitle().replaceAll("<[^>]*>", "");
            vod.setVodName(cleanTitle);

            String picUrl = getPic();
            if (picUrl.startsWith("//")) {
                picUrl = "https:" + picUrl;
            }
            vod.setVodPic(picUrl);
            vod.setVodRemarks(getDuration());
            return vod;
        }
    }
}
