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
import org.apache.logging.log4j.Level;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.artemis.proto.messagesigner.MessageSignerGrpc;
import tech.pegasys.artemis.proto.messagesigner.SignatureRequest;
import tech.pegasys.artemis.proto.messagesigner.SignatureResponse;
import tech.pegasys.artemis.util.alogger.ALogger;
import tech.pegasys.artemis.util.bls.BLSKeyPair;
import tech.pegasys.artemis.util.bls.BLSSignature;

public class ValidatorClient {
  private static final ALogger LOG = new ALogger(ValidatorClient.class.getName());
  private BLSKeyPair keypair;
  private Server server;

  public ValidatorClient(BLSKeyPair keypair, int port) {
    this.keypair = keypair;
    try {
      start(port);
    } catch (IOException e) {
      LOG.log(Level.WARN, "Error starting VC on port " + port);
    }
  }

  private void start(int port) throws IOException {
    /* The port on which the server should run */
    server =
        ServerBuilder.forPort(port).addService(new MessageSignerService(keypair)).build().start();

    LOG.log(
        Level.DEBUG,
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
                System.err.println(
                    "*** Shutting down Validator Client gRPC server since JVM is shutting down");
                stopServer();
                System.err.println("*** Server shut down");
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
        SignatureRequest request, StreamObserver<SignatureResponse> responseObserver) {
      SignatureResponse reply =
          SignatureResponse.newBuilder().setMessage(performSigning(request)).build();
      responseObserver.onNext(reply);
      responseObserver.onCompleted();
    }

    private ByteString performSigning(SignatureRequest request) {
      Bytes message = Bytes.wrap(request.getMessage().toByteArray());
      int domain = request.getDomain();
      return ByteString.copyFrom(BLSSignature.sign(keypair, message, domain).toBytes().toArray());
    }
  }
}
