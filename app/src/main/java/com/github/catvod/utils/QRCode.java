package com.github.catvod.utils;

import android.graphics.Bitmap;
import android.graphics.Color;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.util.HashMap;
import java.util.Map;

/**
 * 基于 Google ZXing 的标准二维码生成器（100% 离线、规范、高识别率）
 */
public class QRCode {

    public static Bitmap getBitmap(String content, int size, int margin) {
        try {
            Map<EncodeHintType, Object> hints = new HashMap<>();
            // 指定字符编码为 UTF-8
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            // 指定纠错等级为 L（B站默认），或 M
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.L);
            // 设置边距（Margin）
            hints.put(EncodeHintType.MARGIN, margin);

            BitMatrix bitMatrix = new MultiFormatWriter().encode(
                    content, BarcodeFormat.QR_CODE, size, size, hints
            );

            int width = bitMatrix.getWidth();
            int height = bitMatrix.getHeight();
            int[] pixels = new int[width * height];

            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    pixels[y * width + x] = bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE;
                }
            }

            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
            return bitmap;
        } catch (Exception e) {
            return null;
        }
    }
}
