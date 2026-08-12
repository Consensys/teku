package tech.pegasys.teku.spec.util;

import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.kzg.KZGCellAndProof;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.Blob;
import tech.pegasys.teku.spec.datastructures.execution.BlobAndCellProofs;

public class KzgUtil {

    public static BlobAndCellProofs computeBlobAndCellProofs(final KZG kzg, final Blob blob) {
        return new BlobAndCellProofs(
                blob,
                kzg.computeCellsAndProofs(blob.getBytes()).stream().map(KZGCellAndProof::proof).toList());
    }
}
