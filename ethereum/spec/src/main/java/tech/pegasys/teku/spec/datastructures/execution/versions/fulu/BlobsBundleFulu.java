/*
 * Copyright Consensys Software Inc., 2025
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.teku.spec.datastructures.execution.versions.fulu;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.kzg.KZG.CELLS_PER_EXT_BLOB;

import java.util.List;
import tech.pegasys.teku.kzg.KZGCommitment;
import tech.pegasys.teku.kzg.KZGProof;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.Blob;
import tech.pegasys.teku.spec.datastructures.execution.BlobsBundle;

public class BlobsBundleFulu extends BlobsBundle {

  public BlobsBundleFulu(
      final List<KZGCommitment> commitments, final List<KZGProof> proofs, final List<Blob> blobs) {
    super(commitments, proofs, blobs);
    checkArgument(
        commitments.size() == blobs.size(),
        "Expected %s commitments but got %s",
        blobs.size(),
        commitments.size());
    checkArgument(
        proofs.size() == blobs.size() * CELLS_PER_EXT_BLOB,
        "Expected %s proofs but got %s",
        blobs.size() * CELLS_PER_EXT_BLOB,
        proofs.size());
  }
}
