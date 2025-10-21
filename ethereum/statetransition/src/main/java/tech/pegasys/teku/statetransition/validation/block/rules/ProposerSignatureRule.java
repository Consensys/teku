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

package tech.pegasys.teku.statetransition.validation.block.rules;

import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.reject;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.constants.Domain;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.statetransition.validation.GossipValidationHelper;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

public class ProposerSignatureRule implements StatefulValidationRule {

  private final Spec spec;
  private final GossipValidationHelper gossipValidationHelper;

  public ProposerSignatureRule(
      final Spec spec, final GossipValidationHelper gossipValidationHelper) {
    this.spec = spec;
    this.gossipValidationHelper = gossipValidationHelper;
  }

  /*
   * [REJECT] The proposer signature, signed_beacon_block.signature, is valid with respect to the proposer_index pubkey.
   */
  @Override
  public Optional<InternalValidationResult> validate(
      final SignedBeaconBlock block, final BeaconState parentState) {
    final Bytes32 domain =
        spec.getDomain(
            Domain.BEACON_PROPOSER,
            spec.getCurrentEpoch(parentState),
            parentState.getFork(),
            parentState.getGenesisValidatorsRoot());
    final Bytes signingRoot = spec.computeSigningRoot(block.getMessage(), domain);

    final boolean isSignatureValid =
        gossipValidationHelper.isSignatureValidWithRespectToProposerIndex(
            signingRoot, block.getProposerIndex(), block.getSignature(), parentState);

    if (!isSignatureValid) {
      return Optional.of(reject("Block signature is invalid"));
    }
    return Optional.empty();
  }
}
