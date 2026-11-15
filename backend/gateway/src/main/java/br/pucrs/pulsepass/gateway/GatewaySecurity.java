package br.pucrs.pulsepass.gateway;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.*;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;
@Configuration
public class GatewaySecurity {
 @Bean @Order(0) SecurityFilterChain gatewayChain(HttpSecurity http,@Value("${pulsepass.gateway-key}") String key)throws Exception {
  return http.securityMatcher("/**").csrf(c->c.disable()).sessionManagement(c->c.sessionCreationPolicy(org.springframework.security.config.http.SessionCreationPolicy.STATELESS)).authorizeHttpRequests(c->c.anyRequest().permitAll()).addFilterBefore(new OncePerRequestFilter(){
   protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException {
    if(request.getRequestURI().equals("/actuator/health") || request.getRequestURI().startsWith("/actuator/health/")){chain.doFilter(request,response);return;}
    String supplied=request.getHeader("X-Gateway-Key");
    if(supplied==null || !MessageDigest.isEqual(key.getBytes(StandardCharsets.UTF_8),supplied.getBytes(StandardCharsets.UTF_8))){response.setStatus(401);response.setContentType("application/json");response.getWriter().write("{\"message\":\"Chave do gateway inválida\"}");return;}chain.doFilter(request,response);
   }
  },UsernamePasswordAuthenticationFilter.class).build();
 }
}
