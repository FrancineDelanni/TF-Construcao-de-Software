package br.pucrs.pulsepass.orders.infra;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.*;
import java.net.http.HttpClient;
import java.time.*;
import java.util.*;
@Component
public class RemoteClients {
 private final RestClient http;private final String inventory,gateway,key,tokenUrl,secret;private String token;private Instant refreshAt=Instant.EPOCH;
 public RemoteClients(@Value("${pulsepass.inventory-url}")String inventory,@Value("${pulsepass.gateway-url}")String gateway,@Value("${pulsepass.gateway-key}")String key,@Value("${pulsepass.token-url}")String tokenUrl,@Value("${pulsepass.orders-client-secret}")String secret){
  var factory=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());factory.setReadTimeout(Duration.ofSeconds(3));this.http=RestClient.builder().requestFactory(factory).build();this.inventory=inventory;this.gateway=gateway;this.key=key;this.tokenUrl=tokenUrl;this.secret=secret;
 }
 private synchronized String token(){
  if(token!=null&&Instant.now().isBefore(refreshAt))return token;
  var form=new LinkedMultiValueMap<String,String>();form.add("grant_type","client_credentials");form.add("client_id","orders-service");form.add("client_secret",secret);
  JsonNode response=http.post().uri(tokenUrl).contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(JsonNode.class);
  if(response==null||!response.hasNonNull("access_token"))throw new IllegalStateException("Token de serviço indisponível");token=response.path("access_token").asText();refreshAt=Instant.now().plusSeconds(Math.max(1,response.path("expires_in").asLong(60)-15));return token;
 }
 public JsonNode bind(UUID reservation,UUID order,String owner){return http.post().uri(inventory+"/api/inventory/internal/reservations/"+reservation+"/bind").headers(h->h.setBearerAuth(token())).body(Map.of("orderId",order,"customerId",owner)).retrieve().body(JsonNode.class);}
 public JsonNode reservation(UUID reservation){return http.get().uri(inventory+"/api/inventory/internal/reservations/"+reservation).headers(h->h.setBearerAuth(token())).retrieve().body(JsonNode.class);}
 public JsonNode payment(UUID attempt,String outcome){
  try{return http.get().uri(gateway+"/payments/"+attempt).header("X-Gateway-Key",key).retrieve().body(JsonNode.class);}catch(HttpClientErrorException.NotFound absent){return http.post().uri(gateway+"/payments").header("X-Gateway-Key",key).body(Map.of("attemptId",attempt,"outcome",outcome)).retrieve().body(JsonNode.class);}
 }
}
