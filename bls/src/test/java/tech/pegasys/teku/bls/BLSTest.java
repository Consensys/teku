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

package tech.pegasys.teku.bls;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.Bytes48;
import org.junit.jupiter.api.Test;

public abstract class BLSTest {

  @Test
  void succeedsWhenWeCanSignAndVerify() {
    BLSKeyPair keyPair = BLSTestUtil.randomKeyPair(42);
    Bytes message = Bytes.wrap("Hello, world!".getBytes(UTF_8));
    BLSSignature signature = BLS.sign(keyPair.getSecretKey(), message);
    assertTrue(BLS.verify(keyPair.getPublicKey(), message, signature));
  }

  @Test
  // The empty signature is not a valid signature
  void succeedsWhenCallingVerifyWithEmptySignatureReturnsFalse() {
    assertFalse(
        BLS.verify(
            BLSTestUtil.randomPublicKey(17),
            Bytes.wrap("Test".getBytes(UTF_8)),
            BLSSignature.empty()));
  }

  @Test
  void returnsTrueEvenIfBLSSignatureIsWrongWhenBLSVerificationIsDisabled() {
    BLSConstants.disableBLSVerification();
    assertTrue(
        BLS.verify(
            BLSTestUtil.randomPublicKey(17),
            Bytes.wrap("Test".getBytes(UTF_8)),
            BLSSignature.empty()));

    BLSConstants.enableBLSVerification();
    assertFalse(
        BLS.verify(
            BLSTestUtil.randomPublicKey(17),
            Bytes.wrap("Test".getBytes(UTF_8)),
            BLSSignature.empty()));
  }

  @Test
  void succeedsWhenAggregatingASingleSignatureReturnsTheSameSignature() {
    BLSSignature signature = BLSTestUtil.randomSignature(1);
    assertEquals(signature, BLS.aggregate(singletonList(signature)));
  }

  @Test
  // The empty signature is not a valid signature
  void succeedsWhenPassingEmptySignatureToAggregateSignaturesThrowsIllegalArgumentException() {
    BLSSignature signature1 = BLSTestUtil.randomSignature(1);
    BLSSignature signature2 = BLSSignature.empty();
    BLSSignature signature3 = BLSTestUtil.randomSignature(3);
    assertThrows(
        IllegalArgumentException.class,
        () -> BLS.aggregate(Arrays.asList(signature1, signature2, signature3)));
  }

  @Test
  void succeedsWhenCorrectlySigningAndVerifyingAggregateSignaturesReturnsTrue() {
    Bytes message = Bytes.wrap("Hello, world!".getBytes(UTF_8));
    BLSKeyPair keyPair1 = BLSTestUtil.randomKeyPair(1);
    BLSKeyPair keyPair2 = BLSTestUtil.randomKeyPair(2);
    BLSKeyPair keyPair3 = BLSTestUtil.randomKeyPair(3);

    List<BLSPublicKey> publicKeys =
        Arrays.asList(keyPair1.getPublicKey(), keyPair2.getPublicKey(), keyPair3.getPublicKey());
    List<BLSSignature> signatures =
        Arrays.asList(
            BLS.sign(keyPair1.getSecretKey(), message),
            BLS.sign(keyPair2.getSecretKey(), message),
            BLS.sign(keyPair3.getSecretKey(), message));
    BLSSignature aggregatedSignature = BLS.aggregate(signatures);

    assertTrue(BLS.fastAggregateVerify(publicKeys, message, aggregatedSignature));
  }

