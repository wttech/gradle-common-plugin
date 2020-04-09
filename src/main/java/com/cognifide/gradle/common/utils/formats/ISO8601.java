/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.cognifide.gradle.common.utils.formats;

import java.time.Clock;
import java.util.*;

public final class ISO8601 {

    private static final Map<String, TimeZone> TZS = new HashMap<String, TimeZone>();

    static {
        TimeZone gmt = TimeZone.getTimeZone("GMT");
        TZS.put("Z", gmt);
        TZS.put("+00:00", gmt);
        TZS.put("-00:00", gmt);

        String[] tzs = {
                "-12:00", "-11:00", "-10:00", "-09:30", "-09:00", "-08:00",
                "-07:00", "-06:00", "-05:00", "-04:30", "-04:00", "-03:30",
                "-03:00", "-02:00", "-01:00", "+01:00", "+02:00", "+03:00",
                "+03:30", "+04:00", "+04:30", "+05:00", "+05:30", "+05:45",
                "+06:00", "+06:30", "+07:00", "+08:00", "+08:45", "+09:00",
                "+09:30", "+10:00", "+10:30", "+11:00", "+11:30", "+12:00",
                "+12:45", "+13:00", "+14:00" };
        for (String tz : tzs) {
            TZS.put(tz, TimeZone.getTimeZone("GMT" + tz));
        }
    }

    private static TimeZone UTC = TimeZone.getTimeZone("UTC");


    public static Calendar parse(String text) {
        if (text == null) {
            throw new IllegalArgumentException("argument can not be null");
        }

        // check optional leading sign
        char sign;
        int start;
        if (text.startsWith("-")) {
            sign = '-';
            start = 1;
        } else if (text.startsWith("+")) {
            sign = '+';
            start = 1;
        } else {
            sign = '+'; // no sign specified, implied '+'
            start = 0;
        }

        int year, month, day, hour, min, sec, ms;
        TimeZone tz;
        try {
            // year (YYYY)
            year = Integer.parseInt(text.substring(start, start + 4));
            start += 4;
            // delimiter '-'
            if (text.charAt(start) != '-') {
                return null;
            }
            start++;
            // month (MM)
            month = Integer.parseInt(text.substring(start, start + 2));
            start += 2;
            // delimiter '-'
            if (text.charAt(start) != '-') {
                return null;
            }
            start++;
            // day (DD)
            day = Integer.parseInt(text.substring(start, start + 2));
            start += 2;
            // delimiter 'T'
            if (text.charAt(start) != 'T') {
                return null;
            }
            start++;
            // hour (hh)
            hour = Integer.parseInt(text.substring(start, start + 2));
            start += 2;
            // delimiter ':'
            if (text.charAt(start) != ':') {
                return null;
            }
            start++;
            // minute (mm)
            min = Integer.parseInt(text.substring(start, start + 2));
            start += 2;
            // delimiter ':'
            if (text.charAt(start) != ':') {
                return null;
            }
            start++;
            // second (ss)
            sec = Integer.parseInt(text.substring(start, start + 2));
            start += 2;
            // delimiter '.'
            if (text.charAt(start) != '.') {
                return null;
            }
            start++;
            // millisecond (SSS)
            ms = Integer.parseInt(text.substring(start, start + 3));
            start += 3;
            // time zone designator (Z or +00:00 or -00:00)
            String tzid = text.substring(start);
            tz = TZS.get(tzid);
            if (tz == null) {
                // offset to UTC specified in the format +00:00/-00:00
                tzid = "GMT" + tzid;
                tz = TimeZone.getTimeZone(tzid);
                // verify id of returned time zone (getTimeZone defaults to "GMT")
                if (!tz.getID().equals(tzid)) {
                    // invalid time zone
                    return null;
                }
            }
        } catch (IndexOutOfBoundsException e) {
            return null;
        } catch (NumberFormatException e) {
            return null;
        }

        // initialize Calendar object
        Calendar cal = Calendar.getInstance(tz);
        cal.setLenient(false);
        // year and era
        if (sign == '-' || year == 0) {
            // not CE, need to set era (BCE) and adjust year
            cal.set(Calendar.YEAR, year + 1);
            cal.set(Calendar.ERA, GregorianCalendar.BC);
        } else {
            cal.set(Calendar.YEAR, year);
            cal.set(Calendar.ERA, GregorianCalendar.AD);
        }
        // month (0-based!)
        cal.set(Calendar.MONTH, month - 1);
        // day of month
        cal.set(Calendar.DAY_OF_MONTH, day);
        // hour
        cal.set(Calendar.HOUR_OF_DAY, hour);
        // minute
        cal.set(Calendar.MINUTE, min);
        // second
        cal.set(Calendar.SECOND, sec);
        // millisecond
        cal.set(Calendar.MILLISECOND, ms);

        try {
            getYear(cal);
        } catch (IllegalArgumentException e) {
            return null;
        }

        return cal;
    }

    public static String format(Date date) throws IllegalArgumentException {
        return format(date, 0);
    }

