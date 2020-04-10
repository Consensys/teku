/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.artemis.benchmarks;

import static tech.pegasys.artemis.bls.hashToG2.HashToCurve.hashToG2;

import com.google.common.collect.Streams;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.milagro.amcl.BLS381.ECP;
import org.apache.milagro.amcl.BLS381.ECP2;
import org.apache.milagro.amcl.BLS381.FP12;
import org.apache.milagro.amcl.BLS381.PAIR;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.bls.BLS;
import tech.pegasys.artemis.bls.BLSKeyPair;
import tech.pegasys.artemis.bls.BLSSignature;
import tech.pegasys.artemis.bls.mikuli.G1Point;
import tech.pegasys.artemis.bls.mikuli.G2Point;

public class BLSTest {

  int sigCnt = 128;
  static final G1Point g1Generator = new G1Point(ECP.generator());

  List<BLSKeyPair> keyPairs = IntStream.range(0, sigCnt).mapToObj(BLSKeyPair::random)
      .collect(Collectors.toList());
  List<Bytes32> messages =
      Stream.generate(Bytes32::random).limit(sigCnt).collect(Collectors.toList());
  List<BLSSignature> signatures = Streams.zip(
      keyPairs.stream(),
      messages.stream(),
      (keyPair, msg) -> BLS.sign(keyPair.getSecretKey(), msg))
      .collect(Collectors.toList());

  public BLSTest() {
//    Constants.setConstants("mainnet");
  }

  @Test
  public void verifySignatureSimple() {
    for (int i = 0; i < sigCnt; i++) {
      boolean res = BLS
          .verify(keyPairs.get(i).getPublicKey(), messages.get(i), signatures.get(i));
      if (!res) throw new IllegalStateException();
    }
  }

  @Test
  public void verifySignatureBatched() {
    FP12 ate1 = PAIR.ate(signatures.get(0).getSignature().g2Point().point, g1Generator.point);
    FP12 ate2 = PAIR.ate(hashToG2(messages.get(0)), keyPairs.get(0).getPublicKey().getPublicKey().g1Point().point);
    assert ate1.equals(ate2);

    G2Point sigSum = null;
    for (int i = 0; i < sigCnt; i++) {
      G2Point sigPoint = signatures.get(i).getSignature().g2Point();
      sigSum = sigSum == null ? sigPoint : sigSum.add(sigPoint);
    }

    FP12 atesProduct = null;
    for (int i = 0; i < sigCnt; i++) {
      ECP pubKeyPoint = keyPairs.get(i).getPublicKey().getPublicKey().g1Point().point;
      ECP2 msgPoint = hashToG2(messages.get(i));
      FP12 ate = PAIR.ate(msgPoint, pubKeyPoint);
      if (atesProduct == null) {
        atesProduct = ate;
      } else {
        atesProduct.mul(ate);
      }
    }

    FP12 ateSig = PAIR.ate(sigSum.point, g1Generator.point);
    boolean res = ateSig.equals(atesProduct);
    if (!res) {
      throw new IllegalStateException();
    }
  }
}
