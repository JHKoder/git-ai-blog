package github.jhkoder.aiblog.sqlviz.simulation;

import java.util.List;

public record SimulationResult(
        List<SimulationStep> steps,
        String summary,
        boolean hasConflict,
        String conflictType
) {}
