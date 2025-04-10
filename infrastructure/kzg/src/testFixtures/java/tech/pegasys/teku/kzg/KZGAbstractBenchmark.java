/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.kzg;

import java.util.Collections;
import java.util.List;
import tech.pegasys.teku.kzg.trusted_setups.TrustedSetupLoader;

public class KZGAbstractBenchmark {
  private final KZG kzg = KZG.getInstance(false);

  public KZGAbstractBenchmark() {
    TrustedSetupLoader.loadTrustedSetupForTests(kzg);
  }

  protected KZG getKzg() {
    return kzg;
  }

  protected void printStats(final List<Integer> validationTimes) {
    int sum = 0;
    final int size = validationTimes.size();

    // Sum of elements
    for (int time : validationTimes) {
      sum += time;
    }

    // Mean
    final double mean = (double) sum / size;
    System.out.printf("Mean, ms: %.2f%n", mean);

    // Standard Deviation
    double sumOfSquares = 0.0;
    for (int time : validationTimes) {
      sumOfSquares += Math.pow(time - mean, 2);
    }
    final double standardDeviation = Math.sqrt(sumOfSquares / size);
    System.out.printf("Std, ms: %.2f%n", standardDeviation);

    // Min and Max
    final int min = Collections.min(validationTimes);
    final int max = Collections.max(validationTimes);
    System.out.println("Min, ms: " + min);
    System.out.println("Max, ms: " + max);
  }
}
