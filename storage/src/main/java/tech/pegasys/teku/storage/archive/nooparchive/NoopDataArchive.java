package tech.pegasys.teku.storage.archive.nooparchive;

import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.storage.archive.DataArchive;
import tech.pegasys.teku.storage.archive.DataArchiveNoopWriter;
import tech.pegasys.teku.storage.archive.DataArchiveWriter;

import java.io.IOException;

public class NoopDataArchive implements DataArchive {

    @Override
    public DataArchiveWriter<BlobSidecar> getBlobSidecarWriter() throws IOException {
        return DataArchiveNoopWriter.NOOP_BLOBSIDECAR_STORE;
    }
}
