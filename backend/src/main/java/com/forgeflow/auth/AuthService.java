package com.forgeflow.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.forgeflow.auth.user.SysUser;
import com.forgeflow.auth.user.UserMapper;

@Service
public class AuthService {
    private static final Duration TOKEN_TTL = Duration.ofMinutes(30);

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final Clock clock;

    @Autowired
    public AuthService(UserMapper userMapper, PasswordEncoder passwordEncoder, JwtEncoder jwtEncoder) {
        this(userMapper, passwordEncoder, jwtEncoder, Clock.systemUTC());
    }

    AuthService(UserMapper userMapper, PasswordEncoder passwordEncoder, JwtEncoder jwtEncoder, Clock clock) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.clock = clock;
    }

    public LoginResponse login(String username, String password) {
        SysUser user = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, username)
                .eq(SysUser::isEnabled, true)
                .last("LIMIT 1"));
        if (user == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("forgeflow")
                .subject(user.getUsername())
                .issuedAt(now)
                .expiresAt(now.plus(TOKEN_TTL))
                .claim("role", user.getRoleCode())
                .claim("authorities", List.of("ROLE_" + user.getRoleCode()))
                .claim("displayName", user.getDisplayName())
                .build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new LoginResponse("Bearer", token, TOKEN_TTL.toSeconds(), user.getRoleCode(), user.getDisplayName());
    }
}
