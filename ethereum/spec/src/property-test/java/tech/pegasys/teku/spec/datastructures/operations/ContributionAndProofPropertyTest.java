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

package tech.pegasys.teku.spec.datastructures.operations;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.Size;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.ContributionAndProof;
import tech.pegasys.teku.spec.networks.Eth2Network;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class ContributionAndProofPropertyTest {
  @Property
  void roundTrip(
      @ForAll final int seed,
      @ForAll("milestone") final SpecMilestone specMilestone,
      @ForAll final Eth2Network network,
      @ForAll final long slot,
      @ForAll @Size(32) final byte[] beaconBlockRoot)
      throws JsonProcessingException {
    final Spec spec = TestSpecFactory.create(specMilestone, network);
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(seed, spec);
    final ContributionAndProof contributionAndProof =
        dataStructureUtil.randomContributionAndProof(
            UInt64.fromLongBits(slot), Bytes32.wrap(beaconBlockRoot));
    final DeserializableTypeDefinition<ContributionAndProof> typeDefinition =
        contributionAndProof.getSchema().getJsonTypeDefinition();
    final String json = JsonUtil.serialize(contributionAndProof, typeDefinition);
    final ContributionAndProof result = JsonUtil.parse(json, typeDefinition);
    assertThat(result).isEqualTo(contributionAndProof);
  }

  @Provide
  Arbitrary<SpecMilestone> milestone() {
    return Arbitraries.of(SpecMilestone.class)
        .filter(m -> m.isGreaterThanOrEqualTo(SpecMilestone.ALTAIR));
  }
}
