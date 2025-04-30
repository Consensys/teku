package tech.pegasys.teku.beaconrestapi.v1.validator;

import com.fasterxml.jackson.core.JsonProcessingException;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beacon.sync.events.SyncState;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v1.validator.GetInclusionListCommitteeDuties;
import tech.pegasys.teku.beaconrestapi.handlers.v1.validator.PostInclusionList;
import tech.pegasys.teku.ethereum.json.types.validator.InclusionListDuties;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.operations.SignedInclusionList;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.SubmitDataError;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;

public class PostInclusionListIntegrationTest extends AbstractDataBackedRestAPIIntegrationTest {
    private DataStructureUtil dataStructureUtil;

    @BeforeEach
    void setup() {
        startRestAPIAtGenesis(SpecMilestone.EIP7805);
        dataStructureUtil = new DataStructureUtil(spec);
    }

    @Test
    void shouldReturnOk() throws IOException {
        final SignedInclusionList signedInclusionList = dataStructureUtil.randomSignedInclusionList();
        when(validatorApiChannel.sendSignedInclusionLists(any()))
                .thenReturn(SafeFuture.completedFuture(List.of()));

        final SignedInclusionList request = signedInclusionList;

        try (Response response =
                     post(
                             PostInclusionList.ROUTE,
                             JsonUtil.serialize(request,
                                     signedInclusionList.getSchema().getJsonTypeDefinition()),
                             Optional.of("eip7805"))) {

            assertThat(response.code()).isEqualTo(SC_OK);
        }
    }

    @Test
    void postEmptyBodyShouldReturnBadRequest() throws IOException {

        try (Response response =
                     post(
                             PostInclusionList.ROUTE,
                             "",
                             Optional.of("eip7805"))) {

            assertThat(response.code()).isEqualTo(SC_BAD_REQUEST);
            verifyNoInteractions(validatorApiChannel);
        }
    }

    @Test
    void postInvalidBodyShouldReturnBadRequest() throws IOException {
        try (Response response =
                     post(
                             PostInclusionList.ROUTE,
                             "invalid body",
                             Optional.of("eip7805"))) {

            assertThat(response.code()).isEqualTo(SC_BAD_REQUEST);
            verifyNoInteractions(validatorApiChannel);
        }
    }

}
