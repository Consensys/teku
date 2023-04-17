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
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.common.collect.Streams;
import com.google.common.io.Resources;
import ethereum.ckzg4844.CKZG4844JNI;
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
import org.apache.tuweni.bytes.Bytes48;
import org.apache.tuweni.units.bigints.UInt256;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tech.pegasys.teku.kzg.ckzg4844.CKZG4844;
import tech.pegasys.teku.kzg.trusted_setups.TrustedSetups;

public final class KZGTest {

  private static final int FIELD_ELEMENTS_PER_BLOB =
      CKZG4844JNI.Preset.MINIMAL.fieldElementsPerBlob;
  private static final int RANDOM_SEED = 5566;
  private static final Random RND = new Random(RANDOM_SEED);
  private static final String TRUSTED_SETUP_PATH = "minimal/trusted_setup.txt";

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
        assertThrows(
            KZGException.class,
            () -> CKZG4844.createInstance(CKZG4844JNI.Preset.MAINNET.fieldElementsPerBlob));
    assertThat(exception)
        .hasMessage(
            "Can't reinitialize C-KZG-4844 library with a different value for fieldElementsPerBlob.");
  }

  @Test
  public void testKzgLoadSameTrustedSetupTwice_shouldNotThrowException() {
    loadTrustedSetup();
    loadTrustedSetup();
  }

  @Test
  public void testKzLoadDifferentTrustedSetupTwice_shouldThrowException() {
    loadTrustedSetup();
    assertThrows(
        KZGException.class, () -> kzg.loadTrustedSetup("mainnet/trusted_setup-not-existing.txt"));
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
                        List.of(Bytes.fromHexString("0x", 128)),
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
                AssertionsForClassTypes.assertThat(ex)
                    .hasMessageContaining(
                        "Expecting equal number of blobs, commitments and proofs for verification"));
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
                  .contains("Invalid blob size. Expected 128 bytes but got");
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
  public void testInvalidLengthG2PointInNewTrustedSetup() {
    assertThatThrownBy(() -> new TrustedSetup(List.of(), List.of(Bytes.fromHexString(""))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Expected G2 point to be 96 bytes");
  }

  @Test
  public void testComputingAndVerifyingProofWithTrustedSetupInitializedFromObject() {
    kzg.loadTrustedSetup(
        new TrustedSetup(
            List.of(
                Bytes48.fromHexString(
                    "97f1d3a73197d7942695638c4fa9ac0fc3688c4f9774b905a14e3a3f171bac586c55e83ff97a1aeffb3af00adb22c6bb"),
                Bytes48.fromHexString(
                    "854262641262cb9e056a8512808ea6864d903dbcad713fd6da8dddfa5ce40d85612c912063ace060ed8c4bf005bab839"),
                Bytes48.fromHexString(
                    "86f708eee5ae0cf40be36993e760d9cb3b2371f22db3209947c5d21ea68e55186b30871c50bf11ef29e5248bf42d5678"),
                Bytes48.fromHexString(
                    "94f9c0bafb23cbbf34a93a64243e3e0f934b57593651f3464de7dc174468123d9698f1b9dfa22bb5b6eb96eae002f29f")),
            List.of(
                Bytes.fromHexString(
                    "93e02b6052719f607dacd3a088274f65596bd0d09920b61ab5da61bbdc7f5049334cf11213945d57e5ac7d055d042b7e024aa2b2f08f0a91260805272dc51051c6e47ad4fa403b02b4510b647ae3d1770bac0326a805bbefd48056c8c121bdb8"),
                Bytes.fromHexString(
                    "99aca9fb2f7760cecb892bf7262c176b334824f5727f680bba701a33e322cb6667531410dfc7c8e4321a3f0ea8af48cb1436638a2093123f046f0f504cc2a864825542873edbbc5d7ed17af125a4f2cf6433c6f4f61b81173726981dd989761d"),
                Bytes.fromHexString(
                    "88e2e982982bf8231e747e9dfcd14c05bd02623d1332734d2af26246c6869fb56ee6c994843f593178a040495ba61f4a083b0e18110b1d9f5224783d8f9a895e8ee744e87929430e9ba96bd29251cbf61240b256d1525600f3d562894d93d659"),
                Bytes.fromHexString(
                    "a2d33775e3d9e6af0d1b27d389e6c021a578e617a3d6627686db6288d4b3dffd7a847a00f7ef01828b7f42885b660e4204923402aca18fbae74ccd4e9c50dd8c2281b38dc09c022342ed1ac695d53f7081cb21f05fdfc0a3508c04759196fcd3"),
                Bytes.fromHexString(
                    "93e02b6052719f607dacd3a088274f65596bd0d09920b61ab5da61bbdc7f5049334cf11213945d57e5ac7d055d042b7e024aa2b2f08f0a91260805272dc51051c6e47ad4fa403b02b4510b647ae3d1770bac0326a805bbefd48056c8c121bdb8"),
                Bytes.fromHexString(
                    "99aca9fb2f7760cecb892bf7262c176b334824f5727f680bba701a33e322cb6667531410dfc7c8e4321a3f0ea8af48cb1436638a2093123f046f0f504cc2a864825542873edbbc5d7ed17af125a4f2cf6433c6f4f61b81173726981dd989761d"),
                Bytes.fromHexString(
                    "88e2e982982bf8231e747e9dfcd14c05bd02623d1332734d2af26246c6869fb56ee6c994843f593178a040495ba61f4a083b0e18110b1d9f5224783d8f9a895e8ee744e87929430e9ba96bd29251cbf61240b256d1525600f3d562894d93d659"),
                Bytes.fromHexString(
                    "a2d33775e3d9e6af0d1b27d389e6c021a578e617a3d6627686db6288d4b3dffd7a847a00f7ef01828b7f42885b660e4204923402aca18fbae74ccd4e9c50dd8c2281b38dc09c022342ed1ac695d53f7081cb21f05fdfc0a3508c04759196fcd3"),
                Bytes.fromHexString(
                    "93e02b6052719f607dacd3a088274f65596bd0d09920b61ab5da61bbdc7f5049334cf11213945d57e5ac7d055d042b7e024aa2b2f08f0a91260805272dc51051c6e47ad4fa403b02b4510b647ae3d1770bac0326a805bbefd48056c8c121bdb8"),
                Bytes.fromHexString(
                    "99aca9fb2f7760cecb892bf7262c176b334824f5727f680bba701a33e322cb6667531410dfc7c8e4321a3f0ea8af48cb1436638a2093123f046f0f504cc2a864825542873edbbc5d7ed17af125a4f2cf6433c6f4f61b81173726981dd989761d"),
                Bytes.fromHexString(
                    "88e2e982982bf8231e747e9dfcd14c05bd02623d1332734d2af26246c6869fb56ee6c994843f593178a040495ba61f4a083b0e18110b1d9f5224783d8f9a895e8ee744e87929430e9ba96bd29251cbf61240b256d1525600f3d562894d93d659"),
                Bytes.fromHexString(
                    "a2d33775e3d9e6af0d1b27d389e6c021a578e617a3d6627686db6288d4b3dffd7a847a00f7ef01828b7f42885b660e4204923402aca18fbae74ccd4e9c50dd8c2281b38dc09c022342ed1ac695d53f7081cb21f05fdfc0a3508c04759196fcd3"),
                Bytes.fromHexString(
                    "93e02b6052719f607dacd3a088274f65596bd0d09920b61ab5da61bbdc7f5049334cf11213945d57e5ac7d055d042b7e024aa2b2f08f0a91260805272dc51051c6e47ad4fa403b02b4510b647ae3d1770bac0326a805bbefd48056c8c121bdb8"),
                Bytes.fromHexString(
                    "99aca9fb2f7760cecb892bf7262c176b334824f5727f680bba701a33e322cb6667531410dfc7c8e4321a3f0ea8af48cb1436638a2093123f046f0f504cc2a864825542873edbbc5d7ed17af125a4f2cf6433c6f4f61b81173726981dd989761d"),
                Bytes.fromHexString(
                    "88e2e982982bf8231e747e9dfcd14c05bd02623d1332734d2af26246c6869fb56ee6c994843f593178a040495ba61f4a083b0e18110b1d9f5224783d8f9a895e8ee744e87929430e9ba96bd29251cbf61240b256d1525600f3d562894d93d659"),
                Bytes.fromHexString(
                    "a2d33775e3d9e6af0d1b27d389e6c021a578e617a3d6627686db6288d4b3dffd7a847a00f7ef01828b7f42885b660e4204923402aca18fbae74ccd4e9c50dd8c2281b38dc09c022342ed1ac695d53f7081cb21f05fdfc0a3508c04759196fcd3"),
                Bytes.fromHexString(
                    "93e02b6052719f607dacd3a088274f65596bd0d09920b61ab5da61bbdc7f5049334cf11213945d57e5ac7d055d042b7e024aa2b2f08f0a91260805272dc51051c6e47ad4fa403b02b4510b647ae3d1770bac0326a805bbefd48056c8c121bdb8"),
                Bytes.fromHexString(
                    "99aca9fb2f7760cecb892bf7262c176b334824f5727f680bba701a33e322cb6667531410dfc7c8e4321a3f0ea8af48cb1436638a2093123f046f0f504cc2a864825542873edbbc5d7ed17af125a4f2cf6433c6f4f61b81173726981dd989761d"),
                Bytes.fromHexString(
                    "88e2e982982bf8231e747e9dfcd14c05bd02623d1332734d2af26246c6869fb56ee6c994843f593178a040495ba61f4a083b0e18110b1d9f5224783d8f9a895e8ee744e87929430e9ba96bd29251cbf61240b256d1525600f3d562894d93d659"),
                Bytes.fromHexString(
                    "a2d33775e3d9e6af0d1b27d389e6c021a578e617a3d6627686db6288d4b3dffd7a847a00f7ef01828b7f42885b660e4204923402aca18fbae74ccd4e9c50dd8c2281b38dc09c022342ed1ac695d53f7081cb21f05fdfc0a3508c04759196fcd3"),
                Bytes.fromHexString(
                    "93e02b6052719f607dacd3a088274f65596bd0d09920b61ab5da61bbdc7f5049334cf11213945d57e5ac7d055d042b7e024aa2b2f08f0a91260805272dc51051c6e47ad4fa403b02b4510b647ae3d1770bac0326a805bbefd48056c8c121bdb8"),
                Bytes.fromHexString(
                    "99aca9fb2f7760cecb892bf7262c176b334824f5727f680bba701a33e322cb6667531410dfc7c8e4321a3f0ea8af48cb1436638a2093123f046f0f504cc2a864825542873edbbc5d7ed17af125a4f2cf6433c6f4f61b81173726981dd989761d"),
                Bytes.fromHexString(
                    "88e2e982982bf8231e747e9dfcd14c05bd02623d1332734d2af26246c6869fb56ee6c994843f593178a040495ba61f4a083b0e18110b1d9f5224783d8f9a895e8ee744e87929430e9ba96bd29251cbf61240b256d1525600f3d562894d93d659"),
                Bytes.fromHexString(
                    "a2d33775e3d9e6af0d1b27d389e6c021a578e617a3d6627686db6288d4b3dffd7a847a00f7ef01828b7f42885b660e4204923402aca18fbae74ccd4e9c50dd8c2281b38dc09c022342ed1ac695d53f7081cb21f05fdfc0a3508c04759196fcd3"),
                Bytes.fromHexString(
                    "93e02b6052719f607dacd3a088274f65596bd0d09920b61ab5da61bbdc7f5049334cf11213945d57e5ac7d055d042b7e024aa2b2f08f0a91260805272dc51051c6e47ad4fa403b02b4510b647ae3d1770bac0326a805bbefd48056c8c121bdb8"),
                Bytes.fromHexString(
                    "99aca9fb2f7760cecb892bf7262c176b334824f5727f680bba701a33e322cb6667531410dfc7c8e4321a3f0ea8af48cb1436638a2093123f046f0f504cc2a864825542873edbbc5d7ed17af125a4f2cf6433c6f4f61b81173726981dd989761d"),
                Bytes.fromHexString(
                    "88e2e982982bf8231e747e9dfcd14c05bd02623d1332734d2af26246c6869fb56ee6c994843f593178a040495ba61f4a083b0e18110b1d9f5224783d8f9a895e8ee744e87929430e9ba96bd29251cbf61240b256d1525600f3d562894d93d659"),
                Bytes.fromHexString(
                    "a2d33775e3d9e6af0d1b27d389e6c021a578e617a3d6627686db6288d4b3dffd7a847a00f7ef01828b7f42885b660e4204923402aca18fbae74ccd4e9c50dd8c2281b38dc09c022342ed1ac695d53f7081cb21f05fdfc0a3508c04759196fcd3"),
                Bytes.fromHexString(
                    "93e02b6052719f607dacd3a088274f65596bd0d09920b61ab5da61bbdc7f5049334cf11213945d57e5ac7d055d042b7e024aa2b2f08f0a91260805272dc51051c6e47ad4fa403b02b4510b647ae3d1770bac0326a805bbefd48056c8c121bdb8"),
                Bytes.fromHexString(
                    "99aca9fb2f7760cecb892bf7262c176b334824f5727f680bba701a33e322cb6667531410dfc7c8e4321a3f0ea8af48cb1436638a2093123f046f0f504cc2a864825542873edbbc5d7ed17af125a4f2cf6433c6f4f61b81173726981dd989761d"),
                Bytes.fromHexString(
                    "88e2e982982bf8231e747e9dfcd14c05bd02623d1332734d2af26246c6869fb56ee6c994843f593178a040495ba61f4a083b0e18110b1d9f5224783d8f9a895e8ee744e87929430e9ba96bd29251cbf61240b256d1525600f3d562894d93d659"),
                Bytes.fromHexString(
                    "a2d33775e3d9e6af0d1b27d389e6c021a578e617a3d6627686db6288d4b3dffd7a847a00f7ef01828b7f42885b660e4204923402aca18fbae74ccd4e9c50dd8c2281b38dc09c022342ed1ac695d53f7081cb21f05fdfc0a3508c04759196fcd3"),
                Bytes.fromHexString(
                    "93e02b6052719f607dacd3a088274f65596bd0d09920b61ab5da61bbdc7f5049334cf11213945d57e5ac7d055d042b7e024aa2b2f08f0a91260805272dc51051c6e47ad4fa403b02b4510b647ae3d1770bac0326a805bbefd48056c8c121bdb8"),
                Bytes.fromHexString(
                    "99aca9fb2f7760cecb892bf7262c176b334824f5727f680bba701a33e322cb6667531410dfc7c8e4321a3f0ea8af48cb1436638a2093123f046f0f504cc2a864825542873edbbc5d7ed17af125a4f2cf6433c6f4f61b81173726981dd989761d"),
                Bytes.fromHexString(
                    "88e2e982982bf8231e747e9dfcd14c05bd02623d1332734d2af26246c6869fb56ee6c994843f593178a040495ba61f4a083b0e18110b1d9f5224783d8f9a895e8ee744e87929430e9ba96bd29251cbf61240b256d1525600f3d562894d93d659"),
                Bytes.fromHexString(
                    "a2d33775e3d9e6af0d1b27d389e6c021a578e617a3d6627686db6288d4b3dffd7a847a00f7ef01828b7f42885b660e4204923402aca18fbae74ccd4e9c50dd8c2281b38dc09c022342ed1ac695d53f7081cb21f05fdfc0a3508c04759196fcd3"),
                Bytes.fromHexString(
                    "93e02b6052719f607dacd3a088274f65596bd0d09920b61ab5da61bbdc7f5049334cf11213945d57e5ac7d055d042b7e024aa2b2f08f0a91260805272dc51051c6e47ad4fa403b02b4510b647ae3d1770bac0326a805bbefd48056c8c121bdb8"),
                Bytes.fromHexString(
                    "99aca9fb2f7760cecb892bf7262c176b334824f5727f680bba701a33e322cb6667531410dfc7c8e4321a3f0ea8af48cb1436638a2093123f046f0f504cc2a864825542873edbbc5d7ed17af125a4f2cf6433c6f4f61b81173726981dd989761d"),
                Bytes.fromHexString(
                    "88e2e982982bf8231e747e9dfcd14c05bd02623d1332734d2af26246c6869fb56ee6c994843f593178a040495ba61f4a083b0e18110b1d9f5224783d8f9a895e8ee744e87929430e9ba96bd29251cbf61240b256d1525600f3d562894d93d659"),
                Bytes.fromHexString(
                    "a2d33775e3d9e6af0d1b27d389e6c021a578e617a3d6627686db6288d4b3dffd7a847a00f7ef01828b7f42885b660e4204923402aca18fbae74ccd4e9c50dd8c2281b38dc09c022342ed1ac695d53f7081cb21f05fdfc0a3508c04759196fcd3"),
                Bytes.fromHexString(
                    "93e02b6052719f607dacd3a088274f65596bd0d09920b61ab5da61bbdc7f5049334cf11213945d57e5ac7d055d042b7e024aa2b2f08f0a91260805272dc51051c6e47ad4fa403b02b4510b647ae3d1770bac0326a805bbefd48056c8c121bdb8"),
                Bytes.fromHexString(
                    "99aca9fb2f7760cecb892bf7262c176b334824f5727f680bba701a33e322cb6667531410dfc7c8e4321a3f0ea8af48cb1436638a2093123f046f0f504cc2a864825542873edbbc5d7ed17af125a4f2cf6433c6f4f61b81173726981dd989761d"),
                Bytes.fromHexString(
                    "88e2e982982bf8231e747e9dfcd14c05bd02623d1332734d2af26246c6869fb56ee6c994843f593178a040495ba61f4a083b0e18110b1d9f5224783d8f9a895e8ee744e87929430e9ba96bd29251cbf61240b256d1525600f3d562894d93d659"),
                Bytes.fromHexString(
                    "a2d33775e3d9e6af0d1b27d389e6c021a578e617a3d6627686db6288d4b3dffd7a847a00f7ef01828b7f42885b660e4204923402aca18fbae74ccd4e9c50dd8c2281b38dc09c022342ed1ac695d53f7081cb21f05fdfc0a3508c04759196fcd3"),
                Bytes.fromHexString(
                    "93e02b6052719f607dacd3a088274f65596bd0d09920b61ab5da61bbdc7f5049334cf11213945d57e5ac7d055d042b7e024aa2b2f08f0a91260805272dc51051c6e47ad4fa403b02b4510b647ae3d1770bac0326a805bbefd48056c8c121bdb8"),
                Bytes.fromHexString(
                    "99aca9fb2f7760cecb892bf7262c176b334824f5727f680bba701a33e322cb6667531410dfc7c8e4321a3f0ea8af48cb1436638a2093123f046f0f504cc2a864825542873edbbc5d7ed17af125a4f2cf6433c6f4f61b81173726981dd989761d"),
                Bytes.fromHexString(
                    "88e2e982982bf8231e747e9dfcd14c05bd02623d1332734d2af26246c6869fb56ee6c994843f593178a040495ba61f4a083b0e18110b1d9f5224783d8f9a895e8ee744e87929430e9ba96bd29251cbf61240b256d1525600f3d562894d93d659"),
                Bytes.fromHexString(
                    "a2d33775e3d9e6af0d1b27d389e6c021a578e617a3d6627686db6288d4b3dffd7a847a00f7ef01828b7f42885b660e4204923402aca18fbae74ccd4e9c50dd8c2281b38dc09c022342ed1ac695d53f7081cb21f05fdfc0a3508c04759196fcd3"),
                Bytes.fromHexString(
                    "93e02b6052719f607dacd3a088274f65596bd0d09920b61ab5da61bbdc7f5049334cf11213945d57e5ac7d055d042b7e024aa2b2f08f0a91260805272dc51051c6e47ad4fa403b02b4510b647ae3d1770bac0326a805bbefd48056c8c121bdb8"),
                Bytes.fromHexString(
                    "99aca9fb2f7760cecb892bf7262c176b334824f5727f680bba701a33e322cb6667531410dfc7c8e4321a3f0ea8af48cb1436638a2093123f046f0f504cc2a864825542873edbbc5d7ed17af125a4f2cf6433c6f4f61b81173726981dd989761d"),
                Bytes.fromHexString(
                    "88e2e982982bf8231e747e9dfcd14c05bd02623d1332734d2af26246c6869fb56ee6c994843f593178a040495ba61f4a083b0e18110b1d9f5224783d8f9a895e8ee744e87929430e9ba96bd29251cbf61240b256d1525600f3d562894d93d659"),
                Bytes.fromHexString(
                    "a2d33775e3d9e6af0d1b27d389e6c021a578e617a3d6627686db6288d4b3dffd7a847a00f7ef01828b7f42885b660e4204923402aca18fbae74ccd4e9c50dd8c2281b38dc09c022342ed1ac695d53f7081cb21f05fdfc0a3508c04759196fcd3"),
                Bytes.fromHexString(
                    "93e02b6052719f607dacd3a088274f65596bd0d09920b61ab5da61bbdc7f5049334cf11213945d57e5ac7d055d042b7e024aa2b2f08f0a91260805272dc51051c6e47ad4fa403b02b4510b647ae3d1770bac0326a805bbefd48056c8c121bdb8"),
                Bytes.fromHexString(
                    "99aca9fb2f7760cecb892bf7262c176b334824f5727f680bba701a33e322cb6667531410dfc7c8e4321a3f0ea8af48cb1436638a2093123f046f0f504cc2a864825542873edbbc5d7ed17af125a4f2cf6433c6f4f61b81173726981dd989761d"),
                Bytes.fromHexString(
                    "88e2e982982bf8231e747e9dfcd14c05bd02623d1332734d2af26246c6869fb56ee6c994843f593178a040495ba61f4a083b0e18110b1d9f5224783d8f9a895e8ee744e87929430e9ba96bd29251cbf61240b256d1525600f3d562894d93d659"),
                Bytes.fromHexString(
                    "a2d33775e3d9e6af0d1b27d389e6c021a578e617a3d6627686db6288d4b3dffd7a847a00f7ef01828b7f42885b660e4204923402aca18fbae74ccd4e9c50dd8c2281b38dc09c022342ed1ac695d53f7081cb21f05fdfc0a3508c04759196fcd3"),
                Bytes.fromHexString(
                    "93e02b6052719f607dacd3a088274f65596bd0d09920b61ab5da61bbdc7f5049334cf11213945d57e5ac7d055d042b7e024aa2b2f08f0a91260805272dc51051c6e47ad4fa403b02b4510b647ae3d1770bac0326a805bbefd48056c8c121bdb8"),
                Bytes.fromHexString(
                    "99aca9fb2f7760cecb892bf7262c176b334824f5727f680bba701a33e322cb6667531410dfc7c8e4321a3f0ea8af48cb1436638a2093123f046f0f504cc2a864825542873edbbc5d7ed17af125a4f2cf6433c6f4f61b81173726981dd989761d"),
                Bytes.fromHexString(
                    "88e2e982982bf8231e747e9dfcd14c05bd02623d1332734d2af26246c6869fb56ee6c994843f593178a040495ba61f4a083b0e18110b1d9f5224783d8f9a895e8ee744e87929430e9ba96bd29251cbf61240b256d1525600f3d562894d93d659"),
                Bytes.fromHexString(
                    "a2d33775e3d9e6af0d1b27d389e6c021a578e617a3d6627686db6288d4b3dffd7a847a00f7ef01828b7f42885b660e4204923402aca18fbae74ccd4e9c50dd8c2281b38dc09c022342ed1ac695d53f7081cb21f05fdfc0a3508c04759196fcd3"),
                Bytes.fromHexString(
                    "93e02b6052719f607dacd3a088274f65596bd0d09920b61ab5da61bbdc7f5049334cf11213945d57e5ac7d055d042b7e024aa2b2f08f0a91260805272dc51051c6e47ad4fa403b02b4510b647ae3d1770bac0326a805bbefd48056c8c121bdb8"),
                Bytes.fromHexString(
                    "99aca9fb2f7760cecb892bf7262c176b334824f5727f680bba701a33e322cb6667531410dfc7c8e4321a3f0ea8af48cb1436638a2093123f046f0f504cc2a864825542873edbbc5d7ed17af125a4f2cf6433c6f4f61b81173726981dd989761d"),
                Bytes.fromHexString(
                    "88e2e982982bf8231e747e9dfcd14c05bd02623d1332734d2af26246c6869fb56ee6c994843f593178a040495ba61f4a083b0e18110b1d9f5224783d8f9a895e8ee744e87929430e9ba96bd29251cbf61240b256d1525600f3d562894d93d659"),
                Bytes.fromHexString(
                    "a2d33775e3d9e6af0d1b27d389e6c021a578e617a3d6627686db6288d4b3dffd7a847a00f7ef01828b7f42885b660e4204923402aca18fbae74ccd4e9c50dd8c2281b38dc09c022342ed1ac695d53f7081cb21f05fdfc0a3508c04759196fcd3"),
                Bytes.fromHexString(
                    "93e02b6052719f607dacd3a088274f65596bd0d09920b61ab5da61bbdc7f5049334cf11213945d57e5ac7d055d042b7e024aa2b2f08f0a91260805272dc51051c6e47ad4fa403b02b4510b647ae3d1770bac0326a805bbefd48056c8c121bdb8"))));
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
        .map(fieldElement -> Bytes.wrap(fieldElement.toArray(ByteOrder.LITTLE_ENDIAN)))
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
