package com.jinxin.unlockhub.data;

public final class UnlockEvent {
    public final long id;
    public final String localDate;
    public final long firstUnlockAt;
    public final boolean synced;

    public UnlockEvent(long id, String localDate, long firstUnlockAt, boolean synced) {
        this.id = id;
        this.localDate = localDate;
        this.firstUnlockAt = firstUnlockAt;
        this.synced = synced;
    }
}
