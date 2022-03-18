package dash;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tool.parser.mpd.MPD;

import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class DashTest {

    private static final Logger logger = LoggerFactory.getLogger(DashTest.class);

    @Test
    public void test() {
        /////////////////////////////////////////////
        // 1) MPD PARSING TEST
        /*MPD mpd = parseMpdTest(dashManager);
        Assert.assertNotNull(mpd);*/
        /////////////////////////////////////////////

        /////////////////////////////////////////////
        // 2) HTTP COMMUNICATION TEST
        /*DashHttpMessageSender dashHttpSender = new DashHttpMessageSender();
        dashHttpSender.start();

        TimeUnit timeUnit = TimeUnit.SECONDS;
        try {
            dashHttpSender.sendSampleMessage();

            *//*timeUnit.sleep(1);
            dashHttpSender.sendSampleMessage();

            timeUnit.sleep(1);
            dashHttpSender.sendSampleMessage();*//*

            timeUnit.sleep(2);
        } catch (Exception e) {
            logger.warn("DashTest.test.Exception", e);
        }

        dashHttpSender.stop();*/
        /////////////////////////////////////////////

        /////////////////////////////////////////////
        //testRtmpSubscribe1();
        /////////////////////////////////////////////
    }

    public static MPD parseMpdTest(DashManager dashManager) {
        return dashManager.parseMpd("/Users/jamesj/GIT_PROJECTS/JDASH/src/test/resources/mpd_examples/mpd_example4.xml");
    }

}
