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

package tech.pegasys.teku;

import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.security.Security;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import tech.pegasys.teku.bls.impl.blst.BlstLoader;
import tech.pegasys.teku.cli.BeaconNodeCommand;
import tech.pegasys.teku.cli.BeaconNodeCommand.StartAction;
import tech.pegasys.teku.config.TekuConfiguration;

public final class Teku {

  static {
    Security.addProvider(new BouncyCastleProvider());
  }

  public static void main(String[] args) {
    Thread.setDefaultUncaughtExceptionHandler(new TekuDefaultExceptionHandler());

    try {
      Optional<Node> maybeNode = Teku.startFromCLIArgs(args);

      maybeNode.ifPresent(
          node ->
              // Detect SIGTERM
              Runtime.getRuntime()
                  .addShutdownHook(
                      new Thread(
                          () -> {
                            System.out.println("Teku is shutting down");
                            node.stop();
                          })));
    } catch (CLIException e) {
      System.exit(e.getResultCode());
    }
  }

  private static int start(StartAction startAction, final String... args) {
    final PrintWriter outputWriter = new PrintWriter(System.out, true, Charset.defaultCharset());
    final PrintWriter errorWriter = new PrintWriter(System.err, true, Charset.defaultCharset());
    return new BeaconNodeCommand(outputWriter, errorWriter, System.getenv(), startAction)
        .parse(args);
  }

  private static Node start(final TekuConfiguration config, final boolean validatorOnly) {
    final Node node;
    if (validatorOnly) {
      node = new ValidatorNode(config);
    } else {
      node = new BeaconNode(config);
    }
    // Check that BLS is available before starting to ensure we get a nice error message if it's not
    if (BlstLoader.INSTANCE.isEmpty()) {
      throw new UnsupportedOperationException("BLS native library unavailable for this platform");
    }

    node.start();

    return node;
  }

  static Optional<Node> startFromCLIArgs(String[] cliArgs) throws CLIException {
    AtomicReference<Node> nodeRef = new AtomicReference<>();
    int result =
        start((config, validatorClient) -> nodeRef.set(start(config, validatorClient)), cliArgs);
    if (result != 0) {
      throw new CLIException(result);
    }
    return Optional.ofNullable(nodeRef.get());
  }

  static BeaconNode startBeaconNode(TekuConfiguration config) {
    return (BeaconNode) start(config, false);
  }

  static ValidatorNode startValidatorNode(TekuConfiguration config) {
    return (ValidatorNode) start(config, true);
  }

  private static class CLIException extends RuntimeException {
    private final int resultCode;

    public CLIException(int resultCode) {
      super("Unable to start Teku. Exit code: " + resultCode);
      this.resultCode = resultCode;
    }

    public int getResultCode() {
      return resultCode;
    }
  }
}
