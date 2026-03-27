package github.jhkoder.aiblog.sqlviz.simulation;

/**
 * 행(row) 단위 잠금 타입.
 *
 * PostgreSQL 4단계:
 *   FOR KEY SHARE < FOR SHARE < FOR NO KEY UPDATE < FOR UPDATE
 *
 * MySQL 2단계:
 *   LOCK IN SHARE MODE < FOR UPDATE
 *
 * 호환성 판정:
 *   두 LockType 중 하나라도 EXCLUSIVE 이면 충돌 (단, 동일 tx는 허용).
 *   둘 다 SHARE 계열이면 공유 가능.
 */
public enum LockType {
    /** FOR KEY SHARE / LOCK IN SHARE MODE — 공유 잠금 (약) */
    FOR_KEY_SHARE,
    /** FOR SHARE — 공유 잠금 */
    SHARE,
    /** FOR NO KEY UPDATE — 부분 배타 잠금 */
    FOR_NO_KEY_UPDATE,
    /** FOR UPDATE / EXCLUSIVE — 배타 잠금 (강) */
    FOR_UPDATE;

    /** 두 잠금 타입이 충돌하는지 반환 */
    public boolean conflictsWith(LockType other) {
        // 둘 다 FOR_KEY_SHARE / SHARE 이면 공존 가능
        boolean thisShared  = this  == FOR_KEY_SHARE || this  == SHARE;
        boolean otherShared = other == FOR_KEY_SHARE || other == SHARE;
        return !(thisShared && otherShared);
    }
}
