package tech.pegasys.teku.beaconrestapi.handlers.tekuv1.admin;

import org.assertj.core.api.AssertionsForInterfaceTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerTest;
import tech.pegasys.teku.infrastructure.restapi.endpoints.CacheLength;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryNetwork;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_SERVICE_UNAVAILABLE;

class AddPeerTest extends AbstractMigratedBeaconHandlerTest {

    private final DiscoveryNetwork<?> discoveryNetwork = mock(DiscoveryNetwork.class);

    @BeforeEach
    public void setup() {
        setHandler(new AddPeer(network));
    }

    @Test
    public void shouldReturnOkWhenAValidListIsProvided() throws Exception {
        final List<String> peers = List.of("/ip4/178.128.136.233/udp/9001/p2p/16Uiu2HAmNSjEXNqaFjfLePKk87WZ7QwuTPd1HPEfJEeYnaC3bGZ1");
        request.setRequestBody(peers);
        when(network.getDiscoveryNetwork()).thenReturn(Optional.of(discoveryNetwork));
        handler.handleRequest(request);
        assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    }

    @Test
    public void shouldThrowExceptionForInvalidPeerAddress() throws Exception {
        final List<String> peers = List.of("invalid-peer-address");
        request.setRequestBody(peers);
        when(network.getDiscoveryNetwork()).thenReturn(Optional.of(discoveryNetwork));
        doThrow(new IllegalArgumentException("Invalid peer address")).when(discoveryNetwork).addStaticPeer(any());
        handler.handleRequest(request);
        assertThat(request.getResponseCode()).isEqualTo(SC_INTERNAL_SERVER_ERROR);
    }

    @Test
    public void shouldReturnBadRequestWhenRequestBodyIsEmpty() throws Exception {
        final List<String> peers = List.of();
        request.setRequestBody(peers);
        when(network.getDiscoveryNetwork()).thenReturn(Optional.of(discoveryNetwork));
        handler.handleRequest(request);
        assertThat(request.getResponseCode()).isEqualTo(SC_BAD_REQUEST);
    }


}