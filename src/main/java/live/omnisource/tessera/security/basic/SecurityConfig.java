package live.omnisource.tessera.security.basic;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration for the application.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@Profile("dev")
public class SecurityConfig {

    @Bean
    @Order(2)
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher("/**")
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**", "/actuator/**"))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/login",
                                "/webjars/**"
                        ).permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/", true)
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .permitAll()
                )
                .httpBasic(Customizer.withDefaults())
                .build();
    }

    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return web -> web.ignoring()
                .requestMatchers("/images/**", "/css/**", "/js/**");
    }

    /**
     * Creates and configures an in-memory {@code UserDetailsService} for the "dev" profile.
     * The service includes a single user with the username "admin", password "admin",
     * and roles "USER" and "ADMIN".
     *
     * @return an instance of {@code UserDetailsService} containing the configured in-memory user.
     */
    @Bean
    public UserDetailsService userDetailsService() {
        UserDetails user = User
                .withUsername("admin")
                .password("{noop}admin")
                // "admin" is intentionally the superuser in non-OIDC profiles.
                // Roles -> ROLE_* authorities.
                .roles("USER", "ADMIN", "TESSERA_PLATFORM_ADMIN", "TESSERA_SECURITY_ADMIN")
                // Permissions -> direct authorities (used by @PreAuthorize and Thymeleaf sec:authorize).
                .authorities(
                        "TESSERA_OPS_VIEW",
                        "TESSERA_OPS_ADMIN",
                        "TESSERA_RBAC_VIEW",
                        "TESSERA_RBAC_EDIT"
                )
                .build();
        return new InMemoryUserDetailsManager(user);
    }
}
