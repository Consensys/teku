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

package tech.pegasys.teku.kzg;

import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes48;

public class NoOpKZG implements KZG {

  public static final NoOpKZG INSTANCE = new NoOpKZG();

  @Override
  public void loadTrustedSetup(final String trustedSetupFile) throws KZGException {
    // DO NOTHING
  }

  @Override
  public void freeTrustedSetup() throws KZGException {
    // DO NOTHING
  }

  @Override
  public boolean verifyCellProofBatch(
      final List<KZGCommitment> commitments,
      final List<KZGCellWithColumnId> cellWithIDs,
      final List<KZGProof> proofs) {
    return true;
  }

  @Override
  public List<KZGCellAndProof> recoverCellsAndProofs(final List<KZGCellWithColumnId> cells) {
    return List.of();
  }

  @Override
  @Deprecated(since = "Use computeCells instead, computeCellsAndProof is not for production")
  public List<KZGCellAndProof> computeCellsAndProofs(final Bytes blob) {
    return List.of();
  }

  @Override
  public List<KZGCell> computeCells(final Bytes blob) {
    return List.of();
  }

  @Override
  public KZGProof computeBlobKzgProof(final Bytes blob, final KZGCommitment kzgCommitment)
      throws KZGException {
    return KZGProof.fromBytesCompressed(Bytes48.ZERO);
  }

  @Override
  public KZGCommitment blobToKzgCommitment(final Bytes blob) throws KZGException {
    return KZGCommitment.fromBytesCompressed(Bytes48.ZERO);
  }

  @Override
  public boolean verifyBlobKzgProofBatch(
      final List<Bytes> blobs,
      final List<KZGCommitment> kzgCommitments,
      final List<KZGProof> kzgProofs)
      throws KZGException {
    return true;
  }

  @Override
  public boolean verifyBlobKzgProof(
      final Bytes blob, final KZGCommitment kzgCommitment, final KZGProof kzgProof)
      throws KZGException {
    return true;
  }
}
