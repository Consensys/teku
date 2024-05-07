/*
 * Copyright Consensys Software Inc., 2022
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

import static ethereum.ckzg4844.CKZG4844JNI.CELLS_PER_EXT_BLOB;

import java.util.List;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes48;

/**
 * This interface specifies all the KZG functions needed for the Deneb specification and is the
 * entry-point for all KZG operations in Teku.
 */
public interface KZG {

  static KZG getInstance() {
    return CKZG4844.getInstance();
  }

  KZG NOOP =
      new KZG() {

        @Override
        public void loadTrustedSetup(final String trustedSetupFile) throws KZGException {}

        @Override
        public void freeTrustedSetup() throws KZGException {}

        @Override
        public boolean verifyBlobKzgProof(
            final Bytes blob, final KZGCommitment kzgCommitment, final KZGProof kzgProof)
            throws KZGException {
          return false;
        }

        @Override
        public boolean verifyBlobKzgProofBatch(
            final List<Bytes> blobs,
            final List<KZGCommitment> kzgCommitments,
            final List<KZGProof> kzgProofs)
            throws KZGException {
          return false;
        }

        @Override
        public KZGCommitment blobToKzgCommitment(final Bytes blob) throws KZGException {
          return KZGCommitment.fromBytesCompressed(Bytes48.ZERO);
        }

        @Override
        public KZGProof computeBlobKzgProof(final Bytes blob, final KZGCommitment kzgCommitment)
            throws KZGException {
          return KZGProof.fromBytesCompressed(Bytes48.ZERO);
        }

        @Override
        public List<KZGCell> computeCells(Bytes blob) {
          List<KZGCell> blobCells = KZGCell.splitBytes(blob);
          return Stream.concat(
                  blobCells.stream(), Stream.generate(() -> KZGCell.ZERO).limit(blobCells.size()))
              .toList();
        }

        @Override
        public List<KZGCellAndProof> computeCellsAndProofs(Bytes blob) {
          return computeCells(blob).stream()
              .map(cell -> new KZGCellAndProof(cell, KZGProof.fromBytesCompressed(Bytes48.ZERO)))
              .toList();
        }

        @Override
        public boolean verifyCellProof(
            KZGCommitment commitment, KZGCellWithColumnId cellWithID, KZGProof proof) {
          return false;
        }

        @Override
        public boolean verifyCellProofBatch(
            List<KZGCommitment> commitments,
            List<KZGCellWithIds> cellWithIDs,
            List<KZGProof> proofs) {
          return false;
        }

        @Override
        public List<KZGCell> recoverCells(List<KZGCellWithColumnId> cells) {
          if (cells.size() < CELLS_PER_EXT_BLOB) {
            throw new IllegalArgumentException("Can't recover from " + cells.size() + " cells");
          }
          return cells.stream().map(KZGCellWithColumnId::cell).limit(CELLS_PER_EXT_BLOB).toList();
        }
      };

  void loadTrustedSetup(String trustedSetupFile) throws KZGException;

  void freeTrustedSetup() throws KZGException;

  boolean verifyBlobKzgProof(Bytes blob, KZGCommitment kzgCommitment, KZGProof kzgProof)
      throws KZGException;

  boolean verifyBlobKzgProofBatch(
      List<Bytes> blobs, List<KZGCommitment> kzgCommitments, List<KZGProof> kzgProofs)
      throws KZGException;

  KZGCommitment blobToKzgCommitment(Bytes blob) throws KZGException;

  KZGProof computeBlobKzgProof(Bytes blob, KZGCommitment kzgCommitment) throws KZGException;

  // EIP-7594 methods

  List<KZGCell> computeCells(Bytes blob);

  List<KZGCellAndProof> computeCellsAndProofs(Bytes blob);

  boolean verifyCellProof(KZGCommitment commitment, KZGCellWithColumnId cellWithID, KZGProof proof);

  boolean verifyCellProofBatch(
      List<KZGCommitment> commitments, List<KZGCellWithIds> cellWithIDs, List<KZGProof> proofs);

  /** Check {@link KzgRecoverAllCellsTestExecutor} for implementation requirements */
  List<KZGCell> recoverCells(List<KZGCellWithColumnId> cells);
}
