package com.forgeflow.auth;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Configuration
public class SecurityConfig {
    @Bean
    SecretKey jwtSigningKey(@Value("${app.jwt.secret}") String encodedSecret) {
        byte[] key = Base64.getDecoder().decode(encodedSecret);
        if (key.length < 32) {
            throw new IllegalArgumentException("APP_JWT_SECRET must decode to at least 32 bytes");
        }
        return new SecretKeySpec(key, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSigningKey));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtSigningKey) {
        return NimbusJwtDecoder.withSecretKey(jwtSigningKey).macAlgorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("authorities");
        authorities.setAuthorityPrefix("");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationConverter converter) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/health", "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/erp/products", "/api/erp/products/*/bom", "/api/erp/production-orders").hasAnyRole("ADMIN", "PLANNER")
                        .requestMatchers(HttpMethod.PUT, "/api/erp/products/*/bom").hasAnyRole("ADMIN", "PLANNER")
                        .requestMatchers(HttpMethod.GET, "/api/erp/**").hasAnyRole("ADMIN", "PLANNER", "APPROVER", "WAREHOUSE", "PURCHASER", "OPERATOR", "QUALITY_INSPECTOR")
                        .requestMatchers(HttpMethod.POST, "/api/oa/production-orders/*/submit").hasAnyRole("ADMIN", "PLANNER")
                        .requestMatchers(HttpMethod.POST, "/api/oa/production-orders/*/decision").hasAnyRole("ADMIN", "APPROVER")
                        .requestMatchers(HttpMethod.GET, "/api/oa/production-orders/pending").hasAnyRole("ADMIN", "APPROVER")
                        .requestMatchers(HttpMethod.GET, "/api/oa/production-orders/*/history").hasAnyRole("ADMIN", "PLANNER", "APPROVER", "WAREHOUSE", "PURCHASER", "OPERATOR", "QUALITY_INSPECTOR")
                        .requestMatchers("/api/oa/**").hasAnyRole("ADMIN", "APPROVER")
                        .requestMatchers("/api/inventory/**").hasAnyRole("ADMIN", "WAREHOUSE")
                        .requestMatchers("/api/purchasing/**").hasAnyRole("ADMIN", "PURCHASER")
                        .requestMatchers("/api/quality/**").hasAnyRole("ADMIN", "QUALITY_INSPECTOR")
                        .requestMatchers("/api/mes/**").hasAnyRole("ADMIN", "OPERATOR")
                        .requestMatchers("/api/dashboard/**").hasAnyRole("ADMIN", "PLANNER", "APPROVER", "WAREHOUSE", "PURCHASER", "OPERATOR", "QUALITY_INSPECTOR")
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(converter)));
        return http.build();
    }
}
