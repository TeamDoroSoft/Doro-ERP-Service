package com.dorosoft.erp.shared.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dorosoft.erp.TestcontainersConfiguration;
import com.dorosoft.erp.platform.web.RequestIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "doro.store.bootstrap.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SecurityConfigIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getWithoutAuthenticationReturns401Problem() throws Exception {
        mockMvc.perform(get("/api/v1/store-settings"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(header().string(RequestIdFilter.REQUEST_ID_HEADER, org.hamcrest.Matchers.startsWith("req-")));
    }

    @Test
    void getWithoutRequiredPermissionReturns403Problem() throws Exception {
        mockMvc.perform(get("/api/v1/store-settings")
                        .headers(actorHeaders("store.settings.update")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void getWithReadPermissionPassesSecurityAndReachesApplication() throws Exception {
        mockMvc.perform(get("/api/v1/store-settings")
                        .headers(actorHeaders("store.settings.read")))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    if (status == 401 || status == 403) {
                        throw new AssertionError("인증·인가를 통과하지 못했습니다: " + status);
                    }
                });
    }

    @Test
    void putWithoutAuthenticationReturns401Problem() throws Exception {
        mockMvc.perform(put("/api/v1/store-settings/profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void putWithoutRequiredPermissionReturns403Problem() throws Exception {
        mockMvc.perform(put("/api/v1/store-settings/profile")
                        .headers(actorHeaders("store.settings.read")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void putWithUpdatePermissionPassesSecurityAndReachesApplication() throws Exception {
        mockMvc.perform(put("/api/v1/store-settings/profile")
                        .headers(actorHeaders("store.settings.update"))
                        .header("If-Match", "0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"매장","address":"주소","contact":"02-1234-5678","timeZone":"Not/AZone"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private org.springframework.http.HttpHeaders actorHeaders(String permissions) {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.set("X-Doro-Actor-Id", "actor-1");
        headers.set("X-Doro-Actor-Role", "STORE_ADMIN");
        headers.set("X-Doro-Actor-Permissions", permissions);
        return headers;
    }

}
