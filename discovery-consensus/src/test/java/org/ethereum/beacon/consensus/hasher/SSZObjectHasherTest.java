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

package org.ethereum.beacon.consensus.hasher;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.ethereum.beacon.core.types.ValidatorIndex;
import org.ethereum.beacon.crypto.Hashes;
import org.ethereum.beacon.ssz.SSZBuilder;
import org.ethereum.beacon.ssz.SSZHasher;
import org.ethereum.beacon.ssz.annotation.SSZSerializable;
import org.ethereum.beacon.ssz.fixtures.AttestationRecord;
import org.ethereum.beacon.ssz.fixtures.Bitfield;
import org.ethereum.beacon.ssz.fixtures.Sign;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.util.bytes.BytesValue;

/** Tests of {@link SSZObjectHasher} */
public class SSZObjectHasherTest {
  private static byte[] DEFAULT_HASH =
      Hashes.sha256(BytesValue.fromHexString("aa")).getArrayUnsafe();
  private static Sign.Signature DEFAULT_SIG = new Sign.Signature();

  static {
    DEFAULT_SIG.r = new BigInteger("23452342342342342342342315643768758756967967");
    DEFAULT_SIG.s = new BigInteger("8713785871");
  }

  private SSZObjectHasher sszHasher;

  @BeforeEach
  public void setup() {
    SSZHasher sszHasher =
        new SSZBuilder().withExplicitAnnotations(false).buildHasher(Hashes::sha256);
    this.sszHasher = new SSZObjectHasher(sszHasher);
  }

  @Test
  public void bitfieldTest() {
    Bitfield bitfield = new Bitfield(BytesValue.fromHexString("abcd").getArrayUnsafe());

    BytesValue hash = sszHasher.getHash(bitfield);
    //    assertEquals(
    //        BytesValue.fromHexString(
    //            "0x02000000abcd0000000000000000000000000000000000000000000000000000"),
    //        hash);
  }

  @Test
  public void SignatureTest() {
    BytesValue hash = sszHasher.getHash(DEFAULT_SIG);
    //    assertEquals(
    //        BytesValue.fromHexString(
    //            "0x3d15cc04a0a366f8e0bc034db6df008f9eaf30d7bd0b1b40c4bd7bd141bd42f7"),
    //        hash);
  }

  @Test
  public void simpleTest() {
    AttestationRecord attestationRecord =
        new AttestationRecord(
            123,
            Collections.emptyList(),
            DEFAULT_HASH,
            new Bitfield(BytesValue.fromHexString("abcdef45").getArrayUnsafe()),
            DEFAULT_HASH,
            12412L,
            12400L,
            DEFAULT_SIG);

    BytesValue hash = sszHasher.getHash(attestationRecord);
    //    assertEquals(
    //        BytesValue.fromHexString(
    //            "0x3dfd0d63b835618cc9eb5f5da13b494b0e4ab41583b66809fed6fc4990f4dd51"),
    //        hash);
  }

  @Test
  public void simpleTruncateTest() {
    AttestationRecord attestationRecord =
        new AttestationRecord(
            123,
            Collections.emptyList(),
            DEFAULT_HASH,
            new Bitfield(BytesValue.fromHexString("abcdef45").getArrayUnsafe()),
            DEFAULT_HASH,
            12412L,
            12400L,
            DEFAULT_SIG);

    // Sig only removed
    BytesValue hash2 = sszHasher.getHashTruncateLast(attestationRecord);
    //    assertEquals(
    //        BytesValue.fromHexString(
    //            "0xae3f28da5903192bff0472fc12baf3acb8c2554606c2449f833d2079188eb871"),
    //        hash2);
  }

  @Test
  public void list32Test() {
    List<byte[]> hashes = new ArrayList<>();
    hashes.add(Hashes.sha256(BytesValue.fromHexString("aa")).getArrayUnsafe());
    hashes.add(Hashes.sha256(BytesValue.fromHexString("bb")).getArrayUnsafe());
    hashes.add(Hashes.sha256(BytesValue.fromHexString("cc")).getArrayUnsafe());
    AttestationRecord attestationRecord =
        new AttestationRecord(
            123,
            Collections.emptyList(),
            DEFAULT_HASH,
            new Bitfield(BytesValue.fromHexString("abcdef45").getArrayUnsafe()),
            DEFAULT_HASH,
            12412L,
            12400L,
            DEFAULT_SIG);

    BytesValue hash = sszHasher.getHash(attestationRecord);
    //    assertEquals(
    //        BytesValue.fromHexString(
    //            "0x3dfd0d63b835618cc9eb5f5da13b494b0e4ab41583b66809fed6fc4990f4dd51"),
    //        hash);
  }

  @Test
  public void smallItemsListTest() {
    List<Long> list = new ArrayList<>();
    list.add(1L);
    list.add(2L);
    list.add(12345L);
    list.add(Long.MAX_VALUE);
    SomeObject someObject = new SomeObject(list);

    BytesValue hash = sszHasher.getHash(someObject);
    //    assertEquals(
    //        BytesValue.fromHexString(
    //            "0xb1a18810e9b465f89b07c45716aef51cb243892a9ca24b37a4c322752fb905d6"),
    //        hash);
  }

  @Test
  public void smallItemTest() {
    AnotherObject anotherObject1 = new AnotherObject(1);
    AnotherObject anotherObject2 = new AnotherObject(2);

    BytesValue hash1 = sszHasher.getHash(anotherObject1);
    BytesValue hash2 = sszHasher.getHash(anotherObject2);
    assertEquals(
        BytesValue.fromHexString(
            "0x0100000000000000000000000000000000000000000000000000000000000000"),
        hash1);
    assertEquals(
        BytesValue.fromHexString(
            "0x0200000000000000000000000000000000000000000000000000000000000000"),
        hash2);
  }

  @Test
  public void listTest() {
    AnotherObject anotherObject1 = new AnotherObject(1);
    AnotherObject anotherObject2 = new AnotherObject(2);
    List<AnotherObject> anotherObjects = new ArrayList<>();
    anotherObjects.add(anotherObject1);
    anotherObjects.add(anotherObject2);
    BytesValue hash = sszHasher.getHash(anotherObjects);
    assertEquals(
        BytesValue.fromHexString(
            "0x79674903e05afe130f6b198098124b6cdaeeb2e62da2b14630eac79193332c52"),
        hash);
  }

  @Test
  public void listTest2() {
    List<ValidatorIndex> list = new ArrayList<>();
    list.add(ValidatorIndex.of(1));
    list.add(ValidatorIndex.of(1));
    BytesValue hash = sszHasher.getHash(list);
  }

  @SSZSerializable
  public static class SomeObject {
    private List<Long> list;

    public SomeObject(List<Long> list) {
      this.list = list;
    }

    public List<Long> getList() {
      return list;
    }
  }

  @SSZSerializable
  public static class AnotherObject {
    private int item;

    public AnotherObject(int item) {
      this.item = item;
    }

    public int getItem() {
      return item;
    }
  }
}
