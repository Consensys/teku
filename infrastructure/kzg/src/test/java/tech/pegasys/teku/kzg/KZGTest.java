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

import static ethereum.ckzg4844.CKZG4844JNI.BLS_MODULUS;
import static ethereum.ckzg4844.CKZG4844JNI.BYTES_PER_BLOB;
import static ethereum.ckzg4844.CKZG4844JNI.FIELD_ELEMENTS_PER_BLOB;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.common.collect.Streams;
import com.google.common.io.Resources;
import ethereum.ckzg4844.CKZGException;
import ethereum.ckzg4844.CKZGException.CKZGError;
import java.math.BigInteger;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tech.pegasys.teku.kzg.ckzg4844.CKZG4844;
import tech.pegasys.teku.kzg.trusted_setups.TrustedSetups;

public final class KZGTest {

  private static final int RANDOM_SEED = 5566;
  private static final Random RND = new Random(RANDOM_SEED);
  private static final String TRUSTED_SETUP_PATH = "trusted_setup.txt";

  private static KZG kzg;

  @BeforeAll
  public static void setUp() {
    kzg = CKZG4844.createInstance();
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
  public void testKzgLoadSameTrustedSetupTwice_shouldNotThrowException() {
    loadTrustedSetup();
    loadTrustedSetup();
  }

  @Test
  public void testKzLoadDifferentTrustedSetupTwice_shouldThrowException() {
    loadTrustedSetup();
    assertThrows(KZGException.class, () -> kzg.loadTrustedSetup("trusted_setup-not-existing.txt"));
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
                    kzg.verifyBlobKzgProofBatch(
                        List.of(Bytes.fromHexString("0x", BYTES_PER_BLOB)),
                        List.of(KZGCommitment.infinity()),
                        List.of(KZGProof.INFINITY))),
            assertThrows(KZGException.class, () -> kzg.blobToKzgCommitment(Bytes.EMPTY)),
            assertThrows(
                KZGException.class,
                () -> kzg.computeBlobKzgProof(Bytes.EMPTY, KZGCommitment.infinity())));

