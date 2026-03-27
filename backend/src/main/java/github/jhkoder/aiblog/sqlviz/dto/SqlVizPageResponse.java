package github.jhkoder.aiblog.sqlviz.dto;

import java.util.List;

public record SqlVizPageResponse(
        List<SqlVizResponse> content,
        int totalPages,
        long totalElements,
        int number,
        int size
) {
    public static SqlVizPageResponse of(List<SqlVizResponse> content, int totalPages,
                                        long totalElements, int number, int size) {
        return new SqlVizPageResponse(content, totalPages, totalElements, number, size);
    }
}
