package game;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CentralLoggingTest {
    @Test
    public void addOutput_routesMessagesThroughSlf4j() {
        Logger logger = (Logger) LoggerFactory.getLogger(CentralLogging.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            CentralLogging.getInstance().addOutput("10.0.0.1\t2.2.2.2\t1\t50.0\n");

            assertEquals(1, appender.list.size());
            assertEquals("10.0.0.1\t2.2.2.2\t1\t50.0", appender.list.get(0).getFormattedMessage());
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    public void addOutput_ignoresBlankMessagesAfterTrimmingLineBreaks() {
        Logger logger = (Logger) LoggerFactory.getLogger(CentralLogging.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            CentralLogging.getInstance().addOutput("\n");
            CentralLogging.getInstance().addOutput("\r\n");

            assertTrue(appender.list.isEmpty());
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
