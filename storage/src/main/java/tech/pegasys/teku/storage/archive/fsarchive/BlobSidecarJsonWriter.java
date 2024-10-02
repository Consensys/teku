package tech.pegasys.teku.storage.archive.fsarchive;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;

import java.io.IOException;
import java.io.OutputStream;

public class BlobSidecarJsonWriter {

    private final ObjectMapper objectMapper;

    public BlobSidecarJsonWriter() {
        objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public void writeBlobSidecar(final OutputStream out, final BlobSidecar blobSidecar) throws IOException {
        objectMapper.writeValue(out, blobSidecar);
    }
}
