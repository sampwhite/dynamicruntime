package org.dynamicruntime.util;

import org.dynamicruntime.exception.DnException;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;

@SuppressWarnings({"WeakerAccess", "unused"})
public class DnDateUtil {
    public static final String SYSTEM_DATE_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";
    public static final ZoneId UTC_TIME_ZONE = ZoneId.of("UTC");

    /** Some common date formatters. */
    public static final DateTimeFormatter systemFormatter = mkSystemDateFormatter(SYSTEM_DATE_TIME_FORMAT);
    public static final DateTimeFormatter shortSystemFormatter =
            mkSystemDateFormatter("yyyy-MM-dd'T'HH:mm:ss'Z'");

    /** The static timezone of the the server running the code.
     * This is mostly used to determine a shared concept of day start and day end that is somewhat aligned with
     * the maintainers and administrators of the server. */
    static ZoneId serverTimeZone = determineDefaultServerTimeZone();
    static DateTimeFormatter dayOnlyFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(serverTimeZone);

    static ZoneId determineDefaultServerTimeZone() {
        String serverTimeZone = System.getProperty("ServerTimeZone");
        if (serverTimeZone == null) {
            // Default server time zone to West coast (not daylight savings) USA.
            // Note that midnight in any USA timezone will be before or equal to
            // the start of day in this timezone. So if a job is launched at the start of a server day,
            // it will start anywhere from midnight to 4:00 AM in the various primary timezones
            // of the USA in either summer or winter reducing the chance that it runs
            // when the servers are busy serving actual users. It also works well
            // as a time to take the system down for an hour or two.
            // If the software is international, then servers specific to different regions
            // should be used. The *shard* in the DnCxt object can be used to determine
            // which server to target when proxying calls.
            serverTimeZone = "UTC-08:00";
        }
        return ZoneId.of(serverTimeZone);
    }

    public static ZoneId getServerTimeZone() {
        return serverTimeZone;
    }

    public static DateTimeFormatter getDayOnlyFormatter() {
        return dayOnlyFormatter;
    }

    /** Only call this at initialization of VM time. */
    public void setServerTimeZone(String timeZone) {
        serverTimeZone = ZoneId.of(timeZone);
        dayOnlyFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(serverTimeZone);
    }

    public static DateTimeFormatter mkSystemDateFormatter(String pattern) {
        return DateTimeFormatter.ofPattern(pattern).withZone(UTC_TIME_ZONE);
    }

    public static Date parseDate(String inputStr) throws DnException {
        String str = inputStr != null ? inputStr.trim() : "";
        if (str.isEmpty()) {
            throw DnException.mkParsing("Date string to be parsed was null or empty.", null);
        }

        // Inspect the string for different possible formats.
        int firstDash = str.indexOf('-');
        int secondDash = 0;
        if (firstDash == 4) {
            secondDash = str.indexOf('-', 5);
        }
        if (secondDash != 7 || str.length() < 10) {
            throw DnException.mkParsing(
                    String.format("Date string '%s' does not follow a recognizable date format.", inputStr), null);
        }
        try {
            ZonedDateTime zdt;
            if (str.length() == 10) {
                zdt = LocalDate.parse(str, dayOnlyFormatter).atStartOfDay(serverTimeZone);
            } else {
                int dotIndex = str.indexOf('.', 10);
                if (dotIndex >= 0 && dotIndex != 19) {
                    throw new DnException(String.format("Date string '%s' does not have a '.' at correct location.",
                            inputStr));
                }
                char lastChar = str.charAt(str.length() - 1);
                if (lastChar != 'Z') {
                    str = str + "Z";
                }
                zdt = (dotIndex > 0) ?
                        ZonedDateTime.parse(str, systemFormatter) :
                        ZonedDateTime.parse(str, shortSystemFormatter);
            }
            return Date.from(zdt.toInstant());
        } catch (DateTimeException dte) {
            throw DnException.mkParsing(String.format("Date string '%s' failed to parse.", inputStr), dte);
        }
    }

    public static String formatDate(Date date) {
        return systemFormatter.format(date.toInstant());
    }

    public static String formatDayPart(Date date) {
        return dayOnlyFormatter.format(date.toInstant());
    }

    public static Date toStartOfDay(Date date) {
        var ld = LocalDate.ofInstant(date.toInstant(), serverTimeZone);
        return Date.from(ld.atStartOfDay(serverTimeZone).toInstant());
    }
}
