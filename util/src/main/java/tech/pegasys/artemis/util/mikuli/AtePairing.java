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

package tech.pegasys.artemis.util.mikuli;

import org.apache.milagro.amcl.BLS381.FP12;
import org.apache.milagro.amcl.BLS381.PAIR;

/** Function that maps 2 points on an elliptic curve to a number. */
final class AtePairing {

  /**
   * @param p1 the point in Group1, not null
   * @param p2 the point in Group2, not null
   * @return GTPoint
   */
  static GTPoint pair(G1Point p1, G2Point p2) {
    FP12 e = PAIR.ate(p2.ecp2Point(), p1.ecpPoint());
    return new GTPoint(PAIR.fexp(e));
  }
}
