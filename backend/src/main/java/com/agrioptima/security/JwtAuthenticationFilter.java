package com.agrioptima.security;

import com.agrioptima.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Authenticates requests carrying {@code Authorization: Bearer <jwt>}.
 * An invalid token leaves the request unauthenticated; protected endpoints then return 401.
 * The user is re-loaded from the database so deleted accounts lose access immediately.
 * Registered only inside the security chain (see SecurityConfig), not as a standalone servlet filter.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    static final String INVALID_TOKEN_ATTRIBUTE = "agrioptima.invalidToken";
    private static final String BEARER = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER)) {
            String token = header.substring(BEARER.length()).trim();
            var principal = jwtService.verify(token)
                    .flatMap(userRepository::findById)
                    .map(UserPrincipal::from);
            if (principal.isPresent()) {
                var auth = new UsernamePasswordAuthenticationToken(
                        principal.get(), null, principal.get().authorities());
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } else {
                request.setAttribute(INVALID_TOKEN_ATTRIBUTE, Boolean.TRUE);
            }
        }
        chain.doFilter(request, response);
    }
}
