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

import java.math.BigInteger;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public final class KZGTest {
  private static final String MAINNET_TRUSTED_SETUP_TEST = "trusted_setups/test_mainnet.txt";
  private static final BigInteger BLS_MODULUS =
      new BigInteger(
          "52435875175126190479447740508185965837690552500527637822603658699938581184513");
  private static final int FIELD_ELEMENTS_PER_BLOB = 4096;
  private static final int RANDOM_SEED = 5566;
  private static final Random RND = new Random(RANDOM_SEED);

  @BeforeEach
  public void setup() {
    KZG.resetTrustedSetup();
  }

  @Test
  public void testKzgDoubleResetDoNotThrow() {
    KZG.resetTrustedSetup();
  }

  @Test
  public void testKzgLoadTrustedSetup() {
    final String resourcePath =
        KZGTest.class.getResource(MAINNET_TRUSTED_SETUP_TEST).toExternalForm();
    KZG.loadTrustedSetup(resourcePath);
    KZG.resetTrustedSetup();
  }

  @Test
  public void testVerifyKzgProof() {
    final String resourcePath =
        KZGTest.class.getResource(MAINNET_TRUSTED_SETUP_TEST).toExternalForm();
    KZG.loadTrustedSetup(resourcePath);
    final int blobsNumber = 4;
    final List<Bytes> blobs =
        IntStream.range(0, blobsNumber)
            .mapToObj(__ -> getSampleBlob())
            .collect(Collectors.toList());
    final List<KZGCommitment> kzgCommitments =
        blobs.stream().map(KZG::blobToKzgCommitment).collect(Collectors.toList());
    final KZGProof kzgProof = KZG.computeAggregateKzgProof(blobs);
    assertThat(KZG.verifyAggregateKzgProof(blobs, kzgCommitments, kzgProof)).isTrue();
    assertThat(
            KZG.verifyAggregateKzgProof(
                blobs.subList(0, 2), kzgCommitments.subList(0, 2), kzgProof))
        .isFalse();
    final KZGProof invalidProof = KZG.computeAggregateKzgProof(blobs.subList(0, 2));
    assertThat(KZG.verifyAggregateKzgProof(blobs, kzgCommitments, invalidProof)).isFalse();
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
        .get();
  }
}
