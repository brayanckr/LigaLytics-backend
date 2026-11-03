package com.ligalytics.auth;

import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.ligalytics.exception.ForbiddenException;
import com.ligalytics.exception.UnauthorizedException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Exige sesión en las rutas protegidas. El token se lee <strong>solo</strong> de la cookie {@code HttpOnly}
 * {@value AuthCookies#SESSION_COOKIE}: la cabecera {@code Authorization} se ignora por completo, de modo que un
 * script no pueda presentar un token robado o copiado.
 *
 * <ul>
 *   <li>Sin cookie, o con una cookie que no corresponde a una sesión vigente, aborta con 401 (y, si había cookie,
 *       ordena al navegador que la borre).</li>
 *   <li>Con sesión válida deja el id del usuario en {@code request.setAttribute("userId", id)} (y el token en
 *       {@link #TOKEN_ATTRIBUTE}, para el cierre de sesión). Los controladores de rutas protegidas reciben el id
 *       con {@code @RequestAttribute("userId") Long userId} y no vuelven a comprobar nada.</li>
 *   <li>Como el navegador adjunta la cookie por sí solo, las peticiones que modifican datos deben llevar además la
 *       cabecera {@code X-Requested-With}: un formulario de otro sitio no puede añadirla y un {@code fetch} desde
 *       otro origen necesita pasar el CORS, que solo permite los orígenes configurados. Sin ella, 403.</li>
 * </ul>
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    /** Id ({@link Long}) del usuario autenticado: los controladores lo piden con {@code @RequestAttribute("userId")}. */
    public static final String USER_ID_ATTRIBUTE = "userId";
    /** Token de la sesión actual; solo lo necesita el cierre de sesión para borrar esa sesión. */
    public static final String TOKEN_ATTRIBUTE = "ligalytics.token";
    /** Cabecera que prueba que la petición la hizo código del propio frontend y no un formulario ajeno. */
    public static final String CSRF_HEADER = "X-Requested-With";

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    private final AuthService authService;
    private final AuthCookies cookies;

    public AuthInterceptor(AuthService authService, AuthCookies cookies) {
        this.authService = authService;
        this.cookies = cookies;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String token = AuthCookies.tokenFrom(request);
        Optional<User> user = authService.authenticate(token);
        if (user.isEmpty()) {
            if (token != null) {
                cookies.clear(response);
            }
            throw new UnauthorizedException("Inicia sesión para continuar");
        }
        if (!SAFE_METHODS.contains(request.getMethod().toUpperCase())) {
            String marker = request.getHeader(CSRF_HEADER);
            if (marker == null || marker.isBlank()) {
                throw new ForbiddenException("Falta la cabecera " + CSRF_HEADER);
            }
        }
        request.setAttribute(USER_ID_ATTRIBUTE, user.get().getId());
        request.setAttribute(TOKEN_ATTRIBUTE, token);
        return true;
    }
}
