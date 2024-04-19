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

import java.util.List;
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
            final Bytes blob, final Bytes48 kzgCommitment, final Bytes48 kzgProof)
            throws KZGException {
          return true;
        }

        @Override
        public boolean verifyBlobKzgProofBatch(
            final List<Bytes> blobs,
            final List<Bytes48> kzgCommitments,
            final List<Bytes48> kzgProofs)
            throws KZGException {
          return true;
        }

        @Override
        public Bytes48 blobToKzgCommitment(final Bytes blob) throws KZGException {
          return Bytes48.ZERO;
        }

        @Override
        public Bytes48 computeBlobKzgProof(final Bytes blob, final Bytes48 kzgCommitment)
            throws KZGException {
          return Bytes48.ZERO;
        }
      };

  void loadTrustedSetup(String trustedSetupFile) throws KZGException;

  void freeTrustedSetup() throws KZGException;

  boolean verifyBlobKzgProof(Bytes blob, Bytes48 kzgCommitment, Bytes48 kzgProof)
      throws KZGException;

  boolean verifyBlobKzgProofBatch(
      List<Bytes> blobs, List<Bytes48> kzgCommitments, List<Bytes48> kzgProofs) throws KZGException;

  Bytes48 blobToKzgCommitment(Bytes blob) throws KZGException;

  Bytes48 computeBlobKzgProof(Bytes blob, Bytes48 kzgCommitment) throws KZGException;
}
