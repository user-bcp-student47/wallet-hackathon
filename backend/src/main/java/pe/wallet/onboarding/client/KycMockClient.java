package pe.wallet.onboarding.client;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "kyc-mock")
@Path("/kyc/verifications")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface KycMockClient {

    record CreateBody(String documentType, String document, String scenario) {}

    record DecisionBody(String decision) {}

    @POST
    Response create(CreateBody body);

    @GET
    @Path("/{id}")
    Response get(@PathParam("id") String id);

    @POST
    @Path("/{id}/decision")
    Response decide(@PathParam("id") String id, DecisionBody body);
}
