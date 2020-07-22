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

import static tech.pegasys.teku.bls.impl.mikuli.hash2g2.Util.fpFromHex;
import static tech.pegasys.teku.bls.impl.mikuli.hash2g2.Util.negate;

import org.apache.milagro.amcl.BLS381.FP;

/** Constants used by the Helper class. */
class Consts {

  // These are eighth-roots of unity
  private static final FP RV1 =
      fpFromHex(
          "0x06af0e0437ff400b6831e36d6bd17ffe48395dabc2d3435e77f76e17009241c5ee67992f72ec05f4c81084fbede3cc09");
  static final FP2Immutable[] ROOTS_OF_UNITY = {
    new FP2Immutable(new FP(1), new FP(0)),
    new FP2Immutable(new FP(0), new FP(1)),
    new FP2Immutable(RV1, RV1),
    new FP2Immutable(RV1, negate(RV1))
  };

  // 3-isogenous curve parameters
  static final FP2Immutable Ell2p_a = new FP2Immutable(new FP(0), new FP(240));
  static final FP2Immutable Ell2p_b = new FP2Immutable(new FP(1012), new FP(1012));

  // Distinguished non-square in Fp2 for SWU map
  static final FP2Immutable xi_2 = new FP2Immutable(new FP(-2), new FP(-1));
  static final FP2Immutable xi_2Pow2 = xi_2.sqr();
  static final FP2Immutable xi_2Pow3 = xi_2Pow2.mul(xi_2);

  // Eta values, used for computing sqrt(g(X1(t)))
  private static final FP ev1 =
      fpFromHex(
          "0x0699be3b8c6870965e5bf892ad5d2cc7b0e85a117402dfd83b7f4a947e02d978498255a2aaec0ac627b5afbdf1bf1c90");
  private static final FP ev2 =
      fpFromHex(
          "0x08157cd83046453f5dd0972b6e3949e4288020b5b8a9cc99ca07e27089a2ce2436d965026adad3ef7baba37f2183e9b5");
  private static final FP ev3 =
      fpFromHex(
          "0x0ab1c2ffdd6c253ca155231eb3e71ba044fd562f6f72bc5bad5ec46a0b7a3b0247cf08ce6c6317f40edbc653a72dee17");
  private static final FP ev4 =
      fpFromHex(
          "0x0aa404866706722864480885d68ad0ccac1967c7544b447873cc37e0181271e006df72162a3d3e0287bf597fbf7f8fc1");
  static final FP2Immutable[] etas = {
    new FP2Immutable(ev1, ev2),
    new FP2Immutable(negate(ev2), ev1),
    new FP2Immutable(ev3, ev4),
    new FP2Immutable(negate(ev4), ev3)
  };

  // Coefficients for the 3-isogeny map from Ell2' to Ell2
  private static final FP2Immutable[] XNUM = {
    new FP2Immutable(
        "0x05c759507e8e333ebb5b7a9a47d7ed8532c52d39fd3a042a88b58423c50ae15d5c2638e343d9c71c6238aaaaaaaa97d6",
        "0x05c759507e8e333ebb5b7a9a47d7ed8532c52d39fd3a042a88b58423c50ae15d5c2638e343d9c71c6238aaaaaaaa97d6"),
    new FP2Immutable(
        "0x000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000",
        "0x11560bf17baa99bc32126fced787c88f984f87adf7ae0c7f9a208c6b4f20a4181472aaa9cb8d555526a9ffffffffc71a"),
    new FP2Immutable(
        "0x11560bf17baa99bc32126fced787c88f984f87adf7ae0c7f9a208c6b4f20a4181472aaa9cb8d555526a9ffffffffc71e",
        "0x08ab05f8bdd54cde190937e76bc3e447cc27c3d6fbd7063fcd104635a790520c0a395554e5c6aaaa9354ffffffffe38d"),
    new FP2Immutable(
        "0x171d6541fa38ccfaed6dea691f5fb614cb14b4e7f4e810aa22d6108f142b85757098e38d0f671c7188e2aaaaaaaa5ed1",
        "0x000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000")
  };

  private static final FP2Immutable[] XDEN = {
    new FP2Immutable(
        "0x000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000",
        "0x1a0111ea397fe69a4b1ba7b6434bacd764774b84f38512bf6730d2a0f6b0f6241eabfffeb153ffffb9feffffffffaa63"),
    new FP2Immutable(
        "0x00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000c",
        "0x1a0111ea397fe69a4b1ba7b6434bacd764774b84f38512bf6730d2a0f6b0f6241eabfffeb153ffffb9feffffffffaa9f"),
    new FP2Immutable(
        "0x000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000001",
        "0x000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000")
  };

