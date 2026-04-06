package live.omnisource.tessera.unit.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import live.omnisource.tessera.apikey.ApiKeyFeatureToggle;
import live.omnisource.tessera.apikey.ApiKeyRateLimiter;
import live.omnisource.tessera.apikey.ApiKeyService;
import live.omnisource.tessera.apikey.entity.ApiKey;
import live.omnisource.tessera.security.apikey.ApiKeyAuthenticationFilter;
import live.omnisource.tessera.security.apikey.ApiKeyAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class ApiKeyAuthenticationFilterTest {

  @Mock ApiKeyService apiKeyService;
  @Mock ApiKeyRateLimiter rateLimiter;
  @Mock ApiKeyFeatureToggle featureToggle;
  @Mock HttpServletRequest request;
  @Mock HttpServletResponse response;
  @Mock FilterChain chain;

  ApiKeyAuthenticationFilter filter;

  @BeforeEach
  void setUp() {
    filter = new ApiKeyAuthenticationFilter(apiKeyService, rateLimiter, featureToggle);
    SecurityContextHolder.clearContext();
  }

  @Test
  void passesThrough_whenNoApiKeyHeader() throws Exception {

    when(request.getHeader("Authorization")).thenReturn(null);
    when(request.getHeader("X-API-Key")).thenReturn(null);

    filter.doFilterInternal(request, response, chain);

    verify(chain).doFilter(request, response);
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  void passesThrough_whenFeatureDisabled() throws Exception {

    when(request.getHeader("Authorization"))
        .thenReturn("Bearer tsk_abcdef1234567890abcdef1234567890");
    when(featureToggle.isEnabled()).thenReturn(false);

    filter.doFilterInternal(request, response, chain);

    verify(chain).doFilter(request, response);
    verify(apiKeyService, never()).validate(any());
  }

  @Test
  void authenticates_withValidBearerToken() throws Exception {
    String rawKey = "tsk_abcdef1234567890abcdef1234567890";
    var apiKey = buildApiKey();

    when(request.getHeader("Authorization")).thenReturn("Bearer " + rawKey);
    when(featureToggle.isEnabled()).thenReturn(true);
    when(apiKeyService.validate(rawKey)).thenReturn(Optional.of(apiKey));
    when(rateLimiter.tryConsume(apiKey.getId(), 60)).thenReturn(true);
    when(rateLimiter.remaining(apiKey.getId())).thenReturn(59L);

    filter.doFilterInternal(request, response, chain);

    verify(chain).doFilter(request, response);
    var auth = SecurityContextHolder.getContext().getAuthentication();
    assertThat(auth).isInstanceOf(ApiKeyAuthenticationToken.class);
    assertThat(auth.isAuthenticated()).isTrue();
  }

  @Test
  void authenticates_withXApiKeyHeader() throws Exception {
    String rawKey = "tsk_abcdef1234567890abcdef1234567890";
    var apiKey = buildApiKey();

    when(request.getHeader("Authorization")).thenReturn(null);
    when(request.getHeader("X-API-Key")).thenReturn(rawKey);
    when(featureToggle.isEnabled()).thenReturn(true);
    when(apiKeyService.validate(rawKey)).thenReturn(Optional.of(apiKey));
    when(rateLimiter.tryConsume(apiKey.getId(), 60)).thenReturn(true);
    when(rateLimiter.remaining(apiKey.getId())).thenReturn(59L);

    filter.doFilterInternal(request, response, chain);

    verify(chain).doFilter(request, response);
  }

  @Test
  void rejects_invalidApiKey() throws Exception {
    String rawKey = "tsk_invalid";
    var writer = new StringWriter();

    when(request.getHeader("Authorization")).thenReturn("Bearer " + rawKey);
    when(featureToggle.isEnabled()).thenReturn(true);
    when(apiKeyService.validate(rawKey)).thenReturn(Optional.empty());
    when(response.getWriter()).thenReturn(new PrintWriter(writer));

    filter.doFilterInternal(request, response, chain);

    verify(response).setStatus(401);
    verify(chain, never()).doFilter(any(), any());
  }

  @Test
  void rejects_whenRateLimited() throws Exception {
    String rawKey = "tsk_abcdef1234567890abcdef1234567890";
    var apiKey = buildApiKey();
    var writer = new StringWriter();

    when(request.getHeader("Authorization")).thenReturn("Bearer " + rawKey);
    when(featureToggle.isEnabled()).thenReturn(true);
    when(apiKeyService.validate(rawKey)).thenReturn(Optional.of(apiKey));
    when(rateLimiter.tryConsume(apiKey.getId(), 60)).thenReturn(false);
    when(rateLimiter.remaining(apiKey.getId())).thenReturn(0L);
    when(response.getWriter()).thenReturn(new PrintWriter(writer));

    filter.doFilterInternal(request, response, chain);

    verify(response).setStatus(429);
    verify(response).setHeader("Retry-After", "60");
    verify(chain, never()).doFilter(any(), any());
  }

  @Test
  void setsRateLimitHeaders_onSuccess() throws Exception {
    String rawKey = "tsk_abcdef1234567890abcdef1234567890";
    var apiKey = buildApiKey();

    when(request.getHeader("Authorization")).thenReturn("Bearer " + rawKey);
    when(featureToggle.isEnabled()).thenReturn(true);
    when(apiKeyService.validate(rawKey)).thenReturn(Optional.of(apiKey));
    when(rateLimiter.tryConsume(apiKey.getId(), 60)).thenReturn(true);
    when(rateLimiter.remaining(apiKey.getId())).thenReturn(55L);

    filter.doFilterInternal(request, response, chain);

    verify(response).setHeader("X-RateLimit-Limit", "60");
    verify(response).setHeader("X-RateLimit-Remaining", "55");
  }

  private ApiKey buildApiKey() {
    var k = new ApiKey();
    k.setId(UUID.randomUUID());
    k.setName("test");
    k.setOwner("admin");
    k.setScopes(List.of("QUERY_READ"));
    k.setRateLimitRpm(60);
    k.setActive(true);
    return k;
  }
}
