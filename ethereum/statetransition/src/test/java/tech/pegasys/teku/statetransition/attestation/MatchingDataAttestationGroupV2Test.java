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

package tech.pegasys.teku.statetransition.attestation;

import static tech.pegasys.teku.spec.SpecMilestone.ELECTRA;
import static tech.pegasys.teku.spec.SpecMilestone.PHASE0;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import java.util.Optional;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;

@TestSpecContext(milestone = {PHASE0, ELECTRA})
public class MatchingDataAttestationGroupV2Test extends MatchingDataAttestationGroupTest {

  @Override
  MatchingDataAttestationGroup instantiateGroup(
      final Spec spec,
      final AttestationData attestationData,
      final Optional<Int2IntMap> committeeSizes) {
    return new MatchingDataAttestationGroupV2(spec, attestationData, committeeSizes);
  }
}
