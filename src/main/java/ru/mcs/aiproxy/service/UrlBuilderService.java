package ru.mcs.aiproxy.service;

import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;
import ru.mcs.aiproxy.config.AuthenticationType;
import ru.mcs.aiproxy.config.ProviderProperties;
import ru.mcs.aiproxy.model.ProxyRequest;

import java.net.URI;

@Service
public class UrlBuilderService {


    public URI build(
            ProxyRequest request
    ) {

        ProviderProperties provider =
                request.provider();


        UriComponentsBuilder builder =
                UriComponentsBuilder
                        .fromHttpUrl(
                                provider.getBaseUrl()
                        )
                        .path(
                                request.path()
                        );


        /*
         * Копируем query параметры клиента
         *
         * Например:
         *
         * /gemini/v1/models?a=b
         *
         */
        request.query()
                .forEach(
                        (name, values) ->
                                values.forEach(
                                        value ->
                                                builder.queryParam(
                                                        name,
                                                        value
                                                )
                                )
                );


        addAuthentication(
                builder,
                provider
        );


        return builder.build()
                .encode()
                .toUri();

    }


    private void addAuthentication(
            UriComponentsBuilder builder,
            ProviderProperties provider
    ) {

        if (provider.getAuth() == null)
            return;


        if (provider.getAuth().getType()
                != AuthenticationType.QUERY)
            return;


        builder.queryParam(
                provider.getAuth()
                        .getParameter(),

                provider.getAuth()
                        .getValue()
        );

    }

}