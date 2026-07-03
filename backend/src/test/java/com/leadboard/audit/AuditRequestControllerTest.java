package com.leadboard.audit;

import com.leadboard.auth.SessionRepository;
import com.leadboard.config.AppProperties;
import com.leadboard.config.ObservabilityMetrics;
import com.leadboard.telegram.TelegramService;
import com.leadboard.tenant.TenantRepository;
import com.leadboard.tenant.TenantUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Covers the F84 code-review fix adding {@code @NotBlank} to {@code name}/{@code contact} on
 * {@link AuditRequestController.AuditRequest} — this is a public (no auth) landing-page form
 * that previously accepted a completely empty body and forwarded it to Telegram.
 */
@WebMvcTest(AuditRequestController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuditRequestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TelegramService telegramService;

    @MockBean
    private SessionRepository sessionRepository;
    @MockBean
    private AppProperties appProperties;
    @MockBean
    private TenantUserRepository tenantUserRepository;
    @MockBean
    private TenantRepository tenantRepository;
    @MockBean
    private ObservabilityMetrics observabilityMetrics;

    @Test
    void submit_missingNameAndContact_isBadRequest() throws Exception {
        mockMvc.perform(post("/api/audit-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(telegramService);
    }

    @Test
    void submit_blankContact_isBadRequest() throws Exception {
        mockMvc.perform(post("/api/audit-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ivan\",\"contact\":\"   \"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(telegramService);
    }

    @Test
    void submit_validRequest_sendsTelegramNotification() throws Exception {
        when(telegramService.sendMessage(anyString())).thenReturn(true);

        mockMvc.perform(post("/api/audit-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ivan\",\"contact\":\"@ivan\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(telegramService).sendMessage(anyString());
    }
}
