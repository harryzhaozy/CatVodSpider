package com.github.catvod.utils;

import android.graphics.Bitmap;
import android.graphics.Color;

import java.io.UnsupportedEncodingEncodingException;
import java.nio.charset.StandardCharsets;

/**
 * 纯 Java 原生实现的 QRCode 二维码生成工具类
 * 零外部依赖，无需 ZXing 库支持
 */
public class QRCode {

    public static Bitmap getBitmap(String content, int size, int margin) {
        try {
            boolean[][] matrix = encode(content);
            if (matrix == null) return null;

            int matrixWidth = matrix.length;
            int matrixHeight = matrix[0].length;
            int totalWidth = matrixWidth + margin * 2;
            int totalHeight = matrixHeight + margin * 2;

            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            float scaleX = (float) size / totalWidth;
            float scaleY = (float) size / totalHeight;

            for (int x = 0; x < size; x++) {
                for (int y = 0; y < size; y++) {
                    int mx = (int) (x / scaleX) - margin;
                    int my = (int) (y / scaleY) - margin;

                    if (mx >= 0 && mx < matrixWidth && my >= 0 && my < matrixHeight && matrix[mx][my]) {
                        bitmap.setPixel(x, y, Color.BLACK);
                    } else {
                        bitmap.setPixel(x, y, Color.WHITE);
                    }
                }
            }
            return bitmap;
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean[][] encode(String content) {
        try {
            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            int version = getBestVersion(bytes.length);
            if (version > 10) version = 10; // 限制最大版本以控制代码体积

            int size = 17 + version * 4;
            boolean[][] modules = new boolean[size][size];
            boolean[][] isReserved = new boolean[size][size];

            // 1. 绘制位置探测图案 (Finder Patterns)
            drawFinderPattern(modules, isReserved, 0, 0);
            drawFinderPattern(modules, isReserved, size - 7, 0);
            drawFinderPattern(modules, isReserved, 0, size - 7);

            // 2. 绘制对齐图案 (Alignment Patterns)
            if (version >= 2) {
                int[] alignPos = getAlignmentPositions(version);
                for (int x : alignPos) {
                    for (int y : alignPos) {
                        if (isReserved[x][y]) continue;
                        drawAlignmentPattern(modules, isReserved, x - 2, y - 2);
                    }
                }
            }

            // 3. 绘制 Timing Lines (校正线)
            for (int i = 8; i < size - 8; i++) {
                if (!isReserved[6][i]) {
                    modules[6][i] = (i % 2 == 0);
                    isReserved[6][i] = true;
                }
                if (!isReserved[i][6]) {
                    modules[i][6] = (i % 2 == 0);
                    isReserved[i][6] = true;
                }
            }

            // 4. 保留格式信息区域 (Format Info)
            for (int i = 0; i < 9; i++) {
                isReserved[i][8] = true;
                isReserved[8][i] = true;
            }
            for (int i = size - 8; i < size; i++) {
                isReserved[8][i] = true;
                isReserved[i][8] = true;
            }
            isReserved[8][size - 8] = true;
            modules[8][size - 8] = true;

            // 5. 填充数据比特流
            byte[] dataBits = generateDataBits(bytes, version);
            fillDataBits(modules, isReserved, dataBits);

            // 6. 应用掩码 0 (Mask Pattern 0: (x + y) % 2 == 0)
            applyMask(modules, isReserved);

            // 7. 写入格式信息 (Format Info for Level L + Mask 0)
            drawFormatInfo(modules, size);

            return modules;
        } catch (Exception e) {
            return null;
        }
    }

    private static void drawFinderPattern(boolean[][] modules, boolean[][] isReserved, int x, int y) {
        for (int r = 0; r < 7; r++) {
            for (int c = 0; c < 7; c++) {
                boolean val = (r == 0 || r == 6 || c == 0 || c == 6 || (r >= 2 && r <= 4 && c >= 2 && c <= 4));
                modules[x + r][y + c] = val;
                isReserved[x + r][y + c] = true;
            }
        }
        // 隔离带 (Separator)
        for (int r = -1; r <= 7; r++) {
            for (int c = -1; c <= 7; c++) {
                int px = x + r;
                int py = y + c;
                if (px >= 0 && px < modules.length && py >= 0 && py < modules.length) {
                    isReserved[px][py] = true;
                }
            }
        }
    }

    private static void drawAlignmentPattern(boolean[][] modules, boolean[][] isReserved, int x, int y) {
        for (int r = 0; r < 5; r++) {
            for (int c = 0; c < 5; c++) {
                boolean val = (r == 0 || r == 4 || c == 0 || c == 4 || (r == 2 && c == 2));
                modules[x + r][y + c] = val;
                isReserved[x + r][y + c] = true;
            }
        }
    }

    private static int[] getAlignmentPositions(int version) {
        if (version == 2) return new int[]{6, 18};
        if (version == 3) return new int[]{6, 22};
        if (version == 4) return new int[]{6, 26};
        if (version == 5) return new int[]{6, 30};
        if (version == 6) return new int[]{6, 34};
        return new int[]{6, 22};
    }

    private static int getBestVersion(int length) {
        if (length <= 17) return 1;
        if (length <= 32) return 2;
        if (length <= 53) return 3;
        if (length <= 78) return 4;
        if (length <= 106) return 5;
        return 6;
    }

    private static byte[] generateDataBits(byte[] bytes, int version) {
        int capacity = getCapacity(version);
        byte[] result = new byte[capacity];

        // Header: Byte Mode (0100) + Count
        int bitPos = 0;
        bitPos = writeBits(result, bitPos, 0x4, 4);
        bitPos = writeBits(result, bitPos, bytes.length, 8);

        // Raw Data
        for (byte b : bytes) {
            bitPos = writeBits(result, bitPos, b & 0xFF, 8);
        }

        // Padding (0xEC, 0x11)
        int padByte = 0;
        while (bitPos < capacity * 8) {
            int pad = (padByte % 2 == 0) ? 0xEC : 0x11;
            writeBits(result, bitPos, pad, Math.min(8, capacity * 8 - bitPos));
            bitPos += 8;
            padByte++;
        }

        return result;
    }

    private static int writeBits(byte[] buffer, int bitPos, int value, int numBits) {
        for (int i = numBits - 1; i >= 0; i--) {
            if (bitPos / 8 < buffer.length) {
                if (((value >> i) & 1) == 1) {
                    buffer[bitPos / 8] |= (1 << (7 - (bitPos % 8)));
                }
            }
            bitPos++;
        }
        return bitPos;
    }

    private static int getCapacity(int version) {
        int[] capacities = {0, 19, 34, 55, 80, 108, 136};
        return version < capacities.length ? capacities[version] : 100;
    }

    private static void fillDataBits(boolean[][] modules, boolean[][] isReserved, byte[] dataBits) {
        int size = modules.length;
        int bitIndex = 0;
        int totalBits = dataBits.length * 8;
        boolean upward = true;

        for (int x = size - 1; x > 0; x -= 2) {
            if (x == 6) x--; // 跳过垂直 Timing Line

            for (int i = 0; i < size; i++) {
                int y = upward ? (size - 1 - i) : i;

                for (int c = 0; c < 2; c++) {
                    int px = x - c;
                    if (!isReserved[px][y]) {
                        boolean bit = false;
                        if (bitIndex < totalBits) {
                            int byteIdx = bitIndex / 8;
                            int bitIdx = 7 - (bitIndex % 8);
                            bit = ((dataBits[byteIdx] >> bitIdx) & 1) == 1;
                            bitIndex++;
                        }
                        modules[px][y] = bit;
                    }
                }
            }
            upward = !upward;
        }
    }

    private static void applyMask(boolean[][] modules, boolean[][] isReserved) {
        int size = modules.length;
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                if (!isReserved[x][y]) {
                    // Mask Pattern 0: (x + y) % 2 == 0
                    if ((x + y) % 2 == 0) {
                        modules[x][y] = !modules[x][y];
                    }
                }
            }
        }
    }

    private static void drawFormatInfo(boolean[][] modules, int size) {
        // L 级纠错 + Mask 0 的 15 位 Mask Format String
        int formatBits = 0x77C4; 

        for (int i = 0; i < 15; i++) {
            boolean bit = ((formatBits >> i) & 1) == 1;

            if (i < 6) modules[8][i] = bit;
            else if (i < 8) modules[8][i + 1] = bit;
            else modules[8 - (i - 7)][8] = bit;

            if (i < 8) modules[size - 1 - i][8] = bit;
            else modules[8][size - 15 + i] = bit;
        }
    }
}