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

package tech.pegasys.teku.networking.eth2;

import com.google.common.primitives.UnsignedLong;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.datastructures.networking.libp2p.rpc.StatusMessage;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.rpc.core.Eth2RpcMethod;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException.DeserializationFailedException;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcEncoding;
import tech.pegasys.teku.util.Waiter;
import tech.pegasys.teku.util.async.SafeFuture;
import tech.pegasys.teku.util.config.Constants;

public class ErrorConditionsIntegrationTest {

  private final Eth2NetworkFactory networkFactory = new Eth2NetworkFactory();

  @AfterEach
  public void tearDown() {
    networkFactory.stopAll();
  }

  @ParameterizedTest(name = "encoding: {0}")
  @MethodSource("getEncodings")
  public void shouldRejectInvalidRequests(final String encodingName, final RpcEncoding encoding)
      throws Exception {
    final Eth2Network network1 = networkFactory.builder().rpcEncoding(encoding).startNetwork();
    final Eth2Network network2 =
        networkFactory.builder().rpcEncoding(encoding).peer(network1).startNetwork();

    final Eth2Peer peer = network1.getPeer(network2.getNodeId()).orElseThrow();

    final Eth2RpcMethod<StatusMessage, StatusMessage> status =
        ((ActiveEth2Network) network1).getBeaconChainMethods().status();
    final SafeFuture<StatusMessage> response =
        peer.requestSingleItem(status, new InvalidStatusMessage());

    Assertions.assertThatThrownBy(() -> Waiter.waitFor(response))
        .isInstanceOf(ExecutionException.class)
        .extracting(Throwable::getCause)
        .isEqualToComparingFieldByField(new DeserializationFailedException());
  }

  public static Stream<Arguments> getEncodings() {
    final List<RpcEncoding> encodings = List.of(RpcEncoding.SSZ, RpcEncoding.SSZ_SNAPPY);
    return encodings.stream().map(e -> Arguments.of(e.getName(), e));
  }

  // Deliberately doesn't serialize to a valid STATUS message.
  private static class InvalidStatusMessage extends StatusMessage {

    public InvalidStatusMessage() {
      super(
          Constants.GENESIS_FORK_VERSION,
          Bytes32.ZERO,
          UnsignedLong.ZERO,
          Bytes32.ZERO,
          UnsignedLong.ZERO);
    }

    @Override
    public int getSSZFieldCount() {
      return 1;
    }

    @Override
    public List<Bytes> get_fixed_parts() {
      return List.of(SSZ.encode(writer -> writer.writeFixedBytes(Bytes.fromHexString("0xABCDEF"))));
    }
  }
}
