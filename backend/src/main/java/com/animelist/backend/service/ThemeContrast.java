package com.animelist.backend.service;

final class ThemeContrast {
    private ThemeContrast() {}
    static String safeDarkColor(String hex) {
        int[] channels = {Integer.parseInt(hex.substring(1, 3), 16), Integer.parseInt(hex.substring(3, 5), 16), Integer.parseInt(hex.substring(5, 7), 16)};
        String result = hex;
        while (luminance(channels) > .025) {
            for (int i = 0; i < channels.length; i++) channels[i] = (int) Math.floor(channels[i] * .95);
            result = String.format("#%02x%02x%02x", channels[0], channels[1], channels[2]);
        }
        return result;
    }
    static String safeAccentColor(String hex) {
        int[] channels = {Integer.parseInt(hex.substring(1, 3), 16), Integer.parseInt(hex.substring(3, 5), 16), Integer.parseInt(hex.substring(5, 7), 16)};
        String result = hex;
        while (luminance(channels) < .3) {
            for (int i = 0; i < channels.length; i++) channels[i] = Math.min(255, (int) Math.ceil(channels[i] + (255 - channels[i]) * .05));
            result = String.format("#%02x%02x%02x", channels[0], channels[1], channels[2]);
        }
        return result;
    }
    private static double luminance(int[] channels) {
        double[] weights = {.2126, .7152, .0722};
        double result = 0;
        for (int i = 0; i < channels.length; i++) {
            double value = channels[i] / 255.0;
            result += weights[i] * (value <= .04045 ? value / 12.92 : Math.pow((value + .055) / 1.055, 2.4));
        }
        return result;
    }
}
