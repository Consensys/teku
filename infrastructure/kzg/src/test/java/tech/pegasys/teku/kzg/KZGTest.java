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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.Bytes48;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.kzg.ckzg4844.CKZG4844;

public final class KZGTest {

  private static final String MAINNET_TRUSTED_SETUP_TEST = "trusted_setups/test_mainnet.txt";
  private static final BigInteger BLS_MODULUS =
      new BigInteger(
          "52435875175126190479447740508185965837690552500527637822603658699938581184513");
  private static final int FIELD_ELEMENTS_PER_BLOB = 4096;
  private static final int RANDOM_SEED = 5566;
  private static final Random RND = new Random(RANDOM_SEED);

  private final KZG kzg = CKZG4844.createInstance(FIELD_ELEMENTS_PER_BLOB);

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
    final Bytes48 emptyCommitment = Bytes48.rightPad(Bytes.fromHexString("c0"));
    final KZGCommitment kzgCommitment = new KZGCommitment(emptyCommitment);
    final KZGProof kzgProof = new KZGProof(emptyCommitment);
    assertThrows(
        KZGException.class,
        () ->
            kzg.verifyAggregateKzgProof(
                Collections.emptyList(), Collections.emptyList(), kzgProof));
    assertThrows(KZGException.class, () -> kzg.blobToKzgCommitment(Bytes.EMPTY));
    assertThrows(KZGException.class, () -> kzg.computeAggregateKzgProof(Collections.emptyList()));
    assertThrows(
        KZGException.class,
        () -> kzg.verifyKzgProof(kzgCommitment, Bytes32.ZERO, Bytes32.ZERO, kzgProof));
  }

  @Test
  public void testVerifyKzgProof() {
    loadTrustedSetup();
    final int blobsNumber = 4;
    final List<Bytes> blobs =
        IntStream.range(0, blobsNumber)
            .mapToObj(__ -> getSampleBlob())
            .collect(Collectors.toList());
    final List<KZGCommitment> kzgCommitments =
        blobs.stream().map(kzg::blobToKzgCommitment).collect(Collectors.toList());
    final KZGProof kzgProof = kzg.computeAggregateKzgProof(blobs);
    assertThat(kzg.verifyAggregateKzgProof(blobs, kzgCommitments, kzgProof)).isTrue();
    assertThat(
            kzg.verifyAggregateKzgProof(
                blobs.subList(0, 2), kzgCommitments.subList(0, 2), kzgProof))
        .isFalse();
    final KZGProof invalidProof = kzg.computeAggregateKzgProof(blobs.subList(0, 2));
    assertThat(kzg.verifyAggregateKzgProof(blobs, kzgCommitments, invalidProof)).isFalse();
  }

  @Test
  public void testVerifyPointEvaluationPrecompile() {
    loadTrustedSetup();

    final Bytes48 emptyCommitment = Bytes48.rightPad(Bytes.fromHexString("c0"));
    final KZGCommitment kzgCommitment = new KZGCommitment(emptyCommitment);
    final KZGProof kzgProof = new KZGProof(emptyCommitment);

    assertThat(kzg.verifyKzgProof(kzgCommitment, Bytes32.ZERO, Bytes32.ZERO, kzgProof)).isTrue();
    assertThat(kzg.computeAggregateKzgProof(Collections.emptyList())).isEqualTo(kzgProof);
  }

  private void loadTrustedSetup() {
    final String trustedSetup =
        KZGTest.class.getResource(MAINNET_TRUSTED_SETUP_TEST).toExternalForm();
    kzg.loadTrustedSetup(trustedSetup);
  }

  private BigInteger randomBigIntegerInModulus(final BigInteger modulus, final Random rnd) {
    while (true) {
      final BigInteger attempt = new BigInteger(modulus.bitLength(), rnd);
      if (attempt.compareTo(modulus) < 0) {
        return attempt;
      }
    }
  }

  private Bytes getSampleBlob() {
    return IntStream.range(0, FIELD_ELEMENTS_PER_BLOB)
        .mapToObj(__ -> randomBigIntegerInModulus(BLS_MODULUS, RND))
        .map(bi -> (Bytes) UInt256.valueOf(bi).toBytes())
        .reduce(Bytes::wrap)
        .orElse(Bytes.EMPTY);
  }
}
