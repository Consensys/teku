/*
 * Copyright Consensys Software Inc., 2022
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

package tech.pegasys.teku.reference.eip7594.networking;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.reference.TestDataUtils.loadYaml;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigInteger;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.ethtests.finder.TestDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.reference.TestExecutor;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.logic.versions.eip7594.helpers.MiscHelpersEip7594;

public class GetCustodyColumnsTestExecutor implements TestExecutor {

  @Override
  public void runTest(final TestDefinition testDefinition) throws Exception {
    final GetCustodyColumnsMetaData metaData =
        loadYaml(testDefinition, "meta.yaml", GetCustodyColumnsMetaData.class);
    final SpecVersion spec = testDefinition.getSpec().getGenesisSpec();
    final List<UInt64> actualResult =
        MiscHelpersEip7594.required(spec.miscHelpers())
            .computeCustodyColumnIndexes(metaData.getNodeId(), metaData.getCustodySubnetCount());
    assertThat(new HashSet<>(actualResult)).isEqualTo(metaData.getResult());
  }

  private static class GetCustodyColumnsMetaData {

    @JsonProperty(value = "node_id", required = true)
    private String nodeId;

    @JsonProperty(value = "custody_subnet_count", required = true)
    private int custodySubnetCount;

    @JsonProperty(value = "result", required = true)
    private List<Integer> result;

    public UInt256 getNodeId() {
      return UInt256.valueOf(new BigInteger(nodeId));
    }

    public int getCustodySubnetCount() {
      return custodySubnetCount;
    }

    public Set<UInt64> getResult() {
      return result.stream().map(UInt64::valueOf).collect(Collectors.toUnmodifiableSet());
    }
  }
}
