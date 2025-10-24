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

package tech.pegasys.teku.statetransition.validation.block.rules.deneb;

import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.reject;

import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.statetransition.validation.StatelessValidationRule;

public record KzgCommitmentsRule(Spec spec) implements StatelessValidationRule {

  private static final Logger LOG = LogManager.getLogger();

  /*
   * [REJECT] The length of KZG commitments is less than or equal to the limitation defined in Consensus Layer
   */
  @Override
  public Optional<InternalValidationResult> validate(final SignedBeaconBlock block) {
    final SszList<SszKZGCommitment> blobKzgCommitments =
        block
            .getMessage()
            .getBody()
            .getOptionalBlobKzgCommitments()
            .orElseThrow(() -> new IllegalStateException("Block missing kzg commitments"));

    final Integer maxBlobsPerBlock = spec.getMaxBlobsPerBlockAtSlot(block.getSlot()).orElseThrow();
    final int blobKzgCommitmentsCount = blobKzgCommitments.size();
    if (blobKzgCommitmentsCount > maxBlobsPerBlock) {
      LOG.trace(
          "BlockValidator: Block has {} kzg commitments, max allowed {}",
          blobKzgCommitmentsCount,
          maxBlobsPerBlock);
      return Optional.of(
          reject(
              "Block has %d kzg commitments, max allowed %d",
              blobKzgCommitmentsCount, maxBlobsPerBlock));
    }

    return Optional.empty();
  }
}
