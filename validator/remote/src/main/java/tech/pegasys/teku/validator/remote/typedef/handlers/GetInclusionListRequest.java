package tech.pegasys.teku.validator.remote.typedef.handlers;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import tech.pegasys.teku.ethereum.json.types.SharedApiTypes;
import tech.pegasys.teku.ethereum.json.types.validator.SyncCommitteeSelectionProof;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigEip7805;
import tech.pegasys.teku.spec.datastructures.execution.Transaction;
import tech.pegasys.teku.spec.datastructures.execution.TransactionSchema;
import tech.pegasys.teku.spec.datastructures.operations.InclusionListSchema;
import tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod;
import tech.pegasys.teku.validator.remote.typedef.ResponseHandler;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_IMPLEMENTED;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.SLOT;
import static tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition.listOf;

public class GetInclusionListRequest extends AbstractTypeDefRequest{

    final Spec spec;

    public GetInclusionListRequest(final Spec spec, final HttpUrl baseEndpoint, final OkHttpClient okHttpClient) {
        super(baseEndpoint, okHttpClient);
        this.spec = spec;
    }

    public Optional<List<Transaction>> submit(UInt64 slot) {
        final UInt64 epoch = spec.computeEpochAtSlot(slot);
        final SpecConfigEip7805 specConfig = spec.getSpecConfig(epoch).toVersionEip7805().orElseThrow();
        return get(
                ValidatorApiMethod.GET_INCLUSION_LIST,
                Map.of(),
                Map.of(SLOT, slot.toString()),
                new ResponseHandler<>(
                        SharedApiTypes.withDataWrapper(
                                "GetInclusionListResponse", listOf(new TransactionSchema(specConfig).getJsonTypeDefinition())))
                        .withHandler(SC_NOT_IMPLEMENTED, (request, response) -> Optional.empty())
                        .withHandler(SC_SERVICE_UNAVAILABLE, (request, response) -> Optional.empty()));
    }

}
