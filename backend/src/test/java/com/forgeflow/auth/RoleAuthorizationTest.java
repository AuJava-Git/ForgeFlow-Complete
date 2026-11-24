package com.forgeflow.auth;

import com.forgeflow.dashboard.DashboardController;
import com.forgeflow.dashboard.DashboardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DashboardController.class, properties = "app.jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
@Import(SecurityConfig.class)
class RoleAuthorizationTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DashboardService dashboardService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void everyRoleCanReadSharedErpCatalogAndOrders() throws Exception {
        for (String role : new String[]{"ADMIN", "PLANNER", "APPROVER", "WAREHOUSE", "PURCHASER", "OPERATOR", "QUALITY_INSPECTOR"}) {
            expect(role, HttpMethod.GET, "/api/erp/role-probe", 404);
        }
    }

    @Test
    void onlyPlannerAndAdminCanCreateProductionOrders() throws Exception {
        expect("ADMIN", HttpMethod.POST, "/api/erp/production-orders", 404);
        expect("PLANNER", HttpMethod.POST, "/api/erp/production-orders", 404);
        expect("APPROVER", HttpMethod.POST, "/api/erp/production-orders", 403);
        expect("WAREHOUSE", HttpMethod.POST, "/api/erp/production-orders", 403);
        expect("PURCHASER", HttpMethod.POST, "/api/erp/production-orders", 403);
        expect("OPERATOR", HttpMethod.POST, "/api/erp/production-orders", 403);
        expect("QUALITY_INSPECTOR", HttpMethod.POST, "/api/erp/production-orders", 403);
    }

    @Test
    void plannerCannotReserveOrPurchaseAndApproverCannotInspectQuality() throws Exception {
        expect("WAREHOUSE", HttpMethod.POST, "/api/inventory/production-orders/1/reserve", 404);
        expect("PLANNER", HttpMethod.POST, "/api/inventory/production-orders/1/reserve", 403);
        expect("PURCHASER", HttpMethod.POST, "/api/purchasing/production-orders/1/shortage-orders", 404);
        expect("PLANNER", HttpMethod.POST, "/api/purchasing/production-orders/1/shortage-orders", 403);
        expect("QUALITY_INSPECTOR", HttpMethod.GET, "/api/quality/pending", 404);
        expect("APPROVER", HttpMethod.GET, "/api/quality/pending", 403);
    }

    @Test
    void eachJobRoleIsLimitedToItsBusinessModule() throws Exception {
        expect("WAREHOUSE", HttpMethod.GET, "/api/inventory/role-probe", 404);
        expect("PLANNER", HttpMethod.GET, "/api/inventory/role-probe", 403);
        expect("PURCHASER", HttpMethod.POST, "/api/purchasing/role-probe", 404);
        expect("PLANNER", HttpMethod.POST, "/api/purchasing/role-probe", 403);
        expect("OPERATOR", HttpMethod.GET, "/api/mes/role-probe", 404);
        expect("APPROVER", HttpMethod.GET, "/api/mes/role-probe", 403);
        expect("QUALITY_INSPECTOR", HttpMethod.GET, "/api/quality/role-probe", 404);
        expect("APPROVER", HttpMethod.GET, "/api/quality/role-probe", 403);
        expect("APPROVER", HttpMethod.GET, "/api/oa/production-orders/pending", 404);
        expect("QUALITY_INSPECTOR", HttpMethod.GET, "/api/oa/production-orders/pending", 403);
    }

    @Test
    void allRolesCanReachTheDashboardButAnonymousUsersCannot() throws Exception {
        for (String role : new String[]{"ADMIN", "PLANNER", "APPROVER", "WAREHOUSE", "PURCHASER", "OPERATOR", "QUALITY_INSPECTOR"}) {
            MockHttpServletRequestBuilder request = request(HttpMethod.GET, "/api/dashboard/role-probe")
                    .with(user("demo").roles(role));
            mockMvc.perform(request).andExpect(status().isNotFound());
        }
        mockMvc.perform(request(HttpMethod.GET, "/api/dashboard/role-probe"))
                .andExpect(status().isUnauthorized());
        expect("GUEST", HttpMethod.GET, "/api/dashboard/role-probe", 403);
        expect("GUEST", HttpMethod.GET, "/api/unmapped", 403);
        expect("ADMIN", HttpMethod.GET, "/api/unmapped", 403);
    }

    private void expect(String role, HttpMethod method, String path, int statusCode) throws Exception {
        mockMvc.perform(request(method, path).with(user("demo").roles(role)))
                .andExpect(status().is(statusCode));
    }
}
