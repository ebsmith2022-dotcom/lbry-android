package io.lbry.browser.autoplay;
import org.junit.Test;
import static org.junit.Assert.*;
public class AutoplayViewCountsTest {
 @Test public void thresholdIncludesExactly100(){assertFalse(AutoplayViewCounts.qualifies(99));assertTrue(AutoplayViewCounts.qualifies(100));assertTrue(AutoplayViewCounts.qualifies(101L));assertTrue(AutoplayViewCounts.qualifies("100"));assertTrue(AutoplayViewCounts.qualifies(3000000000L));}
 @Test public void missingAndInvalidCountsNeverQualify(){for(Object v:new Object[]{null,-1,0,"","unknown","NaN",true,100.5})assertFalse(String.valueOf(v),AutoplayViewCounts.qualifies(v));}
}
