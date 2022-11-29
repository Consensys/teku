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

package tech.pegasys.teku.kzg.impl;

import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.kzg.KZGCommitment;
import tech.pegasys.teku.kzg.KZGProof;

public interface KZG4844 {

  /**
   * Free the current trusted setup
   *
   * @throws KzgException if no trusted setup has been loaded.
   */
  void resetTrustedSetup() throws KzgException;

  /**
   * Loads the trusted setup from a file. Once loaded, the same setup will be used for all the
   * calls. To load a new setup, reset the current one by calling {@link #resetTrustedSetup()} and
   * then load the new one. If no trusted setup has been loaded, all other API calls will throw an
   * exception.
   *
   * @param path a path to a trusted setup file in filesystem
   * @throws KzgException if file not found or arguments from file are incorrect
   */
  void loadTrustedSetup(final String path) throws KzgException;

  /**
   * Calculates aggregated proof for the given blobs
   *
   * @param blobs Blobs
   * @return the aggregated proof
   */
  KZGProof computeAggregateKzgProof(List<Bytes> blobs);

  /**
   * Verify aggregated proof and commitments for the given blobs
   *
   * @param blobs Blobs
   * @param kzgCommitments KZG Commitments
   * @param kzgProof The proof that needs verifying
   * @return true if the proof is valid and false otherwise
   */
  boolean verifyAggregateKzgProof(
      List<Bytes> blobs, List<KZGCommitment> kzgCommitments, KZGProof kzgProof);

  /**
   * Calculates commitment for a given blob
   *
   * @param blob Blob
   * @return the commitment
   */
  KZGCommitment blobToKzgCommitment(Bytes blob);

  /**
   * Verify the proof by point evaluation for the given commitment
   *
   * @param kzgCommitment KZG Commitment
   * @param z Z
   * @param y Y
   * @param kzgProof The proof that needs verifying
   * @return true if the proof is valid and false otherwise
   */
  boolean verifyKzgProof(KZGCommitment kzgCommitment, Bytes32 z, Bytes32 y, KZGProof kzgProof);
}
