/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.teku.networking.eth2.rpc.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException.DeserializationFailedException;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException.LengthOutOfBoundsException;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException.MessageTruncatedException;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException.PayloadTruncatedException;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcResponseDecoder.ResponseSchemaSupplier;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BeaconBlocksByRootRequestMessage;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.BeaconStateSchemaAltair;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.phase0.BeaconStateSchemaPhase0;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.ssz.schema.SszSchema;
import tech.pegasys.teku.ssz.type.Bytes4;

class RpcResponseDecoderTest extends RpcDecoderTestBase {
  private static final Bytes SUCCESS_CODE = Bytes.of(RpcResponseStatus.SUCCESS_RESPONSE_CODE);
  private static final Bytes ERROR_CODE = Bytes.of(1);

  private final RpcResponseDecoder<BeaconBlocksByRootRequestMessage, ?> decoder = RESPONSE_DECODER;

  @Test
  public void decodeNextResponse_shouldParseSingleResponse() throws Exception {
    for (Iterable<ByteBuf> testByteBufSlice :
        testByteBufSlices(SUCCESS_CODE, LENGTH_PREFIX, MESSAGE_DATA)) {

      List<BeaconBlocksByRootRequestMessage> results = new ArrayList<>();
      for (ByteBuf byteBuf : testByteBufSlice) {
        results.addAll(decoder.decodeNextResponses(byteBuf));
        byteBuf.release();
      }
      decoder.complete();
      assertThat(results).containsExactly(MESSAGE);
      assertThat(testByteBufSlice).allSatisfy(b -> assertThat(b.refCnt()).isEqualTo(0));
    }
  }

  @Test
  public void decodeNextResponse_shouldParseMultipleResponses() throws Exception {
    final BeaconBlocksByRootRequestMessage secondMessage = createRequestMessage(2);
    final Bytes secondMessageData = secondMessage.sszSerialize();
    final Bytes compressedPayload = COMPRESSOR.compress(secondMessageData);

    for (Iterable<ByteBuf> testByteBufSlice :
        testByteBufSlices(
            SUCCESS_CODE,
            LENGTH_PREFIX,
            MESSAGE_DATA,
            SUCCESS_CODE,
            getLengthPrefix(secondMessageData.size()),
            compressedPayload)) {

      List<BeaconBlocksByRootRequestMessage> results = new ArrayList<>();
      for (ByteBuf byteBuf : testByteBufSlice) {
        results.addAll(decoder.decodeNextResponses(byteBuf));
        byteBuf.release();
      }
      decoder.complete();
      assertThat(results).containsExactly(MESSAGE, secondMessage);
      assertThat(testByteBufSlice).allSatisfy(b -> assertThat(b.refCnt()).isEqualTo(0));
    }
  }

  @Test
  public void decodeNextResponse_shouldThrowErrorIfStatusCodeIsNotSuccess() throws Exception {
    for (Iterable<ByteBuf> testByteBufSlice :
        testByteBufSlices(ERROR_CODE, ERROR_MESSAGE_LENGTH_PREFIX, ERROR_MESSAGE_DATA)) {

      assertThatThrownBy(
              () -> {
                for (ByteBuf byteBuf : testByteBufSlice) {
                  decoder.decodeNextResponses(byteBuf);
                }
                decoder.complete();
              })
          .isEqualTo(new RpcException(ERROR_CODE.get(0), ERROR_MESSAGE));
    }
  }

  @Test
  public void decodeNextResponse_shouldThrowErrorIfStatusCodeIsNotSuccess_largeErrorCode()
      throws Exception {
    final Bytes errorCode = Bytes.of(255);
    for (Iterable<ByteBuf> testByteBufSlice :
        testByteBufSlices(errorCode, ERROR_MESSAGE_LENGTH_PREFIX, ERROR_MESSAGE_DATA)) {

      assertThatThrownBy(
              () -> {
                for (ByteBuf byteBuf : testByteBufSlice) {
                  decoder.decodeNextResponses(byteBuf);
                }
                decoder.complete();
              })
          .isEqualTo(new RpcException(errorCode.get(0), ERROR_MESSAGE));
    }
  }

