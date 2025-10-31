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
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.statetransition.validation.GossipValidationHelper;
import tech.pegasys.teku.statetransition.validation.StatefulValidationRule;
import tech.pegasys.teku.statetransition.validation.StatelessValidationRule;
import tech.pegasys.teku.statetransition.validation.block.rules.bellatrix.ExecutionPayloadTimestampRule;
import tech.pegasys.teku.statetransition.validation.block.rules.deneb.KzgCommitmentsRule;
import tech.pegasys.teku.statetransition.validation.block.rules.gloas.ExecutionPayloadParentHashRule;
import tech.pegasys.teku.statetransition.validation.block.rules.gloas.ExecutionPayloadParentRootRule;
import tech.pegasys.teku.statetransition.validation.block.rules.phase0.BlockAlreadyImportedRule;
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
      final EquivocationChecker equivocationChecker) {
    this.statelessPipelines = new EnumMap<>(SpecMilestone.class);
    this.statefulPipelines = new EnumMap<>(SpecMilestone.class);

    // Phase0
    final FutureSlotRule futureSlotRule = new FutureSlotRule(gossipValidationHelper);
    final BlockAlreadyImportedRule blockAlreadyImportedRule =
        new BlockAlreadyImportedRule(gossipValidationHelper);
    final LatestFinalizedSlotRule latestFinalizedSlotRule =
        new LatestFinalizedSlotRule(gossipValidationHelper);
    final EquivocationRule equivocationRule = new EquivocationRule(equivocationChecker);
    final BlockParentSeenRule blockParentSeenRule = new BlockParentSeenRule(gossipValidationHelper);
    final BlockParentValidRule blockParentValidRule =
        new BlockParentValidRule(gossipValidationHelper);
    final BlockParentSlotRule blockParentSlotRule = new BlockParentSlotRule(gossipValidationHelper);
    final BlockFinalizedCheckpointRule blockFinalizedCheckpointRule =
        new BlockFinalizedCheckpointRule(gossipValidationHelper);
    final ProposerSignatureRule proposerSignatureRule =
        new ProposerSignatureRule(spec, gossipValidationHelper);
    final ExpectedProposerRule expectedProposerRule =
        new ExpectedProposerRule(gossipValidationHelper);

    // Bellatrix
    final ExecutionPayloadTimestampRule executionPayloadTimestampRule =
        new ExecutionPayloadTimestampRule(spec);

    // Deneb
    final KzgCommitmentsRule kzgCommitmentsRule = new KzgCommitmentsRule(spec);

    // Gloas
    final ExecutionPayloadParentRootRule executionPayloadParentRootRule =
        new ExecutionPayloadParentRootRule();
    final ExecutionPayloadParentHashRule executionPayloadParentHashRule =
        new ExecutionPayloadParentHashRule();

    // Phase0 & Altair
    final List<StatelessValidationRule> phase0StatelessRules =
        List.of(
            futureSlotRule,
            blockAlreadyImportedRule,
            latestFinalizedSlotRule,
            equivocationRule,
            blockParentSeenRule,
            blockParentValidRule,
            blockParentSlotRule,
            blockFinalizedCheckpointRule);
    final List<StatefulValidationRule> phase0StatefulRules =
        List.of(expectedProposerRule, proposerSignatureRule);

    statelessPipelines.put(SpecMilestone.PHASE0, phase0StatelessRules);
    statefulPipelines.put(SpecMilestone.PHASE0, phase0StatefulRules);
    statelessPipelines.put(SpecMilestone.ALTAIR, phase0StatelessRules);
    statefulPipelines.put(SpecMilestone.ALTAIR, phase0StatefulRules);

    // Bellatrix & Capella
    final List<StatefulValidationRule> bellatrixStatefulRules =
        Stream.concat(phase0StatefulRules.stream(), Stream.of(executionPayloadTimestampRule))
            .toList();

    statelessPipelines.put(SpecMilestone.BELLATRIX, phase0StatelessRules);
    statefulPipelines.put(SpecMilestone.BELLATRIX, bellatrixStatefulRules);
    statelessPipelines.put(SpecMilestone.CAPELLA, phase0StatelessRules);
    statefulPipelines.put(SpecMilestone.CAPELLA, bellatrixStatefulRules);

    // Deneb, Electra & Fulu
    final List<StatelessValidationRule> denebStatelessRules =
        Stream.concat(phase0StatelessRules.stream(), Stream.of(kzgCommitmentsRule)).toList();

    statelessPipelines.put(SpecMilestone.DENEB, denebStatelessRules);
    statefulPipelines.put(SpecMilestone.DENEB, bellatrixStatefulRules);
    statelessPipelines.put(SpecMilestone.ELECTRA, denebStatelessRules);
    statefulPipelines.put(SpecMilestone.ELECTRA, bellatrixStatefulRules);
    statelessPipelines.put(SpecMilestone.FULU, denebStatelessRules);
    statefulPipelines.put(SpecMilestone.FULU, bellatrixStatefulRules);

    // Gloas
    final List<StatelessValidationRule> gloasStatelessRules =
        Stream.concat(
                denebStatelessRules.stream().filter(rule -> !(rule instanceof KzgCommitmentsRule)),
                Stream.of(executionPayloadParentRootRule))
            .toList();

    final List<StatefulValidationRule> gloasStatefulRules =
        Stream.concat(
                bellatrixStatefulRules.stream()
                    .filter(rule -> !(rule instanceof ExecutionPayloadTimestampRule)),
                Stream.of(executionPayloadParentHashRule))
            .toList();

    statelessPipelines.put(SpecMilestone.GLOAS, gloasStatelessRules);
    statefulPipelines.put(SpecMilestone.GLOAS, gloasStatefulRules);
  }

  public List<StatelessValidationRule> getStatelessPipelineFor(final SpecMilestone milestone) {
    return statelessPipelines.get(milestone);
  }

  public List<StatefulValidationRule> getStatefulPipelineFor(final SpecMilestone milestone) {
    return statefulPipelines.get(milestone);
  }
}
