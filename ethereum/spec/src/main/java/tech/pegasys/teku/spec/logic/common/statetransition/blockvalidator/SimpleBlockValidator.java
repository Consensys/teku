/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.spec.logic.common.statetransition.blockvalidator;

import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.bls.BLSSignatureVerifier.InvalidSignatureException;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.cache.IndexedAttestationCache;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.BlockProcessingException;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.StateTransitionException;
import tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil;
import tech.pegasys.teku.spec.logic.common.util.BlockProcessorUtil;
import tech.pegasys.teku.spec.logic.common.util.ValidatorsUtil;

/**
 * Base logic of a block validation
 *
 * <p>Delegates bls signature verifications to BLSSignatureVerifier instance Optionally may skip
 * some validations.
 */
class SimpleBlockValidator implements BlockValidator {

  private final SpecConfig specConfig;
  private final BeaconStateUtil beaconStateUtil;
  private final BlockProcessorUtil blockProcessorUtil;
  private final ValidatorsUtil validatorsUtil;
  private final BLSSignatureVerifier signatureVerifier;

  SimpleBlockValidator(
      final SpecConfig specConfig,
      final BeaconStateUtil beaconStateUtil,
      final BlockProcessorUtil blockProcessorUtil,
      final ValidatorsUtil validatorsUtil,
      final BLSSignatureVerifier signatureVerifier) {
    this.specConfig = specConfig;
    this.beaconStateUtil = beaconStateUtil;
    this.blockProcessorUtil = blockProcessorUtil;
    this.validatorsUtil = validatorsUtil;
    this.signatureVerifier = signatureVerifier;
  }

  public SimpleBlockValidator(
      final SpecConfig specConfig,
      final BeaconStateUtil beaconStateUtil,
      final BlockProcessorUtil blockProcessorUtil,
      final ValidatorsUtil validatorsUtil) {
    this(
        specConfig,
        beaconStateUtil,
        blockProcessorUtil,
        validatorsUtil,
        BLSSignatureVerifier.SIMPLE);
  }

  @Override
  public BlockValidationResult validatePreState(
      BeaconState preState,
      SignedBeaconBlock block,
      IndexedAttestationCache indexedAttestationCache) {
    try {
      // Verify signature
      verifyBlockSignature(preState, block);

      // Verify body
      BeaconBlock blockMessage = block.getMessage();
      BeaconBlockBody blockBody = blockMessage.getBody();
      blockProcessorUtil.verifyAttestations(
          preState, blockBody.getAttestations(), signatureVerifier, indexedAttestationCache);
      blockProcessorUtil.verifyRandao(preState, blockMessage, signatureVerifier);

      if (!blockProcessorUtil.verifyProposerSlashings(
          preState, blockBody.getProposer_slashings(), signatureVerifier)) {
        return BlockValidationResult.FAILED;
      }

      if (!blockProcessorUtil.verifyVoluntaryExits(
          preState, blockBody.getVoluntary_exits(), signatureVerifier)) {
        return BlockValidationResult.FAILED;
      }
      return BlockValidationResult.SUCCESSFUL;
    } catch (BlockProcessingException | InvalidSignatureException e) {
      return BlockValidationResult.failedExceptionally(e);
    }
  }

  @Override
  public BlockValidationResult validatePostState(BeaconState postState, SignedBeaconBlock block) {
    if (!block.getMessage().getStateRoot().equals(postState.hashTreeRoot())) {
      return BlockValidationResult.failedExceptionally(
          new StateTransitionException(
              "Block state root does NOT match the calculated state root!\n"
                  + "Block state root: "
                  + block.getMessage().getStateRoot().toHexString()
                  + "New state root: "
                  + postState.hashTreeRoot().toHexString()));
    } else {
      return BlockValidationResult.SUCCESSFUL;
    }
  }

  private void verifyBlockSignature(final BeaconState state, SignedBeaconBlock signed_block)
      throws BlockProcessingException {
    final int proposerIndex = beaconStateUtil.getBeaconProposerIndex(state, signed_block.getSlot());
    final BLSPublicKey proposerPublicKey =
        validatorsUtil
            .getValidatorPubKey(state, UInt64.valueOf(proposerIndex))
            .orElseThrow(
                () ->
                    new BlockProcessingException(
                        "Public key not found for validator " + proposerIndex));
    final Bytes signing_root =
        beaconStateUtil.computeSigningRoot(
            signed_block.getMessage(),
            beaconStateUtil.getDomain(state, specConfig.getDomainBeaconProposer()));
    try {
      signatureVerifier.verifyAndThrow(
          proposerPublicKey, signing_root, signed_block.getSignature());
    } catch (InvalidSignatureException e) {
      throw new BlockProcessingException("Invalid block signature: " + signed_block);
    }
  }
}
