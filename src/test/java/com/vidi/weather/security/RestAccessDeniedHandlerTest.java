package com.vidi.weather.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class RestAccessDeniedHandlerTest {

    @Mock
    private HttpServletRequest request;

    private final RestAccessDeniedHandler handler =
            new RestAccessDeniedHandler(new ObjectMapper().registerModule(new JavaTimeModule()));

    @Test
    void writesNormalizedForbiddenErrorBody() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/weather");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(request, response, new AccessDeniedException("denied"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("\"status\":403").contains("permission");
    }
}
