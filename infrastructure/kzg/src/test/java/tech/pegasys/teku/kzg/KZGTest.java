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

import static ethereum.ckzg4844.CKZG4844JNI.BLS_MODULUS;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.common.io.Resources;
import ethereum.ckzg4844.CKZG4844JNI;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.kzg.ckzg4844.CKZG4844;
import tech.pegasys.teku.kzg.trusted_setups.TrustedSetups;

public final class KZGTest {

  private static final int FIELD_ELEMENTS_PER_BLOB =
      CKZG4844JNI.Preset.MAINNET.fieldElementsPerBlob;
  private static final int RANDOM_SEED = 5566;
  private static final Random RND = new Random(RANDOM_SEED);

  private static KZG kzg;

  @BeforeAll
  public static void setUp() {
    // test initializing with invalid fieldElementsPerBlob
    final KZGException exception =
        assertThrows(KZGException.class, () -> CKZG4844.createInstance(5));
    assertThat(exception)
        .hasMessage("C-KZG-4844 library can't be initialized with 5 fieldElementsPerBlob.");
    kzg = CKZG4844.createInstance(FIELD_ELEMENTS_PER_BLOB);
  }

  @AfterEach
  public void cleanUpIfNeeded() {
    try {
      kzg.freeTrustedSetup();
    } catch (final KZGException ex) {
      // NOOP
    }
  }

  @Test
  public void testCreatingInstanceWithDifferentFieldElementsPerBlob_shouldThrowException() {
    final KZGException exception =
        assertThrows(KZGException.class, () -> CKZG4844.createInstance(4));
    assertThat(exception)
        .hasMessage(
            "Can't reinitialize C-KZG-4844 library with a different value for fieldElementsPerBlob.");
  }

  @Test
  public void testKzgLoadTrustedSetupTwice_shouldThrowException() {
    loadTrustedSetup();
    assertThrows(KZGException.class, this::loadTrustedSetup);
  }

  @Test
  public void testKzgFreeTrustedSetupTwice_shouldThrowException() {
    loadTrustedSetup();
    kzg.freeTrustedSetup();
    assertThrows(KZGException.class, kzg::freeTrustedSetup);
  }

  @Test
  public void testUsageWithoutLoadedTrustedSetup_shouldThrowException() {
    final List<KZGException> exceptions =
        List.of(
            assertThrows(
                KZGException.class,
                () ->
                    kzg.verifyAggregateKzgProof(
                        Collections.emptyList(), Collections.emptyList(), KZGProof.infinity())),
            assertThrows(KZGException.class, () -> kzg.blobToKzgCommitment(Bytes.EMPTY)),
            assertThrows(
                KZGException.class, () -> kzg.computeAggregateKzgProof(Collections.emptyList())));

    assertThat(exceptions)
        .allSatisfy(
            exception -> assertThat(exception).cause().hasMessage("Trusted Setup is not loaded."));
  }

  @Test
  public void testComputingAndVerifyingProof() {
    loadTrustedSetup();
    final int numberOfBlobs = 4;
    final List<Bytes> blobs = getSampleBlobs(numberOfBlobs);
    final List<KZGCommitment> kzgCommitments =
        blobs.stream().map(kzg::blobToKzgCommitment).collect(Collectors.toList());
    final KZGProof kzgProof = kzg.computeAggregateKzgProof(blobs);
    assertThat(kzg.verifyAggregateKzgProof(blobs, kzgCommitments, kzgProof)).isTrue();
    assertThat(kzg.verifyAggregateKzgProof(getSampleBlobs(numberOfBlobs), kzgCommitments, kzgProof))
        .isFalse();
    assertThat(kzg.verifyAggregateKzgProof(blobs, getSampleCommitments(numberOfBlobs), kzgProof))
        .isFalse();
    final KZGProof invalidProof = kzg.computeAggregateKzgProof(getSampleBlobs(numberOfBlobs));
    assertThat(kzg.verifyAggregateKzgProof(blobs, kzgCommitments, invalidProof)).isFalse();
  }

  @Test
  public void testComputingProofWithZeroLengthBlobs() {
    loadTrustedSetup();
    final List<Bytes> blobs =
        Stream.of(
                "0x0d2024ece3e004271319699b8b00cc010628b6bc0be5457f031fb1db0afd3ff8",
                "0x",
                "0x925668a49d06f4")
            .map(Bytes::fromHexString)
            .collect(Collectors.toList());
    assertThat(kzg.computeAggregateKzgProof(blobs).getBytesCompressed())
        .satisfies(proofBytes -> assertThat(proofBytes.isZero()).isFalse());
  }

  private void loadTrustedSetup() {
    final String trustedSetup =
        Resources.getResource(TrustedSetups.class, "mainnet/trusted_setup.txt").toExternalForm();
    kzg.loadTrustedSetup(trustedSetup);
  }

  private List<Bytes> getSampleBlobs(final int count) {
    return IntStream.range(0, count).mapToObj(__ -> getSampleBlob()).collect(Collectors.toList());
  }

  private Bytes getSampleBlob() {
    return IntStream.range(0, FIELD_ELEMENTS_PER_BLOB)
        .mapToObj(__ -> randomBLSFieldElement())
        .map(fieldElement -> (Bytes) fieldElement.toBytes())
        .reduce(Bytes::wrap)
        .orElse(Bytes.EMPTY);
  }

  private List<KZGCommitment> getSampleCommitments(final int count) {
    return IntStream.range(0, count)
        .mapToObj(__ -> getSampleCommitment())
        .collect(Collectors.toList());
  }

  private KZGCommitment getSampleCommitment() {
    return kzg.blobToKzgCommitment(getSampleBlob());
  }

  private UInt256 randomBLSFieldElement() {
    while (true) {
      final BigInteger attempt = new BigInteger(BLS_MODULUS.bitLength(), RND);
      if (attempt.compareTo(BLS_MODULUS) < 0) {
        return UInt256.valueOf(attempt);
      }
    }
  }
}