  @Test
  public void complete_shouldThrowErrorIfLengthPrefixNotFullyProcessed() {
    final Bytes lengthPrefix = getLengthPrefix(1024);
    assertThat(lengthPrefix.size()).isGreaterThan(1);
    for (Iterable<ByteBuf> testByteBufSlice :
        testByteBufSlices(SUCCESS_CODE, lengthPrefix.slice(0, 1))) {

      RpcResponseDecoder<?, ?> decoder = createForkAwareDecoder();
      assertThatThrownBy(
              () -> {
                for (ByteBuf byteBuf : testByteBufSlice) {
                  decoder.decodeNextResponses(byteBuf);
                  byteBuf.release();
                }
                decoder.complete();
              })
          .isInstanceOf(MessageTruncatedException.class);
      assertThat(testByteBufSlice).allSatisfy(b -> assertThat(b.refCnt()).isEqualTo(0));
    }
  }

  @Test
  public void complete_shouldThrowErrorIfContextNotFullyProcessed() {
    for (Iterable<ByteBuf> testByteBufSlice : testByteBufSlices(SUCCESS_CODE, Bytes.of(1, 2))) {

      RpcResponseDecoder<?, ?> decoder = createForkAwareDecoder();
      assertThatThrownBy(
              () -> {
                for (ByteBuf byteBuf : testByteBufSlice) {
                  decoder.decodeNextResponses(byteBuf);
                  byteBuf.release();
                }
                decoder.complete();
              })
          .isInstanceOf(MessageTruncatedException.class);
      assertThat(testByteBufSlice).allSatisfy(b -> assertThat(b.refCnt()).isEqualTo(0));
    }
  }

  @Test
  public void complete_shouldThrowErrorIfErrorMsgNotFullyProcessed() {
    final Bytes errorCode = Bytes.of(255);
    for (Iterable<ByteBuf> testByteBufSlice :
        testByteBufSlices(errorCode, ERROR_MESSAGE_LENGTH_PREFIX, ERROR_MESSAGE_DATA.slice(0, 2))) {

      RpcResponseDecoder<?, ?> decoder = createForkAwareDecoder();
      assertThatThrownBy(
              () -> {
                for (ByteBuf byteBuf : testByteBufSlice) {
                  decoder.decodeNextResponses(byteBuf);
                  byteBuf.release();
                }
                decoder.complete();
              })
          .isInstanceOf(PayloadTruncatedException.class);
      assertThat(testByteBufSlice).allSatisfy(b -> assertThat(b.refCnt()).isEqualTo(0));
    }
  }

  @Test
  public void decodeNextResponse_shouldDecodeBasedOnContext_matchingPhase0Input() throws Exception {
    testDecodeBasedOnContext(true);
  }

  @Test
  public void decodeNextResponse_shouldDecodeBasedOnContext_matchingAltairInput() throws Exception {
    testDecodeBasedOnContext(false);
  }

  private void testDecodeBasedOnContext(final boolean usePhase0State) throws Exception {
    final Spec spec =
        usePhase0State
            ? TestSpecFactory.createMinimalPhase0()
            : TestSpecFactory.createMinimalAltair();
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

    final BeaconState state =
        usePhase0State
            ? dataStructureUtil.stateBuilderPhase0().build()
            : dataStructureUtil.stateBuilderAltair().build();
    final Bytes serializedState = state.sszSerialize();
    final Bytes lengthPrefix = getLengthPrefix(serializedState.size());
    final Bytes contextBytes =
        Bytes4.fromHexStringLenient(usePhase0State ? "1" : "2").getWrappedBytes();
    final Bytes compressedState = COMPRESSOR.compress(serializedState);

    final RpcResponseDecoder<BeaconState, ?> decoder =
        createForkAwareDecoder(spec.getGenesisSpecConfig());

    for (Iterable<ByteBuf> testByteBufSlice :
        testByteBufSlices(SUCCESS_CODE, contextBytes, lengthPrefix, compressedState)) {

      List<BeaconState> results = new ArrayList<>();
      for (ByteBuf byteBuf : testByteBufSlice) {
        results.addAll(decoder.decodeNextResponses(byteBuf));
        byteBuf.release();
      }
      decoder.complete();
      assertThat(results).containsExactly(state);
      assertThat(testByteBufSlice).allSatisfy(b -> assertThat(b.refCnt()).isEqualTo(0));
    }
  }

