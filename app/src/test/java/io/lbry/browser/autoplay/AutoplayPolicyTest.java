package io.lbry.browser.autoplay;
import io.lbry.browser.model.Claim;
import io.lbry.browser.model.Fee;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class AutoplayPolicyTest {
 private Claim video() { Claim c=new Claim();c.setClaimId("next");c.setPermanentUrl("lbry://next#abc");Claim.StreamMetadata m=new Claim.StreamMetadata();m.setStreamType("video");c.setValue(m);return c; }
 private boolean eligible(Claim c){return AutoplayPolicy.eligible(c,"current",Collections.emptyList(),false);}
 @Test public void acceptsOnlyKnownFreeVideos(){Claim c=video();assertTrue(eligible(c));Fee f=new Fee();((Claim.StreamMetadata)c.getValue()).setFee(f);assertFalse(eligible(c));f.setAmount("0.0");assertTrue(eligible(c));for(String amount:Arrays.asList("1","-1","NaN","invalid")){f.setAmount(amount);assertFalse(eligible(c));}c.setValue(new Claim.GenericMetadata());assertFalse(eligible(c));}
 @Test public void excludesRecentCurrentMissingAndNonVideo(){Claim c=video();assertFalse(AutoplayPolicy.eligible(c,"next",Collections.emptyList(),false));assertFalse(AutoplayPolicy.eligible(c,"current",Arrays.asList("next"),false));((Claim.StreamMetadata)c.getValue()).setStreamType("audio");assertFalse(eligible(c));assertFalse(eligible(null));c=video();c.setPermanentUrl(null);assertFalse(eligible(c));}
 @Test public void obeysMaturePreference(){Claim c=video();c.getValue().setTags(Arrays.asList("mature"));assertFalse(eligible(c));assertTrue(AutoplayPolicy.eligible(c,"current",Collections.emptyList(),true));}
}
