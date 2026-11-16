package br.pucrs.pulsepass.iam.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app")
public record IamProperties(
    String publicUrl,
    String keycloakPublicUrl,
    String keycloakInternalUrl,
    String clientId,
    String clientSecret,
    String sessionEncryptionKey) {
  public String issuer() {
    return keycloakPublicUrl + "/realms/pulsepass";
  }

  public String internalOidc() {
    return keycloakInternalUrl + "/realms/pulsepass/protocol/openid-connect";
  }

  public String callback() {
    return publicUrl + "/api/iam/callback";
  }

  public boolean secureCookies() {
    return publicUrl.startsWith("https://");
  }
}
