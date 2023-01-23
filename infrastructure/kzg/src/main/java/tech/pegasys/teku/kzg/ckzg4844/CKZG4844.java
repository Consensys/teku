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
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.kzg.KZGCommitment;
import tech.pegasys.teku.kzg.KZGException;
import tech.pegasys.teku.kzg.KZGProof;

/**
 * Wrapper around jc-kzg-4844
 *
 * <p>This class should be a singleton
 */
public final class CKZG4844 implements KZG {

  private static final Logger LOG = LogManager.getLogger();

  private static CKZG4844 instance;

  private static int initializedFieldElementsPerBlob = -1;

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

  private Optional<String> loadedTrustedSetup = Optional.empty();

  private CKZG4844(final Preset preset) {
    try {
      CKZG4844JNI.loadNativeLibrary(preset);
      LOG.debug("Loaded C-KZG-4844 with {} preset", preset);
    } catch (final Exception ex) {
      throw new KZGException("Failed to load C-KZG-4844 library", ex);
    }
  }

  @Override
  public synchronized void loadTrustedSetup(final String trustedSetup) throws KZGException {
    if (loadedTrustedSetup.isPresent() && Objects.equals(loadedTrustedSetup.get(), trustedSetup)) {
      LOG.trace("Trusted setup {} is already loaded.", trustedSetup);
      return;
    }
    try {
      final String file = CKZG4844Utils.copyTrustedSetupToTempFileIfNeeded(trustedSetup);
      CKZG4844JNI.loadTrustedSetup(file);
      loadedTrustedSetup = Optional.of(trustedSetup);
      LOG.debug("Loaded trusted setup from {}", file);
    } catch (final Exception ex) {
      throw new KZGException("Failed to load trusted setup from " + trustedSetup, ex);
    }
  }

  @Override
  public synchronized void freeTrustedSetup() throws KZGException {
    try {
      CKZG4844JNI.freeTrustedSetup();
      loadedTrustedSetup = Optional.empty();
      LOG.debug("Trusted setup was freed");
    } catch (final Exception ex) {
      throw new KZGException("Failed to free trusted setup", ex);
    }
  }

  @Override
  public KZGProof computeAggregateKzgProof(final List<Bytes> blobs) throws KZGException {
    try {
      final byte[] blobsBytes = CKZG4844Utils.flattenBlobs(blobs);
      final byte[] proof = CKZG4844JNI.computeAggregateKzgProof(blobsBytes, blobs.size());
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
      final byte[] blobsBytes = CKZG4844Utils.flattenBlobs(blobs);
      final byte[] commitmentsBytes = CKZG4844Utils.flattenCommitments(kzgCommitments);
      return CKZG4844JNI.verifyAggregateKzgProof(
          blobsBytes, commitmentsBytes, blobs.size(), kzgProof.toArray());
    } catch (final Exception ex) {
      throw new KZGException(
          "Failed to verify blobs and commitments against KZG proof " + kzgProof, ex);
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
}
