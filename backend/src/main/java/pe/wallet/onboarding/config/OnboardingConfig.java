package pe.wallet.onboarding.config;

import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "onboarding")
public interface OnboardingConfig {

    Demo demo();

    Mock mock();

    interface Demo {
        boolean enabled();
    }

    interface Mock {
        String baseUrl();
    }
}
