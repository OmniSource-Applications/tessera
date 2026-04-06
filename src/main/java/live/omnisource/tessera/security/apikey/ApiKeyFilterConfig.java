package live.omnisource.tessera.security.apikey;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import live.omnisource.tessera.apikey.ApiKeyFeatureToggle;
import live.omnisource.tessera.apikey.ApiKeyRateLimiter;
import live.omnisource.tessera.apikey.ApiKeyService;

/**
 * Provides the API key authentication filter as a managed bean.
 *
 * <p>The filter is NOT auto-registered in the filter chain — each security configuration (dev,
 * OIDC) must explicitly add it via {@code .addFilterBefore(apiKeyFilter,
 * UsernamePasswordAuthenticationFilter.class)}.
 */
@Configuration
public class ApiKeyFilterConfig {

  @Bean
  public ApiKeyAuthenticationFilter apiKeyAuthenticationFilter(
      ApiKeyService apiKeyService,
      ApiKeyRateLimiter rateLimiter,
      ApiKeyFeatureToggle featureToggle) {
    return new ApiKeyAuthenticationFilter(apiKeyService, rateLimiter, featureToggle);
  }
}
