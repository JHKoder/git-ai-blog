package github.jhkoder.aiblog.common;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DbMigrationRunner implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        migrate("ALTER TABLE repos DROP CONSTRAINT IF EXISTS repos_collect_type_check",
                "repos_collect_type_check constraint dropped");
        migrate("ALTER TABLE sqlviz_widgets DROP CONSTRAINT IF EXISTS sqlviz_widgets_scenario_check",
                "sqlviz_widgets_scenario_check constraint dropped");
        migrate("ALTER TABLE sqlviz_widgets DROP CONSTRAINT IF EXISTS sqlviz_widgets_isolation_level_check",
                "sqlviz_widgets_isolation_level_check constraint dropped");
    }

    private void migrate(String sql, String description) {
        try {
            jdbcTemplate.execute(sql);
            log.info("DB migration: {}", description);
        } catch (Exception e) {
            log.warn("DB migration skipped ({}): {}", description, e.getMessage());
        }
    }
}
