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

package org.ethereum.beacon.ssz;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.ethereum.beacon.crypto.Hashes;
import org.ethereum.beacon.ssz.annotation.SSZ;
import org.ethereum.beacon.ssz.annotation.SSZSerializable;
import org.ethereum.beacon.ssz.type.SSZContainerType;
import org.ethereum.beacon.ssz.type.SSZType;
import org.ethereum.beacon.ssz.type.SSZType.Type;
import org.ethereum.beacon.ssz.type.TypeResolver;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.util.bytes.BytesValue;

public class SSZTypeTest {

  @SSZSerializable
  public static class Container1 {
    @SSZ private int a1;

    @SSZ(type = "uint64")
    private int a2;

    @SSZ private boolean b;
    @SSZ List<Integer> c1;
    @SSZ List<List<Integer>> c2;
    @SSZ List<Container2> c3;
    @SSZ private int a3;

    public Container1(
        int a1,
        int a2,
        boolean b,
        List<Integer> c1,
        List<List<Integer>> c2,
        List<Container2> c3,
        int a3) {
      this.a1 = a1;
      this.a2 = a2;
      this.b = b;
      this.c1 = c1;
      this.c2 = c2;
      this.c3 = c3;
      this.a3 = a3;
    }

    public boolean isB() {
      return b;
    }

    public int getA1() {
      return a1;
    }

    public int getA2() {
      return a2;
    }

    public List<Integer> getC1() {
      return c1;
    }

    public List<List<Integer>> getC2() {
      return c2;
    }

    public List<Container2> getC3() {
      return c3;
    }

    public int getA3() {
      return a3;
    }
  }

  @SSZSerializable
  public static class Container2 {
    @SSZ int a1;
    @SSZ Container3 b1;

    @SSZ(vectorLength = 2)
    List<Container3> c1;

    @SSZ(vectorLengthVar = "testSize")
    List<Integer> c2;

    @SSZ Container3 b2;
    @SSZ int a2;

    public Container2(
        int a1, Container3 b1, List<Container3> c1, List<Integer> c2, Container3 b2, int a2) {
      this.a1 = a1;
      this.b1 = b1;
      this.c1 = c1;
      this.c2 = c2;
      this.b2 = b2;
      this.a2 = a2;
    }

    public int getA1() {
      return a1;
    }

    public Container3 getB1() {
      return b1;
    }

    public List<Container3> getC1() {
      return c1;
    }

    public List<Integer> getC2() {
      return c2;
    }

    public Container3 getB2() {
      return b2;
    }

    public int getA2() {
      return a2;
    }
  }

  @SSZSerializable
  public static class Container3 {
    @SSZ int a1;
    @SSZ int a2;

    public Container3(int a1, int a2) {
      this.a1 = a1;
      this.a2 = a2;
    }

    public int getA1() {
      return a1;
    }

    public int getA2() {
      return a2;
    }
  }

  @Test
  public void testTypeResolver1() {
    TypeResolver typeResolver =
        new SSZBuilder()
            .withExternalVarResolver(s -> "testSize".equals(s) ? 1 : null)
            .getTypeResolver();

    SSZType sszType1 = typeResolver.resolveSSZType(Container1.class);
    System.out.println(sszType1.dumpHierarchy());
    assertEquals(Type.CONTAINER, sszType1.getType());
    assertTrue(sszType1.isVariableSize());

    SSZType sszType2 = typeResolver.resolveSSZType(Container2.class);
    System.out.println(sszType2.dumpHierarchy());
    assertEquals(Type.CONTAINER, sszType2.getType());
    assertTrue(sszType2.isFixedSize());
    assertTrue(sszType2.getSize() > 0);
  }

  //  @Test // (expected = ExternalVariableNotDefined.class)
  public void testTypeResolverMissingExternalVar() {
    TypeResolver typeResolver =
        new SSZBuilder().withExternalVarResolver(s -> null).getTypeResolver();

    SSZType sszType1 = typeResolver.resolveSSZType(Container1.class);
    System.out.println(sszType1.dumpHierarchy());
  }

  @Test
  public void testSerializer1() {
    SSZSerializer serializer =
        new SSZBuilder()
            .withExternalVarResolver(s -> "testSize".equals(s) ? 3 : null)
            .buildSerializer();

    Container1 c1 =
        new Container1(
            0x11111111,
            0x22222222,
            true,
            asList(0x1111, 0x2222, 0x3333),
            asList(asList(0x11, 0x22), emptyList(), asList(0x33)),
            asList(
                new Container2(
                    0x44444444,
                    new Container3(0x5555, 0x6666),
                    asList(new Container3(0x7777, 0x8888), new Container3(0x9999, 0xaaaa)),
                    asList(0x1111, 0x2222, 0x3333),
                    new Container3(0, 0),
                    0),
                new Container2(
                    0x55555555,
                    new Container3(0xbbbb, 0xcccc),
                    asList(new Container3(0xdddd, 0xeeee), new Container3(0xffff, 0x1111)),
                    asList(0x1111, 0x2222, 0x3333),
                    new Container3(0, 0),
                    0)),
            0x33333333);

    byte[] bytes = serializer.encode(c1);
    System.out.println(BytesValue.wrap(bytes));

    Container1 res = serializer.decode(bytes, Container1.class);
    System.out.println(res);
  }

