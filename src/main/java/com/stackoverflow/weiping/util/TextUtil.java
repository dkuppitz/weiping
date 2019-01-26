package com.stackoverflow.weiping.util;

public class TextUtil {

    private static String pluralize(final String word, final Number amount) {
        return amount.doubleValue() == 1 ? word : word + "s";
    }

    public static String durationToString(final int minutes) {
        final int d = minutes / (24 * 60);
        final int h = (minutes % (24 * 60)) / 60;
        final int m = minutes % 60;
        final StringBuilder result = new StringBuilder();
        if (d > 0) result.append(String.format("%d %s", d, pluralize("day", d)));
        if (h > 0) {
            if (result.length() > 0) result.append(" ");
            result.append(String.format("%d %s", h, pluralize("hour", h)));
        }
        if (m > 0 || result.length() == 0) {
            if (result.length() > 0) result.append(" ");
            result.append(String.format("%d %s", m, pluralize("minute", m)));
        }
        return result.toString();
    }

    public static String durationToString(final double hours) {
        return durationToString((int) (hours * 60));
    }
}
