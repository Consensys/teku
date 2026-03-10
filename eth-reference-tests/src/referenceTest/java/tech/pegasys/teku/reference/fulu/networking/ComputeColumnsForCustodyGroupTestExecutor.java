/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.reference.fulu.networking;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.reference.TestDataUtils.loadYaml;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import tech.pegasys.teku.ethtests.finder.TestDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.reference.TestExecutor;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.MiscHelpersFulu;

public class ComputeColumnsForCustodyGroupTestExecutor implements TestExecutor {

  @Override
  public void runTest(final TestDefinition testDefinition) throws Exception {
    final ComputeColumnForCustodyGroupMetaData metaData =
        loadYaml(testDefinition, "meta.yaml", ComputeColumnForCustodyGroupMetaData.class);
    final SpecVersion spec = testDefinition.getSpec().getGenesisSpec();
    final List<UInt64> actualResult =
        MiscHelpersFulu.required(spec.miscHelpers())
            .computeColumnsForCustodyGroup(UInt64.valueOf(metaData.getCustodyGroup()));
    assertThat(new HashSet<>(actualResult)).isEqualTo(metaData.getResult());
  }

  private static class ComputeColumnForCustodyGroupMetaData {

    @JsonProperty(value = "custody_group", required = true)
    private int custodyGroup;

    @JsonProperty(value = "result", required = true)
    private List<Integer> result;

    public int getCustodyGroup() {
      return custodyGroup;
    }

    public Set<UInt64> getResult() {
      return result.stream().map(UInt64::valueOf).collect(Collectors.toUnmodifiableSet());
    }
  }
}
