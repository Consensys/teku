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

package org.ethereum.beacon.discovery.util;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.math.BigInteger;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.MutableBytes;
import tech.pegasys.artemis.util.SSZTypes.Bytes4;

public class Utils {

  public static <A, B> Function<Optional<A>, Stream<B>> optionalFlatMap(Function<A, B> func) {
    return opt -> opt.map(a -> Stream.of(func.apply(a))).orElseGet(Stream::empty);
  }

  public static <A, B> Function<A, Stream<B>> nullableFlatMap(Function<A, B> func) {
    return n -> n != null ? Stream.of(func.apply(n)) : Stream.empty();
  }

  public static <C> void futureForward(
      CompletableFuture<C> result, CompletableFuture<C> forwardToFuture) {
    result.whenComplete(
        (res, t) -> {
          if (t != null) {
            forwardToFuture.completeExceptionally(t);
          } else {
            forwardToFuture.complete(res);
          }
        });
  }

  public static <C> Set<C> newLRUSet(int size) {
    return Collections.newSetFromMap(
        new LinkedHashMap<C, Boolean>() {
          protected boolean removeEldestEntry(Map.Entry<C, Boolean> eldest) {
            return size() > size;
          }
        });
  }

  /**
   * @param size required size, in bytes
   * @return byte array representation of BigInteger for unsigned numeric
   *     <p>{@link BigInteger#toByteArray()} adds a bit for the sign. If you work with unsigned
   *     numerics it's always a 0. But if an integer uses exactly 8-some bits, sign bit will add an
   *     extra 0 byte to the result, which could broke some things. This method removes this
   *     redundant prefix byte when extracting byte array from BigInteger
   */
  public static byte[] extractBytesFromUnsignedBigInt(BigInteger bigInteger, int size) {
    byte[] bigIntBytes = bigInteger.toByteArray();
    if (bigIntBytes.length == size) {
      return bigIntBytes;
    } else if (bigIntBytes.length == (size + 1)) {
      byte[] res = new byte[size];
      System.arraycopy(bigIntBytes, 1, res, 0, res.length);
      return res;
    } else if (bigIntBytes.length < size) {
      byte[] res = new byte[size];
      System.arraycopy(bigIntBytes, 0, res, size - bigIntBytes.length, bigIntBytes.length);
      return res;
    } else {
      throw new RuntimeException(
          String.format("Cannot extract bytes of size %s from BigInteger [%s]", size, bigInteger));
    }
  }

  /**
   * Left pad a {@link Bytes} value with zero bytes to create a {@link Bytes4}.
   *
   * @param value The bytes value pad.
   * @return A {@link Bytes4} that exposes the left-padded bytes of {@code value}.
   * @throws IllegalArgumentException if {@code value.size() &gt; 4}.
   */
  public static Bytes leftPad(Bytes value, int length) {
    checkNotNull(value);
    checkArgument(value.size() <= length, "Expected at most %s bytes but got %s", 4, value.size());
    MutableBytes result = MutableBytes.create(4);
    value.copyTo(result, 4 - value.size());
    return result;
  }
}
