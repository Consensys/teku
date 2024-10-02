package tech.pegasys.teku.storage.archive;

import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;

/**
 * Interface for a data archive which stores non-current BlobSidecars and could be extended later
 * to include other data types. It is expected that the DataArchive is on disk or externally stored
 * with slow write and recovery times. Initial interface is write only, but may be expanded to include
 * read operations later.
 */
public interface DataArchive {

    //public boolean archiveBlobSidecar(final BlobSidecar blobSidecar);
}