  @Test
  void fastAggregateVerify_verify4Signers() {
    Bytes message =
        Bytes.fromHexString("0x999bb85f3690c2ccb1607dd3e11a7e114038eb4044bdbdd340bc81aa3e5e0c9e");

    List<BLSPublicKey> publicKeys =
        Stream.of(
                "0x97f1d3a73197d7942695638c4fa9ac0fc3688c4f9774b905a14e3a3f171bac586c55e83ff97a1aeffb3af00adb22c6bb",
                "0xa572cbea904d67468808c8eb50a9450c9721db309128012543902d0ac358a62ae28f75bb8f1c7c42c39a8c5529bf0f4e",
                "0x89ece308f9d1f0131765212deca99697b112d61f9be9a5f1f3780a51335b3ff981747a0b2ca2179b96d2c0c9024e5224",
                "0xac9b60d5afcbd5663a8a44b7c5a02f19e9a77ab0a35bd65809bb5c67ec582c897feb04decc694b13e08587f3ff9b5b60")
            .map(pk -> BLSPublicKey.fromBytesCompressedValidate(Bytes48.fromHexString(pk)))
            .collect(Collectors.toList());

    BLSSignature aggregatedSignature =
        BLSSignature.fromBytesCompressed(
            Bytes.fromHexString(
                "0xb2550663aa862b2741c9abc94f7b0b8a725b6f12b8f214d833e214e87c64235e4b1fb1e1ee64e5ae942cb3e0392699fc0524ae6f35072d1f243668de730be8745ab5be3314f90c107e246cefd1f1b97cd7241cfe97f4c80aeb354e8fac2ea720"));

    assertTrue(BLS.fastAggregateVerify(publicKeys, message, aggregatedSignature));
  }

  @Test
  void succeedsWhenAggregateVerifyWithRepeatedMessagesReturnsFalse() {
    Bytes message1 = Bytes.wrap("Hello, world 1!".getBytes(UTF_8));
    Bytes message2 = Bytes.wrap("Hello, world 2!".getBytes(UTF_8));
    BLSKeyPair keyPair1 = BLSTestUtil.randomKeyPair(1);
    BLSKeyPair keyPair2 = BLSTestUtil.randomKeyPair(2);
    BLSKeyPair keyPair3 = BLSTestUtil.randomKeyPair(3);

    List<BLSPublicKey> publicKeys =
        Arrays.asList(keyPair1.getPublicKey(), keyPair2.getPublicKey(), keyPair3.getPublicKey());
    List<Bytes> messages = Arrays.asList(message1, message2, message2);
    List<BLSSignature> signatures =
        Arrays.asList(
            BLS.sign(keyPair1.getSecretKey(), message1),
            BLS.sign(keyPair2.getSecretKey(), message2),
            BLS.sign(keyPair3.getSecretKey(), message2));
    BLSSignature aggregatedSignature = BLS.aggregate(signatures);

    assertFalse(BLS.aggregateVerify(publicKeys, messages, aggregatedSignature));
  }

  @Test
  void succeedsWhenAggregateVerifyWithDistinctMessagesReturnsTrue() {
    Bytes message1 = Bytes.wrap("Hello, world 1!".getBytes(UTF_8));
    Bytes message2 = Bytes.wrap("Hello, world 2!".getBytes(UTF_8));
    Bytes message3 = Bytes.wrap("Hello, world 3!".getBytes(UTF_8));
    BLSKeyPair keyPair1 = BLSTestUtil.randomKeyPair(1);
    BLSKeyPair keyPair2 = BLSTestUtil.randomKeyPair(2);
    BLSKeyPair keyPair3 = BLSTestUtil.randomKeyPair(3);

    List<BLSPublicKey> publicKeys =
        Arrays.asList(keyPair1.getPublicKey(), keyPair2.getPublicKey(), keyPair3.getPublicKey());
    List<Bytes> messages = Arrays.asList(message1, message2, message3);
    List<BLSSignature> signatures =
        Arrays.asList(
            BLS.sign(keyPair1.getSecretKey(), message1),
            BLS.sign(keyPair2.getSecretKey(), message2),
            BLS.sign(keyPair3.getSecretKey(), message3));
    BLSSignature aggregatedSignature = BLS.aggregate(signatures);

    assertTrue(BLS.aggregateVerify(publicKeys, messages, aggregatedSignature));
  }

