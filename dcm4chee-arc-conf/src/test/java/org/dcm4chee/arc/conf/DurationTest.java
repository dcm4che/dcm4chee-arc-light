package org.dcm4chee.arc.conf;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Oct 2015
 */
public class DurationTest {

    @Test
    public void testParse() throws Exception {
        assertEquals(4L, Duration.parse("PT4S").getSeconds());
        assertEquals(180L, Duration.parse("PT3M").getSeconds());
        assertEquals(7200L, Duration.parse("PT2H").getSeconds());
        assertEquals(24*3600L, Duration.parse("P1D").getSeconds());
        Duration d = Duration.parse("P1DT2H3M4.567S");
        assertEquals(24 * 3600L + 7384L, d.getSeconds());
        assertEquals(567000000, d.getNano());
    }
}