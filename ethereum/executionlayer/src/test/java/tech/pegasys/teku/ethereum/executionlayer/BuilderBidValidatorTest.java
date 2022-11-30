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

package tech.pegasys.teku.ethereum.executionlayer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.spec.schemas.ApiSchemas.SIGNED_VALIDATOR_REGISTRATION_SCHEMA;
import static tech.pegasys.teku.spec.schemas.ApiSchemas.VALIDATOR_REGISTRATION_SCHEMA;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSConstants;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSTestUtil;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.logging.EventLogger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.builder.BuilderBid;
import tech.pegasys.teku.spec.datastructures.builder.SignedBuilderBid;
import tech.pegasys.teku.spec.datastructures.builder.SignedValidatorRegistration;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.block.BlockProcessor;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.BlockProcessingException;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsBellatrix;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class BuilderBidValidatorTest {
  private final Spec spec = TestSpecFactory.createMinimalBellatrix();
  private final Spec specMock = mock(Spec.class);
  private final SpecVersion specVersionMock = mock(SpecVersion.class);
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final BlockProcessor blockProcessor = mock(BlockProcessor.class);
  private final EventLogger eventLogger = mock(EventLogger.class);

  private final BuilderBidValidatorImpl builderBidValidator =
      new BuilderBidValidatorImpl(eventLogger);

  private BeaconState state = dataStructureUtil.randomBeaconState();
  private SignedBuilderBid signedBuilderBid = dataStructureUtil.randomSignedBuilderBid();
  private SignedValidatorRegistration validatorRegistration =
      dataStructureUtil.randomSignedValidatorRegistration();

  @BeforeEach
  void setUp() throws BlockProcessingException {
    BLSConstants.disableBLSVerification();
    when(specMock.getDomain(any(), any(), any(), any()))
        .thenReturn(dataStructureUtil.randomBytes32());
    when(specMock.computeBuilderApplicationSigningRoot(any(), any()))
        .thenReturn(dataStructureUtil.randomBytes32());
    when(specMock.atSlot(any())).thenReturn(specVersionMock);
    when(specVersionMock.getBlockProcessor()).thenReturn(blockProcessor);
    doNothing().when(blockProcessor).validateExecutionPayload(any(), any(), any(), any());
  }

  @AfterEach
  void enableBLS() {
    BLSConstants.enableBLSVerification();
  }

  @Test
  void shouldThrowWithInvalidSignature() {
    BLSConstants.enableBLSVerification();

    assertThatThrownBy(
            () ->
                builderBidValidator.validateAndGetPayloadHeader(
                    spec, signedBuilderBid, validatorRegistration, state))
        .isExactlyInstanceOf(BuilderBidValidationException.class)
        .hasMessage("Invalid Bid Signature");
  }

  @Test
  void shouldValidateSignatureAndThrowWithBlockProcessingExceptionCause() {
    BLSConstants.enableBLSVerification();
    prepareValidSignedBuilderBid();

    assertThatThrownBy(
            () ->
                builderBidValidator.validateAndGetPayloadHeader(
                    spec, signedBuilderBid, validatorRegistration, state))
        .isExactlyInstanceOf(BuilderBidValidationException.class)
        .hasMessage("Invalid proposed payload with respect to consensus.")
        .hasCauseInstanceOf(BlockProcessingException.class);
  }

  @Test
  void shouldNotLogEventIfGasLimitDecreases() throws BuilderBidValidationException {

    prepareGasLimit(UInt64.valueOf(1024_000), UInt64.valueOf(1022_000), UInt64.valueOf(1023_000));

    builderBidValidator.validateAndGetPayloadHeader(
        specMock, signedBuilderBid, validatorRegistration, state);

    verifyNoInteractions(eventLogger);
  }

  @Test
  void shouldNotLogEventIfGasLimitIncreases() throws BuilderBidValidationException {

    prepareGasLimit(UInt64.valueOf(1024_000), UInt64.valueOf(1025_000), UInt64.valueOf(2048_000));

    builderBidValidator.validateAndGetPayloadHeader(
        specMock, signedBuilderBid, validatorRegistration, state);

    verifyNoInteractions(eventLogger);
  }

  @Test
  void shouldNotLogEventIfGasLimitStaysTheSame() throws BuilderBidValidationException {

    prepareGasLimit(UInt64.valueOf(1024_000), UInt64.valueOf(1024_000), UInt64.valueOf(1024_000));

    builderBidValidator.validateAndGetPayloadHeader(
        specMock, signedBuilderBid, validatorRegistration, state);

    verifyNoInteractions(eventLogger);
  }

  @Test
  void shouldLogEventIfGasLimitDoesNotDecrease() throws BuilderBidValidationException {

    prepareGasLimit(UInt64.valueOf(1024_000), UInt64.valueOf(1024_000), UInt64.valueOf(1023_100));

    builderBidValidator.validateAndGetPayloadHeader(
        specMock, signedBuilderBid, validatorRegistration, state);

    verify(eventLogger)
        .builderBidNotHonouringGasLimit(
            UInt64.valueOf(1024_000), UInt64.valueOf(1024_000), UInt64.valueOf(1023_100));
  }

  @Test
  void shouldLogEventIfGasLimitDoesNotIncrease() throws BuilderBidValidationException {

    prepareGasLimit(UInt64.valueOf(1024_000), UInt64.valueOf(1020_000), UInt64.valueOf(1024_100));

    builderBidValidator.validateAndGetPayloadHeader(
        specMock, signedBuilderBid, validatorRegistration, state);

    verify(eventLogger)
        .builderBidNotHonouringGasLimit(
            UInt64.valueOf(1024_000), UInt64.valueOf(1020_000), UInt64.valueOf(1024_100));
  }

  private void prepareValidSignedBuilderBid() {
    final BLSKeyPair keyPair = BLSTestUtil.randomKeyPair(1);
    final BuilderBid builderBid = dataStructureUtil.randomBuilderBid(keyPair.getPublicKey());

    final Bytes signingRoot =
        spec.computeBuilderApplicationSigningRoot(state.getSlot(), builderBid);

    signedBuilderBid =
        spec.atSlot(state.getSlot())
            .getSchemaDefinitions()
            .toVersionBellatrix()
            .orElseThrow()
            .getSignedBuilderBidSchema()
            .create(builderBid, BLS.sign(keyPair.getSecretKey(), signingRoot));
  }

  private void prepareGasLimit(
      UInt64 parentGasLimit, UInt64 proposedGasLimit, UInt64 preferredGasLimit) {

    UInt64 slot = dataStructureUtil.randomUInt64();

    SchemaDefinitionsBellatrix schemaDefinitions =
        spec.atSlot(slot).getSchemaDefinitions().toVersionBellatrix().orElseThrow();

    ExecutionPayloadHeader parentExecutionPayloadHeader =
        createExecutionPayloadHeaderWithGasLimit(schemaDefinitions, parentGasLimit);

    // create current state with parent gasLimit
    state =
        dataStructureUtil
            .randomBeaconState(slot)
            .updated(
                state ->
                    state
                        .toMutableVersionBellatrix()
                        .orElseThrow()
                        .setLatestExecutionPayloadHeader(parentExecutionPayloadHeader));

    // create bid with proposed gasLimit
    signedBuilderBid =
        schemaDefinitions
            .getSignedBuilderBidSchema()
            .create(
                schemaDefinitions
                    .getBuilderBidSchema()
                    .create(
                        createExecutionPayloadHeaderWithGasLimit(
                            schemaDefinitions, proposedGasLimit),
                        dataStructureUtil.randomUInt256(),
                        dataStructureUtil.randomPublicKey()),
                dataStructureUtil.randomSignature());

    // create validator registration with preferred gasLimit
    validatorRegistration =
        SIGNED_VALIDATOR_REGISTRATION_SCHEMA.create(
            VALIDATOR_REGISTRATION_SCHEMA.create(
                dataStructureUtil.randomEth1Address(),
                preferredGasLimit,
                dataStructureUtil.randomUInt64(),
                dataStructureUtil.randomPublicKey()),
            dataStructureUtil.randomSignature());
  }

  private ExecutionPayloadHeader createExecutionPayloadHeaderWithGasLimit(
      SchemaDefinitionsBellatrix schemaDefinitions, UInt64 gasLimit) {
    return schemaDefinitions
        .getExecutionPayloadHeaderSchema()
        .createExecutionPayloadHeader(
            builder ->
                builder
                    .parentHash(Bytes32.random())
                    .feeRecipient(Bytes20.ZERO)
                    .stateRoot(Bytes32.ZERO)
                    .receiptsRoot(Bytes32.ZERO)
                    .logsBloom(Bytes.random(256))
                    .prevRandao(Bytes32.ZERO)
                    .blockNumber(UInt64.ZERO)
                    .gasLimit(gasLimit)
                    .gasUsed(UInt64.ZERO)
                    .timestamp(UInt64.ZERO)
                    .extraData(Bytes32.ZERO)
                    .baseFeePerGas(UInt256.ZERO)
                    .blockHash(Bytes32.random())
                    .transactionsRoot(Bytes32.ZERO)
                    .withdrawalsRoot(() -> Bytes32.ZERO)
                    .excessDataGas(() -> UInt256.ONE));
  }
}
