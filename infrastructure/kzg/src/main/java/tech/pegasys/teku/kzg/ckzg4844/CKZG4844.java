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

import ethereum.ckzg4844.CKZG4844JNI;
import ethereum.ckzg4844.CKZG4844JNI.Preset;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.kzg.KZGCommitment;
import tech.pegasys.teku.kzg.KZGException;
import tech.pegasys.teku.kzg.KZGProof;
import tech.pegasys.teku.kzg.TrustedSetup;

/**
 * Wrapper around jc-kzg-4844
 *
 * <p>This class should be a singleton
 */
public final class CKZG4844 implements KZG {

  private static final Logger LOG = LogManager.getLogger();

  private static CKZG4844 instance;

  private static int initializedFieldElementsPerBlob = -1;

  private Optional<Integer> loadedTrustedSetupHash = Optional.empty();

  public static synchronized CKZG4844 createInstance(final int fieldElementsPerBlob) {
    if (instance == null) {
      final Preset preset = getPreset(fieldElementsPerBlob);
      instance = new CKZG4844(preset);
      initializedFieldElementsPerBlob = fieldElementsPerBlob;
      return instance;
    }
    if (fieldElementsPerBlob != initializedFieldElementsPerBlob) {
      throw new KZGException(
          "Can't reinitialize C-KZG-4844 library with a different value for fieldElementsPerBlob.");
    }
    return instance;
  }

  public static CKZG4844 getInstance() {
    if (instance == null) {
      throw new KZGException("C-KZG-4844 library hasn't been initialized.");
    }
    return instance;
  }

  private static Preset getPreset(final int fieldElementsPerBlob) {
    return Arrays.stream(Preset.values())
        .filter(preset -> preset.fieldElementsPerBlob == fieldElementsPerBlob)
        .findFirst()
        .orElseThrow(
            () ->
                new KZGException(
                    String.format(
                        "C-KZG-4844 library can't be initialized with %d fieldElementsPerBlob.",
                        fieldElementsPerBlob)));
  }

  private CKZG4844(final Preset preset) {
    try {
      CKZG4844JNI.loadNativeLibrary(preset);
      LOG.debug("Loaded C-KZG-4844 with {} preset", preset);
    } catch (final Exception ex) {
      throw new KZGException("Failed to load C-KZG-4844 library", ex);
    }
  }

  @Override
  public synchronized void loadTrustedSetup(final String trustedSetupFilePath) throws KZGException {
    try {
      final TrustedSetup trustedSetup = CKZG4844Utils.parseTrustedSetupFile(trustedSetupFilePath);
      loadTrustedSetup(trustedSetup);
    } catch (IOException ex) {
      throw new KZGException("Failed to load trusted setup from file: " + trustedSetupFilePath, ex);
    }
  }

  @Override
  public void loadTrustedSetup(final TrustedSetup trustedSetup) throws KZGException {
    if (loadedTrustedSetupHash.isPresent()
        && loadedTrustedSetupHash.get().equals(trustedSetup.hashCode())) {
      LOG.trace("Trusted setup {} is already loaded.", trustedSetup);
      return;
    }
    try {
      CKZG4844JNI.loadTrustedSetup(
          CKZG4844Utils.flattenBytes(trustedSetup.getG1Points()),
          trustedSetup.getG1Points().size(),
          CKZG4844Utils.flattenBytes(trustedSetup.getG2Points()),
          trustedSetup.getG2Points().size());
      loadedTrustedSetupHash = Optional.of(trustedSetup.hashCode());
      LOG.debug("Loaded trusted setup: {}", trustedSetup);
    } catch (final Exception ex) {
      throw new KZGException("Failed to load trusted setup: " + trustedSetup, ex);
    }
  }

  @Override
  public synchronized void freeTrustedSetup() throws KZGException {
    try {
      CKZG4844JNI.freeTrustedSetup();
      loadedTrustedSetupHash = Optional.empty();
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
    if (blobs.size() != kzgCommitments.size() || blobs.size() != kzgProofs.size()) {
      throw new KZGException(
          String.format(
              "Expecting equal number of blobs, commitments and proofs for verification, "
                  + "provided instead: blobs=%s, commitments=%s, proofs=%s",
              blobs.size(), kzgCommitments.size(), kzgProofs.size()));
    }
    try {
      final byte[] blobsBytes = CKZG4844Utils.flattenBytes(blobs);
      final byte[] commitmentsBytes = CKZG4844Utils.flattenCommitments(kzgCommitments);
      final byte[] proofBytes = CKZG4844Utils.flattenProofs(kzgProofs);
      return CKZG4844JNI.verifyBlobKzgProofBatch(
          blobsBytes, commitmentsBytes, proofBytes, blobs.size());
    } catch (final Exception ex) {
      throw new KZGException(
          "Failed to verify blobs and commitments against KZG proofs " + kzgProofs, ex);
    }
  }

  @Override
  public KZGCommitment blobToKzgCommitment(final Bytes blob) throws KZGException {
    try {
      final byte[] commitmentBytes = CKZG4844JNI.blobToKzgCommitment(blob.toArray());
      return KZGCommitment.fromArray(commitmentBytes);
    } catch (final Exception ex) {
      throw new KZGException("Failed to produce KZG commitment from blob", ex);
    }
  }

  @Override
  public KZGProof computeBlobKzgProof(final Bytes blob, final KZGCommitment kzgCommitment)
      throws KZGException {
    try {
      final byte[] proof = CKZG4844JNI.computeBlobKzgProof(blob.toArray(), kzgCommitment.toArray());
      return KZGProof.fromArray(proof);
    } catch (final Exception ex) {
      throw new KZGException(
          "Failed to compute KZG proof for blob with commitment " + kzgCommitment, ex);
    }
  }
}
