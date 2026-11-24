package com.forgeflow.mes;

import com.forgeflow.erp.order.ProductionOrder;
import com.forgeflow.erp.order.ProductionOrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class MesTransactionIT {
    private static final String TEST_JWT_SECRET = temporaryJwtSecret();

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("app.jwt.secret", () -> TEST_JWT_SECRET);
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MesService mesService;

    @Autowired
    private ProductionOrderMapper orderMapper;

    @BeforeEach
    void setUp() {
        jdbc.execute("DELETE FROM mes_production_report");
        jdbc.execute("DELETE FROM oa_approval_action");
        jdbc.execute("DELETE FROM erp_production_order");
        jdbc.execute("DELETE FROM erp_bom_line");
        jdbc.execute("DELETE FROM erp_product");
        jdbc.execute("INSERT INTO erp_product (id, code, name, product_type, unit, enabled) VALUES (9001, 'IT-FG', '集成测试成品', 'FINISHED', '件', 1)");
        jdbc.execute("INSERT INTO erp_production_order (id, order_no, product_id, planned_quantity, good_quantity, defect_quantity, status, created_by) VALUES (9001, 'IT-ORDER-9001', 9001, 10, 2, 1, 'IN_PROGRESS', 'it')");
        jdbc.execute("DROP TRIGGER IF EXISTS fail_mes_report_insert");
        jdbc.execute("CREATE TRIGGER fail_mes_report_insert BEFORE INSERT ON mes_production_report FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'forced integration failure'");
    }

    @Test
    void rollsBackReportAndOrderProgressWhenReportInsertFails() {
        assertThatThrownBy(() -> mesService.report(9001L, new ProductionReportRequest(3, 1, "触发回滚"), "operator"))
                .hasMessageContaining("forced integration failure");

        ProductionOrder order = orderMapper.selectById(9001L);
        assertThat(order.getGoodQuantity()).isEqualTo(2);
        assertThat(order.getDefectQuantity()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mes_production_report WHERE order_id = 9001", Integer.class))
                .isZero();
    }

    private static String temporaryJwtSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
