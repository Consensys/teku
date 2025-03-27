/*
 * Copyright Consensys Software Inc., 2025
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
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.common.collect.Streams;
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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tech.pegasys.teku.kzg.trusted_setups.TrustedSetupLoader;

public final class CKZG4844Test {

  private static final int RANDOM_SEED = 5566;
  private static final Random RND = new Random(RANDOM_SEED);

  private static final CKZG4844 CKZG = CKZG4844.getInstance();

  @BeforeEach
  public void setUp() {
    loadTrustedSetup();
  }

  private static void loadTrustedSetup() {
    TrustedSetupLoader.loadTrustedSetupForTests(CKZG);
  }

  @AfterAll
  public static void cleanUp() throws KZGException {
    try {
      CKZG.freeTrustedSetup();
    } catch (KZGException ex) {
      // NOOP
    }
  }

  @Test
  public void testKzgLoadSameTrustedSetupTwice_shouldNotThrowException() {
    loadTrustedSetup();
  }

  @Test
  public void testKzgFreeTrustedSetupTwice_shouldThrowException() {
    CKZG.freeTrustedSetup();
    assertThrows(KZGException.class, CKZG::freeTrustedSetup);
  }

  @Test
  public void testUsageWithoutLoadedTrustedSetup_shouldThrowException() {
    CKZG.freeTrustedSetup();
    final List<KZGException> exceptions =
        List.of(
            assertThrows(
                KZGException.class,
                () ->
                    CKZG.verifyBlobKzgProofBatch(
                        List.of(Bytes.fromHexString("0x", BYTES_PER_BLOB)),
                        List.of(getSampleCommitment()),
                        List.of(getSampleProof()))),
            assertThrows(KZGException.class, () -> CKZG.blobToKzgCommitment(Bytes.EMPTY)),
            assertThrows(
                KZGException.class,
                () -> CKZG.computeBlobKzgProof(Bytes.EMPTY, getSampleCommitment())));

    assertThat(exceptions)
        .allSatisfy(
            exception -> assertThat(exception).cause().hasMessage("Trusted Setup is not loaded."));
  }

  @Test
  public void testComputingAndVerifyingBatchProofs() {
    final int numberOfBlobs = 4;
    final List<Bytes> blobs = getSampleBlobs(numberOfBlobs);
    final List<KZGCommitment> kzgCommitments =
        blobs.stream().map(CKZG::blobToKzgCommitment).collect(Collectors.toList());
    final List<KZGProof> kzgProofs =
        Streams.zip(
                kzgCommitments.stream(),
                blobs.stream(),
                (kzgCommitment, blob) -> CKZG.computeBlobKzgProof(blob, kzgCommitment))
            .collect(Collectors.toList());
    assertThat(CKZG.verifyBlobKzgProofBatch(blobs, kzgCommitments, kzgProofs)).isTrue();

    assertThat(
            CKZG.verifyBlobKzgProofBatch(getSampleBlobs(numberOfBlobs), kzgCommitments, kzgProofs))
        .isFalse();
    assertThat(CKZG.verifyBlobKzgProofBatch(blobs, getSampleCommitments(numberOfBlobs), kzgProofs))
        .isFalse();
    final List<KZGProof> invalidProofs =
        getSampleBlobs(numberOfBlobs).stream()
            .map((Bytes blob) -> CKZG.computeBlobKzgProof(blob, CKZG.blobToKzgCommitment(blob)))
            .collect(Collectors.toList());
    assertThat(CKZG.verifyBlobKzgProofBatch(blobs, kzgCommitments, invalidProofs)).isFalse();
  }

  @Test
  public void testVerifyingEmptyBatch() {
    assertThat(CKZG.verifyBlobKzgProofBatch(List.of(), List.of(), List.of())).isTrue();
  }

  @Test
  public void testComputingAndVerifyingSingleProof() {
    final Bytes blob = getSampleBlob();
    final KZGCommitment kzgCommitment = CKZG.blobToKzgCommitment(blob);
    final KZGProof kzgProof = CKZG.computeBlobKzgProof(blob, kzgCommitment);

    assertThat(CKZG.verifyBlobKzgProof(blob, kzgCommitment, kzgProof)).isTrue();

    assertThat(CKZG.verifyBlobKzgProof(getSampleBlob(), kzgCommitment, kzgProof)).isFalse();
    assertThat(CKZG.verifyBlobKzgProof(blob, getSampleCommitment(), kzgProof)).isFalse();
    final Bytes randomBlob = getSampleBlob();
    final KZGProof invalidProof =
        CKZG.computeBlobKzgProof(randomBlob, CKZG.blobToKzgCommitment(randomBlob));
    assertThat(CKZG.verifyBlobKzgProof(blob, kzgCommitment, invalidProof)).isFalse();
  }

  @Test
  public void testComputingAndVerifyingBatchSingleProof() {
    final int numberOfBlobs = 1;
    final List<Bytes> blobs = getSampleBlobs(numberOfBlobs);
    final List<KZGCommitment> kzgCommitments =
        blobs.stream().map(CKZG::blobToKzgCommitment).collect(Collectors.toList());
    final List<KZGProof> kzgProofs =
        Streams.zip(
                kzgCommitments.stream(),
                blobs.stream(),
                (kzgCommitment, blob) -> CKZG.computeBlobKzgProof(blob, kzgCommitment))
            .collect(Collectors.toList());
    assertThat(kzgProofs.size()).isEqualTo(1);
    assertThat(CKZG.verifyBlobKzgProofBatch(blobs, kzgCommitments, kzgProofs)).isTrue();

    assertThat(
            CKZG.verifyBlobKzgProofBatch(getSampleBlobs(numberOfBlobs), kzgCommitments, kzgProofs))
        .isFalse();
    assertThat(CKZG.verifyBlobKzgProofBatch(blobs, getSampleCommitments(numberOfBlobs), kzgProofs))
        .isFalse();
    final List<KZGProof> invalidProofs =
        getSampleBlobs(numberOfBlobs).stream()
            .map((Bytes blob) -> CKZG.computeBlobKzgProof(blob, CKZG.blobToKzgCommitment(blob)))
            .collect(Collectors.toList());
    assertThat(CKZG.verifyBlobKzgProofBatch(blobs, kzgCommitments, invalidProofs)).isFalse();
  }

  @Test
  public void testVerifyingBatchProofsThrowsIfSizesDoesntMatch() {
    final int numberOfBlobs = 4;
    final List<Bytes> blobs = getSampleBlobs(numberOfBlobs);
    final List<KZGCommitment> kzgCommitments =
        blobs.stream().map(CKZG::blobToKzgCommitment).collect(Collectors.toList());
    final List<KZGProof> kzgProofs =
        Streams.zip(
                kzgCommitments.stream(),
                blobs.stream(),
                (kzgCommitment, blob) -> CKZG.computeBlobKzgProof(blob, kzgCommitment))
            .collect(Collectors.toList());
    final KZGException kzgException1 =
        assertThrows(
            KZGException.class,
            () -> CKZG.verifyBlobKzgProofBatch(blobs, kzgCommitments, List.of(kzgProofs.get(0))));
    final KZGException kzgException2 =
        assertThrows(
            KZGException.class,
            () -> CKZG.verifyBlobKzgProofBatch(blobs, List.of(kzgCommitments.get(0)), kzgProofs));
    final KZGException kzgException3 =
        assertThrows(
            KZGException.class,
            () -> CKZG.verifyBlobKzgProofBatch(List.of(blobs.get(0)), kzgCommitments, kzgProofs));

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
    final Bytes blob = Bytes.fromHexString(blobHex);

    final KZGException kzgException =
        assertThrows(
            KZGException.class,
            () -> CKZG.computeBlobKzgProof(blob, CKZG.blobToKzgCommitment(blob)));

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
  public void incorrectTrustedSetupFilesShouldThrow(final String filename) {
    final Throwable cause =
        assertThrows(
                KZGException.class,
                () -> CKZG.loadTrustedSetup(TrustedSetupLoader.getTrustedSetupFile(filename)))
            .getCause();
    assertThat(cause.getMessage()).contains("Failed to parse trusted setup file");
  }

  @Test
  public void testInvalidLengthG2PointInNewTrustedSetup() {
    assertThatThrownBy(
            () -> new TrustedSetup(List.of(), List.of(Bytes.fromHexString("")), List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Expected G2 point to be 96 bytes");
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
    return CKZG.blobToKzgCommitment(getSampleBlob());
  }

  private KZGProof getSampleProof() {
    return CKZG.computeBlobKzgProof(getSampleBlob(), getSampleCommitment());
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
