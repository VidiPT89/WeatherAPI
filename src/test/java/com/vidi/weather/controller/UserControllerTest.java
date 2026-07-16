package com.vidi.weather.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidi.weather.dto.UserPreferences;
import com.vidi.weather.entity.User;
import com.vidi.weather.model.Units;
import com.vidi.weather.security.AuthenticatedUser;
import com.vidi.weather.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserService userService;

    private final User principalUser = new User("test@example.com", "hash", Units.METRIC);
    private final AuthenticatedUser authenticatedUser = new AuthenticatedUser(principalUser);

    @Test
    void returnsCurrentPreferences() throws Exception {
        mockMvc.perform(get("/api/v1/user/preferences").with(user(authenticatedUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.units").value("metric"));
    }

    @Test
    void updatesPreferredUnits() throws Exception {
        User updated = new User("test@example.com", "hash", Units.IMPERIAL);
        when(userService.updatePreferredUnits(eq(principalUser), any())).thenReturn(updated);

        mockMvc.perform(post("/api/v1/user/preferences")
                        .with(user(authenticatedUser))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UserPreferences("imperial"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.units").value("imperial"));
    }

    @Test
    void returns400_whenUnitsIsInvalid() throws Exception {
        mockMvc.perform(post("/api/v1/user/preferences")
                        .with(user(authenticatedUser))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UserPreferences("kelvin"))))
                .andExpect(status().isBadRequest());
    }
}
