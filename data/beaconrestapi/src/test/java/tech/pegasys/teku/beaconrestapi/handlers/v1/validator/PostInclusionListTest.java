package tech.pegasys.teku.beaconrestapi.handlers.v1.validator;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerTest;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider;
import tech.pegasys.teku.spec.datastructures.operations.InclusionList;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;
import tech.pegasys.teku.spec.util.DataStructureUtil;

import java.io.IOException;
import java.util.List;

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getRequestBodyFromMetadata;
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

//    @Test
//    void shouldReadRequestBody() throws IOException {
//        final InclusionList inclusionList = dataStructureUtil.randomInclusionList();
//        request.setRequestBody(inclusionList);
//        handler.handleRequest(request);
//        Assertions.assertThat(getRequestBodyFromMetadata(handler, data)).isEqualTo(inclusionList);
//    }

    @Test
    void metadata_shouldHandle400() throws JsonProcessingException {
        verifyMetadataErrorResponse(handler, SC_BAD_REQUEST);
    }

    @Test
    void metadata_shouldHandle500() throws JsonProcessingException {
        verifyMetadataErrorResponse(handler, SC_INTERNAL_SERVER_ERROR);
    }

}