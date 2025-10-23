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
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.statetransition.validation.GossipValidationHelper;
import tech.pegasys.teku.statetransition.validation.StatefulValidationRule;
import tech.pegasys.teku.statetransition.validation.StatelessValidationRule;
import tech.pegasys.teku.statetransition.validation.block.rules.bellatrix.ExecutionPayloadTimestampRule;
import tech.pegasys.teku.statetransition.validation.block.rules.deneb.KzgCommitmentsRule;
import tech.pegasys.teku.statetransition.validation.block.rules.gloas.ExecutionPayloadParentHashRule;
import tech.pegasys.teku.statetransition.validation.block.rules.gloas.ExecutionPayloadParentRootRule;
import tech.pegasys.teku.statetransition.validation.block.rules.phase0.BlockFinalizedCheckpointRule;
import tech.pegasys.teku.statetransition.validation.block.rules.phase0.BlockParentSeenRule;
import tech.pegasys.teku.statetransition.validation.block.rules.phase0.BlockParentSlotRule;
import tech.pegasys.teku.statetransition.validation.block.rules.phase0.BlockParentValidRule;
import tech.pegasys.teku.statetransition.validation.block.rules.phase0.EquivocationRule;
import tech.pegasys.teku.statetransition.validation.block.rules.phase0.ExpectedProposerRule;
import tech.pegasys.teku.statetransition.validation.block.rules.phase0.FutureSlotRule;
import tech.pegasys.teku.statetransition.validation.block.rules.phase0.LatestFinalizedSlotRule;
import tech.pegasys.teku.statetransition.validation.block.rules.phase0.ProposerSignatureRule;

public class BlockGossipValidationPipelines {
  private final Map<SpecMilestone, List<StatelessValidationRule>> statelessPipelines;
  private final Map<SpecMilestone, List<StatefulValidationRule>> statefulPipelines;

  public BlockGossipValidationPipelines(
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
    final KzgCommitmentsRule kzgCommitmentsRule = new KzgCommitmentsRule(spec);
    final ExecutionPayloadParentRootRule executionPayloadParentRootRule =
        new ExecutionPayloadParentRootRule();
    final ExecutionPayloadParentHashRule executionPayloadParentHashRule =
        new ExecutionPayloadParentHashRule();

    // Stateful rules
    final ProposerSignatureRule proposerSignatureRule =
        new ProposerSignatureRule(spec, gossipValidationHelper);
    final ExpectedProposerRule expectedProposerRule =
        new ExpectedProposerRule(gossipValidationHelper);
    final ExecutionPayloadTimestampRule executionPayloadTimestampRule =
        new ExecutionPayloadTimestampRule(spec);

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

    // Altair pipelines (same as phase0)
    statelessPipelines.put(SpecMilestone.ALTAIR, statelessPipelines.get(SpecMilestone.PHASE0));
    statefulPipelines.put(SpecMilestone.ALTAIR, statefulPipelines.get(SpecMilestone.PHASE0));

    // Bellatrix pipelines
    statelessPipelines.put(SpecMilestone.BELLATRIX, statelessPipelines.get(SpecMilestone.ALTAIR));
    statefulPipelines.put(
        SpecMilestone.BELLATRIX,
        Stream.concat(
                statefulPipelines.get(SpecMilestone.ALTAIR).stream(),
                Stream.of(executionPayloadTimestampRule))
            .toList());

    // Capella pipelines (same as Bellatrix)
    statelessPipelines.put(SpecMilestone.CAPELLA, statelessPipelines.get(SpecMilestone.BELLATRIX));
    statefulPipelines.put(SpecMilestone.CAPELLA, statefulPipelines.get(SpecMilestone.BELLATRIX));

    // Deneb pipelines
    statelessPipelines.put(
        SpecMilestone.DENEB,
        Stream.concat(
                statelessPipelines.get(SpecMilestone.CAPELLA).stream(),
                Stream.of(kzgCommitmentsRule))
            .toList());
    statefulPipelines.put(SpecMilestone.DENEB, statefulPipelines.get(SpecMilestone.CAPELLA));

    // Electra pipelines (same as Deneb)
    statelessPipelines.put(SpecMilestone.ELECTRA, statelessPipelines.get(SpecMilestone.DENEB));
    statefulPipelines.put(SpecMilestone.ELECTRA, statefulPipelines.get(SpecMilestone.DENEB));

    // Fulu pipelines (same as Electra)
    statelessPipelines.put(SpecMilestone.FULU, statelessPipelines.get(SpecMilestone.ELECTRA));
    statefulPipelines.put(SpecMilestone.FULU, statefulPipelines.get(SpecMilestone.ELECTRA));

    // Gloas pipelines
    final List<StatelessValidationRule> gloasStatelessRules =
        Stream.concat(
                statelessPipelines.get(SpecMilestone.ELECTRA).stream()
                    .filter(
                        statelessValidationRule ->
                            statelessValidationRule instanceof ExecutionPayloadTimestampRule
                                || statelessValidationRule instanceof KzgCommitmentsRule),
                Stream.of(executionPayloadParentRootRule))
            .toList();
    statelessPipelines.put(SpecMilestone.GLOAS, gloasStatelessRules);
    statefulPipelines.put(
        SpecMilestone.GLOAS,
        Stream.concat(
                statefulPipelines.get(SpecMilestone.ELECTRA).stream(),
                Stream.of(executionPayloadParentHashRule))
            .toList());
  }

  public List<StatelessValidationRule> getStatelessPipelineFor(final SpecMilestone milestone) {
    return statelessPipelines.get(milestone);
  }

  public List<StatefulValidationRule> getStatefulPipelineFor(final SpecMilestone milestone) {
    return statefulPipelines.get(milestone);
  }
}
