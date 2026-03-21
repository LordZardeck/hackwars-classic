package util;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

public class GameClockTest {
    @After
    public void tearDown() {
        GameClock.resetForTest();
    }

    @Test
    public void productionModeUsesSystemBackedClock() {
        long beforeMillis = System.currentTimeMillis();
        long clockMillis = GameClock.nowMillis();
        long afterMillis = System.currentTimeMillis();

        Assert.assertTrue(clockMillis >= beforeMillis);
        Assert.assertTrue(clockMillis <= afterMillis);
        Assert.assertTrue(GameClock.nowNanos() > 0L);
    }

    @Test
    public void freezeForTestPinsBothTimeDomains() {
        GameClock.freezeForTest(1234L, 5678L);

        Assert.assertEquals(1234L, GameClock.nowMillis());
        Assert.assertEquals(5678L, GameClock.nowNanos());
    }

    @Test
    public void advanceForTestMovesBothClocksPredictably() {
        GameClock.freezeForTest(100L, 1000L);

        GameClock.advanceForTest(25L, 250L);

        Assert.assertEquals(125L, GameClock.nowMillis());
        Assert.assertEquals(1250L, GameClock.nowNanos());
    }

    @Test
    public void resetForTestRestoresSystemClock() {
        GameClock.freezeForTest(1L, 2L);

        GameClock.resetForTest();

        Assert.assertNotEquals(1L, GameClock.nowMillis());
        Assert.assertNotEquals(2L, GameClock.nowNanos());
    }
}
