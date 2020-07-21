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

package tech.pegasys.teku.bls.impl.mikuli;

import org.apache.milagro.amcl.BLS381.FP12;
import org.apache.milagro.amcl.BLS381.PAIR;

public final class AtePairing {

  /**
   * Calculate the Ate pairing of points p and q.
   *
   * @param p the point in Group1, not null
   * @param q the point in Group2, not null
   * @return GTPoint
   */
  public static GTPoint pair(G1Point p, G2Point q) {
    FP12 e = PAIR.ate(q.ecp2Point(), p.ecpPoint());
    return new GTPoint(PAIR.fexp(e));
  }

  /**
   * Calculate the Ate pairing of points p and q but omits the final exponentiation ({@link
   * #fexp(GTPoint)}) <code>
   *   pair() = fexp(pairNoExp())
   * </code>
   *
   * @param p the point in Group1, not null
   * @param q the point in Group2, not null
   * @return GTPoint
   */
  public static GTPoint pairNoExp(G1Point p, G2Point q) {
    FP12 e = PAIR.ate(q.ecp2Point(), p.ecpPoint());
    return new GTPoint(e);
  }

  /**
   * Calculates the product of pairings while performing the final exponentiation only once. This
   * ought to be more efficient.
   *
   * <p>If pair(-p, q) == pair(r, s) then the result of this is "one" in GT.
   *
   * @param p a point in Group1, not null
   * @param q a point in Group2, not null
   * @param r a point in Group1, not null
   * @param s a point in Group2, not null
   * @return The result of the double pairing
   */
  static GTPoint pair2(G1Point p, G2Point q, G1Point r, G2Point s) {
    FP12 e = PAIR.ate2(q.ecp2Point(), p.ecpPoint(), s.ecp2Point(), r.ecpPoint());
    return new GTPoint(PAIR.fexp(e));
  }

  /**
   * The same as {@link #pair2(G1Point, G2Point, G1Point, G2Point)} but omits the final
   * exponentiation ({@link #fexp(GTPoint)}) <code>
   *   pair2() = fexp(pair2NoExp())
   * </code>
   */
  static GTPoint pair2NoExp(G1Point p, G2Point q, G1Point r, G2Point s) {
    FP12 e = PAIR.ate2(q.ecp2Point(), p.ecpPoint(), s.ecp2Point(), r.ecpPoint());
    return new GTPoint(e);
  }

  /** Calculates Final exponent */
  static GTPoint fexp(GTPoint point) {
    return new GTPoint(PAIR.fexp(point.getPoint()));
  }
}
