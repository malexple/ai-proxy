package ru.mcs.aiproxy.config;

public class ProviderProperties {

    private String baseUrl;

    private AuthProperties auth = new AuthProperties();

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public AuthProperties getAuth() {
        return auth;
    }

    public void setAuth(AuthProperties auth) {
        this.auth = auth;
    }

}