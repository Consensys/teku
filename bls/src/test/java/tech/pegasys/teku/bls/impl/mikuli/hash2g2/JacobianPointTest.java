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

package tech.pegasys.teku.bls.impl.mikuli.hash2g2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static tech.pegasys.teku.bls.impl.mikuli.hash2g2.Helper.isOnCurve;
import static tech.pegasys.teku.bls.impl.mikuli.hash2g2.JacobianPoint.INFINITY;
import static tech.pegasys.teku.bls.impl.mikuli.hash2g2.Util.bigFromHex;

import org.apache.milagro.amcl.BLS381.BIG;
import org.apache.milagro.amcl.BLS381.ECP2;
import org.apache.milagro.amcl.BLS381.FP2;
import org.junit.jupiter.api.Test;

class JacobianPointTest {

  @Test
  void infinityTest() {
    assertTrue(INFINITY.isInfinity());
  }

  @Test
  void jacobianPointAddTest() {
    JacobianPoint a =
        new JacobianPoint(
            new FP2Immutable(new BIG(1), new BIG(2)),
            new FP2Immutable(new BIG(3), new BIG(4)),
            new FP2Immutable(new BIG(5), new BIG(6)));
    JacobianPoint b =
        new JacobianPoint(
            new FP2Immutable(new BIG(7), new BIG(8)),
            new FP2Immutable(new BIG(9), new BIG(10)),
            new FP2Immutable(new BIG(11), new BIG(12)));
    JacobianPoint expected =
        new JacobianPoint(
            new FP2Immutable(new BIG(0x2c8da9c0), new BIG(0x1baa7000)),
            new FP2Immutable(
                "0x1a0111ea397fe69a4b1ba7b6434bacd764774b84f38512bf6730d2a0f6b0f6241eabfffeb153ffffb9fef0223dd9282b",
                "0x1a0111ea397fe69a4b1ba7b6434bacd764774b84f38512bf6730d2a0f6b0f6241eabfffeb153ffffb9feee40646bef2b"),
            new FP2Immutable(
                "0x1a0111ea397fe69a4b1ba7b6434bacd764774b84f38512bf6730d2a0f6b0f6241eabfffeb153ffffb9feffffffff3b3f",
                "0x1a0111ea397fe69a4b1ba7b6434bacd764774b84f38512bf6730d2a0f6b0f6241eabfffeb153ffffb9feffffffff959f"));
    assertEquals(expected, a.add(b));
  }

  @Test
  void jacobianPointDblTest() {
    JacobianPoint a =
        new JacobianPoint(
            new FP2Immutable(new BIG(1), new BIG(2)),
            new FP2Immutable(new BIG(3), new BIG(4)),
            new FP2Immutable(new BIG(5), new BIG(6)));
    JacobianPoint expected =
        new JacobianPoint(
            new FP2Immutable(
                new BIG(0x0179),
                bigFromHex(
                    "0x1a0111ea397fe69a4b1ba7b6434bacd764774b84f38512bf6730d2a0f6b0f6241eabfffeb153ffffb9feffffffffa983")),
            new FP2Immutable(
                new BIG(0x15b5),
                bigFromHex(
                    "0x1a0111ea397fe69a4b1ba7b6434bacd764774b84f38512bf6730d2a0f6b0f6241eabfffeb153ffffb9feffffffff8d5f")),
            new FP2Immutable(
                bigFromHex(
                    "0x1a0111ea397fe69a4b1ba7b6434bacd764774b84f38512bf6730d2a0f6b0f6241eabfffeb153ffffb9feffffffffaa99"),
                new BIG(0x4c)));
    assertEquals(expected, a.dbl());
  }

  @Test
  void negTest() {
    JacobianPoint a =
        new JacobianPoint(
            new FP2Immutable(new BIG(1), new BIG(2)),
            new FP2Immutable(new BIG(3), new BIG(4)),
            new FP2Immutable(new BIG(5), new BIG(6)));
    // Elliptic curve point arithmetic - adding a point to its negative results in the point at
    // infinity
    assertTrue(a.add(a.neg()).isInfinity());
  }

  @Test
  void dblsTest() {
    JacobianPoint a =
        new JacobianPoint(
            new FP2Immutable(new BIG(1), new BIG(2)),
            new FP2Immutable(new BIG(3), new BIG(4)),
            new FP2Immutable(new BIG(5), new BIG(6)));
    assertEquals(a, a.dbls(0));
    assertEquals(a.dbl().dbl().dbl(), a.dbls(3));
  }

