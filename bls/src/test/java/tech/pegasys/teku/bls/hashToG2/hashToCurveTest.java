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

package tech.pegasys.teku.bls.hashToG2;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static tech.pegasys.teku.bls.hashToG2.HashToCurve.hashToG2;
import static tech.pegasys.teku.bls.hashToG2.Helper.clear_h2;
import static tech.pegasys.teku.bls.hashToG2.Helper.hashToField;
import static tech.pegasys.teku.bls.hashToG2.Helper.isInG2;
import static tech.pegasys.teku.bls.hashToG2.Helper.iso3;
import static tech.pegasys.teku.bls.hashToG2.Helper.mapToCurve;

import java.util.ArrayList;
import java.util.stream.Stream;
import org.apache.milagro.amcl.BLS381.ECP2;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class hashToCurveTest {

  private static final int nTest = 256;

  // Smoke test. Generate lots of hashes and make sure that they all land in G2.
  @ParameterizedTest(name = "hashToG2Test:{index}, i={0}")
  @MethodSource("getIndices")
  void hashToG2Test2(Integer i) {
    ECP2 point =
        hashToG2(
            Bytes.concatenate(Bytes.wrap("Hello, world!".getBytes(UTF_8)), Bytes.ofUnsignedInt(i)));
    assertFalse(point.is_infinity());
    assertTrue(isInG2(new JacobianPoint(point)));
  }

  public static Stream<Arguments> getIndices() {
    ArrayList<Arguments> args = new ArrayList<>();
    for (int i = 0; i < nTest; i++) {
      args.add(Arguments.of(i));
    }
    return args.stream();
  }

  // The following are the Official test vectors from
  // https://tools.ietf.org/html/draft-irtf-cfrg-hash-to-curve-07#appendix-G.10

  private static final Bytes TEST_DST =
      Bytes.wrap("BLS12381G2_XMD:SHA-256_SSWU_RO_TESTGEN".getBytes(US_ASCII));

  @Test
  void refTest0() {
    Bytes message = Bytes.EMPTY;

    FP2Immutable[] u = hashToField(message, 2, TEST_DST);
    assertEquals(
        new FP2Immutable(
            "0x0ae8ca9aed945924c3a12f3b6f419cac381bae8f16044ab6c66b41999e4bd0ea169b44f2fce3634a0ddea05b9186c6b2",
            "0x1134506e471554affe377f908c29fc7cd7d247b3a14f9e092b9f4c5b02577939ce01bd6b43d9d59d9a994e9fb5fb5096"),
        u[0]);
    assertEquals(
        new FP2Immutable(
            "0x0b28b14113885b1d8ad08f5da9111add00d8c496fb3d5d7b5d3b6558a058e9e62cd02dafa7a95f968cb3063f09fc0e21",
            "0x03378e456f437ce445b6bc95121566d85b1b3b8ca057064fe7a8a1aad7e8a6e9f886cfb1704ad712e9042f4f002f4bd1"),
        u[1]);

    JacobianPoint q0 = iso3(mapToCurve(u[0]));
    JacobianPoint q1 = iso3(mapToCurve(u[1]));
    assertEquals(
        new JacobianPoint(
            new FP2Immutable(
                "0x090f7997311a1d4ec54520f81046063f4e9e7a64570133dc41c3600ade2a4d21aae59714cf290f95f90a98b658f5b64a",
                "0x08427a6a0dc88a36698823d07ab25d11f95a9508cb5bb1ad2bd57bc02b5efb8c7b1da66ed02b0f915002446e24fd5d38"),
            new FP2Immutable(
                "0x10e03a54fd5ff7a0a69543aeeef42e22cb589e0b33455943cf84f0c5b28e93fe17c0bbba2fafb10aea29b28705eec303",
                "0x053b939496e87877fb1569c911bf618056396fac2458757da71cd83fa152239d605c6a4e4e847295080ea3874f84a832"),
            new FP2Immutable(1)),
        q0);
    assertEquals(
        new JacobianPoint(
            new FP2Immutable(
                "0x0df5643a19f8de7e8e45575551cfb8909f4a75722ec8fbc43cb8df284cdde9e2c61ea0c6116bdd86d84063c96fc7dc7f",
                "0x1241a410598f1d57907850699a694720712feddb916f343db08f2c18481df46cbdf7afe8eaf214127e427736ea281c5b"),
            new FP2Immutable(
                "0x0ad66ed30cb6f55a83feed4b12c141bd41f593292403127b07e1bc6dabacd8ea53f8a322b5d4080e4393184c713865fa",
                "0x0c4e6fb11ad2fe3a081a399df36094465aafb232f7564f4d35abb0092ef9ee855bcfdac2e6775cd7d383241f13ed856a"),
            new FP2Immutable(1)),
        q1);

    JacobianPoint p = clear_h2(q0.add(q1));
    assertEquals(
        new JacobianPoint(
            new FP2Immutable(
                "0x0a650bd36ae7455cb3fe5d8bb1310594551456f5c6593aec9ee0c03d2f6cb693bd2c5e99d4e23cbaec767609314f51d3",
                "0x0fbdae26f9f9586a46d4b0b70390d09064ef2afe5c99348438a3c7d9756471e015cb534204c1b6824617a85024c772dc"),
            new FP2Immutable(
                "0x0d8d49e7737d8f9fc5cef7c4b8817633103faf2613016cb86a1f3fc29968fe2413e232d9208d2d74a89bf7a48ac36f83",
                "0x02e5cf8f9b7348428cc9e66b9a9b36fe45ba0b0a146290c3a68d92895b1af0e1f2d9f889fb412670ae8478d8abd4c5aa"),
            new FP2Immutable(1)),
        p);
  }

  @Test
  void refTest1() {
    Bytes message = Bytes.wrap("abc".getBytes(US_ASCII));

    FP2Immutable[] u = hashToField(message, 2, TEST_DST);
    assertEquals(
        new FP2Immutable(
            "0x0a7d239c9bdb41ed2ad810820a8b4f0703f60cf5833440cd684e386e235b0f092da91adbaa69562b911ebd3f820655f2",
            "0x16302b56f5a9f538c7168cd5194957903b82be6749171f8de112c8bd3360ca24847d0567d6e42eae0c43a7fd8530b378"),
        u[0]);
    assertEquals(
        new FP2Immutable(
            "0x0a1cb4196dec71b1f704f3533cdf27f247e3ea175ddcc1ca6df0f45c587eb77efc6c493848f4df98e24a32753dfcf96b",
            "0x07aac42db7f3dfbc5146c70ca0ac6157893abf4e2162e303510e0cefb8d024c24080b9c2a9896f6c03ffe680fc18b788"),
        u[1]);
    JacobianPoint q0 = iso3(mapToCurve(u[0]));
    JacobianPoint q1 = iso3(mapToCurve(u[1]));

    assertEquals(
        new JacobianPoint(
            new FP2Immutable(
                "0x0c292ac371849207564e7b8f4edf47dc4b4d7a618dbacf6a322dc732f014cc2a22049eb69de11657c301cb4202b98541",
                "0x0f37118e477c16005cae8f639e54119ff796eafe80461bf39ecce5c0192b93075febc80d4f73f9e0893adafa17b13b45"),
            new FP2Immutable(
                "0x15853304d7fd9f47df2ef6c4bd1fb0b3500386b23d1acc530be0c14e027f15b0aa83856d82edb723f3d857358ecffb80",
                "0x0626fcfc6b3d8460df7ed2aeca6449cf6701dc7ff51c143ed20054ecf18732f4c5985455864c79a4065b13e26ecccf9f"),
            new FP2Immutable(1)),
        q0);
    assertEquals(
        new JacobianPoint(
            new FP2Immutable(
                "0x0bce3e2dd15f6acf55cce0e3a4cde190a6d45434a8b0ba7cf79ff37f737ed90dbfd2988a257db65e10e684e5876b50db",
                "0x19c1ad3eb0abb3590087d706eb155a4cd166484e82cdccb2465ce193b15a27d919aaa37d1824a9a9d87f31fefca1baee"),
            new FP2Immutable(
                "0x110c9643a8dfd00123bb9e6a956426f26bedb0d430130026ce49b862431e80f5e306850239c857474f564915fc9a4ba6",
                "0x1748ca13032a2c262295863897a15cd9a7e0baf003336bec6fc6e40b982d866fe3250619fdd2ceadb49fab8055f47e65"),
            new FP2Immutable(1)),
        q1);

    JacobianPoint p = clear_h2(q0.add(q1));
    assertEquals(
        new JacobianPoint(
            new FP2Immutable(
                "0x1953ce6d4267939c7360756d9cca8eb34aac4633ef35369a7dc249445069888e7d1b3f9d2e75fbd468fbcbba7110ea02",
                "0x03578447618463deb106b60e609c6f7cc446dc6035f84a72801ba17c94cd800583b493b948eff0033f09086fdd7f6175"),
            new FP2Immutable(
                "0x0882ab045b8fe4d7d557ebb59a63a35ac9f3d312581b509af0f8eaa2960cbc5e1e36bb969b6e22980b5cbdd0787fcf4e",
                "0x0184d26779ae9d4670aca9b267dbd4d3b30443ad05b8546d36a195686e1ccc3a59194aea05ed5bce7c3144a29ec047c4"),
            new FP2Immutable(1)),
        p);
  }

  @Test
  void refTest2() {
    Bytes message = Bytes.wrap("abcdef0123456789".getBytes(US_ASCII));

    FP2Immutable[] u = hashToField(message, 2, TEST_DST);
    assertEquals(
        new FP2Immutable(
            "0x0e17df0242a3dd0e7454a4b580cafdc956650736b45181b329ca89ee2348570a1d7a221554c7122b91e6e3c3525d396d",
            "0x0298e9fa0ff37440cd2862e91c0a27fed05087247acf79232f1a4eb7cf8f65997a92319a8cbd00f7b73ee9e82241eade"),
        u[0]);
    assertEquals(
        new FP2Immutable(
            "0x1200056764f11beacdb6009acaf823e100da27b4bfe45e94097a52c1fed615b32dbc5503f964ab5277a7c30d9a2bf0de",
            "0x0d1d7feb418f29dbf4d4459c839dd33f904d4292d016f701b35e4a7611798c83de1b7deb1c6c1521e9142cc36a7d0579"),
        u[1]);

    JacobianPoint q0 = iso3(mapToCurve(u[0]));
    JacobianPoint q1 = iso3(mapToCurve(u[1]));
    assertEquals(
        new JacobianPoint(
            new FP2Immutable(
                "0x1552566a422494f9edd07e21ee59067ecf031f333b3961b710fac1245fd003552c294ac47ef982432f0f1e1e9d07c4b6",
                "0x115a9de418d20ce3105eaa2db025d183cc679327c6d6a229960d536b9fce33d3242f9819680a9200265ec2dd02b44b19"),
            new FP2Immutable(
                "0x0cef664ee9270354c3bc06d1e0570e4d6663cc528711afca10118955990126f87917c87f7b9c4cf73aaf05c1b5875c6f",
                "0x0b136f41d233ea420bc3658c4156f717fb190775d3690d139c0923c231e44af54d780119b8edf16038208b63feb1f3ee"),
            new FP2Immutable(1)),
        q0);
    assertEquals(
        new JacobianPoint(
            new FP2Immutable(
                "0x0332d5027c68f38ca78c6c63c013178fb58b31283a6135f6bf5629d18c76144accfd96905f51a49284f4ef622dfec003",
                "0x04865f680c5f2203de00f95dd6652c9b3dc0d36361ee0df16a39a86d5f7cfc8df3674f3c3fddde88fb027353eac1a3dc"),
            new FP2Immutable(
                "0x1651e6cc8af2241989a9006dd59a9cd41fc1bbc3a7f9e32875889ae54913b8398dfa106aff43ff1cfa9019141d9ad565",
                "0x09324bdbfedfb886899a7961f7827702743ef550f548bb89ab15d4b24c7c086196891fc300e3e39c21aec0257543a3fd"),
            new FP2Immutable(1)),
        q1);

    JacobianPoint p = clear_h2(q0.add(q1));
    assertEquals(
        new JacobianPoint(
            new FP2Immutable(
                "0x17b461fc3b96a30c2408958cbfa5f5927b6063a8ad199d5ebf2d7cdeffa9c20c85487204804fab53f950b2f87db365aa",
                "0x195fad48982e186ce3c5c82133aefc9b26d55979b6f530992a8849d4263ec5d57f7a181553c8799bcc83da44847bdc8d"),
            new FP2Immutable(
                "0x174a3473a3af2d0302b9065e895ca4adba4ece6ce0b41148ba597001abb152f852dd9a96fb45c9de0a43d944746f833e",
                "0x005cdf3d984e3391e7e969276fb4bc02323c5924a4449af167030d855acc2600cf3d4fab025432c6d868c79571a95bef"),
            new FP2Immutable(1)),
        p);
  }

  @Test
  void refTest3() {
    Bytes message =
        Bytes.wrap(
            ("a512_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                    + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                    + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                    + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                    + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                    + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                    + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                    + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                    + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                    + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
                .getBytes(US_ASCII));

    FP2Immutable[] u = hashToField(message, 2, TEST_DST);
    assertEquals(
        new FP2Immutable(
            "0x0ca92554c8c45581eac2eed7ec2db1fe757af0a2803dc8e63180600eed2516f64b1c0d850c72a75c417f58723815795b",
            "0x12ef692f69b1d61854b80e071c7fd751b19da2c194ba0fbee9e68454073dd3693e2c56852938aa1b090991018ff15a94"),
        u[0]);
    assertEquals(
        new FP2Immutable(
            "0x11043d352059287fe7424285da213d4cc414df4d5592ee2507503088b3f89220697753ea8cd47fa13c9a15dbfb0ef20c",
            "0x110efeacfff2801024c019cee7adbc3d8144c3b73c548ad8f0759c4976e0b3070293056f884dc0a1b3728546dddc6bcb"),
        u[1]);

    JacobianPoint q0 = iso3(mapToCurve(u[0]));
    JacobianPoint q1 = iso3(mapToCurve(u[1]));
    assertEquals(
        new JacobianPoint(
            new FP2Immutable(
                "0x089b04f318946ce75b5b8c98607041488005ed412a4a99e7106b340427d35682036cecc076827e700e47c17f65ee3f09",
                "0x03bef411c75f97147673952b19ee293e28df019be2fdecf5db09afb7caad4a5e984750b19c2007b50ae0b26f83088e8b"),
            new FP2Immutable(
                "0x18b1ef96738c5df727e1fa2098178fe371751c0c169af30bdb95be22a0ecbf0a75c0e6c63e4a32f241250f877859c086",
                "0x0d04c624db798ca46a352637fa76516c83a5d98e147a25f629fb1e02a9a453970e42d835ba765bd7d94a4a3f9f50e4a1"),
            new FP2Immutable(1)),
        q0);
    assertEquals(
        new JacobianPoint(
            new FP2Immutable(
                "0x121b1257fbd3dda5f478b5de6aee2ca88780248c59afad1a9c9c9db5d03752792270cecc7cc676a1b91ee898b7f76977",
                "0x17eadb5c134a1cc0305ad5d99f6e2a1cd906a2fdac318d4356527c70fc94242ddb664486c814ebd5959a2cf4225a783a"),
            new FP2Immutable(
                "0x00f0793bcfaf12e5d23fdd4173f7539e3cf182a0f5a1c98b488f59daca5ecf7b694912a93f6b81498a5c2282c09ee63f",
                "0x081adf3c45b42c35fdb678c8bdec1d8c12f9d5a30b22cf52c1afc967d6ddc82fdae0673f76a5186a84f3602c7a22f6b8"),
            new FP2Immutable(1)),
        q1);

    JacobianPoint p = clear_h2(q0.add(q1));
    assertEquals(
        new JacobianPoint(
            new FP2Immutable(
                "0x0a162306f3b0f2bb326f0c4fb0e1fea020019c3af796dcd1d7264f50ddae94cacf3cade74603834d44b9ab3d5d0a6c98",
                "0x123b6bd9feeba26dd4ad00f8bfda2718c9700dc093ea5287d7711844644eb981848316d3f3f57d5d3a652c6cdc816aca"),
            new FP2Immutable(
                "0x15c1d4f1a685bb63ee67ca1fd96155e3d091e852a684b78d085fd34f6091e5249ddddbdcf2e7ec82ce6c04c63647eeb7",
                "0x05483f3b96d9252dd4fc0868344dfaf3c9d145e3387db23fa8e449304fab6a7b6ec9c15f05c0a1ea66ff0efcc03e001a"),
            new FP2Immutable(1)),
        p);
  }
}
