package github.jhkoder.aiblog.sqlviz.domain;

public enum SqlVizScenario {
    DEADLOCK,
    DIRTY_READ,
    NON_REPEATABLE_READ,
    PHANTOM_READ,
    LOST_UPDATE,
    MVCC
}
