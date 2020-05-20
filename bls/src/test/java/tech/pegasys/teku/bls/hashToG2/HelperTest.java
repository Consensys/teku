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

package tech.pegasys.teku.bls.hashToG2;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static tech.pegasys.teku.bls.hashToG2.Chains.h2Chain;
import static tech.pegasys.teku.bls.hashToG2.Chains.mxChain;
import static tech.pegasys.teku.bls.hashToG2.Helper.clear_h2;
import static tech.pegasys.teku.bls.hashToG2.Helper.g2_map_to_infinity;
import static tech.pegasys.teku.bls.hashToG2.Helper.hashToBase;
import static tech.pegasys.teku.bls.hashToG2.Helper.iso3;
import static tech.pegasys.teku.bls.hashToG2.Helper.mapToCurve;
import static tech.pegasys.teku.bls.hashToG2.Util.bigFromHex;

import org.apache.milagro.amcl.BLS381.ECP2;
import org.apache.milagro.amcl.BLS381.FP;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

class HelperTest {

  /*
   * Most of these tests use data generated from the reference implementation at
   * https://github.com/algorand/bls_sigs_ref/tree/master/python-impl
   */

  @Test
  void isOnCurve() {
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

    assertTrue(Helper.isOnCurve(p));
  }

  @Test
  void isNotOnCurve() {
    JacobianPoint p =
        new JacobianPoint(
            new FP2Immutable(new FP(1), new FP(2)),
            new FP2Immutable(new FP(3), new FP(4)),
            new FP2Immutable(new FP(5), new FP(6)));

    assertFalse(Helper.isOnCurve(p));
  }

  @Test
  void hashToBaseTest0() {
    FP2Immutable expected =
        new FP2Immutable(
            bigFromHex(
                "0x10ed54fad72302a4360d5a117438c62c315725190547ccdc47d547697c5cf604bffbb46a6fd701e11225ffef14c01631"),
            bigFromHex(
                "0x0f6ec0d3f825ce20f349eb143fc9032c223a14ca76c1785956afdbdebd1ce1ce13460fdfe2be99126384bd4cd6dda52f"));
    FP2Immutable actual =
        hashToBase(Bytes.wrap("sample".getBytes(UTF_8)), (byte) 0, Bytes.fromHexString("0x02"));

    assertEquals(expected, actual);
  }

  @Test
  void hashToBaseTest1() {
    FP2Immutable expected =
        new FP2Immutable(
            bigFromHex(
                "0x00ced1044f94139d0c41b78826645228e52e93ec04a00483aa8d9d3700f1f356d3a438bcc9e979893eb6336017aa87ef"),
            bigFromHex(
                "0x11eb9661fec24f5736f0d66c4561284488c2dc9314568f1d8ec2904bbbef78e099127af7d0ab72efdfc0c7e9225ff2e8"));
    FP2Immutable actual =
        hashToBase(Bytes.wrap("sample".getBytes(UTF_8)), (byte) 1, Bytes.fromHexString("0x02"));

    assertEquals(expected, actual);
  }

  @Test
  void mapToCurveTest0() {
    JacobianPoint expected =
        new JacobianPoint(
            new FP2Immutable(
                bigFromHex(
                    "0x1342d0008a5e53defb031c00fe0da4589358f588d396bb448c12f0fab77e9b6a06ff6a572fe8d793ed195f44ac031ea8"),
                bigFromHex(
                    "0x0bf209ccef2b279abee6be370cbca289b966556ededc3d47d7952af553ac8e5a367fdad8421642d9b699aae74d819ae8")),
            new FP2Immutable(
                bigFromHex(
                    "0x151181a23dd3035391f91d773eec70a9c8d7a1b92818c760a962ddeb9b1e5be1cefda84a7acfe2a28f7d094540d84b21"),
                bigFromHex(
                    "0x0341434c9fbe1f2773a862f1ce17de6b8bb70f2ce2ef70a1a00c77626758abca0ba7af2399b9ccce83854c4e6d6bee70")),
            new FP2Immutable(
                bigFromHex(
                    "0x12c090b820bfe75144b20df6b0a420885ba4d1944699827e17be874bf7409e7f2d11c82f23967fe4cf94c2087182e108"),
                bigFromHex(
                    "0x10d3ed6c4687dee2c95f4c12db89f6e32bc810059c317b39b190be1bd9d24baf5c19cc42390e7df1a01eaa764bb3889e")));
    FP2Immutable u =
        new FP2Immutable(
            bigFromHex(
                "0x10ed54fad72302a4360d5a117438c62c315725190547ccdc47d547697c5cf604bffbb46a6fd701e11225ffef14c01631"),
            bigFromHex(
                "0x0f6ec0d3f825ce20f349eb143fc9032c223a14ca76c1785956afdbdebd1ce1ce13460fdfe2be99126384bd4cd6dda52f"));
    JacobianPoint actual = mapToCurve(u);

    assertEquals(expected, actual);
  }

