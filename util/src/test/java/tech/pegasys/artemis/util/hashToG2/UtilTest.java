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

package tech.pegasys.artemis.util.hashToG2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static tech.pegasys.artemis.util.hashToG2.FP2Immutable.ONE;
import static tech.pegasys.artemis.util.hashToG2.Util.HKDF_Expand;
import static tech.pegasys.artemis.util.hashToG2.Util.HKDF_Extract;
import static tech.pegasys.artemis.util.hashToG2.Util.HMAC_SHA256;
import static tech.pegasys.artemis.util.hashToG2.Util.ROOTS_OF_UNITY;
import static tech.pegasys.artemis.util.hashToG2.Util.bigFromHex;
import static tech.pegasys.artemis.util.hashToG2.Util.clear_h2;
import static tech.pegasys.artemis.util.hashToG2.Util.hashToBase;
import static tech.pegasys.artemis.util.hashToG2.Util.iso3;
import static tech.pegasys.artemis.util.hashToG2.Util.mapToCurve;
import static tech.pegasys.artemis.util.hashToG2.Util.mx_chain;
import static tech.pegasys.artemis.util.hashToG2.Util.onCurveG2;
import static tech.pegasys.artemis.util.hashToG2.Util.os2ip;
import static tech.pegasys.artemis.util.hashToG2.Util.psi;
import static tech.pegasys.artemis.util.hashToG2.Util.xi_2;
import static tech.pegasys.artemis.util.hashToG2.Util.xi_2Pow2;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import org.apache.milagro.amcl.BLS381.BIG;
import org.apache.milagro.amcl.BLS381.ECP2;
import org.apache.milagro.amcl.BLS381.FP2;
import org.apache.milagro.amcl.BLS381.ROM;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.util.mikuli.G2Point;

class UtilTest {

  /*
   * HMAC_SHA256 Tests
   *
   * HMAC reference results from here: https://www.freeformatter.com/hmac-generator.html
   */

  @Test
  void hmacSixtyFourByteKey() {
    byte[] text = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789?!".getBytes();
    byte[] key = "1234567890123456789012345678901234567890123456789012345678901234".getBytes();
    Bytes expected =
        Bytes.fromHexString("0x72e4eae543b5669e6a777f9c984029860756f80a2b658d1bca6da5693c580ba1");
    Bytes actual = HMAC_SHA256(text, key);
    assertEquals(actual, expected);
  }

  @Test
  void hmacSixtyFiveByteKey() {
    byte[] text = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789?!".getBytes();
    byte[] key = "12345678901234567890123456789012345678901234567890123456789012345".getBytes();
    Bytes expected =
        Bytes.fromHexString("0x7e1bef8bdcd6c63dd009aaeff87a6ba51a3e00efff6353ac514f6fae41361407");
    Bytes actual = HMAC_SHA256(text, key);
    assertEquals(actual, expected);
  }

  /*
   * Further HMAC_SHA256 Tests
   *
   * HMAC test vectors from https://tools.ietf.org/html/rfc4231
   */

  @Test
  void hmacRefTest1() {
    byte[] data = Bytes.fromHexString("0x4869205468657265").toArray();
    byte[] key = Bytes.fromHexString("0x0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b").toArray();
    Bytes expected =
        Bytes.fromHexString("0xb0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7");
    Bytes actual = HMAC_SHA256(data, key);
    assertEquals(actual, expected);
  }

  @Test
  void hmacRefTest2() {
    byte[] data =
        Bytes.fromHexString("0x7768617420646f2079612077616e7420666f72206e6f7468696e673f").toArray();
    byte[] key = Bytes.fromHexString("0x4a656665").toArray();
    Bytes expected =
        Bytes.fromHexString("0x5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843");
    Bytes actual = HMAC_SHA256(data, key);
    assertEquals(actual, expected);
  }

