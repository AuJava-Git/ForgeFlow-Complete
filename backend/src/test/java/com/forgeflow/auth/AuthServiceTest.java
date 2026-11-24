package com.forgeflow.auth;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.forgeflow.auth.user.SysUser;
import com.forgeflow.auth.user.UserMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthServiceTest {
    private final UserMapper userMapper = mock(UserMapper.class);
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final SecretKey signingKey = new SecretKeySpec(new byte[32], "HmacSHA256");
    private final JwtEncoder jwtEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(signingKey));
    private final JwtDecoder jwtDecoder = NimbusJwtDecoder.withSecretKey(signingKey).macAlgorithm(MacAlgorithm.HS256).build();
    private final Clock clock = Clock.fixed(Instant.parse("2099-01-01T00:00:00Z"), ZoneOffset.UTC);
    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userMapper, passwordEncoder, jwtEncoder, clock);
    }

    @Test
    void rejectsInvalidCredentials() {
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> authService.login("missing", "incorrect"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void issuesBearerTokenWithRoleAndExpiry() {
        SysUser user = new SysUser();
        user.setUsername("planner");
        user.setDisplayName("计划员");
        user.setPasswordHash(passwordEncoder.encode("local-password"));
        user.setRole("PLANNER");
        user.setEnabled(true);
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(user);

        LoginResponse response = authService.login("planner", "local-password");
        Jwt token = jwtDecoder.decode(response.accessToken());

        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.role()).isEqualTo("PLANNER");
        assertThat(token.getSubject()).isEqualTo("planner");
        assertThat(token.getClaimAsString("role")).isEqualTo("PLANNER");
        assertThat(token.getIssuedAt()).isEqualTo(Instant.parse("2099-01-01T00:00:00Z"));
        assertThat(token.getExpiresAt()).isEqualTo(Instant.parse("2099-01-01T00:30:00Z"));
    }
}
