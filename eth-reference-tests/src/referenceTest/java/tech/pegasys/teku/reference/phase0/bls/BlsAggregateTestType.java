/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.reference.phase0.bls;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.function.ThrowingConsumer;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSSignature;

public class BlsAggregateTestType implements ThrowingConsumer<BlsTestData> {

  @Override
  public void accept(final BlsTestData blsTestData) throws Throwable {
    final List<BLSSignature> signatures = blsTestData.getInputSignatures();
    final BLSSignature expectedSignature = blsTestData.getOutputSignature();
    assertThat(BLS.aggregate(signatures)).isEqualTo(expectedSignature);
  }
}
