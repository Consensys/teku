package tech.pegasys.teku.beaconrestapi.handlers.v1.validator;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerTest;
import tech.pegasys.teku.spec.datastructures.operations.InclusionList;

import java.io.IOException;
import java.util.List;

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getRequestBodyFromMetadata;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataErrorResponse;

class PostInclusionListTest extends AbstractMigratedBeaconHandlerTest {

    final InclusionList inclusionList = dataStructureUtil.randomInclusionList();

    @BeforeEach
    void setup() {
        setHandler(new PostInclusionList(syncDataProvider, validatorDataProvider));
        request.setRequestBody(inclusionList);
    }

    @Test
    void shouldReadRequestBody() throws IOException {
        final String data = "[\"1\"]";
        Assertions.assertThat(getRequestBodyFromMetadata(handler, data)).isEqualTo(inclusionList);
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