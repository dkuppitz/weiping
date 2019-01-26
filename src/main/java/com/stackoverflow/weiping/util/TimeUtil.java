package com.stackoverflow.weiping.util;

import java.time.DayOfWeek;
import java.time.LocalTime;

public class TimeUtil {

    public static LocalTime toLocalTime(final int minutes) {
        return LocalTime.of(minutes / 60, minutes % 60);
    }

    public static int toMinutes(final LocalTime time) {
        return time.getHour() * 60 + time.getMinute();
    }

    public static int daysBetween(final DayOfWeek day1, final DayOfWeek day2) {
        return day1.getValue() <= day2.getValue()
                ? (day2.getValue() - day1.getValue())
                : (7 - (day1.getValue() - day2.getValue()));
    }
}
