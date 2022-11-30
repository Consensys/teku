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

package tech.pegasys.teku.kzg.impl.ckzg;

import ethereum.ckzg4844.CKzg4844JNI;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.Bytes48;
import tech.pegasys.teku.kzg.KZGCommitment;
import tech.pegasys.teku.kzg.KZGProof;
import tech.pegasys.teku.kzg.impl.KZG4844;
import tech.pegasys.teku.kzg.impl.KzgException;

/**
 * Wrapper around JNI C-KZG library implementing stripped down KZG specification needed for EIP-4844
 */
public final class CkzgKZG4844 implements KZG4844 {

  @Override
  public void resetTrustedSetup() throws KzgException {
    try {
      CKzg4844JNI.freeTrustedSetup();
    } catch (final Exception ex) {
      throw new KzgException("Failed to reset trusted setup", ex);
    }
  }

  @Override
  public void loadTrustedSetup(final String path) throws KzgException {
    try {
      CKzg4844JNI.loadTrustedSetup(path);
    } catch (final Exception ex) {
      throw new KzgException(String.format("Failed to load trusted setup: %s", path), ex);
    }
  }

  @Override
  public KZGProof computeAggregateKzgProof(final List<Bytes> blobs) throws KzgException {
    try {
      final byte[] result =
          CKzg4844JNI.computeAggregateKzgProof(
              blobs.stream().reduce(Bytes::wrap).orElseThrow().toArray(), blobs.size());
      return KZGProof.fromBytesCompressed(Bytes48.wrap(result));
    } catch (final Exception ex) {
      throw new KzgException("Failed to compute aggregated KZG Proof for Blobs", ex);
    }
  }

  @Override
  public boolean verifyAggregateKzgProof(
      final List<Bytes> blobs, final List<KZGCommitment> kzgCommitments, final KZGProof kzgProof)
      throws KzgException {
    try {
      return CKzg4844JNI.verifyAggregateKzgProof(
          blobs.stream().reduce(Bytes::wrap).orElseThrow().toArray(),
          kzgCommitments.stream()
              .map(kzgCommitment -> (Bytes) kzgCommitment.getBytesCompressed())
              .reduce(Bytes::wrap)
              .orElseThrow()
              .toArray(),
          blobs.size(),
          kzgProof.getBytesCompressed().toArray());
    } catch (final Exception ex) {
      throw new KzgException(
          String.format("Failed to verify blobs against KZG Proof %s", kzgProof), ex);
    }
  }

  @Override
  public KZGCommitment blobToKzgCommitment(final Bytes blob) throws KzgException {
    try {
      return KZGCommitment.fromBytesCompressed(
          Bytes48.wrap(CKzg4844JNI.blobToKzgCommitment(blob.toArray())));
    } catch (final Exception ex) {
      throw new KzgException("Failed to produce KZG Commitment from Blob", ex);
    }
  }

  @Override
  public boolean verifyKzgProof(
      final KZGCommitment kzgCommitment, final Bytes32 z, final Bytes32 y, final KZGProof kzgProof)
      throws KzgException {
    try {
      return CKzg4844JNI.verifyKzgProof(
          kzgCommitment.getBytesCompressed().toArray(),
          z.toArray(),
          y.toArray(),
          kzgProof.getBytesCompressed().toArray());
    } catch (final Exception ex) {
      throw new KzgException(
          String.format(
              "Failed to verify KZG Commitment %s against KZG Proof %s", kzgCommitment, kzgProof),
          ex);
    }
  }
}
