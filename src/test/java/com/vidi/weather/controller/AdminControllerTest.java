package com.vidi.weather.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vidi.weather.dto.UserResponse;
import com.vidi.weather.entity.User;
import com.vidi.weather.model.Role;
import com.vidi.weather.model.Units;
import com.vidi.weather.security.AuthenticatedUser;
import com.vidi.weather.service.AdminUserService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminController.class)
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminUserService adminUserService;

    private final AuthenticatedUser adminUser =
            new AuthenticatedUser(new User("admin@example.com", "hash", Units.METRIC).withRole(Role.ADMIN));

    @Test
    void listsEveryUser_forAdmin() throws Exception {
        when(adminUserService.listUsers()).thenReturn(List.of(
                new UserResponse(1L, "admin@example.com", "admin", "metric", Instant.now()),
                new UserResponse(2L, "someone@example.com", "user", "imperial", Instant.now())));

        mockMvc.perform(get("/api/v1/admin/users").with(user(adminUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("admin@example.com"))
                .andExpect(jsonPath("$[1].role").value("user"));
    }

    @Test
    void deletesAUser_forAdmin() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/users/2").with(user(adminUser)).with(csrf()))
                .andExpect(status().isNoContent());

        verify(adminUserService).deleteUser(2L, adminUser.getUser());
    }

    // 403-for-non-admin is asserted in AuthAndSecurityIntegrationTest instead: @WebMvcTest
    // doesn't load the app's SecurityConfig, so the hasRole("ADMIN") rule is never enforced here.
}
