package br.pucrs.pulsepass.common;
import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.*;
class SecurityConfigTest {
 @Test void rejectsWrongAudience() {
  var jwt=Jwt.withTokenValue("fixture").header("alg","RS256").subject("cliente").audience(List.of("other")).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
  assertTrue(SecurityConfig.audienceValidator("pulsepass-api").validate(jwt).hasErrors());
  assertFalse(SecurityConfig.audienceValidator("other").validate(jwt).hasErrors());
 }
 @Test void rejectsExpiredAndWrongIssuer() {
  var jwt=Jwt.withTokenValue("fixture").header("alg","RS256").issuer("https://other").issuedAt(Instant.now().minusSeconds(200)).expiresAt(Instant.now().minusSeconds(120)).build();
  assertTrue(JwtValidators.createDefaultWithIssuer("https://expected").validate(jwt).hasErrors());
 }
}
