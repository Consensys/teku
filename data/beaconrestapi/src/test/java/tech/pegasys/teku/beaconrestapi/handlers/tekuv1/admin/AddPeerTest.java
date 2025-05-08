package tech.pegasys.teku.beaconrestapi.handlers.tekuv1.admin;

import org.assertj.core.api.AssertionsForInterfaceTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerTest;
import tech.pegasys.teku.infrastructure.restapi.endpoints.CacheLength;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_SERVICE_UNAVAILABLE;

class AddPeerTest extends AbstractMigratedBeaconHandlerTest {

    @BeforeEach
    public void setup() {
        setHandler(new AddPeer(node));
    }

    @Test
    public void shouldReturnOkWhenAValidListIsProvided() throws Exception {
        handler.handleRequest(request);
        assertThat(request.getResponseCode()).isEqualTo(SC_OK);
        AssertionsForInterfaceTypes.assertThat(request.getCacheLength()).isEqualTo(CacheLength.NO_CACHE);
    }

    @Test
    public void shouldReturnOkWhenQueryParamAndNoRejectedExecutions() throws Exception {
        rejectedExecutionCount = 0;
        request.setOptionalQueryParameter("failOnRejectedCount", "true");
        handler.handleRequest(request);
        assertThat(request.getResponseCode()).isEqualTo(SC_OK);
        AssertionsForInterfaceTypes.assertThat(request.getCacheLength()).isEqualTo(CacheLength.NO_CACHE);
    }

    @Test
    public void shouldReturnServiceNotAvailableWhenQueryParamAndRejectedExecutions()
            throws Exception {
        rejectedExecutionCount = 1;
        request.setOptionalQueryParameter("failOnRejectedCount", "true");
        handler.handleRequest(request);
        assertThat(request.getResponseCode()).isEqualTo(SC_SERVICE_UNAVAILABLE);
        AssertionsForInterfaceTypes.assertThat(request.getCacheLength()).isEqualTo(CacheLength.NO_CACHE);
    }
}