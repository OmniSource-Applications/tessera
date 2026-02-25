package live.omnisource.tessera.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Controller for handling authentication-related requests.
 */
@Controller
public class AuthController {

    /**
     * Handles the login request and returns the login view.
     *
     * @return The login view name
     */
    @GetMapping("/login")
    public String login() {
        return "login";
    }
}
