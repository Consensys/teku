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

package tech.pegasys.artemis.validator.client;

import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_signing_root;

import com.google.protobuf.ByteString;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.artemis.proto.messagesigner.MessageSignerGrpc;
import tech.pegasys.artemis.proto.messagesigner.SignatureRequest;
import tech.pegasys.artemis.proto.messagesigner.SignatureResponse;
import tech.pegasys.artemis.util.bls.BLS;
import tech.pegasys.artemis.util.bls.BLSKeyPair;

public class ValidatorClient {
  private static final Logger LOG = LogManager.getLogger();
  private BLSKeyPair keypair;
  private Server server;

  public ValidatorClient(BLSKeyPair keypair, int port) {
    this.keypair = keypair;
    try {
      start(port);
    } catch (IOException e) {
      LOG.warn("Error starting VC on port {}", port);
    }
  }

  private void start(int port) throws IOException {
    /* The port on which the server should run */
    server =
        ServerBuilder.forPort(port).addService(new MessageSignerService(keypair)).build().start();

    LOG.debug(
        "ValidatorClient started. Listening on {} representing public key: {}",
        port,
        keypair.getPublicKey());

    Runtime.getRuntime().addShutdownHook(new Thread(this::stopServer));
  }

  private void stopServer() {
    if (server != null) {
      server.shutdown();
    }
  }

  private static class MessageSignerService extends MessageSignerGrpc.MessageSignerImplBase {
    private final BLSKeyPair keypair;

    MessageSignerService(BLSKeyPair keypair) {
      this.keypair = keypair;
    }

    @Override
    public void signMessage(
        SignatureRequest request, StreamObserver<SignatureResponse> responseObserver) {
      SignatureResponse reply =
          SignatureResponse.newBuilder().setMessage(performSigning(request)).build();
      responseObserver.onNext(reply);
      responseObserver.onCompleted();
    }

    private ByteString performSigning(SignatureRequest request) {
      final Bytes message = Bytes.wrap(request.getMessage().toByteArray());
      final Bytes domain = Bytes.wrap(request.getDomain().toByteArray());
      final Bytes signing_root = compute_signing_root(message, domain);
      return ByteString.copyFrom(
          BLS.sign(keypair.getSecretKey(), signing_root).toBytes().toArray());
    }
  }
}
