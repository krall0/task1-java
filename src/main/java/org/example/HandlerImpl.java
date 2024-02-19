package org.example;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static java.util.concurrent.TimeUnit.SECONDS;

public class HandlerImpl implements Handler {

    private static final int TIMEOUT_SEC = 15;

    private final Client client;

    public HandlerImpl(Client client) {
        this.client = client;
    }

    @Override
    public ApplicationStatusResponse performOperation(String id) {

        AtomicInteger failCounter = new AtomicInteger();

        CompletableFuture<Response.Success> srv1resp = this.getStatus(id, failCounter, client::getApplicationStatus1);
        CompletableFuture<Response.Success> srv2resp = this.getStatus(id, failCounter, client::getApplicationStatus2);

        CompletableFuture<ApplicationStatusResponse> appResp = srv1resp.applyToEither(srv2resp, this::mapToAppResp);

        try {
            return appResp.get(TIMEOUT_SEC, SECONDS);
        } catch (Exception e) {
            return new ApplicationStatusResponse.Failure(null, failCounter.get());
        }
    }

    ApplicationStatusResponse mapToAppResp(Response.Success r) {
        return new ApplicationStatusResponse.Success(r.applicationId(), r.applicationStatus());
    }

    CompletableFuture<Response.Success> getStatus(String id, AtomicInteger failCounter, Function<String, Response> srvCall) {
        return CompletableFuture.supplyAsync(() -> {

            Response response = srvCall.apply(id);

            while (response instanceof Response.RetryAfter delay) {
                try {
                    Thread.sleep(delay.delay().toMillis());
                    response = srvCall.apply(id);
                } catch (InterruptedException e) {
                    throw new RuntimeException();
                }
            }

            if (response instanceof Response.Failure) {
                failCounter.getAndIncrement();
                throw new RuntimeException();
            }

            return (Response.Success) response;
        });
    }
}