  @Test
  void hmacRefTest3() {
    byte[] data =
        Bytes.fromHexString(
                "0x"
                    + "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd"
                    + "dddddddddddddddddddddddddddddddddddd")
            .toArray();
    byte[] key = Bytes.fromHexString("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa").toArray();
    Bytes expected =
        Bytes.fromHexString("0x773ea91e36800e46854db8ebd09181a72959098b3ef8c122d9635514ced565fe");
    Bytes actual = HMAC_SHA256(data, key);
    assertEquals(actual, expected);
  }

  @Test
  void hmacRefTest4() {
    byte[] data =
        Bytes.fromHexString(
                "0x"
                    + "cdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcd"
                    + "cdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcd")
            .toArray();
    byte[] key =
        Bytes.fromHexString("0x0102030405060708090a0b0c0d0e0f10111213141516171819").toArray();
    Bytes expected =
        Bytes.fromHexString("0x82558a389a443c0ea4cc819899f2083a85f0faa3e578f8077a2e3ff46729665b");
    Bytes actual = HMAC_SHA256(data, key);
    assertEquals(actual, expected);
  }

  @Test
  void hmacRefTest5() {
    byte[] data = Bytes.fromHexString("0x546573742057697468205472756e636174696f6e").toArray();
    byte[] key = Bytes.fromHexString("0x0c0c0c0c0c0c0c0c0c0c0c0c0c0c0c0c0c0c0c0c").toArray();
    Bytes expected = Bytes.fromHexString("0xa3b6167473100ee06e0c796c2955552b");
    Bytes actual = HMAC_SHA256(data, key).slice(0, 16);
    assertEquals(actual, expected);
  }

  @Test
  void hmacRefTest6() {
    byte[] data =
        Bytes.fromHexString(
                "0x"
                    + "54657374205573696e67204c6172676572205468616e20426c6f636b2d53697a"
                    + "65204b6579202d2048617368204b6579204669727374")
            .toArray();
    byte[] key =
        Bytes.fromHexString(
                "0x"
                    + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                    + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                    + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                    + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                    + "aaaaaa")
            .toArray();
    Bytes expected =
        Bytes.fromHexString("0x60e431591ee0b67f0d8a26aacbf5b77f8e0bc6213728c5140546040f0ee37f54");
    Bytes actual = HMAC_SHA256(data, key);
    assertEquals(actual, expected);
  }

  @Test
  void hmacRefTest7() {
    byte[] data =
        Bytes.fromHexString(
                "0x"
                    + "5468697320697320612074657374207573696e672061206c6172676572207468"
                    + "616e20626c6f636b2d73697a65206b657920616e642061206c61726765722074"
                    + "68616e20626c6f636b2d73697a6520646174612e20546865206b6579206e6565"
                    + "647320746f20626520686173686564206265666f7265206265696e6720757365"
                    + "642062792074686520484d414320616c676f726974686d2e")
            .toArray();
    byte[] key =
        Bytes.fromHexString(
                "0x"
                    + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                    + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                    + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                    + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                    + "aaaaaa")
            .toArray();
    Bytes expected =
        Bytes.fromHexString("0x9b09ffa71b942fcb27635fbcd5b0e944bfdc63644f0713938a7f51535c3a35e2");
    Bytes actual = HMAC_SHA256(data, key);
    assertEquals(actual, expected);
  }

  /*
   * HKDF Tests
   *
   * Test vectors from https://tools.ietf.org/html/rfc5869#appendix-A
   */

  @Test
  void hkdfRefTest1() {
    Bytes ikm = Bytes.fromHexString("0x0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b");
    Bytes salt = Bytes.fromHexString("0x000102030405060708090a0b0c");
    Bytes info = Bytes.fromHexString("0xf0f1f2f3f4f5f6f7f8f9");
    int l = 42;

    Bytes prk =
        Bytes.fromHexString("0x077709362c2e32df0ddc3f0dc47bba6390b6c73bb50f9c3122ec844ad7c2b3e5");
    Bytes okm =
        Bytes.fromHexString(
            "0x3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865");

    assertEquals(prk, HKDF_Extract(salt, ikm));
    assertEquals(okm, HKDF_Expand(prk, info, l));
  }

