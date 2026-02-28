package live.omnisource.tessera.security.oidc;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;

/**
 * OIDC-only security configuration.
 *
 * When the "oidc" profile is active we rely on oauth2Login + JWT/OIDC authentication.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@Profile("oidc")
public class OidcSecurityConfig {

    @Bean
    SecurityFilterChain oidcSecurityFilterChain(
            HttpSecurity http,
            ClientRegistrationRepository clientRegistrationRepository
    ) throws Exception {
        return http
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**", "/actuator/**"))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/webjars/**",
                                "/login"
                        ).permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        // tighten actuator later with a dedicated ops permission mapping
                        .requestMatchers("/actuator/**").hasAuthority("TESSERA_OPS_VIEW")
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().authenticated()
                )
                .oauth2Login(Customizer.withDefaults())
                .oauth2Client(Customizer.withDefaults())
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessHandler(oidcLogoutSuccessHandler(clientRegistrationRepository))
                )
                .build();
    }

    private LogoutSuccessHandler oidcLogoutSuccessHandler(ClientRegistrationRepository clientRegistrationRepository) {
        OidcClientInitiatedLogoutSuccessHandler handler =
                new OidcClientInitiatedLogoutSuccessHandler(clientRegistrationRepository);
        handler.setPostLogoutRedirectUri("{baseUrl}/");
        return handler;
    }

    @Bean
    public WebSecurityCustomizer oidcWebSecurityCustomizer() {
        return web -> web.ignoring()
                .requestMatchers("/images/**", "/css/**", "/js/**");
    }
}
