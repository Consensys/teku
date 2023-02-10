/*
 * Copyright ConsenSys Software Inc., 2022
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

/**
 * This interface specifies all the KZG functions needed for the Deneb specification and is the
 * entry-point for all KZG operations in Teku.
 */
public interface KZG {

  KZG NOOP =
      new KZG() {
        @Override
        public void loadTrustedSetup(final String trustedSetup) throws KZGException {}

        @Override
        public void loadTrustedSetup(TrustedSetup trustedSetup) throws KZGException {}

        @Override
        public void freeTrustedSetup() throws KZGException {}

        @Override
        public KZGProof computeAggregateKzgProof(final List<Bytes> blobs) throws KZGException {
          return KZGProof.INFINITY;
        }

        @Override
        public boolean verifyAggregateKzgProof(
            final List<Bytes> blobs,
            final List<KZGCommitment> kzgCommitments,
            final KZGProof kzgProof)
            throws KZGException {
          return true;
        }

        @Override
        public KZGCommitment blobToKzgCommitment(final Bytes blob) throws KZGException {
          return KZGCommitment.infinity();
        }
      };

  void loadTrustedSetup(String trustedSetup) throws KZGException;

  void loadTrustedSetup(TrustedSetup trustedSetup) throws KZGException;

  void freeTrustedSetup() throws KZGException;

  KZGProof computeAggregateKzgProof(List<Bytes> blobs) throws KZGException;

  boolean verifyAggregateKzgProof(
      List<Bytes> blobs, List<KZGCommitment> kzgCommitments, KZGProof kzgProof) throws KZGException;

  KZGCommitment blobToKzgCommitment(Bytes blob) throws KZGException;
}
