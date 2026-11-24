package com.forgeflow.seed;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@ActiveProfiles("dev")
class DemoWorkbenchSeedIT {
    private static final String TEST_JWT_SECRET = temporaryJwtSecret();
    private static final String TEST_BCRYPT_HASH = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("app.jwt.secret", () -> TEST_JWT_SECRET);
        registry.add("spring.flyway.placeholders.demo_password_hash", () -> TEST_BCRYPT_HASH);
        registry.add("spring.flyway.out-of-order", () -> "true");
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void seedsFourFinishedProductsSixMaterialsAndMultipleBomLines() {
        assertThat(count("SELECT COUNT(*) FROM erp_product WHERE code LIKE 'FG-DEMO-%' AND product_type = 'FINISHED'"))
                .isEqualTo(4);
        assertThat(count("SELECT COUNT(*) FROM erp_product WHERE code LIKE 'RM-DEMO-%' AND product_type = 'MATERIAL'"))
                .isEqualTo(6);
        assertThat(count("SELECT COUNT(*) FROM erp_bom_line b JOIN erp_product p ON p.id = b.product_id WHERE p.code LIKE 'FG-DEMO-%'"))
                .isGreaterThanOrEqualTo(10);
    }

    @Test
    void seedsEveryOrderStateAndHasTasksForAllOperationalQueues() {
        assertThat(count("SELECT COUNT(DISTINCT status) FROM erp_production_order WHERE order_no LIKE 'DEMO-%'"))
                .isEqualTo(6);
        assertThat(count("SELECT COUNT(*) FROM erp_production_order WHERE order_no LIKE 'DEMO-%' AND status = 'DRAFT'"))
                .isGreaterThan(0);
        assertThat(count("SELECT COUNT(*) FROM erp_production_order WHERE order_no LIKE 'DEMO-%' AND status = 'PENDING_APPROVAL'"))
                .isGreaterThan(0);
        assertThat(count("SELECT COUNT(*) FROM erp_production_order WHERE order_no LIKE 'DEMO-%' AND status = 'APPROVED' AND material_status <> 'ISSUED'"))
                .isGreaterThan(0);
        assertThat(count("SELECT COUNT(*) FROM purchasing_order po JOIN purchasing_order_line pol ON pol.purchase_order_id = po.id WHERE po.order_no LIKE 'DEMO-%' AND pol.ordered_quantity > pol.received_quantity"))
                .isGreaterThan(0);
        assertThat(count("SELECT COUNT(*) FROM erp_production_order WHERE order_no LIKE 'DEMO-%' AND status = 'IN_PROGRESS'"))
                .isGreaterThan(0);
        assertThat(count("SELECT COUNT(*) FROM erp_production_order WHERE order_no LIKE 'DEMO-%' AND status = 'APPROVED' AND material_status = 'ISSUED'"))
                .isGreaterThan(0);
        assertThat(count("SELECT COUNT(*) FROM mes_production_report r JOIN erp_production_order o ON o.id = r.order_id LEFT JOIN quality_inspection q ON q.report_id = r.id WHERE o.order_no LIKE 'DEMO-%' AND r.good_quantity > 0 AND q.id IS NULL"))
                .isGreaterThan(0);
    }

    @Test
    void keepsCompletedOrderReportsInspectionAndFinishedReceiptQuantitiesClosed() {
        assertThat(count("""
                SELECT COUNT(*) FROM erp_production_order o
                JOIN erp_product p ON p.id = o.product_id
                WHERE o.order_no = 'DEMO-DONE-001'
                  AND o.status = 'COMPLETED'
                  AND o.good_quantity = (SELECT COALESCE(SUM(r.good_quantity), 0) FROM mes_production_report r WHERE r.order_id = o.id)
                  AND o.defect_quantity = (SELECT COALESCE(SUM(r.defect_quantity), 0) FROM mes_production_report r WHERE r.order_id = o.id)
                  AND o.planned_quantity = o.good_quantity + o.defect_quantity
                  AND (SELECT COALESCE(SUM(q.passed_quantity + q.rejected_quantity), 0)
                       FROM quality_inspection q JOIN mes_production_report r ON r.id = q.report_id WHERE r.order_id = o.id)
                      = (SELECT COALESCE(SUM(r.good_quantity), 0) FROM mes_production_report r WHERE r.order_id = o.id)
                  AND (SELECT COALESCE(SUM(fgr.quantity), 0)
                       FROM inv_finished_goods_receipt fgr JOIN quality_inspection q ON q.id = fgr.inspection_id WHERE q.order_id = o.id)
                      = (SELECT COALESCE(SUM(q.passed_quantity), 0) FROM quality_inspection q WHERE q.order_id = o.id)
                """))
                .isEqualTo(1);
    }

    @Test
    void limitsSeededPurchaseLinesToTheExactProductionShortage() {
        assertThat(count("""
                SELECT COUNT(*) FROM purchasing_order po
                JOIN erp_production_order o ON o.id = po.production_order_id
                JOIN purchasing_order_line pol ON pol.purchase_order_id = po.id
                JOIN erp_product material ON material.id = pol.material_id
                JOIN erp_order_material_requirement r ON r.order_id = o.id AND r.material_id = pol.material_id
                JOIN inv_stock_balance b ON b.product_id = pol.material_id
                WHERE po.order_no = 'DEMO-PO-001'
                  AND material.code = 'RM-DEMO-001'
                  AND o.status = 'APPROVED'
                  AND po.status = 'OPEN'
                  AND pol.received_quantity = 0
                  AND pol.ordered_quantity = 10.0000
                  AND pol.ordered_quantity = GREATEST(r.required_quantity - (b.on_hand_quantity - b.reserved_quantity), 0)
                  AND pol.ordered_quantity > 0
                """))
                .isEqualTo(1);
    }

    @Test
    void addsDedicatedDemoUsersForWarehousePurchasingAndQuality() {
        assertThat(count("SELECT COUNT(*) FROM sys_user WHERE username IN ('demo-warehouse', 'demo-purchaser', 'demo-quality')"))
                .isEqualTo(3);
        assertThat(count("SELECT COUNT(*) FROM sys_user WHERE (username, role_code) IN (('demo-warehouse','WAREHOUSE'),('demo-purchaser','PURCHASER'),('demo-quality','QUALITY_INSPECTOR'))"))
                .isEqualTo(3);
    }

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    private static String temporaryJwtSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
