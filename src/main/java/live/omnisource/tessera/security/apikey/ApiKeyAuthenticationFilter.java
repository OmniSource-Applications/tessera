package live.omnisource.tessera.security.apikey;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import live.omnisource.tessera.apikey.ApiKeyFeatureToggle;
import live.omnisource.tessera.apikey.ApiKeyRateLimiter;
import live.omnisource.tessera.apikey.ApiKeyService;
import lombok.extern.slf4j.Slf4j;

/**
 * Extracts API keys from {@code Authorization: Bearer tsk_...} or {@code X-API-Key: tsk_...}
 * headers, validates them, enforces rate limits, and sets the SecurityContext.
 *
 * <p>This filter only activates when the API key feature is enabled AND the request carries an API
 * key header. If the feature is disabled or no key header is present, the request passes through to
 * the next filter (session/form auth, OIDC, etc.).
 */
@Slf4j
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

  private static final String BEARER_PREFIX = "Bearer ";
  private static final String API_KEY_HEADER = "X-API-Key";

  private final ApiKeyService apiKeyService;
  private final ApiKeyRateLimiter rateLimiter;
  private final ApiKeyFeatureToggle featureToggle;

  public ApiKeyAuthenticationFilter(
      ApiKeyService apiKeyService,
      ApiKeyRateLimiter rateLimiter,
      ApiKeyFeatureToggle featureToggle) {
    this.apiKeyService = apiKeyService;
    this.rateLimiter = rateLimiter;
    this.featureToggle = featureToggle;
  }

  @Override
  public void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {

    String rawKey = extractKey(request);
    if (rawKey == null || !featureToggle.isEnabled()) {
      // No API key present or feature disabled — let other auth mechanisms handle it
      chain.doFilter(request, response);
      return;
    }

    // Validate the key
    var validated = apiKeyService.validate(rawKey);
    if (validated.isEmpty()) {
      writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid or expired API key.");
      return;
    }

    var apiKey = validated.get();

    // Rate limit check
    if (!rateLimiter.tryConsume(apiKey.getId(), apiKey.getRateLimitRpm())) {
      long remaining = rateLimiter.remaining(apiKey.getId());
      response.setHeader("X-RateLimit-Limit", String.valueOf(apiKey.getRateLimitRpm()));
      response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, remaining)));
      response.setHeader("Retry-After", "60");
      writeError(response, 429, "Rate limit exceeded. Try again later.");
      return;
    }

    // Set rate limit headers on success
    long remaining = rateLimiter.remaining(apiKey.getId());
    response.setHeader("X-RateLimit-Limit", String.valueOf(apiKey.getRateLimitRpm()));
    response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, remaining)));

    // Set authentication
    var auth = new ApiKeyAuthenticationToken(apiKey);
    SecurityContextHolder.getContext().setAuthentication(auth);

    log.debug(
        "API key auth successful: key='{}' owner='{}' scopes={}",
        apiKey.getName(),
        apiKey.getOwner(),
        apiKey.getScopes());

    chain.doFilter(request, response);
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    // Only attempt API key auth on /api/** paths
    String path = request.getServletPath();
    return !path.startsWith("/api/");
  }

  private String extractKey(HttpServletRequest request) {
    // Try Authorization: Bearer tsk_...
    String auth = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (auth != null && auth.startsWith(BEARER_PREFIX)) {
      String token = auth.substring(BEARER_PREFIX.length()).trim();
      if (token.startsWith(ApiKeyService.KEY_PREFIX)) {
        return token;
      }
    }

    // Try X-API-Key: tsk_...
    String xApiKey = request.getHeader(API_KEY_HEADER);
    if (xApiKey != null && xApiKey.startsWith(ApiKeyService.KEY_PREFIX)) {
      return xApiKey.trim();
    }

    return null;
  }

  private void writeError(HttpServletResponse response, int status, String message)
      throws IOException {
    response.setStatus(status);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.getWriter().write("{\"error\":\"" + message + "\"}");
  }
}
