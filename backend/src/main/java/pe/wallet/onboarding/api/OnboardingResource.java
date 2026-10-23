package pe.wallet.onboarding.api;

import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import pe.wallet.onboarding.api.OnboardingDtos.DecisionRequest;
import pe.wallet.onboarding.api.OnboardingDtos.EventExport;
import pe.wallet.onboarding.api.OnboardingDtos.RunView;
import pe.wallet.onboarding.api.OnboardingDtos.StartRunResponse;
import pe.wallet.onboarding.api.OnboardingDtos.SubmitKycRequest;
import pe.wallet.onboarding.api.OnboardingDtos.SummaryView;
import pe.wallet.onboarding.domain.RunEntity;
import pe.wallet.onboarding.service.OnboardingService;

@Path("/api/onboarding")
@Produces(MediaType.APPLICATION_JSON)
public class OnboardingResource {

    private final OnboardingService service;

    public OnboardingResource(OnboardingService service) {
        this.service = service;
    }

    @POST
    @Path("/runs")
    public Response start() {
        RunEntity run = service.start();
        return Response.status(Response.Status.CREATED).entity(new StartRunResponse(run.id, run.createdAt)).build();
    }

    @GET
    @Path("/runs/{id}")
    public RunView get(@PathParam("id") String id) {
        return service.view(id);
    }

    @POST
    @Path("/runs/{id}/steps/{step}")
    public Response completeStep(@PathParam("id") String id, @PathParam("step") String step) {
        service.completeStep(id, step);
        return Response.noContent().build();
    }

    @POST
    @Path("/runs/{id}/kyc")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response submitKyc(@PathParam("id") String id, @Valid SubmitKycRequest request) {
        RunView view = service.submitKyc(id, request.document(), request.scenario());
        return Response.status(Response.Status.CREATED).entity(view).build();
    }

    @POST
    @Path("/runs/{id}/kyc/decision")
    @Consumes(MediaType.APPLICATION_JSON)
    public RunView decide(@PathParam("id") String id, @Valid DecisionRequest request) {
        return service.decide(id, request.decision());
    }

    @POST
    @Path("/runs/{id}/close")
    public Response close(@PathParam("id") String id) {
        service.close(id);
        return Response.noContent().build();
    }

    @GET
    @Path("/summary")
    public SummaryView summary() {
        return service.summary();
    }

    @GET
    @Path("/events")
    public List<EventExport> events() {
        return service.events();
    }
}
