package github.jhkoder.aiblog.sqlviz.simulation;

public record SimulationStep(
        int step,
        String txId,
        String operation,
        String sql,
        String result,
        String detail,
        int durationMs
) {}
