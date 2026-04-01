package github.jhkoder.aiblog.sqlviz.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;

@Entity
@Table(name = "sqlviz_widgets", indexes = {
        @Index(name = "idx_sqlviz_member_id", columnList = "memberId")
})
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

    /** sqlsJson의 SHA-256 해시 (64자 hex). 중복 감지 인덱스에 사용. */
    @Column(name = "sqls_hash", length = 64)
    private String sqlsHash;

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
        w.sqlsHash = sha256Hex(sqlsJson);
        w.scenario = scenario;
        w.isolationLevel = isolationLevel;
        return w;
    }

    public void updateSimulation(String simulationJson) {
        this.simulationJson = simulationJson;
    }

    public static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
