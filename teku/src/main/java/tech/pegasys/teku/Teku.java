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
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import tech.pegasys.teku.bls.impl.blst.BlstLoader;
import tech.pegasys.teku.cli.BeaconNodeCommand;
import tech.pegasys.teku.cli.BeaconNodeCommand.StartAction;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.infrastructure.async.SafeFuture;

public final class Teku implements TekuFacade {

  static final Teku INSTANCE = new Teku();

  public static void main(String[] args) {
    Teku teku = INSTANCE;
    int result = teku.start(teku::start, args);
    if (result != 0) {
      System.exit(result);
    }
  }

  private final PrintWriter outputWriter =
      new PrintWriter(System.out, true, Charset.defaultCharset());
  private final PrintWriter errorWriter =
      new PrintWriter(System.err, true, Charset.defaultCharset());

  private Teku() {
    Thread.setDefaultUncaughtExceptionHandler(new TekuDefaultExceptionHandler());
    Security.addProvider(new BouncyCastleProvider());
  }

  private int start(StartAction startAction, final String... args) {
    return new BeaconNodeCommand(outputWriter, errorWriter, System.getenv(), startAction)
        .parse(args);
  }

  private Node start(final TekuConfiguration config, final boolean validatorOnly) {
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
    // Detect SIGTERM
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  System.out.println("Teku is shutting down");
                  node.stop();
                }));
    return node;
  }

  @Override
  public NodeFacade startFromCLIArgs(String[] cliArgs) {
    SafeFuture<Node> nodePromise = new SafeFuture<>();
    int result =
        start(
            (config, validatorClient) -> nodePromise.complete(start(config, validatorClient)),
            cliArgs);
    if (result != 0) {
      throw new RuntimeException("Unable to start Teku. Exit code: " + result);
    }
    return nodePromise.join();
  }

  @Override
  public BeaconNodeFacade startBeaconNode(TekuConfiguration config) {
    return (BeaconNodeFacade) start(config, false);
  }
}
