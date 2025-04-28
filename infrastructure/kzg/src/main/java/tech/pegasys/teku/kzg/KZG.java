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

import java.math.BigInteger;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes48;

/**
 * This interface specifies all the KZG functions needed for the Deneb specification and is the
 * entry-point for all KZG operations in Teku.
 */
public interface KZG {
  BigInteger BLS_MODULUS =
      new BigInteger(
          "52435875175126190479447740508185965837690552500527637822603658699938581184513");
  int BYTES_PER_G1 = 48;
  int BYTES_PER_G2 = 96;
  int CELLS_PER_EXT_BLOB = 128;
  int FIELD_ELEMENTS_PER_BLOB = 4096;

  static KZG getInstance(final boolean rustKzgEnabled) {
    return rustKzgEnabled ? RustWithCKZG.getInstance() : CKZG4844.getInstance();
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
          return true;
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
          return List.of();
        }

        @Override
        public List<KZGCellAndProof> computeCellsAndProofs(Bytes blob) {
          return List.of();
        }

        @Override
        public boolean verifyCellProofBatch(
            List<KZGCommitment> commitments,
            List<KZGCellWithColumnId> cellWithIDs,
            List<KZGProof> proofs) {
          return true;
        }

        @Override
        public List<KZGCellAndProof> recoverCellsAndProofs(List<KZGCellWithColumnId> cells) {
          return List.of();
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

  // Fulu PeerDAS methods

  List<KZGCell> computeCells(Bytes blob);

  @Deprecated(since = "Use computeCells instead, computeCellsAndProof is not for production")
  List<KZGCellAndProof> computeCellsAndProofs(Bytes blob);

  boolean verifyCellProofBatch(
      List<KZGCommitment> commitments,
      List<KZGCellWithColumnId> cellWithIDs,
      List<KZGProof> proofs);

  List<KZGCellAndProof> recoverCellsAndProofs(List<KZGCellWithColumnId> cells);
}
