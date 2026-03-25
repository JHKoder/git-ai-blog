package github.jhkoder.aiblog.sqlviz.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "sqlviz_widgets")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SqlVizWidget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String sqlsJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SqlVizScenario scenario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IsolationLevel isolationLevel;

    @Column(columnDefinition = "TEXT")
    private String simulationJson;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public static SqlVizWidget create(Long memberId, String title, String sqlsJson,
                                      SqlVizScenario scenario, IsolationLevel isolationLevel) {
        SqlVizWidget w = new SqlVizWidget();
        w.memberId = memberId;
        w.title = title;
        w.sqlsJson = sqlsJson;
        w.scenario = scenario;
        w.isolationLevel = isolationLevel;
        return w;
    }

    public void updateSimulation(String simulationJson) {
        this.simulationJson = simulationJson;
    }
}