  @Test
  void succeedsWhenAggregateVerifyWithInfinitePairReturnsFalse() {
    Bytes message1 = Bytes.wrap("Hello, world 1!".getBytes(UTF_8));
    Bytes message2 = Bytes.wrap("Hello, world 2!".getBytes(UTF_8));
    Bytes message3 = Bytes.wrap("Hello, world 3!".getBytes(UTF_8));
    BLSKeyPair keyPair1 = BLSTestUtil.randomKeyPair(1);
    BLSKeyPair keyPair2 = BLSTestUtil.randomKeyPair(2);

    List<BLSPublicKey> publicKeys =
        Arrays.asList(keyPair1.getPublicKey(), keyPair2.getPublicKey(), infinityG1());
    List<Bytes> messages = Arrays.asList(message1, message2, message3);
    List<BLSSignature> signatures =
        Arrays.asList(
            BLS.sign(keyPair1.getSecretKey(), message1),
            BLS.sign(keyPair2.getSecretKey(), message2),
            infinityG2());
    BLSSignature aggregatedSignature = BLS.aggregate(signatures);

    assertFalse(BLS.aggregateVerify(publicKeys, messages, aggregatedSignature));
  }

  @Test
  // The standard says that this is INVALID
  void aggregateThrowsExceptionForEmptySignatureList() {
    assertThrows(IllegalArgumentException.class, () -> BLS.aggregate(new ArrayList<>()));
  }

  @Test
  // The standard says that this is INVALID
  void aggregateVerifyReturnsFalseForEmptyPubkeysList() {
    assertFalse(BLS.aggregateVerify(new ArrayList<>(), new ArrayList<>(), BLSSignature.empty()));
  }

  @Test
  // The standard says that this is INVALID
  void fastAggregateVerifyReturnsFalseForEmptyPubkeysList() {
    assertFalse(BLS.fastAggregateVerify(new ArrayList<>(), Bytes.EMPTY, BLSSignature.empty()));
  }

  static BLSPublicKey infinityG1() {
    return BLSPublicKey.fromBytesCompressed(
        Bytes48.fromHexString(
            "0x"
                + "c0000000000000000000000000000000"
                + "00000000000000000000000000000000"
                + "00000000000000000000000000000000"));
  }

  static BLSSignature infinityG2() {
    return BLSSignature.fromBytesCompressed(
        Bytes.fromHexString(
            "0x"
                + "c000000000000000000000000000000000000000000000000000000000000000"
                + "0000000000000000000000000000000000000000000000000000000000000000"
                + "0000000000000000000000000000000000000000000000000000000000000000"));
  }

  static BLSSecretKey zeroSK() {
    return BLSSecretKey.fromBytes(Bytes32.ZERO);
  }

  @Test
  void succeedsWhenPubkeyAndSignatureBothTheIdentityIsFailed() {
    Bytes message = Bytes.wrap("Hello, world!".getBytes(UTF_8));
    assertFalse(BLS.verify(infinityG1(), message, infinityG2()));
  }

  @Test
  void succeedsWhenZeroSecretKeyGivesInfinitePublicKey() {
    assertEquals(infinityG1(), new BLSPublicKey(zeroSK()));
  }

  @Test
  void succeedsWhenSigningWithZeroPKeyThrows() {
    Bytes message = Bytes.wrap("Hello, world!".getBytes(UTF_8));
    assertThrows(IllegalArgumentException.class, () -> BLS.sign(zeroSK(), message));
  }

  @Test
  void aggregateInfinitePublicKeyAndSignature() {
    BLSKeyPair keyPair1 = BLSTestUtil.randomKeyPair(1);
    BLSKeyPair keyPairInf = new BLSKeyPair(zeroSK());

    Bytes message = Bytes.wrap("Hello, world!".getBytes(UTF_8));
    BLSSignature sig1 = BLS.sign(keyPair1.getSecretKey(), message);

    BLSPublicKey pubKeyAggr =
        BLSPublicKey.aggregate(List.of(keyPair1.getPublicKey(), keyPairInf.getPublicKey()));
    BLSSignature sigAggr = BLS.aggregate(List.of(sig1, infinityG2()));
    boolean res1 = BLS.verify(pubKeyAggr, message, sigAggr);
    assertFalse(res1);
  }

  @Test
  void batchVerify2InfinitePublicKeyAndSignature() {
    BLSKeyPair keyPairInf = new BLSKeyPair(zeroSK());

    Bytes message = Bytes.wrap("Hello, world!".getBytes(UTF_8));

    BatchSemiAggregate semiAggregate1 =
        BLS.prepareBatchVerify(0, List.of(keyPairInf.getPublicKey()), message, infinityG2());
    BatchSemiAggregate semiAggregateInf =
        BLS.prepareBatchVerify(1, List.of(keyPairInf.getPublicKey()), message, infinityG2());

    boolean res1 = BLS.completeBatchVerify(List.of(semiAggregate1, semiAggregateInf));
    assertFalse(res1);
  }

