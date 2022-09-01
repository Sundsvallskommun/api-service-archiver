package se.sundsvall.byggrarchiver.testutils;

import generated.se.sundsvall.archive.Attachment;
import generated.se.sundsvall.archive.ByggRArchiveRequest;
import org.mockito.ArgumentMatcher;

public class ArchiveMessageAttachmentMatcher implements ArgumentMatcher<ByggRArchiveRequest> {
    private final ByggRArchiveRequest left;

    public ArchiveMessageAttachmentMatcher(ByggRArchiveRequest left) {
        this.left = left;
    }

    @Override
    public boolean matches(ByggRArchiveRequest right) {
        Attachment aLeft = left.getAttachment();
        Attachment aRight = right.getAttachment();

        if (aLeft.equals(aRight)) return true;
        return aLeft.getName().equals(aRight.getName())
                && aLeft.getExtension().equals(aRight.getExtension())
                && aLeft.getFile().equals(aRight.getFile());
    }
}
