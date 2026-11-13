package io.lbry.browser.autoplay;

import java.math.BigDecimal;
import java.util.Collection;
import io.lbry.browser.model.Claim;

/** Fail closed for incomplete metadata or an unknown price. */
public final class AutoplayPolicy {
    private AutoplayPolicy() {}
    public static boolean eligible(Claim c, String currentId, Collection<String> recent, boolean mature) {
        if (c == null || c.getClaimId() == null || c.getClaimId().equals(currentId)
                || recent.contains(c.getClaimId()) || c.isUnresolved() || c.isLoadingPlaceholder()
                || c.getPermanentUrl() == null || c.getPermanentUrl().isEmpty()
                || !(c.getValue() instanceof Claim.StreamMetadata) || (!mature && c.isMature())) return false;
        Claim.StreamMetadata metadata = (Claim.StreamMetadata) c.getValue();
        if (!"video".equalsIgnoreCase(metadata.getStreamType())) return false;
        if (metadata.getFee() == null) return true;
        try { return new BigDecimal(metadata.getFee().getAmount()).compareTo(BigDecimal.ZERO) == 0; }
        catch (RuntimeException error) { return false; }
    }
}