  @Test
  void hkdfRefTest2() {
    Bytes ikm =
        Bytes.fromHexString(
            "0x"
                + "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f"
                + "202122232425262728292a2b2c2d2e2f303132333435363738393a3b3c3d3e3f"
                + "404142434445464748494a4b4c4d4e4f");
    Bytes salt =
        Bytes.fromHexString(
            "0x"
                + "606162636465666768696a6b6c6d6e6f707172737475767778797a7b7c7d7e7f"
                + "808182838485868788898a8b8c8d8e8f909192939495969798999a9b9c9d9e9f"
                + "a0a1a2a3a4a5a6a7a8a9aaabacadaeaf");
    Bytes info =
        Bytes.fromHexString(
            "0x"
                + "b0b1b2b3b4b5b6b7b8b9babbbcbdbebfc0c1c2c3c4c5c6c7c8c9cacbcccdcecf"
                + "d0d1d2d3d4d5d6d7d8d9dadbdcdddedfe0e1e2e3e4e5e6e7e8e9eaebecedeeef"
                + "f0f1f2f3f4f5f6f7f8f9fafbfcfdfeff");
    int l = 82;

    Bytes prk =
        Bytes.fromHexString("0x06a6b88c5853361a06104c9ceb35b45cef760014904671014a193f40c15fc244");
    Bytes okm =
        Bytes.fromHexString(
            "0x"
                + "b11e398dc80327a1c8e7f78c596a49344f012eda2d4efad8a050cc4c19afa97c"
                + "59045a99cac7827271cb41c65e590e09da3275600c2f09b8367793a9aca3db71"
                + "cc30c58179ec3e87c14c01d5c1f3434f1d87");

    assertEquals(prk, HKDF_Extract(salt, ikm));
    assertEquals(okm, HKDF_Expand(prk, info, l));
  }

  @Test
  void hkdfRefTest3() {
    Bytes ikm = Bytes.fromHexString("0x0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b");
    Bytes salt = Bytes.EMPTY;
    Bytes info = Bytes.EMPTY;
    int l = 42;

    Bytes prk =
        Bytes.fromHexString("0x19ef24a32c717b167f33a91d6f648bdf96596776afdb6377ac434c1c293ccb04");
    Bytes okm =
        Bytes.fromHexString(
            "0x8da4e775a563c18f715f802a063c5a31b8a11f5c5ee1879ec3454e5f3c738d2d9d201395faa4b61a96c8");

    assertEquals(prk, HKDF_Extract(salt, ikm));
    assertEquals(okm, HKDF_Expand(prk, info, l));
  }

  @Test
  void os2ipTest1() {
    // Big-endian bytes
    byte[] bytes = {1, 2, 3};
    assertEquals(BigInteger.valueOf(66051L), os2ip(Bytes.wrap(bytes)));
  }

  @Test
  void readingCurveModulusToBigInteger() {
    String hexModulus =
        "1a0111ea397fe69a4b1ba7b6434bacd764774b84f38512bf6730d2a0f6b0f6241eabfffeb153ffffb9feffffffffaaab";
    BigInteger fromHex = new BigInteger(hexModulus, 16);
    BigInteger fromRom = new BigInteger((new BIG(ROM.Modulus)).toString(), 16);
    assertEquals(fromHex, fromRom);
  }

  @Test
  void rootsOfUnityTest() {
    for (FP2Immutable root : ROOTS_OF_UNITY) {
      assertEquals(ONE, root.sqr().sqr().sqr().reduce());
    }
  }

  @Test
  void xi2sqEqualsXi2SquaredTest() {
    assertEquals(xi_2Pow2, xi_2.sqr());
  }

