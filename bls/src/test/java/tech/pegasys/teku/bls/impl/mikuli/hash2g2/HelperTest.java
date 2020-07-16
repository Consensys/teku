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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static tech.pegasys.teku.bls.impl.mikuli.hash2g2.Chains.h2Chain;
import static tech.pegasys.teku.bls.impl.mikuli.hash2g2.Chains.mxChain;
import static tech.pegasys.teku.bls.impl.mikuli.hash2g2.Helper.clearH2;
import static tech.pegasys.teku.bls.impl.mikuli.hash2g2.Helper.isInG2;
import static tech.pegasys.teku.bls.impl.mikuli.hash2g2.Helper.isOnCurve;
import static tech.pegasys.teku.bls.impl.mikuli.hash2g2.Helper.mapG2ToInfinity;
import static tech.pegasys.teku.bls.impl.mikuli.hash2g2.Helper.psi;
import static tech.pegasys.teku.bls.impl.mikuli.hash2g2.Helper.psi2;

import org.apache.milagro.amcl.BLS381.ECP2;
import org.apache.milagro.amcl.BLS381.FP;
import org.junit.jupiter.api.Test;

class HelperTest {

  /*
   * Most of these tests use data generated from the reference implementation at
   * https://github.com/algorand/bls_sigs_ref/tree/master/python-impl
   */

  @Test
  void isOnCurveTest() {
    JacobianPoint p =
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

    assertTrue(isOnCurve(p));
  }

  @Test
  void isNotOnCurve() {
    JacobianPoint p =
        new JacobianPoint(
            new FP2Immutable(new FP(1), new FP(2)),
            new FP2Immutable(new FP(3), new FP(4)),
            new FP2Immutable(new FP(5), new FP(6)));

    assertFalse(isOnCurve(p));
  }

  @Test
  void clearH2Test() {
    // Check that the implementation of clear_h2() matches a known correct value for an arbitrary
    // point
    JacobianPoint a =
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
    JacobianPoint expected =
        new JacobianPoint(
            new FP2Immutable(
                "0x16e8fe7af2c3366b444bf79673f540016ae13df03367f409e6e218b30db675f7186ab56809583f33e672edf61f812627",
                "0x03920e2e7294fcd8a40dd98f1252061d72ec9f82b16732a2f573b509c53271fcef2eab796d58ba443def766269085fb4"),
            new FP2Immutable(
                "0x033fe28c591de8a46d2bbe8cd381c0b6a13bae7e0a8ca6a87c6a727c9cd52dbf7b882a4bba9123ee6ee480e34368ba1f",
                "0x050a1ef21da10a01b75d187a5a6931c7e4c33794d7dc20d2c619ce00f9bfb0380a40d3cff0101ffac993e742798f9fff"),
            new FP2Immutable(
                "0x0f008a6b5637c0f7e5957c3c56ba616c5477f1dce08d6805f42dc495faed05ad046304ec7e2c7229694b82f6c886c6f8",
                "0x00a0ede04aa5881555e0dc51a8db295b393cb349ea7be5547829b2f102191b2d06118e71a8a756db2316cf15a8378b72"));
    assertEquals(expected, clearH2(a));
  }

  @Test
  void clearH2TestCompare() {
    // Check that the implementation of clear_h2() matches the slower method for an arbitrary point
    JacobianPoint a =
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
    assertEquals(clearH2Slow(a), clearH2(a));
  }

  @Test
  void G2MapToInfinityTest() {
    // The generator point of G2 is certainly in G2
    JacobianPoint g2Generator = new JacobianPoint(ECP2.generator());
    assertTrue(mapG2ToInfinity(g2Generator).isInfinity());
  }

  @Test
  void pointIsInG2() {
    JacobianPoint inG2 =
        new JacobianPoint(
            new FP2Immutable(
                "0x16e8fe7af2c3366b444bf79673f540016ae13df03367f409e6e218b30db675f7186ab56809583f33e672edf61f812627",
                "0x03920e2e7294fcd8a40dd98f1252061d72ec9f82b16732a2f573b509c53271fcef2eab796d58ba443def766269085fb4"),
            new FP2Immutable(
                "0x033fe28c591de8a46d2bbe8cd381c0b6a13bae7e0a8ca6a87c6a727c9cd52dbf7b882a4bba9123ee6ee480e34368ba1f",
                "0x050a1ef21da10a01b75d187a5a6931c7e4c33794d7dc20d2c619ce00f9bfb0380a40d3cff0101ffac993e742798f9fff"),
            new FP2Immutable(
                "0x0f008a6b5637c0f7e5957c3c56ba616c5477f1dce08d6805f42dc495faed05ad046304ec7e2c7229694b82f6c886c6f8",
                "0x00a0ede04aa5881555e0dc51a8db295b393cb349ea7be5547829b2f102191b2d06118e71a8a756db2316cf15a8378b72"));
    assertTrue(isInG2(inG2));
  }

  @Test
  void pointIsNotInG2() {
    JacobianPoint notInG2 =
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
    assertFalse(isInG2(notInG2));
  }

  @Test
  void psi2EqualsPsiPsi() {
    // A point on the curve, not necessarily in G2
    JacobianPoint a =
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
    assertEquals(psi(psi(a)), psi2(a));
  }

  /**
   * Cofactor clearing - slow version: multiply by h_eff.
   *
   * <p>This is compatible with the version given in section 4.1 of Budroni and Pintore, "Efficient
   * hash maps to G2 on BLS curves," ePrint 2017/419 https://eprint.iacr.org/2017/419 It is
   * equivalent to multiplying by h_eff from
   * https://datatracker.ietf.org/doc/html/draft-irtf-cfrg-hash-to-curve-07#section-8.8.2
   *
   * @param p the point to be transformed to the G2 group
   * @return a corresponding point in the G2 group
   */
  private static JacobianPoint clearH2Slow(JacobianPoint p) {
    JacobianPoint work, work3;

    // h2
    work = h2Chain(p);
    // 3 * h2
    work3 = work.add(work.dbl());
    // 3 * z * h2
    work = mxChain(work3);
    // 3 * z^2 * h2
    work = mxChain(work);
    // 3 * z^2 * h2 - 3 * h2 = 3 * (z^2 - 1) * h2
    work = work.add(work3.neg());

    return work;
  }
}
