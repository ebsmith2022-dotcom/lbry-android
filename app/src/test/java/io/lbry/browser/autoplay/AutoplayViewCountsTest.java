package io.lbry.browser.autoplay;
import org.junit.Test;
import static org.junit.Assert.*;
public class AutoplayViewCountsTest {
 @Test public void thresholdIncludesExactly40(){assertFalse(AutoplayViewCounts.qualifies(39));assertTrue(AutoplayViewCounts.qualifies(40));assertTrue(AutoplayViewCounts.qualifies(41L));assertTrue(AutoplayViewCounts.qualifies("40"));assertTrue(AutoplayViewCounts.qualifies(3000000000L));}
 @Test public void missingAndInvalidCountsNeverQualify(){for(Object v:new Object[]{null,-1,0,"","unknown","NaN",true,40.5})assertFalse(String.valueOf(v),AutoplayViewCounts.qualifies(v));}
}