  /*
   * The remaining tests use data generated by the reference implementation at
   * https://github.com/kwantam/bls_sigs_ref/tree/master/python-impl
   */

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
  void mxChainTest() {
    JacobianPoint a =
        new JacobianPoint(
            new FP2Immutable(new BIG(1), new BIG(2)),
            new FP2Immutable(new BIG(3), new BIG(4)),
            new FP2Immutable(new BIG(5), new BIG(6)));
    JacobianPoint expected =
        new JacobianPoint(
            new FP2Immutable(
                "0x14eb89798ed67c7c0c9a7ab4627f64a9295fa5aa738b23bc41d7e57bc75e3cfec576007ea509a867a1746954aae2cca9",
                "0x00d29230f0dd01305ad70a52ceed8ee8e88ee072b69d9a535065785228d324b339a8b3116461ecfaec453c303da68240"),
            new FP2Immutable(
                "0x0170b865e217be3a5ecd56a0470270453cbd51ad2c04e0ff053455380a5a6841e5b580ff5dddbdf664ddc25acfecba58",
                "0x0020c819b3da9c3e6e856a5235a3bf28b2f1401340d3bd41deaad48d17cb1e100655dd7028f6cb1708dc239175f4205a"),
            new FP2Immutable(
                "0x00e34380275c83c4308fc707542a3ecefa0ca80aeffd3791bef2fc8fbfbbb970f41c34ed98454b5884f90a838eccb68a",
                "0x05b84312465a31b1dbe87388923b6244befe2f355ebda12b88f133237cf2c13158f1253b9e2f09749beb4099338957a4"));
    assertEquals(expected, mx_chain(a));
  }

  // Unit tests based on tracing the Python code with standard reference data
  @Test
  void hashToBaseTest0() {
    FP2Immutable expected =
        new FP2Immutable(
            bigFromHex(
                "0x0088cd5956f5bcec90c2058f687f248c411c5dec4e84363b75fad9c6a64e8f8f4607a89a4cc67b2030343a010e72f007"),
            bigFromHex(
                "0x093ec788e7fcfb915ffaac1e66e67d5d020f2b5c7131d82cee7dd361dff283d0023d5b39768595dca7db13c4a40c2287"));
    FP2Immutable actual =
        hashToBase(
            Bytes.wrap("sample".getBytes(StandardCharsets.US_ASCII)),
            (byte) 0,
            Bytes.fromHexString("0x02"));

    assertEquals(expected, actual);
  }

  @Test
  void hashToBaseTest1() {
    FP2Immutable expected =
        new FP2Immutable(
            bigFromHex(
                "0x081d1f51370a9e6f59ed62fa605e891c40b20d98601fe7c3fa6a8efabcf0c1c3a0ff05963ab388a4b9ec4d35e97c0863"),
            bigFromHex(
                "0x01fbe48c2b138982f28317f684364327114adecadd94b599347bded08ef7b7ba22d814f1c64f1c77023ec9425383c184"));
    FP2Immutable actual =
        hashToBase(
            Bytes.wrap("sample".getBytes(StandardCharsets.US_ASCII)),
            (byte) 1,
            Bytes.fromHexString("0x02"));

    assertEquals(expected, actual);
  }

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

