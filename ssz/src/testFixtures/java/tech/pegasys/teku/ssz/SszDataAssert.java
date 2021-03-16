/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.ssz;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.assertj.core.api.AbstractAssert;
import tech.pegasys.teku.ssz.schema.SszContainerSchema;

public class SszDataAssert<T extends SszData> extends AbstractAssert<SszDataAssert<T>, T> {

  public static <T extends SszData> SszDataAssert<T> assertThatSszData(T sszData) {
    return new SszDataAssert<>(sszData, SszDataAssert.class);
  }

  private SszDataAssert(T t, Class<?> selfType) {
    super(t, selfType);
  }

  /** Compares two views by their getters recursively (if views are composite) */
  public SszDataAssert<T> isEqualByGettersTo(T expected) {
    List<String> res = compareByGetters(actual, expected);
    if (!res.isEmpty()) {
      String errMessage =
          IntStream.range(0, res.size() - 1)
              .mapToObj(i -> "  ".repeat(i) + res.get(i))
              .collect(Collectors.joining("\n"));
      errMessage += " ERROR: " + res.get(res.size() - 1);
      failWithActualExpectedAndMessage(actual, expected, errMessage);
    }
    return this;
  }

  public SszDataAssert<T> isEqualBySszTo(T expected) {
    assertThat(actual.sszSerialize()).isEqualTo(expected.sszSerialize());
    return this;
  }

  public SszDataAssert<T> isEqualByHashTreeRootTo(T expected) {
    assertThat(actual.hashTreeRoot()).isEqualTo(expected.hashTreeRoot());
    return this;
  }

  public SszDataAssert<T> isEqualByHashCodeTo(T expected) {
    assertThat(actual.hashCode()).isEqualTo(expected.hashCode());
    return this;
  }

  /**
   * Check than none of the following assertions are satisfied:
   *
   * <ul>
   *   <li>{@link #isEqualTo(Object)}
   *   <li>{@link #isEqualByGettersTo(SszData)}
   *   <li>{@link #isEqualBySszTo(SszData)}
   *   <li>{@link #isEqualByHashTreeRootTo(SszData)}
   * </ul>
   */
  public SszDataAssert<T> isNotEqualByAllMeansTo(T expected) {
    assertNot(() -> isEqualTo(expected), "isEqualTo");
    assertNot(() -> isEqualByGettersTo(expected), "isEqualByGettersTo");
    assertNot(() -> isEqualBySszTo(expected), "isEqualBySszTo");
    assertNot(() -> isEqualByHashTreeRootTo(expected), "isEqualByHashTreeRootTo");
    return this;
  }

  /**
   * Compares {@link SszData} with the following assertions:
   *
   * <ul>
   *   <li>{@link #isEqualTo(Object)}
   *   <li>{@link #isEqualByHashCodeTo(SszData)}
   *   <li>{@link #isEqualByGettersTo(SszData)}
   *   <li>{@link #isEqualBySszTo(SszData)}
   *   <li>{@link #isEqualByHashTreeRootTo(SszData)}
   * </ul>
   */
  public SszDataAssert<T> isEqualByAllMeansTo(T expected) {
    isEqualTo(expected);
    isEqualByHashCodeTo(expected);
    isEqualByGettersTo(expected);
    isEqualBySszTo(expected);
    isEqualByHashTreeRootTo(expected);
    return this;
  }

  @SuppressWarnings("EmptyCatch")
  private void assertNot(Runnable assertion, String error) {
    try {
      assertion.run();
      failWithMessage("Expecting negative assertion: " + error);
    } catch (AssertionError ignored) {
    }
  }

  public static boolean isEqualByGetters(SszData actual, SszData expected) {
    return compareByGetters(actual, expected).isEmpty();
  }

  private static List<String> compareByGetters(SszData actual, SszData expected) {
    if (!actual.getSchema().equals(expected.getSchema())) {
      return List.of(
          "Schemas don't match. Expected: "
              + expected.getSchema()
              + ", actual: "
              + actual.getSchema());
    }
    if (actual instanceof SszComposite) {
      SszComposite<?> c1 = (SszComposite<?>) actual;
      SszComposite<?> c2 = (SszComposite<?>) expected;
      if (c1.size() != c2.size()) {
        return List.of(
            "Expected SszList size doesn't match actual: " + c2.size() + " != " + c1.size());
      }
      for (int i = 0; i < c1.size(); i++) {
        List<String> res = compareByGetters(c1.get(i), c2.get(i));
        if (!res.isEmpty()) {
          String traceDetails;
          if (actual instanceof SszContainer) {
            SszContainerSchema<?> containerSchema = ((SszContainer) actual).getSchema();
            traceDetails =
                containerSchema.toString() + "." + containerSchema.getFieldNames().get(i);
          } else {
            traceDetails = actual.getSchema().toString() + "[" + i + "]";
          }
          return prepend(res, traceDetails);
        }
      }
      return Collections.emptyList();
    } else {
      if (!actual.equals(expected)) {
        return List.of("Primitive values differ. Expected: " + expected + ", actual: " + actual);
      } else {
        return Collections.emptyList();
      }
    }
  }

  @SuppressWarnings("unchecked")
  private static <T> List<T> prepend(List<T> list, T... args) {
    return Stream.concat(Stream.of(args), list.stream()).collect(Collectors.toList());
  }
}
