package ru.mcs.aiproxy.filter;

import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;
import ru.mcs.aiproxy.config.AppProperties;

@Component
public class AuthFilter implements WebFilter {

    private static final String API_KEY_HEADER = "X-API-Key";

    private final AppProperties appProperties;


    public AuthFilter(
            AppProperties appProperties
    ) {
        this.appProperties = appProperties;
    }


    @Override
    public Mono<Void> filter(
            ServerWebExchange exchange,
            WebFilterChain chain
    ) {

        String path =
                exchange.getRequest()
                        .getPath()
                        .value();

        if ("/actuator/health".equals(path)) {
            return chain.filter(exchange);
        }

        AppProperties.Security security =
                appProperties.getSecurity();

        if (security == null || !security.isEnabled()) {
            return chain.filter(exchange);
        }

        String configuredToken =
                security.getToken();

        if (configuredToken == null || configuredToken.isBlank()) {
            return unauthorized(
                    exchange,
                    "Proxy token is not configured"
            );
        }

        String bearerToken =
                extractBearerToken(
                        exchange.getRequest()
                                .getHeaders()
                                .getFirst(HttpHeaders.AUTHORIZATION)
                );

        String apiKey =
                exchange.getRequest()
                        .getHeaders()
                        .getFirst(API_KEY_HEADER);

        if (configuredToken.equals(bearerToken) ||
                configuredToken.equals(apiKey)) {
            return chain.filter(exchange);
        }

        return unauthorized(
                exchange,
                "Unauthorized"
        );

    }


    private String extractBearerToken(
            String authorization
    ) {

        if (authorization == null) {
            return null;
        }

        if (!authorization.startsWith("Bearer ")) {
            return null;
        }

        return authorization.substring(7);

    }


    private Mono<Void> unauthorized(
            ServerWebExchange exchange,
            String message
    ) {

        exchange.getResponse()
                .setStatusCode(HttpStatus.UNAUTHORIZED);

        exchange.getResponse()
                .getHeaders()
                .setContentType(MediaType.APPLICATION_JSON);

        byte[] body =
                ("{\"error\":\"" + message + "\"}")
                        .getBytes(StandardCharsets.UTF_8);

        return exchange.getResponse()
                .writeWith(
                        Mono.just(
                                exchange.getResponse()
                                        .bufferFactory()
                                        .wrap(body)
                        )
                );

    }

}