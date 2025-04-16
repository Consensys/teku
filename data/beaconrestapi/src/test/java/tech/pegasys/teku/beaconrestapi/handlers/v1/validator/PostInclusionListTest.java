package tech.pegasys.teku.beaconrestapi.handlers.v1.validator;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerTest;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider;
import tech.pegasys.teku.spec.datastructures.operations.InclusionList;
import tech.pegasys.teku.spec.datastructures.operations.SignedInclusionList;
import tech.pegasys.teku.spec.datastructures.operations.SignedInclusionListSchema;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;
import tech.pegasys.teku.spec.util.DataStructureUtil;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.ETH_CONSENSUS_HEADER_TYPE;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getRequestBodyFromMetadata;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataEmptyResponse;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataErrorResponse;
import static tech.pegasys.teku.spec.SpecMilestone.EIP7805;

public class PostInclusionListTest extends AbstractMigratedBeaconHandlerTest {

    @BeforeEach
    void setUp() {
        spec = TestSpecFactory.createMinimalEip7805();
        dataStructureUtil = new DataStructureUtil(spec);
        schemaDefinitionCache = new SchemaDefinitionCache(spec);
        setHandler(new PostInclusionList(validatorDataProvider, schemaDefinitionCache));

    }

    @Test
    void shouldBeAbleToSubmitSignedInclusionList() throws Exception {
        final SignedInclusionList signedInclusionList = dataStructureUtil.randomSignedInclusionList();
        request.setRequestBody(signedInclusionList);

        when(validatorDataProvider.sendSignedInclusionList(anyList()))
                .thenReturn(SafeFuture.completedFuture(List.of()));

        handler.handleRequest(request);

        assertThat(request.getResponseCode()).isEqualTo(SC_OK);
        assertThat(request.getResponseBody()).isNull();
    }

    @Test
    void shouldReadRequestBody() throws IOException {
        final SignedInclusionList signedInclusionList = dataStructureUtil.randomSignedInclusionList();
        SignedInclusionListSchema signedInclusionListSchema = schemaDefinitionCache.getSchemaDefinition(EIP7805).toVersionEip7805().orElseThrow().getSignedInclusionListSchema();
        String data = JsonUtil.serialize(signedInclusionList,signedInclusionListSchema.getJsonTypeDefinition());

        Assertions.assertThat(getRequestBodyFromMetadata(handler, data)).isEqualTo(signedInclusionList);
    }

    @Test
    void metadata_shouldHandle200() {
        verifyMetadataEmptyResponse(handler, SC_OK);
    }

    @Test
    void metadata_shouldHandle400() throws JsonProcessingException {
        verifyMetadataErrorResponse(handler, SC_BAD_REQUEST);
    }

    @Test
    void metadata_shouldHandle500() throws JsonProcessingException {
        verifyMetadataErrorResponse(handler, SC_INTERNAL_SERVER_ERROR);
    }

}