  @Test
  void mapToCurveTest1() {
    JacobianPoint expected =
        new JacobianPoint(
            new FP2Immutable(
                bigFromHex(
                    "0x1672d11f2bd00372b88f5ebb6ccd0e5a9f6fe7fa1520a7f17fd611b5bbc96a8e19f0e90f33deb97a4f04f6f13655a0ec"),
                bigFromHex(
                    "0x137f31d84abb1e324714df0dbd2f7f99bca0ff4aaabc6e50c891c86919cb8c3d75bad7268faf7f355c1a5441d4ed069c")),
            new FP2Immutable(
                bigFromHex(
                    "0x03dd14ee96fbb25c04313d7a287a308d2d6d0971afa72471ce021ce579e01409df3be2d25d3f8147450f7a879dcee0d1"),
                bigFromHex(
                    "0x0c2738232da6c8e6b091ac3944519e536a342d408ac22f8f797a420dd9137cb320026f78568d414ac850edd65fdae7ec")),
            new FP2Immutable(
                bigFromHex(
                    "0x02a0ce75f1dd2edd4717ab7f009ae53650e4e75f1573d6960db15746c9ffb400a8ed8f9ce4021e822393cc7305271c7c"),
                bigFromHex(
                    "0x078fa9a21cf22caaa895f57688571fac14b81e463a3d8e1f87dcc8917d60b2d7328dafc1fc6ba185fb105b70ff223a2c")));
    FP2Immutable u =
        new FP2Immutable(
            bigFromHex(
                "0x00ced1044f94139d0c41b78826645228e52e93ec04a00483aa8d9d3700f1f356d3a438bcc9e979893eb6336017aa87ef"),
            bigFromHex(
                "0x11eb9661fec24f5736f0d66c4561284488c2dc9314568f1d8ec2904bbbef78e099127af7d0ab72efdfc0c7e9225ff2e8"));
    JacobianPoint actual = mapToCurve(u);

    assertEquals(expected, actual);
  }

  @Test
  void iso3Test() {
    JacobianPoint p =
        new JacobianPoint(
            new FP2Immutable(
                "0x050090b01ac45e35dee17d7a90f8f86774f292cde66f2e37fb7248921bd1280187b0a326c89e39e0eb7555901aa87667",
                "0x08be1317456de52e3962a5bffc42802f5d1806ebf5efbce4099c3bec7ae01525b9fff24fbf96b2dcca033d42f29b642f"),
            new FP2Immutable(
                "0x08f191243dbbaa2c4cc0857d334b109ee8556600b0cbfd6efe3a2b60ff74514832cb314497f76befeed5a43cc1c2e1c6",
                "0x0433f124c764e1d22db57c3b95296e15fe4fa59f651d4d6b42f620ec61de7899fef6b9ca14480f22f55e04c006c66b44"),
            new FP2Immutable(
                "0x0aa3f25fedcbbcb64ff54772a86e97b5907bbb9fdc5d782049c8f298baf9094e254bff2cffde720b66249f56b51c1164",
                "0x036b729a0d4baf2e27462383df58d6ac8024063bf0e14316472403fd32f9435db35c331e79aef84d552cf7dbcf075ef0"));
    JacobianPoint expected =
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

    assertEquals(expected, iso3(p));
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
    assertEquals(expected, clear_h2(a));
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
    assertEquals(clear_h2_slow(a), clear_h2(a));
  }

  @Test
  void G2MapToInfinityTest() {
    // The generator point of G2 is certainly in G2
    JacobianPoint g2Generator = new JacobianPoint(ECP2.generator());
    assertTrue(g2_map_to_infinity(g2Generator).isInfinity());
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
    assertTrue(Helper.isInG2(inG2));
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
    assertFalse(Helper.isInG2(notInG2));
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
  static JacobianPoint clear_h2_slow(JacobianPoint p) {
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
