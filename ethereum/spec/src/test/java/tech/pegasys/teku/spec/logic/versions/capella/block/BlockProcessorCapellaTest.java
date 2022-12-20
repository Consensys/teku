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

package tech.pegasys.teku.spec.logic.versions.capella.block;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.Withdrawal;
import tech.pegasys.teku.spec.datastructures.operations.BlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.spec.logic.common.statetransition.blockvalidator.BlockValidationResult;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.StateTransitionException;
import tech.pegasys.teku.spec.logic.versions.bellatrix.block.BlockProcessorBellatrixTest;
import tech.pegasys.teku.spec.logic.versions.eip4844.blobs.BlobsSidecarAvailabilityChecker;
import tech.pegasys.teku.spec.logic.versions.eip4844.block.KzgCommitmentsProcessor;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsCapella;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class BlockProcessorCapellaTest extends BlockProcessorBellatrixTest {

  @Override
  protected Spec createSpec() {
    return TestSpecFactory.createMainnetCapella();
  }

  @Test
  void shouldRejectBellatrixBlock() {
    final DataStructureUtil data = new DataStructureUtil(TestSpecFactory.createMinimalBellatrix());
    BeaconState preState = createBeaconState();
    final SignedBeaconBlock block = data.randomSignedBeaconBlock(preState.getSlot().increment());
    assertThatThrownBy(
            () ->
                spec.processBlock(
                    preState,
                    block,
                    BLSSignatureVerifier.SIMPLE,
                    Optional.empty(),
                    KzgCommitmentsProcessor.NOOP,
                    BlobsSidecarAvailabilityChecker.NOOP))
        .isInstanceOf(StateTransitionException.class);
  }

  @Test
  void shouldCreateExpectedWithdrawalAddress() {
    final DataStructureUtil data = new DataStructureUtil(TestSpecFactory.createMinimalCapella());
    Bytes20 eth1Address = data.randomBytes20();

    Bytes32 bytes32 = BlockProcessorCapella.getWithdrawalAddressFromEth1Address(eth1Address);
    // ends with eth1 address
    assertThat(bytes32.toHexString()).endsWith(eth1Address.toUnprefixedHexString());
    // starts with 0x01 (eth1 prefix) and 0x00 x 11 (buffer)
    assertThat(bytes32.toHexString()).startsWith("0x010000000000000000000000");
  }

  @Test
  void shouldNotSweepMoreValidatorsThanLimit() {
    final int maxValidatorsPerWithdrawalSweep =
        spec.getGenesisSpecConfig().toVersionCapella().get().getMaxValidatorsPerWithdrawalSweep();
    final DataStructureUtil data = new DataStructureUtil(TestSpecFactory.createMinimalCapella());

    final Validator withdrawableValidator1 =
        makeValidator(data.randomPublicKey(), data.randomEth1WithdrawalCredentials());
    final UInt64 withdrawableValidator1Balance =
        spec.getGenesisSpecConfig().getMaxEffectiveBalance().plus(1024000);

    final Validator withdrawableValidator2 =
        makeValidator(data.randomPublicKey(), data.randomEth1WithdrawalCredentials());
    final UInt64 withdrawableValidator2Balance =
        spec.getGenesisSpecConfig().getMaxEffectiveBalance().plus(1024000);

    final List<Validator> validators = new ArrayList<>();
    final List<UInt64> balances = new ArrayList<>();
    for (int i = 0; i < maxValidatorsPerWithdrawalSweep - 1; i++) {
      validators.add(dataStructureUtil.randomValidator());
      balances.add(spec.getGenesisSpecConfig().getMaxEffectiveBalance());
    }

    // Withdrawable validator within limit
    validators.add(withdrawableValidator1);
    balances.add(withdrawableValidator1Balance);

    // Withdrawable validator outside limit
    validators.add(withdrawableValidator2);
    balances.add(withdrawableValidator2Balance);

    final BeaconState preState = createBeaconStateWithValidatorsAndBalances(validators, balances);

    final Optional<List<Withdrawal>> withdrawals =
        spec.getBlockProcessor(preState.getSlot()).getExpectedWithdrawals(preState);
    assertThat(withdrawals).isPresent();
    assertThat(withdrawals.get()).hasSize(1);
  }

  @Test
  void shouldFindPartialWithdrawals() {
    final DataStructureUtil data = new DataStructureUtil(TestSpecFactory.createMinimalCapella());
    Validator validator =
        makeValidator(data.randomPublicKey(), data.randomEth1WithdrawalCredentials());
    BeaconState preState =
        createBeaconState(
            true, spec.getGenesisSpecConfig().getMaxEffectiveBalance().plus(1024000), validator);
    final Optional<List<Withdrawal>> withdrawals =
        spec.getBlockProcessor(preState.getSlot()).getExpectedWithdrawals(preState);
    assertThat(withdrawals.get().get(0).getAmount()).isEqualTo(UInt64.valueOf(1024000));
  }

  @Test
  void shouldFindFullWithdrawals() {
    final DataStructureUtil data = new DataStructureUtil(TestSpecFactory.createMinimalCapella());
    Validator validator =
        makeValidator(
            data.randomPublicKey(),
            data.randomEth1WithdrawalCredentials(),
            UInt64.ZERO,
            UInt64.ZERO);
    final UInt64 balance = spec.getGenesisSpecConfig().getMaxEffectiveBalance().plus(1024000);
    BeaconState preState = createBeaconState(true, balance, validator);
    final Optional<List<Withdrawal>> withdrawals =
        spec.getBlockProcessor(preState.getSlot()).getExpectedWithdrawals(preState);
    assertThat(withdrawals.get().get(0).getAmount()).isEqualTo(balance);
  }

  @Test
  public void shouldRejectBlockWithMoreThanOneBlsExecutionChangesForSameValidator() {
    final int validatorIndex = 0;
    final ChainBuilder chain = ChainBuilder.create(spec);
    chain.generateGenesis();

    final BeaconState state = chain.getGenesis().getState();
    final BLSPublicKey validatorPubKey = chain.getValidatorKeys().get(0).getPublicKey();

    final SignedBlsToExecutionChange signedBlsToExecutionChange1 =
        createSignedBlsToExecutionChange(validatorIndex, validatorPubKey);
    final SignedBlsToExecutionChange signedBlsToExecutionChange2 =
        createSignedBlsToExecutionChange(validatorIndex, validatorPubKey);

    final SszList<SignedBlsToExecutionChange> blsToExecutionChangesListWithDuplicate =
        createBlsToExecutionChangeList(signedBlsToExecutionChange1, signedBlsToExecutionChange2);

    final BlockProcessorCapella capellaBlockProcessor =
        ((BlockProcessorCapella) spec.getGenesisSpec().getBlockProcessor());

    final BlockValidationResult validationResult =
        capellaBlockProcessor.verifyBlsToExecutionChangesPreProcessing(
            state, blsToExecutionChangesListWithDuplicate, BLSSignatureVerifier.NO_OP);

    assertThat(validationResult.isValid()).isFalse();
    assertThat(validationResult.getFailureReason())
        .contains("Duplicated BlsToExecutionChange for validator " + validatorIndex);
  }

  private SignedBlsToExecutionChange createSignedBlsToExecutionChange(
      final int validatorIndex, final BLSPublicKey validatorPubKey) {
    final SchemaDefinitionsCapella capellaSchema =
        SchemaDefinitionsCapella.required(spec.getGenesisSchemaDefinitions());

    final BlsToExecutionChange blsToExecutionChange =
        capellaSchema
            .getBlsToExecutionChangeSchema()
            .create(
                UInt64.valueOf(validatorIndex),
                validatorPubKey,
                dataStructureUtil.randomEth1Address());

    return capellaSchema
        .getSignedBlsToExecutionChangeSchema()
        .create(blsToExecutionChange, dataStructureUtil.randomSignature());
  }

  private SszList<SignedBlsToExecutionChange> createBlsToExecutionChangeList(
      final SignedBlsToExecutionChange... signedBlsToExecutionChanges) {
    return SchemaDefinitionsCapella.required(spec.getGenesisSchemaDefinitions())
        .getBeaconBlockSchema()
        .getBodySchema()
        .toVersionCapella()
        .orElseThrow()
        .getBlsToExecutionChangesSchema()
        .of(signedBlsToExecutionChanges);
  }

  protected BeaconState createBeaconStateWithValidatorsAndBalances(
      final List<Validator> validators, final List<UInt64> balances) {
    return spec.getGenesisSpec()
        .getSchemaDefinitions()
        .getBeaconStateSchema()
        .createEmpty()
        .updated(
            beaconState -> {
              beaconState.setSlot(dataStructureUtil.randomUInt64());
              beaconState.setFork(
                  new Fork(
                      spec.getGenesisSpecConfig().getGenesisForkVersion(),
                      spec.getGenesisSpecConfig().getGenesisForkVersion(),
                      SpecConfig.GENESIS_EPOCH));
              beaconState.getValidators().appendAll(validators);
              beaconState.getBalances().appendAllElements(balances);
            });
  }
}
