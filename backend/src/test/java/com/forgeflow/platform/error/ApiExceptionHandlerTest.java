package com.forgeflow.platform.error;

import com.forgeflow.auth.InvalidCredentialsException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionHandlerTest {
    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void invalidCredentialsAreReportedAsUnauthorized() {
        var response = handler.invalidCredentials(new InvalidCredentialsException());
        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(response.getProperties()).containsEntry("code", "INVALID_CREDENTIALS");
    }
}
