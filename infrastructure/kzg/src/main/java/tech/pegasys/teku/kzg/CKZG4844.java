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

import ethereum.ckzg4844.CKZG4844JNI;
import java.util.List;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;

/**
 * Wrapper around jc-kzg-4844
 *
 * <p>This class should be a singleton
 */
final class CKZG4844 implements KZG {

  private static final Logger LOG = LogManager.getLogger();

  private static CKZG4844 instance;

  static synchronized CKZG4844 getInstance() {
    if (instance == null) {
      instance = new CKZG4844();
    }
    return instance;
  }

  private Optional<String> loadedTrustedSetupFile = Optional.empty();

  private CKZG4844() {
    try {
      CKZG4844JNI.loadNativeLibrary();
      LOG.info("Loaded C-KZG-4844 library");
    } catch (final Exception ex) {
      throw new KZGException("Failed to load C-KZG-4844 library", ex);
    }
  }

  /** Only one trusted setup at a time can be loaded. */
  @Override
  public synchronized void loadTrustedSetup(final String trustedSetupFile) throws KZGException {
    if (loadedTrustedSetupFile.isPresent()
        && loadedTrustedSetupFile.get().equals(trustedSetupFile)) {
      LOG.trace("Trusted setup from {} is already loaded", trustedSetupFile);
      return;
    }
    try {
      loadedTrustedSetupFile.ifPresent(
          currentTrustedSetupFile -> {
            LOG.debug(
                "Freeing current trusted setup {} in order to load trusted setup from {}",
                currentTrustedSetupFile,
                trustedSetupFile);
            freeTrustedSetup();
          });
      final TrustedSetup trustedSetup = CKZG4844Utils.parseTrustedSetupFile(trustedSetupFile);
      final List<Bytes> g1Points = trustedSetup.g1Points();
      final List<Bytes> g2Points = trustedSetup.g2Points();
      CKZG4844JNI.loadTrustedSetup(
          CKZG4844Utils.flattenG1Points(g1Points),
          g1Points.size(),
          CKZG4844Utils.flattenG2Points(g2Points),
          g2Points.size());
      LOG.debug("Loaded trusted setup from {}", trustedSetupFile);
      loadedTrustedSetupFile = Optional.of(trustedSetupFile);
    } catch (final Exception ex) {
      throw new KZGException("Failed to load trusted setup from " + trustedSetupFile, ex);
    }
  }

  @Override
  public synchronized void freeTrustedSetup() throws KZGException {
    try {
      CKZG4844JNI.freeTrustedSetup();
      loadedTrustedSetupFile = Optional.empty();
      LOG.debug("Trusted setup was freed");
    } catch (final Exception ex) {
      throw new KZGException("Failed to free trusted setup", ex);
    }
  }

  @Override
  public boolean verifyBlobKzgProofBatch(
      final List<Bytes> blobs,
      final List<KZGCommitment> kzgCommitments,
      final List<KZGProof> kzgProofs)
      throws KZGException {
    try {
      final byte[] blobsBytes = CKZG4844Utils.flattenBlobs(blobs);
      final byte[] commitmentsBytes = CKZG4844Utils.flattenCommitments(kzgCommitments);
      final byte[] proofsBytes = CKZG4844Utils.flattenProofs(kzgProofs);
      return CKZG4844JNI.verifyBlobKzgProofBatch(
          blobsBytes, commitmentsBytes, proofsBytes, blobs.size());
    } catch (final Exception ex) {
      throw new KZGException(
          "Failed to verify blobs and commitments against KZG proofs " + kzgProofs, ex);
    }
  }

  @Override
  public KZGCommitment blobToKzgCommitment(final Bytes blob) throws KZGException {
    try {
      final byte[] commitmentBytes = CKZG4844JNI.blobToKzgCommitment(blob.toArrayUnsafe());
      return KZGCommitment.fromArray(commitmentBytes);
    } catch (final Exception ex) {
      throw new KZGException("Failed to produce KZG commitment from blob", ex);
    }
  }

  @Override
  public KZGProof computeBlobKzgProof(final Bytes blob, final KZGCommitment kzgCommitment)
      throws KZGException {
    try {
      final byte[] proof =
          CKZG4844JNI.computeBlobKzgProof(blob.toArrayUnsafe(), kzgCommitment.toArrayUnsafe());
      return KZGProof.fromArray(proof);
    } catch (final Exception ex) {
      throw new KZGException(
          "Failed to compute KZG proof for blob with commitment " + kzgCommitment, ex);
    }
  }
}
