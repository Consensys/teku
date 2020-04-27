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
import static tech.pegasys.teku.bls.hashToG2.IetfTools.HKDF_Expand;
import static tech.pegasys.teku.bls.hashToG2.IetfTools.HKDF_Extract;
import static tech.pegasys.teku.bls.hashToG2.IetfTools.HMAC_SHA256;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

class IetfToolsTest {

  /*
   * HMAC_SHA256 Tests
   *
   * HMAC reference results from here: https://www.freeformatter.com/hmac-generator.html
   */

  @Test
  void hmacSixtyFourByteKey() {
    byte[] text =
        "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789?!".getBytes(UTF_8);
    byte[] key = "1234567890123456789012345678901234567890123456789012345678901234".getBytes(UTF_8);
    Bytes expected =
        Bytes.fromHexString("0x72e4eae543b5669e6a777f9c984029860756f80a2b658d1bca6da5693c580ba1");
    Bytes actual = HMAC_SHA256(text, key);
    assertEquals(expected, actual);
  }

  @Test
  void hmacSixtyFiveByteKey() {
    byte[] text =
        "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789?!".getBytes(UTF_8);
    byte[] key =
        "12345678901234567890123456789012345678901234567890123456789012345".getBytes(UTF_8);
    Bytes expected =
        Bytes.fromHexString("0x7e1bef8bdcd6c63dd009aaeff87a6ba51a3e00efff6353ac514f6fae41361407");
    Bytes actual = HMAC_SHA256(text, key);
    assertEquals(expected, actual);
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
    assertEquals(expected, actual);
  }

  @Test
  void hmacRefTest2() {
    byte[] data =
        Bytes.fromHexString("0x7768617420646f2079612077616e7420666f72206e6f7468696e673f").toArray();
    byte[] key = Bytes.fromHexString("0x4a656665").toArray();
    Bytes expected =
        Bytes.fromHexString("0x5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843");
    Bytes actual = HMAC_SHA256(data, key);
    assertEquals(expected, actual);
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
    assertEquals(expected, actual);
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
    assertEquals(expected, actual);
  }

  @Test
  void hmacRefTest5() {
    byte[] data = Bytes.fromHexString("0x546573742057697468205472756e636174696f6e").toArray();
    byte[] key = Bytes.fromHexString("0x0c0c0c0c0c0c0c0c0c0c0c0c0c0c0c0c0c0c0c0c").toArray();
    Bytes expected = Bytes.fromHexString("0xa3b6167473100ee06e0c796c2955552b");
    Bytes actual = HMAC_SHA256(data, key).slice(0, 16);
    assertEquals(expected, actual);
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
    assertEquals(expected, actual);
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
    assertEquals(expected, actual);
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
}
