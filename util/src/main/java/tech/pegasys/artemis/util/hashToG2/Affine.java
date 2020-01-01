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

package tech.pegasys.artemis.util.hashToG2;

import org.apache.milagro.amcl.BLS381.ECP2;

public class Affine {

  /**
   * Convert from Jacobian to Milagro ECP2 by applying the affine transformation.
   *
   * @return the ECP2 point corresponding to this Jacobian point
   */
  public static ECP2 jacobianToAffine(JacobianPoint p) {
    JacobianPoint q = p.toAffine();
    return new ECP2(q.getX().getFp2(), q.getY().getFp2());
  }

  /**
   * Convert from Milagro ECP2 to Jacobian by setting the z-coord to 1.
   *
   * <p>The getters for the ECP2 point ensure that the affine transformation is applied before
   * conversion.
   *
   * @return the Jacobian point corresponding to this ECP2 point
   */
  public static JacobianPoint affineToJacobian(ECP2 p) {
    return new JacobianPoint(
        new FP2Immutable(p.getX()), new FP2Immutable(p.getY()), FP2Immutable.ONE);
  }
}
