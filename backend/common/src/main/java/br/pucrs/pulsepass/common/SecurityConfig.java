package br.pucrs.pulsepass.common;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
@ConditionalOnProperty(name="pulsepass.security.enabled", havingValue="true", matchIfMissing=true)
public class SecurityConfig {
    @Bean
    JwtDecoder jwtDecoder(@Value("${JWT_ISSUER:http://localhost:8081/realms/pulsepass}") String issuer,
            @Value("${JWT_JWK_SET_URI:http://localhost:8081/realms/pulsepass/protocol/openid-connect/certs}") String jwks,
            @Value("${JWT_AUDIENCE:pulsepass-api}") String audience) {
        var decoder = NimbusJwtDecoder.withJwkSetUri(jwks).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer), audienceValidator(audience)));
        return decoder;
    }
    static OAuth2TokenValidator<Jwt> audienceValidator(String audience) {
        return jwt -> jwt.getAudience().contains(audience) ? OAuth2TokenValidatorResult.success()
            : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token","Audience inválida",null));
    }
    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Map<String,Object> realm = jwt.getClaimAsMap("realm_access");
            Object roles = realm == null ? null : realm.get("roles");
            if (!(roles instanceof Collection<?> values)) return List.of();
            return values.stream().filter(String.class::isInstance)
                .map(v -> new SimpleGrantedAuthority("ROLE_"+v)).map(a -> (org.springframework.security.core.GrantedAuthority)a).toList();
        });
        return http.csrf(c -> c.disable()).sessionManagement(c -> c.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(c -> c.requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/api/inventory/internal/**").hasRole("ServiceOrders")
                .anyRequest().hasAnyRole("Cliente","Organizador","Operador","Admin"))
            .oauth2ResourceServer(c -> c.jwt(j -> j.jwtAuthenticationConverter(converter))).build();
    }
}
