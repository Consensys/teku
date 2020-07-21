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

package tech.pegasys.teku.bls.impl.mikuli;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static tech.pegasys.teku.bls.impl.mikuli.Util.calculateYFlag;

import org.apache.milagro.amcl.BLS381.BIG;
import org.junit.jupiter.api.Test;

class UtilTest {

  @Test
  void qDiv2Test() {
    // pDiv2 = P / 2 + 1 (plus one since P is odd)
    BIG a = new BIG(Util.pDiv2);
    a.imul(2);
    a.norm();
    a.add(new BIG(1));
    a.sub(Util.P);
    assertTrue(a.iszilch());
  }

  @Test
  void yFlagTest0() {
    BIG a = Util.pDiv2;
    assertFalse(calculateYFlag(a));
  }

  @Test
  void yFlagTest1() {
    BIG a = Util.pDiv2.minus(new BIG(1));
    assertFalse(calculateYFlag(a));
  }

  @Test
  void yFlagTest2() {
    BIG a = Util.pDiv2.plus(new BIG(1));
    assertTrue(calculateYFlag(a));
  }
}
