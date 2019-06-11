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

import com.google.protobuf.ByteString;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.artemis.proto.messagesigner.MessageSignRequest;
import tech.pegasys.artemis.proto.messagesigner.MessageSignResponse;
import tech.pegasys.artemis.proto.messagesigner.MessageSignerGrpc;
import tech.pegasys.artemis.util.bls.BLSKeyPair;
import tech.pegasys.artemis.util.bls.BLSSignature;

public class ValidatorClient {
  private BLSKeyPair keypair;
  private Server server;

  public ValidatorClient(BLSKeyPair keypair, int port) {
    this.keypair = keypair;
    try {
      start(port);
    } catch (IOException e) {
      System.out.println("Error starting VC on port " + port);
    }
  }

  private void start(int port) throws IOException {
    /* The port on which the server should run */
    server =
        ServerBuilder.forPort(port).addService(new MessageSignerService(keypair)).build().start();

    System.out.println(
        "ValidatorClient started. Listening on "
            + port
            + " representing public key: "
            + keypair.getPublicKey());

    Runtime.getRuntime()
        .addShutdownHook(
            new Thread() {
              @Override
              public void run() {
                // Use stderr here since the logger may have been reset by its JVM shutdown hook.
                System.err.println("*** shutting down gRPC server since JVM is shutting down");
                stopServer();
                System.err.println("*** server shut down");
              }
            });
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
        MessageSignRequest request, StreamObserver<MessageSignResponse> responseObserver) {
      MessageSignResponse reply =
          MessageSignResponse.newBuilder()
              .setSignedAttestationMessage(performSigning(request))
              .build();
      responseObserver.onNext(reply);
      responseObserver.onCompleted();
    }

    private ByteString performSigning(MessageSignRequest request) {
      Bytes attestationMessage = Bytes.wrap(request.getAttestationMessage().toByteArray());
      int domain = request.getDomain();
      return ByteString.copyFrom(
          BLSSignature.sign(keypair, attestationMessage, domain).toBytes().toArray());
    }
  }
}
