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
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.ssz.type.Bytes4;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException.DeserializationFailedException;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException.LengthOutOfBoundsException;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException.MessageTruncatedException;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException.PayloadTruncatedException;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcByteBufDecoder;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.context.RpcContextCodec;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.context.TestForkDigestContextDecoder;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BeaconBlocksByRootRequestMessage;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.BeaconStateSchemaAltair;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.phase0.BeaconStateSchemaPhase0;
import tech.pegasys.teku.spec.util.DataStructureUtil;

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
    final BeaconState state = beaconState(usePhase0State);
    final Bytes serializedState = state.sszSerialize();
    final Bytes lengthPrefix = getLengthPrefix(serializedState.size());
    final Bytes contextBytes = TestContextCodec.getContextBytes(usePhase0State).getWrappedBytes();
    final Bytes compressedState = COMPRESSOR.compress(serializedState);

    final RpcResponseDecoder<BeaconState, ?> decoder = createForkAwareDecoder();

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

  private void testDecodeBasedOnContextWithMismatchedPayload(final boolean usePhase0) {
    final RpcResponseDecoder<BeaconState, ?> decoder = createForkAwareDecoder();

    final BeaconState state = beaconState(usePhase0);
    final Bytes serializedState = state.sszSerialize();
    final Bytes lengthPrefix = getLengthPrefix(serializedState.size());
    // Use wrong context for state
    final Bytes contextBytes = TestContextCodec.getContextBytes(!usePhase0).getWrappedBytes();
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

  private BeaconState beaconState(final boolean usePhase0State) {
    final Spec spec =
        usePhase0State
            ? TestSpecFactory.createMinimalPhase0()
            : TestSpecFactory.createMinimalAltair();
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

    return usePhase0State
        ? dataStructureUtil.stateBuilderPhase0().build()
        : dataStructureUtil.stateBuilderAltair().build();
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
    return RpcResponseDecoder.create(ENCODING, new TestContextCodec());
  }

  private static class TestContextCodec implements RpcContextCodec<Bytes4, BeaconState> {
    private static final Spec phase0Spec = TestSpecFactory.createMinimalPhase0();
    private static final Spec altairSpec = TestSpecFactory.createMinimalAltair();

    static final Bytes4 FORK_DIGEST_PHASE0 = Bytes4.fromHexStringLenient("0x01");
    static final Bytes4 FORK_DIGEST_ALTAIR = Bytes4.fromHexStringLenient("0x02");

    public static Bytes4 getContextBytes(final boolean forPhase0) {
      return forPhase0 ? FORK_DIGEST_PHASE0 : FORK_DIGEST_ALTAIR;
    }

    @Override
    public RpcByteBufDecoder<Bytes4> getContextDecoder() {
      return new TestForkDigestContextDecoder();
    }

    @Override
    public Bytes encodeContext(final BeaconState responsePayload) {
      // Unused for these tests
      return Bytes.EMPTY;
    }

    @Override
    public Optional<SszSchema<BeaconState>> getSchemaFromContext(final Bytes4 forkDigest) {
      final SszSchema<BeaconState> phase0Schema =
          SszSchema.as(
              BeaconState.class, BeaconStateSchemaPhase0.create(phase0Spec.getGenesisSpecConfig()));
      final SszSchema<BeaconState> altairSchema =
          SszSchema.as(
              BeaconState.class, BeaconStateSchemaAltair.create(altairSpec.getGenesisSpecConfig()));
      Optional<SszSchema<BeaconState>> schema = Optional.empty();
      if (forkDigest.equals(FORK_DIGEST_PHASE0)) {
        schema = Optional.of(phase0Schema);
      } else if (forkDigest.equals(FORK_DIGEST_ALTAIR)) {
        schema = Optional.of(altairSchema);
      }
      return schema;
    }
  }
}