  @Test
  public void testSerializer2() {
    SSZSerializer serializer =
        new SSZBuilder()
            .withExternalVarResolver(s -> "testSize".equals(s) ? 2 : null)
            .buildSerializer();

    Container3 c3 = new Container3(0x5555, 0x6666);

    byte[] bytes = serializer.encode(c3);
    System.out.println(BytesValue.wrap(bytes));

    Container3 res = serializer.decode(bytes, Container3.class);
    System.out.println(res);
  }

  @Test
  public void testSerializer3() {
    SSZSerializer serializer =
        new SSZBuilder()
            .withExternalVarResolver(s -> "testSize".equals(s) ? 3 : null)
            .buildSerializer();

    Container2 c2 =
        new Container2(
            0x44444444,
            new Container3(0x5555, 0x6666),
            asList(new Container3(0x7777, 0x8888), new Container3(0x9999, 0xaaaa)),
            asList(0x1111, 0x2222, 0x3333),
            new Container3(0xbbbb, 0xcccc),
            0xdddd);

    byte[] bytes = serializer.encode(c2);
    System.out.println(BytesValue.wrap(bytes));

    Container2 res = serializer.decode(bytes, Container2.class);
    System.out.println(res);
  }

  @SSZSerializable
  public static class C2 {
    @SSZ public Boolean b1;
  }

  @Test
  public void testSerializer4() {
    SSZSerializer serializer =
        new SSZBuilder()
            .withExternalVarResolver(s -> "testSize".equals(s) ? 3 : null)
            .buildSerializer();

    C2 obj = new C2();
    obj.b1 = true;

    byte[] bytes = serializer.encode(obj);
    System.out.println(BytesValue.wrap(bytes));

    C2 res = serializer.decode(bytes, C2.class);
    System.out.println(res);

    assertTrue(obj.b1);
  }

  @SSZSerializable
  public interface Ifc1 {
    @SSZ(order = 0)
    int getA1();

    @SSZ(order = 1)
    long getA2();

    int getA3();
  }

  @SSZSerializable
  public static class Impl1 implements Ifc1 {

    @Override
    public int getA1() {
      return 0x1111;
    }

    @Override
    public long getA2() {
      return 0x2222;
    }

    @Override
    public int getA3() {
      return 0x3333;
    }

    public int getA4() {
      return 0x4444;
    }
  }

  @SSZSerializable
  public static class Impl2 extends Impl1 {

    @Override
    public int getA3() {
      return 0x5555;
    }

    public int getA5() {
      return 0x6666;
    }
  }

  @Test
  public void testTypeResolver2() throws Exception {
    SSZBuilder sszBuilder =
        new SSZBuilder().withExternalVarResolver(s -> "testSize".equals(s) ? 1 : null);
    SSZSerializer serializer = sszBuilder.buildSerializer();

    SSZType sszType = sszBuilder.getTypeResolver().resolveSSZType(Impl2.class);
    System.out.println(sszType.dumpHierarchy());

    assertTrue(sszType instanceof SSZContainerType);
    SSZContainerType containerType = (SSZContainerType) sszType;

    assertEquals(2, containerType.getChildTypes().size());
    assertEquals("a1", containerType.getChildTypes().get(0).getTypeDescriptor().getName());
    assertEquals("a2", containerType.getChildTypes().get(1).getTypeDescriptor().getName());

    byte[] bytes1 = serializer.encode(new Impl2());
    System.out.println(BytesValue.wrap(bytes1));

    byte[] bytes2 = serializer.encode(new Impl1());
    System.out.println(BytesValue.wrap(bytes2));
    assertArrayEquals(bytes1, bytes2);
    assertTrue(BytesValue.wrap(bytes1).toString().contains("1111"));
    assertTrue(BytesValue.wrap(bytes1).toString().contains("2222"));
  }

  @SSZSerializable
  public static class H1 {
    @SSZ public int a1;
    @SSZ public long a2;
    @SSZ public int a3;
  }

  @SSZSerializable
  public static class H2 {
    @SSZ public int a1;
    @SSZ public long a2;
  }

  @Test
  public void testHashTruncated1() throws Exception {
    SSZHasher hasher = new SSZBuilder().buildHasher(Hashes::sha256);

    H1 h1 = new H1();
    h1.a1 = 0x1111;
    h1.a2 = 0x2222;
    h1.a3 = 0x3333;

    H2 h2 = new H2();
    h2.a1 = 0x1111;
    h2.a2 = 0x2222;

    byte[] h1h = hasher.hash(h1);
    byte[] h2h = hasher.hash(h2);
    byte[] h1hTrunc = hasher.hashTruncateLast(h1, H1.class);

    assertArrayEquals(h2h, h1hTrunc);
  }
}