  @Test
  public void decodeNextResponse_shouldDecodeBasedOnContext_mismatchedPhase0Input()
      throws Exception {
    testDecodeBasedOnContextWithMismatchedPayload(true);
  }

  @Test
  public void decodeNextResponse_shouldDecodeBasedOnContext_mismatchedAltairInput()
      throws Exception {
    testDecodeBasedOnContextWithMismatchedPayload(false);
  }

  private void testDecodeBasedOnContextWithMismatchedPayload(final boolean usePhase0State)
      throws Exception {
    final Spec spec =
        usePhase0State
            ? TestSpecFactory.createMinimalPhase0()
            : TestSpecFactory.createMinimalAltair();
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
    final RpcResponseDecoder<BeaconState, ?> decoder =
        createForkAwareDecoder(spec.getGenesisSpecConfig());

    final BeaconState state =
        usePhase0State
            ? dataStructureUtil.stateBuilderPhase0().build()
            : dataStructureUtil.stateBuilderAltair().build();
    final Bytes serializedState = state.sszSerialize();
    final Bytes lengthPrefix = getLengthPrefix(serializedState.size());
    // Use wrong context for state
    final Bytes contextBytes =
        Bytes4.fromHexStringLenient(usePhase0State ? "2" : "1").getWrappedBytes();
    final Bytes compressedState = COMPRESSOR.compress(serializedState);

    for (List<ByteBuf> testByteBufSlice :
        testByteBufSlices(SUCCESS_CODE, contextBytes, lengthPrefix, compressedState)) {

      List<BeaconState> results = new ArrayList<>();
      List<Exception> failures = new ArrayList<>();
      boolean failed = false;
      for (ByteBuf byteBuf : testByteBufSlice) {
        try {
          if (!failed) {
            results.addAll(decoder.decodeNextResponses(byteBuf));
          }
        } catch (Exception e) {
          failed = true;
          failures.add(e);
        } finally {
          byteBuf.release();
        }
      }
      completeIgnoringUnprocessedData(decoder);
      assertThat(results).isEmpty();
      assertThat(failures.size()).isEqualTo(1);
      for (Exception failure : failures) {
        assertThat(failure)
            .isInstanceOfAny(
                DeserializationFailedException.class, LengthOutOfBoundsException.class);
      }
      assertThat(testByteBufSlice).allSatisfy(b -> assertThat(b.refCnt()).isEqualTo(0));
    }
  }

  private void completeIgnoringUnprocessedData(RpcResponseDecoder<?, ?> decoder) {
    try {
      decoder.complete();
    } catch (Exception e) {
      assertThat(e)
          .isInstanceOfAny(MessageTruncatedException.class, PayloadTruncatedException.class);
    }
  }

  private RpcResponseDecoder<BeaconState, Bytes4> createForkAwareDecoder() {
    final Spec spec = TestSpecFactory.createMinimalPhase0();
    return createForkAwareDecoder(spec.getGenesisSpecConfig());
  }

  private RpcResponseDecoder<BeaconState, Bytes4> createForkAwareDecoder(final SpecConfig config) {
    final SszSchema<BeaconState> phase0Schema =
        SszSchema.as(BeaconState.class, BeaconStateSchemaPhase0.create(config));
    final SszSchema<BeaconState> altairSchema =
        SszSchema.as(BeaconState.class, BeaconStateSchemaAltair.create(config));
    ResponseSchemaSupplier<Bytes4, BeaconState> schemaSupplier =
        (contextBytes) -> {
          Optional<SszSchema<BeaconState>> schema = Optional.empty();
          if (contextBytes.equals(Bytes4.fromHexStringLenient("0x01"))) {
            schema = Optional.of(phase0Schema);
          } else if (contextBytes.equals(Bytes4.fromHexStringLenient("0x02"))) {
            schema = Optional.of(altairSchema);
          }
          return schema;
        };

    return RpcResponseDecoder.createForkAwareDecoder(ENCODING, schemaSupplier);
  }
}
