package pe.wallet.onboarding.api;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import pe.wallet.onboarding.config.OnboardingConfig;

@Path("/api/config")
@Produces(MediaType.APPLICATION_JSON)
public class ConfigResource {

    private final OnboardingConfig config;

    public ConfigResource(OnboardingConfig config) {
        this.config = config;
    }

    public record ConfigResponse(boolean demoEnabled) {}

    @GET
    public ConfigResponse get() {
        return new ConfigResponse(config.demo().enabled());
    }
}
