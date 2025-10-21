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

package tech.pegasys.teku.statetransition.validation.block;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.statetransition.validation.GossipValidationHelper;
import tech.pegasys.teku.statetransition.validation.block.rules.BlockFinalizedCheckpointRule;
import tech.pegasys.teku.statetransition.validation.block.rules.BlockParentSeenRule;
import tech.pegasys.teku.statetransition.validation.block.rules.BlockParentSlotRule;
import tech.pegasys.teku.statetransition.validation.block.rules.BlockParentValidRule;
import tech.pegasys.teku.statetransition.validation.block.rules.EquivocationRule;
import tech.pegasys.teku.statetransition.validation.block.rules.ExpectedProposerRule;
import tech.pegasys.teku.statetransition.validation.block.rules.FutureSlotRule;
import tech.pegasys.teku.statetransition.validation.block.rules.LatestFinalizedSlotRule;
import tech.pegasys.teku.statetransition.validation.block.rules.ProposerSignatureRule;
import tech.pegasys.teku.statetransition.validation.block.rules.StatefulValidationRule;
import tech.pegasys.teku.statetransition.validation.block.rules.StatelessValidationRule;

public class ForkBlockValidationPipelines {
  private final Map<SpecMilestone, List<StatelessValidationRule>> statelessPipelines;
  private final Map<SpecMilestone, List<StatefulValidationRule>> statefulPipelines;

  public ForkBlockValidationPipelines(
      final Spec spec,
      final GossipValidationHelper gossipValidationHelper,
      final Map<SlotAndProposerIndex, Bytes32> receivedValidBlockRoots) {
    this.statelessPipelines = new EnumMap<>(SpecMilestone.class);
    this.statefulPipelines = new EnumMap<>(SpecMilestone.class);

    // Stateless rules
    final FutureSlotRule futureSlotRule = new FutureSlotRule(gossipValidationHelper);
    final LatestFinalizedSlotRule latestFinalizedSlotRule =
        new LatestFinalizedSlotRule(gossipValidationHelper);
    final EquivocationRule equivocationRule = new EquivocationRule(receivedValidBlockRoots);
    final BlockParentSeenRule blockParentSeenRule = new BlockParentSeenRule(gossipValidationHelper);
    final BlockParentValidRule blockParentValidRule =
        new BlockParentValidRule(gossipValidationHelper);
    final BlockParentSlotRule blockParentSlotRule = new BlockParentSlotRule(gossipValidationHelper);
    final BlockFinalizedCheckpointRule blockFinalizedCheckpointRule =
        new BlockFinalizedCheckpointRule(gossipValidationHelper);

    // Stateful rules
    final ProposerSignatureRule proposerSignatureRule =
        new ProposerSignatureRule(spec, gossipValidationHelper);
    final ExpectedProposerRule expectedProposerRule =
        new ExpectedProposerRule(gossipValidationHelper);

    // Phase0 pipelines
    statelessPipelines.put(
        SpecMilestone.PHASE0,
        List.of(
            futureSlotRule,
            latestFinalizedSlotRule,
            equivocationRule,
            blockParentSeenRule,
            blockParentValidRule,
            blockParentSlotRule,
            blockFinalizedCheckpointRule));
    statefulPipelines.put(
        SpecMilestone.PHASE0, List.of(expectedProposerRule, proposerSignatureRule));
  }

  public List<StatelessValidationRule> getStatelessPipelineFor(final SpecMilestone milestone) {
    return statelessPipelines.get(milestone);
  }

  public List<StatefulValidationRule> getStatefulPipelineFor(final SpecMilestone milestone) {
    return statefulPipelines.get(milestone);
  }
}
