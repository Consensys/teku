package tech.pegasys.teku.storage.archive;

import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;

import java.io.IOException;

public interface DataArchiveWriterFactory {

    DataArchiveWriter<BlobSidecar> getBlobSidecarWriter() throws IOException;
}
