package io.lbry.browser.model;

import org.junit.Test;
import static org.junit.Assert.*;

public class ClaimThumbnailTest {
    @Test public void missingThumbnailAndMissingUrlUsePlaceholder() {
        Claim claim = new Claim();
        assertNull(claim.getThumbnailUrl(640, 360, 85));
        Claim.GenericMetadata metadata = new Claim.GenericMetadata();
        claim.setValue(metadata);
        assertNull(claim.getThumbnailUrl(640, 360, 85));
        Claim.Resource thumbnail = new Claim.Resource();
        metadata.setThumbnail(thumbnail);
        assertNull(claim.getThumbnailUrl(640, 360, 85));
        thumbnail.setUrl("");
        assertNull(claim.getThumbnailUrl(640, 360, 85));
        thumbnail.setUrl("   ");
        assertNull(claim.getThumbnailUrl(640, 360, 85));
    }
    @Test public void validThumbnailKeepsCdnSizing() {
        Claim claim = new Claim();
        Claim.GenericMetadata metadata = new Claim.GenericMetadata();
        Claim.Resource thumbnail = new Claim.Resource();
        thumbnail.setUrl("https://example.com/image.jpg");
        metadata.setThumbnail(thumbnail);
        claim.setValue(metadata);
        assertEquals("https://thumbnails.odycdn.com/optimize/s:640:360/quality:85/plain/https://example.com/image.jpg",
                claim.getThumbnailUrl(640, 360, 85));
    }
}
