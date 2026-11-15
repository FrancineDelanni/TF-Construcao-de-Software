package br.pucrs.pulsepass.delivery.domain;

import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.*;
import java.security.spec.*;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TicketSigner {
 private final PrivateKey privateKey;
 private final PublicKey publicKey;
 public TicketSigner(@Value("${pulsepass.delivery.key-directory:./.local/delivery-keys}") String directory) throws Exception {
  Path dir=Path.of(directory);Files.createDirectories(dir);
  Path file=dir.resolve("ed25519.keypair");
  var encoder=Base64.getEncoder(); var decoder=Base64.getDecoder();
  if(!Files.exists(file)) {
   KeyPair pair=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
   Path temporary=Files.createTempFile(dir,"keypair-",".tmp");
   Files.setPosixFilePermissions(temporary,PosixFilePermissions.fromString("rw-------"));
   Files.writeString(temporary,encoder.encodeToString(pair.getPrivate().getEncoded())+"\n"+encoder.encodeToString(pair.getPublic().getEncoded()));
   try {Files.move(temporary,file,StandardCopyOption.ATOMIC_MOVE);} finally {Files.deleteIfExists(temporary);}
  }
  var lines=Files.readAllLines(file);var factory=KeyFactory.getInstance("Ed25519");
  privateKey=factory.generatePrivate(new PKCS8EncodedKeySpec(decoder.decode(lines.get(0))));
  publicKey=factory.generatePublic(new X509EncodedKeySpec(decoder.decode(lines.get(1))));
 }
 public String sign(String payload) {
  try {
   var signature=Signature.getInstance("Ed25519");signature.initSign(privateKey);signature.update(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
   return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8))+"."+Base64.getUrlEncoder().withoutPadding().encodeToString(signature.sign());
  } catch(GeneralSecurityException ex) {throw new IllegalStateException("Falha de assinatura",ex);}
 }
 public boolean verify(String signed) {
  try {
   var parts=signed.split("\\.");if(parts.length!=2) return false;
   var signature=Signature.getInstance("Ed25519");signature.initVerify(publicKey);signature.update(Base64.getUrlDecoder().decode(parts[0]));
   return signature.verify(Base64.getUrlDecoder().decode(parts[1]));
  } catch(GeneralSecurityException|IllegalArgumentException ex) {return false;}
 }
 public String publicKey() {return Base64.getEncoder().encodeToString(publicKey.getEncoded());}
}
