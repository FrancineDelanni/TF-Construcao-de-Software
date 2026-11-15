package br.pucrs.pulsepass.iam;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class IamSecurity {
    @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth.requestMatchers("/api/iam/**", "/actuator/health", "/actuator/health/**").permitAll().anyRequest().denyAll())
            .headers(headers -> headers.contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'")))
            .build();
    }
    @Bean JwtDecoder oidcDecoder(IamProperties config) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(config.internalOidc() + "/certs").build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(config.issuer()));
        return decoder;
    }
}
