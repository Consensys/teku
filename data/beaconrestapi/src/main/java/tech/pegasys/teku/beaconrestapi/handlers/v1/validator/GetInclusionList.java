package tech.pegasys.teku.beaconrestapi.handlers.v1.validator;

import com.fasterxml.jackson.core.JsonProcessingException;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.SyncDataProvider;
import tech.pegasys.teku.api.ValidatorDataProvider;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.execution.Transaction;
import tech.pegasys.teku.spec.datastructures.execution.TransactionSchema;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeContribution;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeContributionSchema;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsAltair;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsEip7805;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.EPOCH_PARAMETER;
import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.SLOT_PARAMETER;
import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.ETH_EXECUTION_TRANSACTION_TYPE;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR_REQUIRED;

public class GetInclusionList extends RestApiEndpoint {
    public static final String ROUTE = "/eth/v1/validator/inclusion_list/{epoch}";

    private final ValidatorDataProvider validatorDataProvider;
    private final SyncDataProvider syncDataProvider;

    public GetInclusionList(final DataProvider provider) {
        this(provider.getSyncDataProvider(), provider.getValidatorDataProvider());
    }

    public GetInclusionList(final SyncDataProvider syncDataProvider, final ValidatorDataProvider validatorDataProvider) {
        super(
            EndpointMetadata.get(ROUTE)
                .operationId("produceInclusionList")
                .summary("Produce an inclusion list")
                .description("Requests the beacon node to produce an inclusion list")
                .tags(TAG_VALIDATOR, TAG_VALIDATOR_REQUIRED)
                .pathParam(SLOT_PARAMETER.withDescription("The slot for which an inclusion list should be created."))
                .response(SC_OK, "Request successful", ETH_EXECUTION_TRANSACTION_TYPE)
                .build());
        this.syncDataProvider = syncDataProvider;
        this.validatorDataProvider = validatorDataProvider;
    }

    @Override
    public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
        if (!validatorDataProvider.isStoreAvailable() || syncDataProvider.isSyncing()) {
            request.respondError(SC_SERVICE_UNAVAILABLE, SERVICE_UNAVAILABLE);
            return;
        }

        final UInt64 slot = request.getPathParameter(SLOT_PARAMETER);

        final SafeFuture<Optional<List<Transaction>>> future = validatorDataProvider.getInclusionList(slot);

        request.respondAsync(
                future.thenApply(
                        maybeTransactions ->
                                maybeTransactions
                                        .map(AsyncApiResponse::respondOk)
                                        .orElseGet(AsyncApiResponse::respondNotFound)));

    }


}
