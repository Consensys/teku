package tech.pegasys.teku.beaconrestapi.handlers.tekuv1.admin;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.logging.log4j.Level;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.NetworkDataProvider;
import tech.pegasys.teku.api.provider.Peer;
import tech.pegasys.teku.beaconrestapi.BeaconRestApi;
import tech.pegasys.teku.beaconrestapi.JsonTypeDefinitionBeaconRestApi;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.StringBasedPrimitiveTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryNetwork;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_ACCEPTED;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_TEKU;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.STRING_TYPE;

public class AddPeer extends RestApiEndpoint {

    public static final String ROUTE = "/teku/v1/admin/add_peer";
    final NetworkDataProvider networkDataProvider;

    public AddPeer(final DataProvider dataProvider) {
        this(dataProvider.getNetworkDataProvider());
    }

    AddPeer(final NetworkDataProvider networkDataProvider) {
        super(
                EndpointMetadata.post(ROUTE)
                        .operationId("AddPeer")
                        .summary("Add a peer to the node")
                        .description("Adds a peer to the node.")
                        .tags(TAG_TEKU)
                        .requestBodyType(DeserializableTypeDefinition.listOf(STRING_TYPE))
                        .response(SC_OK, "Peer added successfully")
                        .withBadRequestResponse(Optional.of("Invalid peer address"))
                        .withInternalErrorResponse()
                        .build());
        this.networkDataProvider = networkDataProvider;
    }

    @Override
    public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
        DiscoveryNetwork<?> discoveryNetwork =
                networkDataProvider.getDiscoveryNetwork().orElseThrow();
        final List<String> peerAddress = request.getRequestBody();
        if(peerAddress.isEmpty()) {
            request.respondError(SC_BAD_REQUEST, "No peer address provided");
            return;
        }
        try {
            peerAddress.forEach(discoveryNetwork::addStaticPeer);
            request.respondWithCode(SC_OK);
        }
        catch (final IllegalArgumentException e) {
            request.respondError(SC_INTERNAL_SERVER_ERROR,e.getMessage());
        }
    }
}