  @Test
  void batchVerifyInfinitePublicKeyAndSignature() {
    BLSKeyPair keyPair1 = BLSTestUtil.randomKeyPair(1);
    BLSKeyPair keyPairInf = new BLSKeyPair(zeroSK());

    Bytes message = Bytes.wrap("Hello, world!".getBytes(UTF_8));
    BLSSignature sig1 = BLS.sign(keyPair1.getSecretKey(), message);

    BatchSemiAggregate semiAggregate1 =
        BLS.prepareBatchVerify(0, List.of(keyPair1.getPublicKey()), message, sig1);
    BatchSemiAggregate semiAggregateInf =
        BLS.prepareBatchVerify(1, List.of(keyPairInf.getPublicKey()), message, infinityG2());

    boolean res1 = BLS.completeBatchVerify(List.of(semiAggregate1, semiAggregateInf));
    assertFalse(res1);
  }

  @Test
  void batchVerifyInvalidInfiniteSignature() {
    BLSKeyPair keyPair1 = BLSTestUtil.randomKeyPair(1);

    Bytes message = Bytes.wrap("Hello, world!".getBytes(UTF_8));

    BatchSemiAggregate semiAggregate =
        BLS.prepareBatchVerify(0, List.of(keyPair1.getPublicKey()), message, infinityG2());

    boolean res1 = BLS.completeBatchVerify(List.of(semiAggregate));
    assertFalse(res1);
  }

  @Test
  void testSignatureVerifyForSomeRealValues() {
    String signingRoot = "0x95b8e2ba063ab62f68ebe7db0a9669ab9e7906aa4e060e1cc0b67b294ce8c5e4";
    String sig =
        "0xab51f352e90509ca5085ec43af9ad3ea4ae42bf30c91af7dcdc113ef79cfc8601b756f18d8cf634436d8b6b0095fc5680066f382eb3728a7090c55c9afb66e8f94b44d2682db8ef5de4b89928d1744824df174e0c800b9e934b0ad14e6388163";
    String pk =
        "0xb5e8f551c28abd6ef8253581ffad0834bfd8fafa9948d09b337c9c5f21d6e7fd6065a1ee35ac5146ac17344f97490301";

    Bytes msg = Bytes.fromHexString(signingRoot);
    BLSSignature signature = BLSSignature.fromBytesCompressed(Bytes.fromHexString(sig));
    BLSPublicKey publicKey = BLSPublicKey.fromBytesCompressed(Bytes48.fromHexString(pk));

    boolean res = BLS.verify(publicKey, msg, signature);
    assertTrue(res);
  }

  @Test
  void succeedsWhenWeCanSignAndVerifyWithValidDST() {
    final Bytes DST =
        Bytes.wrap(
            "BLS_SIG_BLS12381G2_XMD:SHA-256_SSWU_RO_NUL_".getBytes(StandardCharsets.US_ASCII));
    final BLSKeyPair keyPair = BLSTestUtil.randomKeyPair(42);
    final Bytes message = Bytes.wrap("Hello, world!".getBytes(UTF_8));
    final BLSSignature signature = BLS.sign(keyPair.getSecretKey(), message, DST);
    assertTrue(BLS.verify(keyPair.getPublicKey(), message, signature, DST));
  }

  @Test
  void verifyWithDifferentDSTFails() {
    final Bytes DST =
        Bytes.wrap(
            "BLS_SIG_BLS12381G2_XMD:SHA-256_SSWU_RO_NUL_".getBytes(StandardCharsets.US_ASCII));
    final BLSKeyPair keyPair = BLSTestUtil.randomKeyPair(42);
    final Bytes message = Bytes.wrap("Hello, world!".getBytes(UTF_8));
    final BLSSignature signature = BLS.sign(keyPair.getSecretKey(), message, DST);
    assertFalse(BLS.verify(keyPair.getPublicKey(), message, signature)); // uses ETH2_DST
  }
}
