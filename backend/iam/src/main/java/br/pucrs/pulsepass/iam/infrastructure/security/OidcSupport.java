package br.pucrs.pulsepass.iam.infrastructure.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

public final class OidcSupport {
  private static final SecureRandom RANDOM = new SecureRandom();

  private OidcSupport() {}

  public static boolean equal(String a, String b) {
    return a != null
        && b != null
        && MessageDigest.isEqual(
            a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
  }

  public static String safeReturnTo(String path) {
    return path.startsWith("/")
            && !path.startsWith("//")
            && !path.contains("\\")
            && !path.contains("\r")
            && !path.contains("\n")
        ? path
        : "/";
  }

  public static String random() {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  public static String challenge(String verifier) {
    try {
      return Base64.getUrlEncoder()
          .withoutPadding()
          .encodeToString(
              MessageDigest.getInstance("SHA-256")
                  .digest(verifier.getBytes(StandardCharsets.US_ASCII)));
    } catch (Exception error) {
      throw new IllegalStateException(error);
    }
  }
}
