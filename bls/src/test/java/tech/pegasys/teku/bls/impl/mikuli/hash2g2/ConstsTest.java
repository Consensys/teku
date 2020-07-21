/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.bls.impl.mikuli.hash2g2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static tech.pegasys.teku.bls.impl.mikuli.hash2g2.Consts.k_cx;
import static tech.pegasys.teku.bls.impl.mikuli.hash2g2.Consts.k_cx_abs;
import static tech.pegasys.teku.bls.impl.mikuli.hash2g2.Consts.k_cy;
import static tech.pegasys.teku.bls.impl.mikuli.hash2g2.Consts.k_qi_x;
import static tech.pegasys.teku.bls.impl.mikuli.hash2g2.FP2Immutable.ONE;

import org.apache.milagro.amcl.BLS381.FP;
import org.junit.jupiter.api.Test;

class ConstsTest {

  @Test
  void rootsOfUnityTest() {
    for (FP2Immutable root : Consts.ROOTS_OF_UNITY) {
      assertEquals(ONE, root.sqr().sqr().sqr().reduce());
    }
  }

  @Test
  void psiConstsTestCx() {
    FP tmp = abs(k_cx);
    tmp.sub(k_cx_abs);
    assertTrue(tmp.iszilch());
  }

  @Test
  void psiConstsTestCy() {
    FP tmp = abs(k_cy);
    tmp.sub(new FP(-1));
    assertTrue(tmp.iszilch());
  }

  @Test
  void psiConstsTestQix() {
    FP tmp = new FP(k_qi_x);
    tmp.sqr();
    tmp.sub(k_cx_abs);
    assertTrue(tmp.iszilch());
  }

  // The magnitude of a complex number
  private FP abs(FP2Immutable a) {
    FP x = new FP(a.getFp2().getA());
    FP y = new FP(a.getFp2().getB());
    x.sqr();
    y.sqr();
    x.add(y);
    return x;
  }
}