    public static String format(Clock clock) throws IllegalArgumentException {
        return format(clock.millis(), clock.getZone().getRules().getOffset(clock.instant()).getTotalSeconds());
    }

    public static String format(long millisSinceEpoch) throws IllegalArgumentException {
        return format(millisSinceEpoch, 0);
    }

    public static String format(Date date, int tzOffsetInSeconds) throws IllegalArgumentException {
        if (date == null) {
            throw new IllegalArgumentException("argument can not be null");
        }
        return format(date.getTime(), tzOffsetInSeconds);
    }

    public static String format(long millisSinceEpoch, int tzOffsetInSeconds) throws IllegalArgumentException {
        Calendar cal = Calendar.getInstance();
        cal.setTimeZone(tzOffsetInSeconds == 0 ? UTC : new SimpleTimeZone(tzOffsetInSeconds * 1000, ""));
        cal.setTimeInMillis(millisSinceEpoch);
        return format(cal);
    }

    public static String format(Calendar cal) throws IllegalArgumentException {
        return format(cal, true);
    }

    private static String format(Calendar cal, boolean includeMs) throws IllegalArgumentException {
        if (cal == null) {
            throw new IllegalArgumentException("argument can not be null");
        }

        StringBuilder buf = new StringBuilder();
        // year ([-]YYYY)
        appendZeroPaddedInt(buf, getYear(cal), 4);
        buf.append('-');
        // month (MM)
        appendZeroPaddedInt(buf, cal.get(Calendar.MONTH) + 1, 2);
        buf.append('-');
        // day (DD)
        appendZeroPaddedInt(buf, cal.get(Calendar.DAY_OF_MONTH), 2);
        buf.append('T');
        // hour (hh)
        appendZeroPaddedInt(buf, cal.get(Calendar.HOUR_OF_DAY), 2);
        buf.append(':');
        // minute (mm)
        appendZeroPaddedInt(buf, cal.get(Calendar.MINUTE), 2);
        buf.append(':');
        // second (ss)
        appendZeroPaddedInt(buf, cal.get(Calendar.SECOND), 2);
        if (includeMs) {
            buf.append('.');
            // millisecond (SSS)
            appendZeroPaddedInt(buf, cal.get(Calendar.MILLISECOND), 3);
        }
        // time zone designator (Z or +00:00 or -00:00)
        TimeZone tz = cal.getTimeZone();
        // determine offset of timezone from UTC (incl. daylight saving)
        int offset = tz.getOffset(cal.getTimeInMillis());
        if (offset != 0) {
            int hours = Math.abs((offset / (60 * 1000)) / 60);
            int minutes = Math.abs((offset / (60 * 1000)) % 60);
            buf.append(offset < 0 ? '-' : '+');
            appendZeroPaddedInt(buf, hours, 2);
            buf.append(':');
            appendZeroPaddedInt(buf, minutes, 2);
        } else {
            buf.append('Z');
        }
        return buf.toString();
    }

    public static int getYear(Calendar cal) throws IllegalArgumentException {
        // determine era and adjust year if necessary
        int year = cal.get(Calendar.YEAR);
        if (cal.isSet(Calendar.ERA)
                && cal.get(Calendar.ERA) == GregorianCalendar.BC) {
            year = 0 - year + 1;
        }

        if (year > 9999 || year < -9999) {
            throw new IllegalArgumentException("Calendar has more than four " +
                    "year digits, cannot be formatted as ISO8601: " + year);
        }
        return year;
    }

    public static class SHORT {

        public static String format(Date date) throws IllegalArgumentException {
            return format(date, 0);
        }

        public static String format(Clock clock) throws IllegalArgumentException {
            return format(clock.millis(), clock.getZone().getRules().getOffset(clock.instant()).getTotalSeconds());
        }

        public static String format(long millisSinceEpoch) throws IllegalArgumentException {
            return format(millisSinceEpoch, 0);
        }

        public static String format(Date date, int tzOffsetInSeconds) throws IllegalArgumentException {
            if (date == null) {
                throw new IllegalArgumentException("argument can not be null");
            }
            return format(date.getTime(), tzOffsetInSeconds);
        }

        public static String format(long millisSinceEpoch, int tzOffsetInSeconds) throws IllegalArgumentException {
            Calendar cal = Calendar.getInstance();
            cal.setTimeZone(tzOffsetInSeconds == 0 ? UTC : new SimpleTimeZone(tzOffsetInSeconds * 1000, ""));
            cal.setTimeInMillis(millisSinceEpoch);
            return format(cal);
        }

        public static String format(Calendar cal) throws IllegalArgumentException {
            return ISO8601.format(cal, false);
        }
    }

    private static void appendZeroPaddedInt(StringBuilder buf, int n, int precision) {
        if (n < 0) {
            buf.append('-');
            n = -n;
        }

        for (int exp = precision - 1; exp > 0; exp--) {
            if (n < Math.pow(10, exp)) {
                buf.append('0');
            } else {
                break;
            }
        }
        buf.append(n);
    }
}