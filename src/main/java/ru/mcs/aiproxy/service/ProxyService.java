package ru.mcs.aiproxy.service;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import ru.mcs.aiproxy.model.ProxyRequest;

import java.net.URI;

@Service
public class ProxyService {

    private final WebClient webClient;

    private final UrlBuilderService urlBuilderService;


    public ProxyService(
            WebClient webClient,
            UrlBuilderService urlBuilderService
    ) {
        this.webClient = webClient;
        this.urlBuilderService = urlBuilderService;
    }


    public Mono<ServerResponse> forward(
            ProxyRequest request
    ) {

        URI uri = urlBuilderService.build(request);


        WebClient.RequestBodySpec clientRequest =
                webClient
                        .method(request.method())
                        .uri(uri);


        copyHeaders(
                request.headers(),
                clientRequest
        );


        applyAuthentication(
                request,
                clientRequest
        );


        return clientRequest
                .body(
                        request.body(),
                        DataBuffer.class
                )
                .exchangeToMono(response ->
                        response.bodyToMono(byte[].class)
                                .defaultIfEmpty(new byte[0])
                                .flatMap(bodyBytes -> {

                                    // Превращаем байты в строку — JSON-текст
                                    String body = new String(bodyBytes);

                                    ServerResponse.BodyBuilder builder =
                                            ServerResponse.status(
                                                    response.statusCode()
                                            );

                                    // Копируем все заголовки, кроме тех, что мешают
                                    response.headers()
                                            .asHttpHeaders()
                                            .forEach((name, values) -> {

                                                if (HttpHeaders.CONTENT_LENGTH.equalsIgnoreCase(name))
                                                    return;

                                                if (HttpHeaders.TRANSFER_ENCODING.equalsIgnoreCase(name))
                                                    return;

                                                if (HttpHeaders.CONTENT_ENCODING.equalsIgnoreCase(name))
                                                    return;

                                                values.forEach(value ->
                                                        builder.header(name, value)
                                                );

                                            });

                                    // Гарантируем, что Content-Type — JSON
                                    builder.header(
                                            HttpHeaders.CONTENT_TYPE,
                                            "application/json"
                                    );

                                    return builder.bodyValue(body);

                                })
                );

    }



    private void copyHeaders(
            HttpHeaders source,
            WebClient.RequestBodySpec target
    ) {

        source.forEach(
                (name, values) -> {

                    if (HttpHeaders.HOST.equalsIgnoreCase(name))
                        return;

                    if (HttpHeaders.CONTENT_LENGTH.equalsIgnoreCase(name))
                        return;

                    if (HttpHeaders.ACCEPT_ENCODING.equalsIgnoreCase(name))
                        return;

                    values.forEach(
                            value ->
                                    target.header(
                                            name,
                                            value
                                    )
                    );

                }
        );

    }



    private void applyAuthentication(
            ProxyRequest request,
            WebClient.RequestBodySpec target
    ) {

        var auth =
                request.provider()
                        .getAuth();


        if (auth == null ||
                auth.getType() == null)
            return;


        switch (auth.getType()) {

            case QUERY -> {

                // уже добавлено в URI через UrlBuilderService

            }

            case BEARER -> {

                target.header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + auth.getValue()
                );

            }

            case HEADER -> {

                target.header(
                        auth.getHeader(),
                        auth.getValue()
                );

            }

        }

    }

}