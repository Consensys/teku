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

package tech.pegasys.teku.statetransition.validation;

import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import tech.pegasys.teku.bls.BLSKeyGenerator;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.Attestation.AttestationSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.generator.AttestationGenerator;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.spec.logic.common.block.AbstractBlockProcessor;
import tech.pegasys.teku.spec.logic.common.util.AsyncBLSSignatureVerifier;
import tech.pegasys.teku.storage.client.ChainUpdater;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.server.StateStorageMode;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

/**
 * The following validations MUST pass before forwarding the attestation on the subnet.
 *
 * <p>The attestation's committee index (attestation.data.index) is for the correct subnet.
 *
 * <ul>
 *   <li>Phase 0
 *       <p>attestation.data.slot is within the last ATTESTATION_PROPAGATION_SLOT_RANGE slots
 *       (within a * MAXIMUM_GOSSIP_CLOCK_DISPARITY allowance) -- i.e. attestation.data.slot + *
 *       ATTESTATION_PROPAGATION_SLOT_RANGE >= current_slot >= attestation.data.slot (a client MAY
 *       queue * future attestations for processing at the appropriate slot).
 *   <li>Deneb
 *       <p>attestation.data.slot is equal to or earlier than the current_slot (with a
 *       MAXIMUM_GOSSIP_CLOCK_DISPARITY allowance) -- i.e. attestation.data.slot <= current_slot (a
 *       client MAY queue future attestation for processing at the appropriate slot).
 *       <p>the epoch of attestation.data.slot is either the current or previous epoch (with a
 *       MAXIMUM_GOSSIP_CLOCK_DISPARITY allowance) -- i.e.
 *       compute_epoch_at_slot(attestation.data.slot) in (get_previous_epoch(state),
 *       get_current_epoch(state))
 * </ul>
 *
 * <p>The attestation is unaggregated -- that is, it has exactly one participating validator
 * (len([bit for bit in attestation.aggregation_bits if bit == 0b1]) == 1).
 *
 * <p>The attestation is the first valid attestation received for the participating validator for
 * the slot, attestation.data.slot.
 *
 * <p>The block being voted for (attestation.data.beacon_block_root) passes validation.
 *
 * <p>The signature of attestation is valid.
 */
abstract class AbstractAttestationValidatorTest {
  private static final Logger LOG = LogManager.getLogger();

  private static final List<BLSKeyPair> VALIDATOR_KEYS = BLSKeyGenerator.generateKeyPairs(64);

  protected final Spec spec = createSpec();
  protected final AttestationSchema attestationSchema =
      spec.getGenesisSchemaDefinitions().getAttestationSchema();
  protected final StorageSystem storageSystem =
      InMemoryStorageSystemBuilder.buildDefault(StateStorageMode.ARCHIVE);
  protected final RecentChainData recentChainData = storageSystem.recentChainData();
  protected final ChainBuilder chainBuilder = ChainBuilder.create(spec, VALIDATOR_KEYS);
  protected final ChainUpdater chainUpdater =
      new ChainUpdater(storageSystem.recentChainData(), chainBuilder);
  protected final AttestationGenerator attestationGenerator =
      new AttestationGenerator(spec, chainBuilder.getValidatorKeys());
  protected final AsyncBLSSignatureVerifier signatureVerifier =
      AsyncBLSSignatureVerifier.wrap(BLSSignatureVerifier.SIMPLE);

  protected final AttestationValidator validator =
      new AttestationValidator(spec, recentChainData, signatureVerifier, new StubMetricsSystem());

  @BeforeAll
  public static void init() {
    AbstractBlockProcessor.depositSignatureVerifier = BLSSignatureVerifier.NO_OP;
  }

  @AfterAll
  public static void reset() {
    AbstractBlockProcessor.depositSignatureVerifier =
        AbstractBlockProcessor.DEFAULT_DEPOSIT_SIGNATURE_VERIFIER;
  }

  @BeforeEach
  public void setUp() {
    chainUpdater.initializeGenesis(false);
  }

  public abstract Spec createSpec();

  protected Predicate<? super CompletableFuture<InternalValidationResult>> rejected(
      final String messageContents) {
    return result -> {
      try {
        InternalValidationResult internalValidationResult = result.get();
        return internalValidationResult.isReject()
            && internalValidationResult.getDescription().orElseThrow().contains(messageContents);
      } catch (Exception e) {
        LOG.error("failed to evaluate rejected predicate", e);
        return false;
      }
    };
  }

  protected InternalValidationResult validate(final Attestation attestation) {
    final BeaconState state = safeJoin(recentChainData.getBestState().orElseThrow());

    return validator
        .validate(
            ValidatableAttestation.fromNetwork(
                spec, attestation, spec.computeSubnetForAttestation(state, attestation)))
        .join();
  }

  protected boolean hasSameValidators(
      final Attestation attestation1, final Attestation attestation) {
    return attestation.getAggregationBits().equals(attestation1.getAggregationBits());
  }
}
