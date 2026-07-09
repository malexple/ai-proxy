package ru.mcs.aiproxy.config;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Security security = new Security();

    private Map<String, ProviderProperties> providers = new LinkedHashMap<>();

    public Security getSecurity() {
        return security;
    }

    public void setSecurity(Security security) {
        this.security = security;
    }

    public Map<String, ProviderProperties> getProviders() {
        return providers;
    }

    public void setProviders(Map<String, ProviderProperties> providers) {
        this.providers = providers;
    }


    public static class Security {

        private boolean enabled = true;

        private String token;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

    }

}