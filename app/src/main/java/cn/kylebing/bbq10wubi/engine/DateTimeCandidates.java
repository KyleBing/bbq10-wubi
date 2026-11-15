package cn.kylebing.bbq10wubi.engine;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public final class DateTimeCandidates {
    private DateTimeCandidates() {}

    public static List<String> candidates(String preedit) {
        if (preedit == null) return null;
        String key = preedit.trim().toLowerCase(Locale.US);
        if (key.length() == 0) return null;
        Date now = new Date();
        if ("date".equals(key)) return unique(concat(dateFormats(now), dateTimeFormats(now)));
        if ("time".equals(key)) return timeFormats(now);
        if ("week".equals(key)) return weekFormats(now);
        return null;
    }

    private static List<String> dateFormats(Date date) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        int y = cal.get(Calendar.YEAR);
        int m = cal.get(Calendar.MONTH) + 1;
        int d = cal.get(Calendar.DAY_OF_MONTH);
        String mm = pad2(m);
        String dd = pad2(d);
        String weekday = weekdayCN(cal.get(Calendar.DAY_OF_WEEK));
        ArrayList<String> list = new ArrayList<String>();
        list.add(y + "-" + mm + "-" + dd);
        list.add(y + "/" + mm + "/" + dd);
        list.add(y + "." + mm + "." + dd);
        list.add("" + y + mm + dd);
        list.add(y + "年" + m + "月" + d + "日");
        list.add(m + "月" + d + "日");
        list.add(mm + "-" + dd);
        list.add(mm + "/" + dd);
        list.add(weekday);
        list.add(y + "年" + m + "月" + d + "日 " + weekday);
        return unique(list);
    }

    private static List<String> timeFormats(Date date) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        int h = cal.get(Calendar.HOUR_OF_DAY);
        int mi = cal.get(Calendar.MINUTE);
        int s = cal.get(Calendar.SECOND);
        String hh = pad2(h);
        String mm = pad2(mi);
        String ss = pad2(s);
        long ts = date.getTime() / 1000;
        ArrayList<String> list = new ArrayList<String>();
        list.add(hh + ":" + mm + ":" + ss);
        list.add(hh + ":" + mm);
        list.add(String.valueOf(ts));
        list.add(h + "时" + mi + "分");
        list.add(hh + mm + ss);
        list.add(hh + mm);
        list.add(h + "时" + mi + "分" + s + "秒");
        list.add(String.valueOf(date.getTime()));
        return unique(list);
    }

    private static List<String> weekFormats(Date date) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        String full = weekdayCN(cal.get(Calendar.DAY_OF_WEEK));
        String shortW = full.replace("星期", "周");
        String libai = full.replace("星期", "礼拜");
        Calendar iso = Calendar.getInstance();
        iso.setMinimalDaysInFirstWeek(4);
        iso.setFirstDayOfWeek(Calendar.MONDAY);
        iso.setTime(date);
        int week = iso.get(Calendar.WEEK_OF_YEAR);
        int weekYear = iso.get(Calendar.YEAR);
        String enFull = new SimpleDateFormat("EEEE", Locale.US).format(date);
        String enShort = new SimpleDateFormat("EEE", Locale.US).format(date);
        ArrayList<String> list = new ArrayList<String>();
        list.add(full);
        list.add(shortW);
        list.add("第" + week + "周");
        list.add(weekYear + "年第" + week + "周");
        list.add(libai);
        list.add(enFull);
        list.add(enShort);
        list.add(weekYear + "-W" + pad2(week));
        return unique(list);
    }

    private static List<String> dateTimeFormats(Date date) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        int y = cal.get(Calendar.YEAR);
        int mo = cal.get(Calendar.MONTH) + 1;
        int d = cal.get(Calendar.DAY_OF_MONTH);
        int h = cal.get(Calendar.HOUR_OF_DAY);
        int mi = cal.get(Calendar.MINUTE);
        int s = cal.get(Calendar.SECOND);
        String mm = pad2(mo);
        String dd = pad2(d);
        String hh = pad2(h);
        String mi2 = pad2(mi);
        String ss = pad2(s);
        SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US);
        iso.setTimeZone(TimeZone.getDefault());
        ArrayList<String> list = new ArrayList<String>();
        list.add(y + "-" + mm + "-" + dd + " " + hh + ":" + mi2 + ":" + ss);
        list.add(y + "/" + mm + "/" + dd + " " + hh + ":" + mi2 + ":" + ss);
        list.add("" + y + mm + dd + hh + mi2 + ss);
        list.add(y + "年" + mo + "月" + d + "日 " + h + "时" + mi + "分" + s + "秒");
        list.add(y + "-" + mm + "-" + dd + " " + hh + ":" + mi2);
        list.add(iso.format(date));
        return unique(list);
    }

    private static String weekdayCN(int dayOfWeek) {
        String[] names = {"", "星期日", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六"};
        if (dayOfWeek < 0 || dayOfWeek >= names.length) return "";
        return names[dayOfWeek];
    }

    private static String pad2(int n) {
        return n < 10 ? "0" + n : String.valueOf(n);
    }

    private static List<String> concat(List<String> a, List<String> b) {
        ArrayList<String> out = new ArrayList<String>(a.size() + b.size());
        out.addAll(a);
        out.addAll(b);
        return out;
    }

    private static List<String> unique(List<String> items) {
        LinkedHashSet<String> seen = new LinkedHashSet<String>();
        for (String s : items) {
            if (s != null && s.length() > 0) seen.add(s);
        }
        return new ArrayList<String>(seen);
    }
}
