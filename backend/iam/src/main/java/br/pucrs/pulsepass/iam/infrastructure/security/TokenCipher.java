package br.pucrs.pulsepass.iam.infrastructure.security;

import br.pucrs.pulsepass.iam.infrastructure.config.IamProperties;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public final class TokenCipher {
  private static final SecureRandom RANDOM = new SecureRandom();
  private final SecretKeySpec key;

  public TokenCipher(IamProperties config) {
    byte[] bytes = Base64.getDecoder().decode(config.sessionEncryptionKey());
    if (bytes.length != 32)
      throw new IllegalArgumentException(
          "SESSION_ENCRYPTION_KEY must contain 32 base64 encoded bytes");
    key = new SecretKeySpec(bytes, "AES");
  }

  public String encrypt(String text) {
    try {
      byte[] iv = new byte[12];
      RANDOM.nextBytes(iv);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
      byte[] encrypted = cipher.doFinal(text.getBytes(StandardCharsets.UTF_8));
      byte[] output = Arrays.copyOf(iv, iv.length + encrypted.length);
      System.arraycopy(encrypted, 0, output, iv.length, encrypted.length);
      return Base64.getEncoder().encodeToString(output);
    } catch (Exception e) {
      throw new IllegalStateException("Unable to encrypt session");
    }
  }

  public String decrypt(String value) {
    try {
      byte[] input = Base64.getDecoder().decode(value);
      if (input.length < 29) throw new IllegalArgumentException();
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, Arrays.copyOf(input, 12)));
      return new String(cipher.doFinal(input, 12, input.length - 12), StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new IllegalStateException("Invalid encrypted session");
    }
  }
}