    assertTrue(onCurveG2(p));
  }

  @Test
  void mapToCurveTest1() {
    JacobianPoint expected =
        new JacobianPoint(
            new FP2Immutable(
                bigFromHex(
                    "0x1762ea1dbb8e4da53030c7d05505cc3d1806d6783d9c742fe3b9b788356c0de69e9a516198abe81e9c7929aab9d9d7c6"),
                bigFromHex(
                    "0x0c54bf873db22737f92b55715b21cecc5c48ab2a208e6fcc5d48e74b9d37c219433102700b4de9908c289a414e51cd66")),
            new FP2Immutable(
                bigFromHex(
                    "0x0ff2b0a75fcdbe6d997f69396e715b90ece0e7e4e38319be2d38b636497777cb70a4db0c1ccd067cab89aa5636bbb26a"),
                bigFromHex(
                    "0x003a16b622516bc4e93fc43b1b2f885de22c98bc577798ef554ecb4ecf6581a32f0096393d8b782820b47b4e354d933c")),
            new FP2Immutable(
                bigFromHex(
                    "0x0806edcca4844b5f021e2d1b40824a7dde3238f46904dda809df51ab2d085f5a92c193330c819497f36db9fb38c26e38"),
                bigFromHex(
                    "0x163d1dfec04142fd3a186043c93527ae3dc7126e6b984683601e03303ee31814dfb7ffe8c781e5f59551a6526dbd05c3")));
    FP2Immutable u =
        new FP2Immutable(
            bigFromHex(
                "0x0088cd5956f5bcec90c2058f687f248c411c5dec4e84363b75fad9c6a64e8f8f4607a89a4cc67b2030343a010e72f007"),
            bigFromHex(
                "0x093ec788e7fcfb915ffaac1e66e67d5d020f2b5c7131d82cee7dd361dff283d0023d5b39768595dca7db13c4a40c2287"));
    JacobianPoint actual = mapToCurve(u);

    assertEquals(expected, actual);
  }

  @Test
  void mapToCurveTest2() {
    JacobianPoint expected =
        new JacobianPoint(
            new FP2Immutable(
                bigFromHex(
                    "0x15e67394bed2e1974a2e8e5044bf2e3097aaef37994b0cd42ac2455b5fbda6cc624b184256641064d54258a5b5d56449"),
                bigFromHex(
                    "0x11bd77ebd0307404d4f57b5b46152801b61ba003a4e8b389522c6a2b49a9413cf79d6ddd689affa728fec7d251fc3617")),
            new FP2Immutable(
                bigFromHex(
                    "0x0c17d03d3cb4613ca820c7952eaa8a624c7913b7440c64fb8c8811e10bf7b0b94fbea06a1e64493631737a50ad7d73e6"),
                bigFromHex(
                    "0x199fecb08dce28132a1a41e38ff39f8194860e8fa839870b7f1f1e48ce44fa3bb942363aeb637640975e53e72677e019")),
            new FP2Immutable(
                bigFromHex(
                    "0x09f7c5348e63a7d2cc09108a9a2f7207c043fb374332a98abbdac92d410ea8279ce607a2aa01552535968089de185e89"),
                bigFromHex(
                    "0x021b64d79d018dfca0685168d5389b66d1970ee1eea984799f34faffa6f164ef066716388e1b282cf41e5327515b798a")));
    FP2Immutable u =
        new FP2Immutable(
            bigFromHex(
                "0x081d1f51370a9e6f59ed62fa605e891c40b20d98601fe7c3fa6a8efabcf0c1c3a0ff05963ab388a4b9ec4d35e97c0863"),
            bigFromHex(
                "0x01fbe48c2b138982f28317f684364327114adecadd94b599347bded08ef7b7ba22d814f1c64f1c77023ec9425383c184"));
    JacobianPoint actual = mapToCurve(u);

    assertEquals(expected, actual);
  }

  @Test
  void iso3Test() {
    JacobianPoint p =
        new JacobianPoint(
            new FP2Immutable(
                "0x030cf314b7e545d7fb586ab7b4e1da8341108facab511c9dffa9e23088fe90acfaf8fd562f917821473042dc3a883398",
                "0x0ac6b007741e43fe578f39cd43c9044ce6ed7e72efcf93294eb0ee1f50da0ee5be3e3a975f285ee6fa0701c098254d75"),
            new FP2Immutable(
                "0x00be3f9bd23c5bcd8b479e969b89693bef044e34e965f9c36d3adddb680b7257fdaf05fe9539708b6847fb4192ea1a3d",
                "0x14f7cbf23e6412db18a28b03623c338284b025e6f661f05091bdeb9b0546aef839b305b49321f3c60db4990ba10c44ac"),
            new FP2Immutable(
                "0x07e275e35a1bdff2ec26be5925481db1b0a08eddc54d7482d5728d1188055371a1a39442f5c796f924ad57b26af0d538",
                "0x115b2b6c2811470d12ae6ae2d72dd0ba2181e1af834aa4311bcfac3bf697724051b02e8785c8642ab8149df6895fc186"));
    JacobianPoint expected =
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

    assertEquals(expected, iso3(p));
  }

  @Test
  void psiTest1() {
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
                "0x04db65bd4b55d4b2d80bde5c49c2eb94fbe4d7fb72a188c50980c2f23e9f071badc56f522276151a243a11242537356d",
                "0x03268cf2aec8b142fa23ec17ee064cb610845c951d61d1087edb35de7725b9db9370c9aa42fc2a3a762a188a64d3f5da"),
            new FP2Immutable(
                "0x011294819a545471ecb1d05db3e234787b8c4d84176588cbf05feac50dbc6b251c9efc8b3aea32d6d5afc0d2d74382c4",
                "0x150f53136a51c6dff7fe06555b3e202194cb274ba45662844b322a8cce8a5ed89988af7a72aba26e1338716543bca320"),
            new FP2Immutable(
                "0x0ba99e1866fc4c0cf43ffe6ad960556284ca7d8e855a7e3af2797bacfb8c57a2a7a5e18a2484982c888a1103fd0dc124",
                "0x123d01115638dab1120456555999d2407d14ddd2bf8876cdb821c68f46ce519b7fe3d8e4ab9eb03aa75bb0bd4b7f65af"));
    assertEquals(expected, psi(a));
  }

  @Test
  void psiTest2() {
    JacobianPoint a =
        new JacobianPoint(
            new FP2Immutable(
                "0x05961af88b4c983e2faa5bf2d27a9d7a3817157ccc7d0dbca371ef85913c044464963739fe30f6983bff1dd91e9d0477",
                "0x16d37bd0d28d29c9d7151c8d01c9e5d937171a2dedbb524e41209fd27f07e5d7e59ac4669185354a58712b8d955aa1e5"),
            new FP2Immutable(
                "0x106b07f33bc81015e7f92b1169fb74263acf68657005edd8d700d466f34212ed160c284c4d12ee604f5e4c6d1ecce960",
                "0x121b85e8f7e238522d894d7b0d165e864f09ce43d35f405c206d938c2b8e555048b8505e320d63462145a451813cdffb"),
            new FP2Immutable(
                "0x0f94d2523d89f219d8c01a38fb304c62b9ee8a3c76ffa36045645386c0715d7a47567f407d8032cbe9a72b59d45a7408",
                "0x15136dd936dcf5213e7cf6ffc48cce0ccb23e7040f68b2b13704b66a777b7cb967ceaaa48cc6b53c073972aa76a714fd"));
    JacobianPoint expected =
        new JacobianPoint(
            new FP2Immutable(
                "0x178bf79fda81334520519a907280f95823826300216e10ae98288a63a40f6e0eb436865cb868fc9eafda02b36bf13eb5",
                "0x02a8a98fb84ddbd0c6999a272be4058ac8696bc1c1b8383ff8062cefad3a2c0e06eb6320dce4a96908c83b1232ba4db3"),
            new FP2Immutable(
                "0x19ed0d4b4baf409457286376a12ecb89fb08439848807a539dc2d5dfc2b79fd8c136193a258068eeb90ae98ee7c8605a",
                "0x108e6f79a9a8a32868b1165d44945bac9a835e1ea741d7dbb1d26c84298f94e99097bc6b042d7fca6e05f2bd82768c53"),
            new FP2Immutable(
                "0x15e3b100a8e93dce134f620381083712e54b7c2e83441906e1195e500632111b55f0368f9b8b8c28fa495b3de9fbeab6",
                "0x13a650b413494731ba236bb3899b871c039047584514b33d0d8347e8163582d5053ca3e961e80fc5f018a863df3387b2"));
    assertEquals(expected, psi(a));
  }

  @Test
  void clearH2Test() {
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
                "0x15dbfd2d92b2bca073366fe4749e398cff164176c2ccb96063b76dc07916b9f3f12191fb6c22bf3941a717a806a402ee",
                "0x06fac8a75ef205fa01e3804a246806047fd5b00f1ad232b54e7dd3da46d25bdec8e558fbe533c2c144f67c05c0e98beb"),
            new FP2Immutable(
                "0x0dfc3a41a58b83eaf27ec2b1a662d1021e54b778cb3f7f21eb3791b32337da3c9eb85641ac386de14bebff8361dcc0d2",
                "0x016030f96ab7730b4211cee7fb4e48ff73144fba22ec219dea1712ae0dada6f609a9f341337e57f2fb9aec0934d61630"),
            new FP2Immutable(
                "0x107ba7a801702390ec39a648c99016f2208543ff1ad0530da82a1e10875916b10cb9c9e7a8ba65530f200d925ad46dda",
                "0x05d764887319704b18045f3799d5bd17348a2716d1d3246abe9b10a256942a7d241f83ab08b59e9b69f20f8e01d892f3"));
    assertEquals(expected, clear_h2(a));
  }

  // End-to-end reference tests from Python implementation data (RFC6979)
  @Test
  void refTest1() {
    FP2 x =
        new FP2(
            bigFromHex(
                "0x015395810c9355ecb17318aa253b5a3813283bd040e5095db43badac2d68198c4052a3fb95a0f8a525e6fdd8d87f6fd2"),
            bigFromHex(
                "0x0ed9c99489a1271562683a480e0e6cff61b35b428634602603b8a93d63c28e82347179e9ba2ab73f0cd9b02dc4c439f6"));
    FP2 y =
        new FP2(
            bigFromHex(
                "0x175e30a2b398a852aee5d7092ab8b4c06184e5f2e6d98de48fc91975487378db28c427e8b1f3228aecbcb9c4f6bd395d"),
            bigFromHex(
                "0x0f42496740190bbdcd47ab4f018ed0b8abec33f1f944ed01e12d9a6ef919a9b0efa3a5f52c54ea2ec08b111c356166f3"));
    G2Point expected = new G2Point(new ECP2(x, y));

    // Cipher suite is 0x02 in the current test data
    G2Point actual =
        hashToCurve.hashToCurve(
            Bytes.wrap("sample".getBytes(StandardCharsets.US_ASCII)), Bytes.fromHexString("0x02"));

    assertEquals(expected, actual);
  }

  @Test
  void refTest2() {
    FP2 x =
        new FP2(
            bigFromHex(
                "0x06977bf29c8323a178b743ad78024ed3f24982cdebc907e1222fd118b628c3bd2d94b91542daec09aed5f50eb7092a86"),
            bigFromHex(
                "0x1864574071934364e9ec08d3c339c38f0d4940f702e4cd9872d94fc5ed5977f3d239ed19d96430502465c88054548ccc"));
    FP2 y =
        new FP2(
            bigFromHex(
                "0x176fc275af8f788657761d2f9e16d1c36dc911f763b2d4d47db9364def2ecf69594a7b746b763c85dda1ffe41e27ce47"),
            bigFromHex(
                "0x1403de3f25f1c95dd4618104d077f0f4ea834357ed0b445ff36b86a23885407df08fab14ab7ef4c925d3ed7596d71e94"));
    G2Point expected = new G2Point(new ECP2(x, y));

    // Cipher suite is 0x02 in the current test data
    G2Point actual =
        hashToCurve.hashToCurve(
            Bytes.wrap("test".getBytes(StandardCharsets.US_ASCII)), Bytes.fromHexString("0x02"));

    assertEquals(expected, actual);
  }
}