  private static final FP2Immutable[] YNUM = {
    new FP2Immutable(
        "0x1530477c7ab4113b59a4c18b076d11930f7da5d4a07f649bf54439d87d27e500fc8c25ebf8c92f6812cfc71c71c6d706",
        "0x1530477c7ab4113b59a4c18b076d11930f7da5d4a07f649bf54439d87d27e500fc8c25ebf8c92f6812cfc71c71c6d706"),
    new FP2Immutable(
        "0x000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000",
        "0x05c759507e8e333ebb5b7a9a47d7ed8532c52d39fd3a042a88b58423c50ae15d5c2638e343d9c71c6238aaaaaaaa97be"),
    new FP2Immutable(
        "0x11560bf17baa99bc32126fced787c88f984f87adf7ae0c7f9a208c6b4f20a4181472aaa9cb8d555526a9ffffffffc71c",
        "0x08ab05f8bdd54cde190937e76bc3e447cc27c3d6fbd7063fcd104635a790520c0a395554e5c6aaaa9354ffffffffe38f"),
    new FP2Immutable(
        "0x124c9ad43b6cf79bfbf7043de3811ad0761b0f37a1e26286b0e977c69aa274524e79097a56dc4bd9e1b371c71c718b10",
        "0x000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000")
  };

  private static final FP2Immutable[] YDEN = {
    new FP2Immutable(
        "0x1a0111ea397fe69a4b1ba7b6434bacd764774b84f38512bf6730d2a0f6b0f6241eabfffeb153ffffb9feffffffffa8fb",
        "0x1a0111ea397fe69a4b1ba7b6434bacd764774b84f38512bf6730d2a0f6b0f6241eabfffeb153ffffb9feffffffffa8fb"),
    new FP2Immutable(
        "0x000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000",
        "0x1a0111ea397fe69a4b1ba7b6434bacd764774b84f38512bf6730d2a0f6b0f6241eabfffeb153ffffb9feffffffffa9d3"),
    new FP2Immutable(
        "0x000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000012",
        "0x1a0111ea397fe69a4b1ba7b6434bacd764774b84f38512bf6730d2a0f6b0f6241eabfffeb153ffffb9feffffffffaa99"),
    new FP2Immutable(
        "0x000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000001",
        "0x000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000")
  };

  static final FP2Immutable[][] map_coeffs = {XNUM, XDEN, YNUM, YDEN};

  // Constants for Psi, the untwist-Frobenius-twist endomorphism
  private static final FP iwscBase =
      fpFromHex(
          "0x0d0088f51cbff34d258dd3db21a5d66bb23ba5c279c2895fb39869507b587b120f55ffff58a9ffffdcff7fffffffd556");
  private static final FP iwscBaseM1 =
      fpFromHex(
          "0x0d0088f51cbff34d258dd3db21a5d66bb23ba5c279c2895fb39869507b587b120f55ffff58a9ffffdcff7fffffffd555");
  static final FP2Immutable iwsc = new FP2Immutable(iwscBase, iwscBaseM1);
  static final FP k_qi_x =
      fpFromHex(
          "0x1a0111ea397fe699ec02408663d4de85aa0d857d89759ad4897d29650fb85f9b409427eb4f49fffd8bfd00000000aaad");
  static final FP k_qi_y =
      fpFromHex(
          "0x06af0e0437ff400b6831e36d6bd17ffe48395dabc2d3435e77f76e17009241c5ee67992f72ec05f4c81084fbede3cc09");
  // 1 / (1 + I)^((p - 1) / 3)
  static final FP2Immutable k_cx = new FP2Immutable(new FP(0), k_qi_x);
  // 1 / (1 + I)^((p - 1) / 2)
  static final FP2Immutable k_cy =
      new FP2Immutable(
          fpFromHex(
              "0x135203e60180a68ee2e9c448d77a2cd91c3dedd930b1cf60ef396489f61eb45e304466cf3e67fa0af1ee7b04121bdea2"),
          k_qi_y);

  // Constant for Psi^2 - it's the absolute value of k_cx, which is k_qi_x squared.
  // 1 / 2^((p - 1) / 3)
  static final FP k_cx_abs =
      fpFromHex(
          "0x1a0111ea397fe699ec02408663d4de85aa0d857d89759ad4897d29650fb85f9b409427eb4f49fffd8bfd00000000aaac");
}
