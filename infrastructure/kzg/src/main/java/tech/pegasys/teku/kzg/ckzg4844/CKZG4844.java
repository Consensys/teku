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

package tech.pegasys.teku.kzg.ckzg4844;

import ethereum.ckzg4844.CKzg4844JNI;
import java.net.URL;
import java.util.List;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.kzg.KZGCommitment;
import tech.pegasys.teku.kzg.KZGException;
import tech.pegasys.teku.kzg.KZGProof;
import tech.pegasys.teku.kzg.KZGUtils;

/** Wrapper around jc-kzg-4844 */
public final class CKZG4844 implements KZG {

  private static final Logger LOG = LogManager.getLogger();

  public CKZG4844() {
    try {
      LOG.info("Loaded C-KZG-4844 library");
    } catch (final Exception ex) {
      throw new KZGException("Failed to load C-KZG-4844 library", ex);
    }
  }

  @Override
  public void loadTrustedSetup(final URL trustedSetup) throws KZGException {
    try {
      final String file = KZGUtils.copyTrustedSetupToTempFileIfNeeded(trustedSetup);
      CKzg4844JNI.loadTrustedSetup(file);
      LOG.info("Loaded trusted setup from {}", file);
    } catch (final Exception ex) {
      throw new KZGException("Failed to load trusted setup from " + trustedSetup, ex);
    }
  }

  @Override
  public void freeTrustedSetup() throws KZGException {
    try {
      CKzg4844JNI.freeTrustedSetup();
    } catch (final Exception ex) {
      throw new KZGException("Failed to free trusted setup", ex);
    }
  }

  @Override
  public KZGProof computeAggregateKzgProof(final List<Bytes> blobs) throws KZGException {
    try {
      final byte[] blobsBytes = KZGUtils.flattenBytesListToArray(blobs);
      final byte[] proof = CKzg4844JNI.computeAggregateKzgProof(blobsBytes, blobs.size());
      return KZGProof.fromArray(proof);
    } catch (final Exception ex) {
      throw new KZGException("Failed to compute aggregated KZG proof for blobs", ex);
    }
  }

  @Override
  public boolean verifyAggregateKzgProof(
      final List<Bytes> blobs, final List<KZGCommitment> kzgCommitments, final KZGProof kzgProof)
      throws KZGException {
    try {
      final byte[] blobsBytes = KZGUtils.flattenBytesListToArray(blobs);
      final Stream<Bytes> commitmentsBytesStream =
          kzgCommitments.stream().map(KZGCommitment::getBytesCompressed);
      final byte[] commitmentsBytes = KZGUtils.flattenBytesStreamToArray(commitmentsBytesStream);
      return CKzg4844JNI.verifyAggregateKzgProof(
          blobsBytes, commitmentsBytes, blobs.size(), kzgProof.toArray());
    } catch (final Exception ex) {
      throw new KZGException(
          "Failed to verify blobs and commitments against KZG proof " + kzgProof, ex);
    }
  }

  @Override
  public KZGCommitment blobToKzgCommitment(final Bytes blob) throws KZGException {
    try {
      final byte[] commitmentBytes = CKzg4844JNI.blobToKzgCommitment(blob.toArray());
      return KZGCommitment.fromArray(commitmentBytes);
    } catch (final Exception ex) {
      throw new KZGException("Failed to produce KZG commitment from blob " + blob, ex);
    }
  }

  @Override
  public boolean verifyKzgProof(
      final KZGCommitment kzgCommitment, final Bytes32 z, final Bytes32 y, final KZGProof kzgProof)
      throws KZGException {
    try {
      return CKzg4844JNI.verifyKzgProof(
          kzgCommitment.toArray(), z.toArray(), y.toArray(), kzgProof.toArray());
    } catch (final Exception ex) {
      throw new KZGException(
          String.format(
              "Failed to verify KZG commitment %s against KZG proof %s", kzgCommitment, kzgProof),
          ex);
    }
  }
}