  @Test
  void toAffineTest() {
    JacobianPoint a =
        new JacobianPoint(
            new FP2Immutable(
                "0x0a9dd1e7e9f8c3a63ced70517af4fa36162daa78223598adc3188c32e2895c4ba5bc931ffe0ace2abeab99445a37c762",
                "0x1070d96414de33b6544529eaa3f34d62715470ff5562cd38795e13df55994fa73a40d019260fb58f13f3bfc96290e206"),
            new FP2Immutable(
                "0x0527a7f5355ae68984f0e5b6b6f0196b5f12b2c9c654f23775f6185153431295261e2be3fcb14b9ac7eae7af384fae09",
                "0x0020abd890deaba3bc09c77e55aec6bb4e7ddbcf74398fe36f2163930f9fbea5fc35c514ffdce44ccb1ae1c2dd6a444c"),
            new FP2Immutable(
                "0x0f2fd428b0be2b652deb7d922412bc082171420e723c93c43d1efe6b22acb0f7fff928f441fd09d3aab9c97acec9f579",
                "0x117318fc47ee37c48a854cac7b5e83a5f3a10693e200aa696d99dbd3d8721e081bf662149a67b81aa71f08c4f030959d"));
    JacobianPoint expected =
        new JacobianPoint(
            new FP2Immutable(
                "0x122c60c87bacfb7fd9fb22fd7fec812983971c0bca1abf7fa05d04980a39c1b605c7b3c08050b1b89ddff12338b644ae",
                "0x17704a556f8634edb35e52dbf560bb3a39e35aef19172232f34e4dfa008106e5365bd8a19eb06ee4e41950e65b478cd7"),
            new FP2Immutable(
                "0x0374fe17a6875101c7172d962c383f065326c8e8c3f4971456224efe18619f404d7cfab6b3d7df460f44f55f72176eb2",
                "0x0d15e558d16fe21b72637e9f0d53684cd52fe9dc41dc3dba522370048239b9b687003c8d3e928c2d17ed7451f9c91676"),
            FP2Immutable.ONE);
    assertTrue(isOnCurve(a));
    assertEquals(expected.toString(), a.toAffine().toString());
  }

  @Test
  void equalsTest() {
    // Adding points in a different order results in different representations in Jacobian space.
    // Nonetheless, the points are the same since elliptic curve addition is commutative in affine
    // space.
    JacobianPoint a =
        new JacobianPoint(
            new FP2Immutable(
                "0x0a9dd1e7e9f8c3a63ced70517af4fa36162daa78223598adc3188c32e2895c4ba5bc931ffe0ace2abeab99445a37c762",
                "0x1070d96414de33b6544529eaa3f34d62715470ff5562cd38795e13df55994fa73a40d019260fb58f13f3bfc96290e206"),
            new FP2Immutable(
                "0x0527a7f5355ae68984f0e5b6b6f0196b5f12b2c9c654f23775f6185153431295261e2be3fcb14b9ac7eae7af384fae09",
                "0x0020abd890deaba3bc09c77e55aec6bb4e7ddbcf74398fe36f2163930f9fbea5fc35c514ffdce44ccb1ae1c2dd6a444c"),
            new FP2Immutable(
                "0x0f2fd428b0be2b652deb7d922412bc082171420e723c93c43d1efe6b22acb0f7fff928f441fd09d3aab9c97acec9f579",
                "0x117318fc47ee37c48a854cac7b5e83a5f3a10693e200aa696d99dbd3d8721e081bf662149a67b81aa71f08c4f030959d"));
    JacobianPoint b =
        new JacobianPoint(
            new FP2Immutable(
                "0x0c8977fab5175ac2f09e5f39e29d016f11c094ef10f237d2a5e23f482d0bfb4466688527cd31685bfe481725c31462cc",
                "0x0b305838069012861bb63501841c91bd5bc7e1359d44cd196681fb14c03e544c22205bced326d490eb886aaa3ed52918"),
            new FP2Immutable(
                "0x172cf997b3501882861c07e852fadbf5753eb8a3e1d2ce375e6aed07cf9c1b5ff1cbf1124c6e3b0cf4607c683eafd1a4",
                "0x0d9dacf241a753d55cff6d45b568b716a2ad68ba29d23f92dea6e7cf6ed54e96cdac4a2b95213f93439b946ebc63349c"),
            new FP2Immutable(
                "0x05594bb289f0ebfd8fa3f020c6e1eaf4c49b97d8ccaf3470a3a02da4b3e7104778105bd6c7e0caf97206c77a8b501d4d",
                "0x0625151f905fad40eb0e2b9b0a46d9afe531256c6d5e39897a27d94700f037a761a741d11275180bd18e620289e02a16"));
    assertTrue(isOnCurve(a));
    assertTrue(isOnCurve(b));
    assertNotEquals(a.add(b).getZ(), b.add(a).getZ());
    assertEquals(a.add(b), b.add(a));
  }

  @Test
  void matchesMilagro() {
    // This is primarily intended as a test of the roundtrip from ECP2 to JacobianPoint and back
    // again, but is also fun.

    // Create a Milagro ECP2 point on the curve
    ECP2 pM = new ECP2(new FP2(2));
    assertFalse(pM.is_infinity());

    // Create the equivalent Jacobian point
    JacobianPoint pJ = new JacobianPoint(pM);

    // Do some operations on the ECP2 point (multiply by 5)
    ECP2 qM = new ECP2(pM);
    qM.dbl();
    qM.dbl();
    pM.add(qM);
    pM.affine();

    // Do the same operations on the JacobianPoint
    pJ = pJ.dbl().dbl().add(pJ);

    // Convert the Jacobian point to an ECP2 point and compare
    // NB: ECP2 equals() method is broken somehow, so workaround with toString()
    assertEquals(pM.toString(), pJ.toECP2().toString());
  }
}
