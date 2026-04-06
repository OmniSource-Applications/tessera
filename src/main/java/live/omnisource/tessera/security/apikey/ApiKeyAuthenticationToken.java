package live.omnisource.tessera.security.apikey;

import java.util.Collection;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import live.omnisource.tessera.apikey.entity.ApiKey;

/** Spring Security Authentication object for API key-authenticated requests. */
public class ApiKeyAuthenticationToken extends AbstractAuthenticationToken {

  private final ApiKey apiKey;
  private final String rawKey;

  /** Pre-authentication (raw key extracted from header, not yet validated). */
  public ApiKeyAuthenticationToken(String rawKey) {
    super((Collection<? extends GrantedAuthority>) null);
    this.rawKey = rawKey;
    this.apiKey = null;
    setAuthenticated(false);
  }

  /** Post-authentication (validated key with scopes mapped to authorities). */
  public ApiKeyAuthenticationToken(ApiKey apiKey) {
    super(
        apiKey.getScopes().stream()
            .map(scope -> new SimpleGrantedAuthority("SCOPE_" + scope))
            .toList());
    this.apiKey = apiKey;
    this.rawKey = null;
    setAuthenticated(true);
  }

  @Override
  public Object getCredentials() {
    return rawKey;
  }

  @Override
  public Object getPrincipal() {
    return apiKey != null ? apiKey.getOwner() : rawKey;
  }

  public ApiKey getApiKey() {
    return apiKey;
  }
}
