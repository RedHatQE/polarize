package com.github.redhatqe.byzantine.utils;

import java.time.LocalDateTime;

/**
 * This class should only have static members
 */
public class FileUtils {
    public static String makeTimeStamp() {
        LocalDateTime now = LocalDateTime.now();
        String timestamp = "%s-%s-%d-%d-%d-%d-%d";
        timestamp = String.format(timestamp, now.getMonth().toString(), now.getDayOfMonth(), now.getYear(),
                now.getHour(), now.getMinute(), now.getSecond(), now.getNano());
        return timestamp;
    }

    public static String makeTimeStamp(String base, String end) {
        LocalDateTime now = LocalDateTime.now();
        if (!base.equals(""))
            base += "-";
        String timestamp = "%s%s-%s-%d-%d-%d-%d-%d";
        timestamp = String.format(timestamp, base, now.getMonth().toString(), now.getDayOfMonth(), now.getYear(),
                now.getHour(), now.getMinute(), now.getSecond(), now.getNano());
        return timestamp.trim() + end;
    }

    public static String removeLast(String s) {
        return s.substring(0, s.length() - 1);
    }
}
