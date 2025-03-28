/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.beaconrestapi.handlers.v1.events;

import tech.pegasys.teku.ethereum.json.types.EthereumTypes;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.operations.SignedInclusionList;

public class InclusionListEvent extends Event<InclusionListEvent.InclusionListData> {
  InclusionListEvent(final SignedInclusionList signedInclusionList, final SpecMilestone milestone) {
    super(
        SerializableTypeDefinition.object(InclusionListEvent.InclusionListData.class)
            .name("InclusionListEvent")
            .withField("version", EthereumTypes.MILESTONE_TYPE, InclusionListData::milestone)
            .withField(
                "data",
                signedInclusionList.getSchema().getJsonTypeDefinition(),
                InclusionListData::data)
            .build(),
        new InclusionListData(milestone, signedInclusionList));
  }

  record InclusionListData(SpecMilestone milestone, SignedInclusionList data) {}
}
