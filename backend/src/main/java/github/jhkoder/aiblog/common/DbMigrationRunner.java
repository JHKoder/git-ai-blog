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
        try {
            jdbcTemplate.execute("ALTER TABLE repos DROP CONSTRAINT IF EXISTS repos_collect_type_check");
            log.info("DB migration: repos_collect_type_check constraint dropped (if existed)");
        } catch (Exception e) {
            log.warn("DB migration skipped: {}", e.getMessage());
        }
    }
}
