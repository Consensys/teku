/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.spec.datastructures.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.apache.tuweni.bytes.Bytes;

public class Mutator {
  /** The types of mutations that can occur. */
  private enum Mutation {
    REPLACE,
    ADD_BEFORE,
    ADD_AFTER,
    DELETE
  }

  /** Likelihood that a specific mutation will happen. */
  private static final Map<Mutation, Double> MUTATION_PROBABILITIES =
      Map.of(
          Mutation.REPLACE, 0.01,
          Mutation.ADD_BEFORE, 0.001,
          Mutation.ADD_AFTER, 0.001,
          Mutation.DELETE, 0.001);

  /** The total probability that a mutation of any kind will happen. */
  private static final double MUTATION_PROBABILITY =
      MUTATION_PROBABILITIES.values().stream().reduce(0.0, Double::sum);

  /** If a random number falls in this range, that mutation should happen. */
  private static final Map<Mutation, Range> RANGE_MAP = new HashMap<>();

  static {
    // Make a map of non-overlapping ranges. The range reflects the probability of that mutation.
    // When fuzzing, a random number will be chosen. To determine which mutation should occur, we
    // check which of these ranges the random number falls into. For example, you could expect this
    // map to look something like:
    //
    // {
    //    REPLACE: Range(0, 0.01),
    //    ADD_BEFORE: Range(0.01, 0.011),
    //    ADD_AFTER: Range(0.011, 0.012),
    //    DELETE: Range(0.012, 0.013)
    // }
    double start = 0.0;
    for (Map.Entry<Mutation, Double> entry : MUTATION_PROBABILITIES.entrySet()) {
      RANGE_MAP.put(entry.getKey(), new Range(start, start + entry.getValue()));
      start += entry.getValue();
    }
  }

  /**
   * A helper function for generating random input data. Instead of blinding making data, start with
   * a known valid data and slightly mutate that. By default, you can expect a ~1% mutation rate.
   *
   * @param input The bytes to mutate, should resemble valid data.
   * @param seed The seed that should be used for randomness, for reproducibility.
   * @return A mutated version of the input.
   */
  public static Bytes mutate(final Bytes input, final int seed) {
    final Random random = new Random(seed);
    final List<Bytes> sections = new ArrayList<>();

    for (final byte b : input.toArray()) {
      final double randomNumber = Math.abs(random.nextGaussian());
      if (randomNumber < MUTATION_PROBABILITY) {

        Mutation op = null;
        for (final Map.Entry<Mutation, Range> entry : RANGE_MAP.entrySet()) {
          if (entry.getValue().contains(randomNumber)) {
            op = entry.getKey();
            break;
          }
        }
        assertThat(op).isNotNull();

        switch (op) {
          case ADD_BEFORE:
            final byte before = (byte) random.nextInt();
            sections.add(Bytes.of(before, b));
            break;
          case ADD_AFTER:
            final byte after = (byte) random.nextInt();
            sections.add(Bytes.of(b, after));
            break;
          case REPLACE:
            final byte replace = (byte) random.nextInt();
            sections.add(Bytes.of(replace));
            break;
          case DELETE:
            break;
        }
      } else {
        sections.add(Bytes.of(b));
      }
    }
    return Bytes.concatenate(sections);
  }

  private static class Range {
    private final double a;
    private final double b;

    public Range(final double a, final double b) {
      assertThat(a).isLessThanOrEqualTo(b);
      this.a = a;
      this.b = b;
    }

    /** Does the value fall into the exclusive [a, b) range. */
    public boolean contains(final double value) {
      return a <= value && value < b;
    }
  }
}
