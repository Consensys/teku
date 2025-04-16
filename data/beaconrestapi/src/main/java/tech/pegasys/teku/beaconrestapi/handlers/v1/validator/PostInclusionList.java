package tech.pegasys.teku.beaconrestapi.handlers.v1.validator;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.commons.lang3.NotImplementedException;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.SyncDataProvider;
import tech.pegasys.teku.api.ValidatorDataProvider;
import tech.pegasys.teku.spec.datastructures.operations.InclusionList;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.SignedInclusionList;
import tech.pegasys.teku.spec.datastructures.operations.SignedInclusionListSchema;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;

import java.util.Optional;
import java.util.function.Function;

import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.EPOCH_PARAMETER;
import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.ETH_CONSENSUS_VERSION_TYPE;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR_REQUIRED;

public class PostInclusionList extends RestApiEndpoint {

    public static final String ROUTE = "/eth/v1/validator/inclusion_list";
    private final ValidatorDataProvider validatorDataProvider;
    private final SchemaDefinitionCache schemaDefinitionCache;

    public PostInclusionList(DataProvider dataProvider, final SchemaDefinitionCache schemaDefinitionCache) {
        this(dataProvider.getValidatorDataProvider(), schemaDefinitionCache);
    }

    public PostInclusionList(final ValidatorDataProvider validatorDataProvider, final SchemaDefinitionCache schemaDefinitionCache) {
        super(
                EndpointMetadata.get(ROUTE)
                        .operationId("publishInclusionList")
                        .summary("Publish an inclusion list")
                        .description("Verifies given inclusion list and publishes it on appropriate gossipsub topic.")
                        .tags(TAG_VALIDATOR, TAG_VALIDATOR_REQUIRED)
                        .headerRequired(ETH_CONSENSUS_VERSION_TYPE.withDescription("The active consensus version to which the inclusion list being submitted belongs."))
                        .requestBodyType(getRequestType(schemaDefinitionCache))
                        .response(SC_OK, "Request successful")
                        .withBadRequestResponse(Optional.of("Invalid inclusion list"))
                        .withInternalErrorResponse()
                        .build());
        this.validatorDataProvider = validatorDataProvider;
        this.schemaDefinitionCache = schemaDefinitionCache;
    }


    @Override
    public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
        throw new NotImplementedException();
    }

    private static DeserializableTypeDefinition<InclusionList> getRequestType(final SchemaDefinitionCache schemaDefinitionCache) {
        return schemaDefinitionCache.getSchemaDefinition(SpecMilestone.EIP7805)
                .toVersionEip7805().orElseThrow()
                .getInclusionListSchema()
                .getJsonTypeDefinition();
    }

}
