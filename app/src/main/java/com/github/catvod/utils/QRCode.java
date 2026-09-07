package com.github.catvod.utils;

import android.graphics.Bitmap;
import android.graphics.Color;

import java.nio.charset.StandardCharsets;

public class QRCode {

    public static Bitmap getBitmap(String content, int width, int margin) {
        try {
            byte[] data = content.getBytes(StandardCharsets.UTF_8);
            // B站二维码URL较长，强制使用固定稳定的 Version 8 (50x50 矩阵)
            int size = 49; 
            boolean[][] grid = new boolean[size][size];
            boolean[][] isReserved = new boolean[size][size];

            // 1. 绘制三大寻的角框 (Finder Patterns)
            drawFinder(grid, isReserved, 0, 0);
            drawFinder(grid, isReserved, size - 7, 0);
            drawFinder(grid, isReserved, 0, size - 7);

            // 2. 绘制对齐图案 (Alignment Pattern) - V8 的对齐点在 24, 42
            drawAlignment(grid, isReserved, 22, 22);
            drawAlignment(grid, isReserved, 22, 40);
            drawAlignment(grid, isReserved, 40, 22);
            drawAlignment(grid, isReserved, 40, 40);

            // 3. 绘制校正线条 (Timing lines)
            for (int i = 8; i < size - 8; i++) {
                if (!isReserved[6][i]) {
                    grid[6][i] = (i % 2 == 0);
                    isReserved[6][i] = true;
                }
                if (!isReserved[i][6]) {
                    grid[i][6] = (i % 2 == 0);
                    isReserved[i][6] = true;
                }
            }

            // 4. 填充数据流
            fillData(grid, isReserved, data);

            // 5. 导出 Bitmap
            Bitmap bitmap = Bitmap.createBitmap(width, width, Bitmap.Config.ARGB_8888);
            int scale = width / (size + margin * 2);
            if (scale < 1) scale = 1;

            for (int y = 0; y < width; y++) {
                for (int x = 0; x < width; x++) {
                    int gx = (x / scale) - margin;
                    int gy = (y / scale) - margin;
                    if (gx >= 0 && gx < size && gy >= 0 && gy < size && grid[gy][gx]) {
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

    private static void drawFinder(boolean[][] grid, boolean[][] res, int r, int c) {
        for (int i = -1; i <= 7; i++) {
            for (int j = -1; j <= 7; j++) {
                int row = r + i, col = c + j;
                if (row >= 0 && row < grid.length && col >= 0 && col < grid.length) {
                    res[row][col] = true;
                }
            }
        }
        for (int i = 0; i < 7; i++) {
            for (int j = 0; j < 7; j++) {
                grid[r + i][c + j] = (i == 0 || i == 6 || j == 0 || j == 6 || (i >= 2 && i <= 4 && j >= 2 && j <= 4));
            }
        }
    }

    private static void drawAlignment(boolean[][] grid, boolean[][] res, int r, int c) {
        // 如果该区域已被 Finder 覆盖则跳过
        if (res[r][c]) return; 
        for (int i = -2; i <= 2; i++) {
            for (int j = -2; j <= 2; j++) {
                int row = r + i, col = c + j;
                res[row][col] = true;
                grid[row][col] = (Math.abs(i) == 2 || Math.abs(j) == 2 || (i == 0 && j == 0));
            }
        }
    }

    private static void fillData(boolean[][] grid, boolean[][] res, byte[] data) {
        int size = grid.length;
        int bitIdx = 0;
        int totalBits = data.length * 8;
        boolean up = true;

        for (int col = size - 1; col > 0; col -= 2) {
            if (col == 6) col--; // 跳过校正线
            for (int i = 0; i < size; i++) {
                int row = up ? (size - 1 - i) : i;
                for (int c = 0; c < 2; c++) {
                    int curCol = col - c;
                    if (!res[row][curCol]) {
                        boolean val = false;
                        if (bitIdx < totalBits) {
                            int b = data[bitIdx / 8] & 0xFF;
                            val = ((b >> (7 - (bitIdx % 8))) & 1) == 1;
                            bitIdx++;
                        }
                        // 应用基础掩码 Pattern
                        if ((row + curCol) % 2 == 0) val = !val;
                        grid[row][curCol] = val;
                    }
                }
            }
            up = !up;
        }
    }
}
