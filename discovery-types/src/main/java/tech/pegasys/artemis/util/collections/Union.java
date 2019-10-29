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

package tech.pegasys.artemis.util.collections;

import javax.annotation.Nullable;

/**
 * Dedicated type representing SSZ Union
 * (https://github.com/ethereum/eth2.0-specs/blob/dev/specs/simple-serialize.md#composite-types)
 *
 * <p>It may hold one of predefined type value or special Null value It also holds the index of type
 * currently stored in this structure
 *
 * <p>For SSZ serialization/deserialiazation/hashing this type can be used in one of the following
 * means:
 *
 * <p>Direct subclassing with declaring all the SSZ Union types explicitly like the following:
 * <code>
 *     @SSZSerializable
 *     public static class SafeUnion extends UnionImpl {
 *
 *       public SafeUnion() {
 *         setValue(0, null);
 *       }
 *
 *       public SafeUnion(UInt64 intMember) {
 *         setValue(1, intMember);
 *       }
 *
 *       @SSZ(order = 1)
 *       public Null getNull() {
 *         throw new RuntimeException("Shouldn't be called");
 *       }
 *
 *       @SSZ(order = 2)
 *       public UInt64 getIntMember() {
 *         return getValueSafe(1);
 *       }
 *     }
 * </code> or by using anonymous helpers ({@link U2}, {@link U3} ...) which incorporate Union types
 * information withing generic type arguments. Like below: <code>
 *     @SSZSerializable
 *     public static class AnonymousUnionContainer {
 *       @SSZ
 *       public WriteUnion.U3<Null, UInt64, List<Integer>> union = WriteUnion.U3.create();
 *     }
 * </code> Please note that the latter usecase is only possible when the Union is a member of
 * another SSZ container since only in this case generic types specialization is available at
 * runtime
 *
 * <p>This class denotes read-only Union. See {@link MutableUnion} for read-write version and {@link
 * UnionImpl} for implementing class
 */
public interface Union {

  /**
   * Special class representing Null SSZ Union member Note that Null member MUST declared at first
   * place
   */
  final class Null {}

  /** Returns the index of Union type which is currently stored */
  int getTypeIndex();

  /** Returns the value stored or null if this Union has Null member and current type index is 0 */
  @Nullable
  <C> C getValue();

  /**
   * Returns the current value and checks that passed index is the current type index
   *
   * @throws IllegalStateException if indicies don't match
   */
  default <C> C getValueSafe(int typeIndex) {
    if (typeIndex != getTypeIndex()) {
      throw new IllegalStateException(
          "Union type index ("
              + getTypeIndex()
              + ") and requested value index ("
              + typeIndex
              + ") not match");
    }
    return getValue();
  }

  interface GenericTypedUnion extends Union {}

  interface U2<P1, P2> extends GenericTypedUnion {
    default P1 getMember1() {
      return getValueSafe(0);
    }

    default P2 getMember2() {
      return getValueSafe(1);
    }

    static <P1, P2> U2<P1, P2> create() {
      return new GenericUnionImpl();
    }
  }

  interface U3<P1, P2, P3> extends U2<P1, P2> {
    default P3 getMember3() {
      return getValueSafe(2);
    }

    static <P1, P2, P3> U3<P1, P2, P3> create() {
      return new GenericUnionImpl();
    }
  }

  interface U4<P1, P2, P3, P4> extends U3<P1, P2, P3> {
    default P4 getMember4() {
      return getValueSafe(3);
    }

    static <P1, P2, P3, P4> U4<P1, P2, P3, P4> create() {
      return new GenericUnionImpl();
    }
  }

  interface U5<P1, P2, P3, P4, P5> extends U4<P1, P2, P3, P4> {
    default P5 getMember5() {
      return getValueSafe(4);
    }

    static <P1, P2, P3, P4, P5> U5<P1, P2, P3, P4, P5> create() {
      return new GenericUnionImpl();
    }
  }
}