    assertThat(exceptions)
        .allSatisfy(
            exception -> assertThat(exception).cause().hasMessage("Trusted Setup is not loaded."));
  }

  @Test
  public void testComputingAndVerifyingBatchProofs() {
    loadTrustedSetup();
    final int numberOfBlobs = 4;
    final List<Bytes> blobs = getSampleBlobs(numberOfBlobs);
    final List<KZGCommitment> kzgCommitments =
        blobs.stream().map(kzg::blobToKzgCommitment).collect(Collectors.toList());
    final List<KZGProof> kzgProofs =
        Streams.zip(
                kzgCommitments.stream(),
                blobs.stream(),
                (kzgCommitment, blob) -> kzg.computeBlobKzgProof(blob, kzgCommitment))
            .collect(Collectors.toList());
    assertThat(kzg.verifyBlobKzgProofBatch(blobs, kzgCommitments, kzgProofs)).isTrue();

    assertThat(
            kzg.verifyBlobKzgProofBatch(getSampleBlobs(numberOfBlobs), kzgCommitments, kzgProofs))
        .isFalse();
    assertThat(kzg.verifyBlobKzgProofBatch(blobs, getSampleCommitments(numberOfBlobs), kzgProofs))
        .isFalse();
    final List<KZGProof> invalidProofs =
        getSampleBlobs(numberOfBlobs).stream()
            .map((Bytes blob) -> kzg.computeBlobKzgProof(blob, kzg.blobToKzgCommitment(blob)))
            .collect(Collectors.toList());
    assertThat(kzg.verifyBlobKzgProofBatch(blobs, kzgCommitments, invalidProofs)).isFalse();
  }

  @Test
  public void testVerifyingEmptyBatch() {
    loadTrustedSetup();
    assertThat(kzg.verifyBlobKzgProofBatch(List.of(), List.of(), List.of())).isTrue();
  }

  @Test
  public void testComputingAndVerifyingBatchSingleProof() {
    loadTrustedSetup();
    final int numberOfBlobs = 1;
    final List<Bytes> blobs = getSampleBlobs(numberOfBlobs);
    final List<KZGCommitment> kzgCommitments =
        blobs.stream().map(kzg::blobToKzgCommitment).collect(Collectors.toList());
    final List<KZGProof> kzgProofs =
        Streams.zip(
                kzgCommitments.stream(),
                blobs.stream(),
                (kzgCommitment, blob) -> kzg.computeBlobKzgProof(blob, kzgCommitment))
            .collect(Collectors.toList());
    assertThat(kzgProofs.size()).isEqualTo(1);
    assertThat(kzg.verifyBlobKzgProofBatch(blobs, kzgCommitments, kzgProofs)).isTrue();

    assertThat(
            kzg.verifyBlobKzgProofBatch(getSampleBlobs(numberOfBlobs), kzgCommitments, kzgProofs))
        .isFalse();
    assertThat(kzg.verifyBlobKzgProofBatch(blobs, getSampleCommitments(numberOfBlobs), kzgProofs))
        .isFalse();
    final List<KZGProof> invalidProofs =
        getSampleBlobs(numberOfBlobs).stream()
            .map((Bytes blob) -> kzg.computeBlobKzgProof(blob, kzg.blobToKzgCommitment(blob)))
            .collect(Collectors.toList());
    assertThat(kzg.verifyBlobKzgProofBatch(blobs, kzgCommitments, invalidProofs)).isFalse();
  }

  @Test
  public void testVerifyingBatchProofsThrowsIfSizesDoesntMatch() {
    loadTrustedSetup();
    final int numberOfBlobs = 4;
    final List<Bytes> blobs = getSampleBlobs(numberOfBlobs);
    final List<KZGCommitment> kzgCommitments =
        blobs.stream().map(kzg::blobToKzgCommitment).collect(Collectors.toList());
    final List<KZGProof> kzgProofs =
        Streams.zip(
                kzgCommitments.stream(),
                blobs.stream(),
                (kzgCommitment, blob) -> kzg.computeBlobKzgProof(blob, kzgCommitment))
            .collect(Collectors.toList());
    final KZGException kzgException1 =
        assertThrows(
            KZGException.class,
            () -> kzg.verifyBlobKzgProofBatch(blobs, kzgCommitments, List.of(kzgProofs.get(0))));
    final KZGException kzgException2 =
        assertThrows(
            KZGException.class,
            () -> kzg.verifyBlobKzgProofBatch(blobs, List.of(kzgCommitments.get(0)), kzgProofs));
    final KZGException kzgException3 =
        assertThrows(
            KZGException.class,
            () -> kzg.verifyBlobKzgProofBatch(List.of(blobs.get(0)), kzgCommitments, kzgProofs));

    Stream.of(kzgException1, kzgException2, kzgException3)
        .forEach(
            ex ->
                assertThat(ex)
                    .cause()
                    .isInstanceOf(CKZGException.class)
                    .hasMessageMatching(
                        "Invalid .+ size. Expected \\d+ bytes but got \\d+. \\(C_KZG_BADARGS\\)"));
  }

  @ParameterizedTest(name = "blob={0}")
  @ValueSource(
      strings = {
        "0x0d2024ece3e004271319699b8b00cc010628b6bc0be5457f031fb1db0afd3ff8",
        "0x",
        "0x925668a49d06f4"
      })
  public void testComputingProofWithIncorrectLengthBlobDoesNotCauseSegfault(final String blobHex) {
    loadTrustedSetup();
    final Bytes blob = Bytes.fromHexString(blobHex);

    final KZGException kzgException =
        assertThrows(
            KZGException.class, () -> kzg.computeBlobKzgProof(blob, kzg.blobToKzgCommitment(blob)));

    assertThat(kzgException)
        .cause()
        .satisfies(
            cause -> {
              // non-canonical blobs
              assertThat(cause).isInstanceOf(CKZGException.class);
              final CKZGException cryptoException = (CKZGException) cause;
              assertThat(cryptoException.getError()).isEqualTo(CKZGError.C_KZG_BADARGS);
              assertThat(cryptoException.getErrorMessage())
                  .contains("Invalid blob size. Expected 131072 bytes but got");
            });
  }

  @ParameterizedTest(name = "trusted_setup={0}")
  @ValueSource(
      strings = {
        "broken/trusted_setup_g1_length.txt",
        "broken/trusted_setup_g2_length.txt",
        "broken/trusted_setup_g2_bytesize.txt"
      })
  public void incorrectTrustedSetupFilesShouldThrow(final String path) {
    final String trustedSetup = Resources.getResource(TrustedSetups.class, path).toExternalForm();
    final Throwable cause =
        assertThrows(KZGException.class, () -> kzg.loadTrustedSetup(trustedSetup)).getCause();
    assertThat(cause.getMessage()).contains("Failed to parse trusted setup file");
  }

  @Test
  public void monomialTrustedSetupFilesShouldThrow() {
    final String trustedSetup =
        Resources.getResource(TrustedSetups.class, "trusted_setup_monomial.txt").toExternalForm();
    final KZGException kzgException =
        assertThrows(KZGException.class, () -> kzg.loadTrustedSetup(trustedSetup));
    assertThat(kzgException.getMessage()).contains("Failed to load trusted setup");
    assertThat(kzgException.getCause().getMessage())
        .contains("There was an error while loading the Trusted Setup. (C_KZG_BADARGS)");
  }

  @Test
  public void testInvalidLengthG2PointInNewTrustedSetup() {
    assertThatThrownBy(() -> new TrustedSetup(List.of(), List.of(Bytes.fromHexString(""))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Expected G2 point to be 96 bytes");
  }

  private void loadTrustedSetup() {
    final String trustedSetup =
        Resources.getResource(TrustedSetups.class, TRUSTED_SETUP_PATH).toExternalForm();
    kzg.loadTrustedSetup(trustedSetup);
  }

  private List<Bytes> getSampleBlobs(final int count) {
    return IntStream.range(0, count).mapToObj(__ -> getSampleBlob()).collect(Collectors.toList());
  }

  private Bytes getSampleBlob() {
    return IntStream.range(0, FIELD_ELEMENTS_PER_BLOB)
        .mapToObj(__ -> randomBLSFieldElement())
        .map(fieldElement -> Bytes.wrap(fieldElement.toArray(ByteOrder.BIG_ENDIAN)))
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
