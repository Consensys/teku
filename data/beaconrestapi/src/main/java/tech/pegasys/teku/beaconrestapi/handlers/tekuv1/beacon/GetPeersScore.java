package tech.pegasys.teku.beaconrestapi.handlers.tekuv1.beacon;

import static tech.pegasys.teku.beaconrestapi.RestApiConstants.CACHE_NONE;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_OK;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.TAG_V1_NODE;

import io.javalin.core.util.Header;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.NetworkDataProvider;
import tech.pegasys.teku.api.response.v1.node.GetPeerScoresResponse;
import tech.pegasys.teku.api.response.v1.node.Peer;
import tech.pegasys.teku.api.response.v1.node.PeerScore;
import tech.pegasys.teku.api.response.v1.node.PeersResponse;
import tech.pegasys.teku.provider.JsonProvider;

import java.util.List;

public class GetPeersScore implements Handler {
    public static final String ROUTE = "/teku/v1/nodes/peer_scores";
    private final JsonProvider jsonProvider;
    private final NetworkDataProvider network;

    public GetPeersScore(final DataProvider provider, final JsonProvider jsonProvider) {
        this.jsonProvider = jsonProvider;
        this.network = provider.getNetworkDataProvider();
    }

    GetPeersScore(final NetworkDataProvider network, final JsonProvider jsonProvider) {
        this.network = network;
        this.jsonProvider = jsonProvider;
    }

    @OpenApi(
            path = ROUTE,
            method = HttpMethod.GET,
            summary = "Get node peers",
            tags = {TAG_V1_NODE},
            description = "Retrieves data about the node's network peers.",
            responses = {
                    @OpenApiResponse(status = RES_OK, content = @OpenApiContent(from = PeersResponse.class)),
                    @OpenApiResponse(status = RES_INTERNAL_ERROR)
            })
    @Override
    public void handle(@NotNull final Context ctx) throws Exception {
        ctx.header(Header.CACHE_CONTROL, CACHE_NONE);
        List<PeerScore> peerScores = network.getPeerScores();

        ctx.result(jsonProvider.objectToJSON(new GetPeerScoresResponse(peerScores)));
    }
}
