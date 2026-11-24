package com.forgeflow.oa;

import com.forgeflow.auth.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.security.test.context.support.WithMockUser;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ApprovalController.class, properties = "app.jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
@Import(SecurityConfig.class)
class ApprovalAuthorizationTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ApprovalService approvalService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    @WithMockUser(roles = "PLANNER")
    void plannerCannotDecideApproval() throws Exception {
        mockMvc.perform(post("/api/oa/production-orders/42/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":true,\"comment\":\"ready\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousPendingApprovalRequestIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/oa/production-orders/pending"))
                .andExpect(status().isUnauthorized());
    }
}
