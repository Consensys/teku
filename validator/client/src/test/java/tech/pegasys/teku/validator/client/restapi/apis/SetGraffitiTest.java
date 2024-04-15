package tech.pegasys.teku.validator.client.restapi.apis;


import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_FORBIDDEN;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_IMPLEMENTED;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NO_CONTENT;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_UNAUTHORIZED;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataEmptyResponse;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataErrorResponse;

class SetGraffitiTest {
    private final SetGraffiti handler = new SetGraffiti();

    @Test
    void metadata_shouldHandle204() {
        verifyMetadataEmptyResponse(handler, SC_NO_CONTENT);
    }

    @Test
    void metadata_shouldHandle400() throws JsonProcessingException {
        verifyMetadataErrorResponse(handler, SC_BAD_REQUEST);
    }

    @Test
    void metadata_shouldHandle401() throws JsonProcessingException {
        verifyMetadataErrorResponse(handler, SC_UNAUTHORIZED);
    }

    @Test
    void metadata_shouldHandle403() throws JsonProcessingException {
        verifyMetadataErrorResponse(handler, SC_FORBIDDEN);
    }

    @Test
    void metadata_shouldHandle500() throws JsonProcessingException {
        verifyMetadataErrorResponse(handler, SC_INTERNAL_SERVER_ERROR);
    }

    @Test
    void metadata_shouldHandle501() throws JsonProcessingException {
        verifyMetadataErrorResponse(handler, SC_NOT_IMPLEMENTED);
    }
}