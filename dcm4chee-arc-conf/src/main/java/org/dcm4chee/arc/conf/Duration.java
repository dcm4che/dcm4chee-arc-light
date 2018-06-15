package org.dcm4chee.arc.conf;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Oct 2015
 */
public class Duration {

    private static final Pattern PATTERN = Pattern.compile(
            "P(?:([0-9]+)D)?(T(?:([0-9]+)H)?(?:([0-9]+)M)?(?:([0-9]+)(?:[.,]([0-9]{0,9}))?S)?)?",
            Pattern.CASE_INSENSITIVE);
    private final String text;
    private final long seconds;
    private final int nano;


    private Duration(String text, long seconds, int nano) {
        this.text = text;
        this.seconds = seconds;
        this.nano = nano;
    }

    public static Duration valueOf(String text) {
        Matcher matcher = PATTERN.matcher(text);
        if (matcher.matches() && !"T".equals(matcher.group(2))) {
            String dayMatch = matcher.group(1);
            String hourMatch = matcher.group(3);
            String minuteMatch = matcher.group(4);
            String secondMatch = matcher.group(5);
            String fractionMatch = matcher.group(6);
            if (dayMatch != null || hourMatch != null || minuteMatch != null || secondMatch != null)
                try {
                    return new Duration(text,
                          (((parseNumber(dayMatch) * 24
                            + parseNumber(hourMatch)) * 60)
                            + parseNumber(minuteMatch)) * 60
                            + parseNumber(secondMatch),
                            parseFraction(fractionMatch));
                } catch (NumberFormatException ignore) {
                }
        }
        throw new IllegalArgumentException(text);
    }

    private static long parseNumber(String parsed) {
        return parsed != null ? Long.parseLong(parsed) : 0L;
    }

    private static int parseFraction(String parsed) {
        return parsed != null ? Integer.parseInt((parsed + "000000000").substring(0, 9)) : 0;
    }

    @Override
    public String toString() {
        return text;
    }

    public long getSeconds() {
        return seconds;
    }

    public int getNano() {
        return nano;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Duration duration = (Duration) o;
        return seconds == duration.seconds && nano == duration.nano;
    }

    @Override
    public int hashCode() {
        return 31 * (int) (seconds ^ (seconds >>> 32)) + nano;
    }
}
