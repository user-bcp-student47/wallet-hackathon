package pe.wallet.onboarding;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.util.Map;

/** Levanta un mock HTTP que reemplaza al mock KYC real durante las pruebas. */
public class WireMockResource implements QuarkusTestResourceLifecycleManager {

    private static WireMockServer server;

    public static WireMockServer server() {
        return server;
    }

    @Override
    public Map<String, String> start() {
        server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();
        return Map.of("onboarding.mock.base-url", server.baseUrl());
    }

    @Override
    public void stop() {
        if (server != null) {
            server.stop();
        }
    }
}
