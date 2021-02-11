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

package tech.pegasys.teku.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.assertj.core.api.AbstractAssert;

public class DoubleAssert extends AbstractAssert<DoubleAssert, Double> {

  private DoubleAssert(final Double aDouble) {
    super(aDouble, DoubleAssert.class);
  }

  public static DoubleAssert assertThatDouble(final Double actual) {
    return new DoubleAssert(actual);
  }

  public void isApproximately(final Double expected) {
    isApproximately(expected, 0.00005);
  }

  public void isApproximately(final Double expected, final double tolerance) {
    final double difference = Math.abs(actual - expected);
    assertThat(difference)
        .withFailMessage(
            "Expected "
                + actual
                + " to differ from "
                + expected
                + " by less than "
                + tolerance
                + ", but got a different of: "
                + difference)
        .isLessThan(tolerance);
  }
}
