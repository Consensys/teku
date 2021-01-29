/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.bls.impl.mikuli;

import java.util.Random;
import org.apache.milagro.amcl.BLS381.BIG;
import org.apache.milagro.amcl.BLS381.ECP;

public class MikuliTestUtil {
  /**
   * Generate a random point on the curve from a seed value. The same seed value gives the same
   * point.
   *
   * @param seed the seed value
   * @return a random point on the curve.
   */
  public static G1Point randomG1Point(long seed) {
    return randomG1Point(new Random(seed));
  }

  static G1Point randomG1Point(Random rng) {
    ECP point;
    byte[] xBytes = new byte[48];

    // Repeatedly choose random X coords until one is on the curve. This generally takes only a
    // couple of attempts.
    do {
      rng.nextBytes(xBytes);
      point = new ECP(BIG.fromBytes(xBytes));
    } while (point.is_infinity());

    // Multiply by the cofactor to ensure that we end up on G1
    return new G1Point(G1Point.scaleWithCofactor(point));
  }
}
