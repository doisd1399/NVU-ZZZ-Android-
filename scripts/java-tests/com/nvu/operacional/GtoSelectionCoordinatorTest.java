package com.nvu.operacional;

public final class GtoSelectionCoordinatorTest {
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        GtoSelectionCoordinator c = new GtoSelectionCoordinator();

        // Normal browsing: latest frame sequence advances, no critical window.
        require(c.onFrameProcessed() == 1L, "frame 1");
        require(c.onFrameProcessed() == 2L, "frame 2");
        require(!c.isCriticalWindow(), "not critical before touch");

        // Touch marker is between frame 2 and frame 3 on the same logical handler.
        require(c.markTouch() == 2L, "marker after frame 2");
        require(c.isCriticalWindow(), "critical after touch");
        require(!c.isPostTouch(2L), "pre-touch frame cannot be post-touch");
        long f3 = c.onFrameProcessed();
        require(f3 == 3L && c.isPostTouch(f3), "first frame after touch must correlate");

        c.finishCriticalWindow();
        require(!c.isCriticalWindow(), "critical window closes");

        // Critical case from audit: touch before any freight-list frame was recognized.
        c.reset();
        require(c.markTouch() == 0L, "touch can be queued before first recognized frame");
        long first = c.onFrameProcessed();
        require(first == 1L && c.isPostTouch(first), "first visible list frame remains correlated");

        // Page navigation / cancelled touch can reset without corrupting sequence.
        c.finishCriticalWindow();
        long next = c.onFrameProcessed();
        require(next == 2L, "frame order remains monotonic after cancelled touch");

        System.out.println("GtoSelectionCoordinatorTest: PASS");
    }
}
