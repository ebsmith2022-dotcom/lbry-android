package io.lbry.browser.autoplay;
import org.junit.Test;
import static org.junit.Assert.*;
public class AutoplayViewCountsTest {
 @Test public void thresholdIncludesExactly10(){assertFalse(AutoplayViewCounts.qualifies(9));assertTrue(AutoplayViewCounts.qualifies(10));assertTrue(AutoplayViewCounts.qualifies(11L));assertTrue(AutoplayViewCounts.qualifies("10"));assertTrue(AutoplayViewCounts.qualifies(3000000000L));}
 @Test public void missingAndInvalidCountsNeverQualify(){for(Object v:new Object[]{null,-1,0,"","unknown","NaN",true,10.5})assertFalse(String.valueOf(v),AutoplayViewCounts.qualifies(v));}
}
