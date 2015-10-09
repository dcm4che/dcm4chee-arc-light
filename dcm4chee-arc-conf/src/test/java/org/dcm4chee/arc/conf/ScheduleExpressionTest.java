package org.dcm4chee.arc.conf;

import org.junit.Test;

import java.util.GregorianCalendar;

import static org.junit.Assert.assertEquals;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Oct 2015
 */
public class ScheduleExpressionTest {

    @Test
    public void testCeil() throws Exception {
        ScheduleExpression[] schedules = {
                ScheduleExpression.valueOf("hour=18-6 dayOfWeek=*"),
                ScheduleExpression.valueOf("hour=* dayOfWeek=0,6")
        };
        assertEquals(new GregorianCalendar(2015, 9, 8, 18, 0), ScheduleExpression.ceil(
                 new GregorianCalendar(2015, 9, 8, 10, 0), schedules));
        assertEquals(new GregorianCalendar(2015, 9, 10, 10, 0), ScheduleExpression.ceil(
                 new GregorianCalendar(2015, 9, 10, 10, 0), schedules));
    }
}