package cl.duoc.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;
import org.springframework.http.HttpStatus;

import javax.crypto.SecretKey;

@Component
public class JwtWebMvcFilter implements HandlerFilterFunction<ServerResponse, ServerResponse> {

    private static final Logger logger = LoggerFactory.getLogger(JwtWebMvcFilter.class);

    @Value("${app.jwt.secret}")
    private String secretKey;

    @Override
    public ServerResponse filter(ServerRequest request, HandlerFunction<ServerResponse> next) throws Exception {
        String path = request.path();
        logger.info("Filtrando petición a: {}", path);

        String authHeader = request.headers().firstHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            logger.warn("Token ausente o formato inválido en: {}", path);
            return ServerResponse.status(HttpStatus.UNAUTHORIZED).build();
        }

        String token = authHeader.substring(7);

        try {
            byte[] keyBytes = Decoders.BASE64.decode(secretKey);
            SecretKey key = Keys.hmacShaKeyFor(keyBytes);

            Jws<Claims> claimsJws = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token);

            Claims claims = claimsJws.getPayload();
            String username = claims.getSubject();
            Object roles = claims.get("roles");
            String rolesStr = "";

            if (roles instanceof java.util.Collection<?>) {
                java.util.Collection<?> rolesList = (java.util.Collection<?>) roles;
                rolesStr = rolesList.stream()
                        .map(Object::toString)
                        .collect(java.util.stream.Collectors.joining(","));
            } else if (roles != null) {
                rolesStr = roles.toString();
            }

            logger.info("Token válido para: {}. Usuario: {}, Roles: {}", path, username, rolesStr);

            // Creamos una nueva petición con las cabeceras adicionales
            ServerRequest modifiedRequest = ServerRequest.from(request)
                    .header("X-User-Name", username)
                    .header("X-User-Roles", rolesStr)
                    .build();

            return next.handle(modifiedRequest);
        } catch (Exception e) {
            logger.error("Token inválido para {}: {}", path, e.getMessage());
            return ServerResponse.status(HttpStatus.UNAUTHORIZED).build();
        }
    }
}