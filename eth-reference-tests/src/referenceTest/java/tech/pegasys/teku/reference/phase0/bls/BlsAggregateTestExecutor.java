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
import static tech.pegasys.teku.ethtests.finder.BlsTestFinder.BLS_DATA_FILE;
import static tech.pegasys.teku.reference.phase0.bls.BlsTests.parseSignature;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.stream.Collectors;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.ethtests.finder.TestDefinition;
import tech.pegasys.teku.reference.phase0.TestDataUtils;
import tech.pegasys.teku.reference.phase0.TestExecutor;

public class BlsAggregateTestExecutor implements TestExecutor {

  @Override
  public void runTest(final TestDefinition testDefinition) throws Throwable {
    final Data data = TestDataUtils.loadYaml(testDefinition, BLS_DATA_FILE, Data.class);
    final List<BLSSignature> signatures = data.getInput();
    final BLSSignature expectedSignature = data.getOutput();
    assertThat(BLS.aggregate(signatures)).isEqualTo(expectedSignature);
  }

  private static class Data {
    @JsonProperty(value = "input", required = true)
    private List<String> input;

    @JsonProperty(value = "output", required = true)
    private String output;

    public List<BLSSignature> getInput() {
      return input.stream().map(BlsTests::parseSignature).collect(Collectors.toList());
    }

    public BLSSignature getOutput() {
      return parseSignature(output);
    }
  }
}
