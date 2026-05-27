/*
 * Copyright Consensys Software Inc., 2026
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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.annotations.VisibleForTesting;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import tech.pegasys.teku.bls.impl.blst.BlstLoader;
import tech.pegasys.teku.cli.BeaconNodeCommand;
import tech.pegasys.teku.cli.NodeMode;
import tech.pegasys.teku.cli.StartAction;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.infrastructure.io.JemallocDetector;
import tech.pegasys.teku.infrastructure.logging.LoggingConfigurator;

public final class Teku {

  private static final String NETTY_MAX_DIRECT_MEMORY_PROPERTY = "io.netty.maxDirectMemory";
  private static final String NETTY_MAX_DIRECT_MEMORY_OPTION = "--Xnetty-max-direct-memory";
  private static final String NETTY_MAX_DIRECT_MEMORY_YAML_KEY = "Xnetty-max-direct-memory";
  private static final String CONFIG_FILE_OPTION_LONG = "--config-file";
  private static final String CONFIG_FILE_OPTION_SHORT = "-c";
  private static final String CONFIG_FILE_ENV_VAR = "TEKU_CONFIG_FILE";

  static {
    // Disable libsodium in tuweni Hash because the check for it's presence can be very slow.
    System.setProperty("org.apache.tuweni.crypto.useSodium", "false");
  }

  public static void main(final String[] args) {
    applyNettyMaxDirectMemoryOverride(args);
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

  private static int start(final StartAction startAction, final String... args) {
    final PrintWriter outputWriter = new PrintWriter(System.out, true, Charset.defaultCharset());
    final PrintWriter errorWriter = new PrintWriter(System.err, true, Charset.defaultCharset());
    final LoggingConfigurator loggingConfigurator = new LoggingConfigurator();
    return new BeaconNodeCommand(
            outputWriter, errorWriter, System.getenv(), startAction, loggingConfigurator)
        .parse(args);
  }

  private static Node start(final TekuConfiguration config, final NodeMode nodeMode) {
    final Node node;

    switch (nodeMode) {
      case BOOTNODE_ONLY -> node = new Bootnode(config);
      case VC_ONLY -> node = new ValidatorNode(config);
      case COMBINED -> node = new BeaconNode(config);
      default -> throw new IllegalStateException("Expected node mode to be set");
    }

    // Check that BLS is available before starting to ensure we get a nice error message if it's not
    if (BlstLoader.INSTANCE.isEmpty()) {
      throw new UnsupportedOperationException("BLS native library unavailable for this platform");
    }
    JemallocDetector.logJemallocPresence();

    node.start();

    return node;
  }

  static Optional<Node> startFromCLIArgs(final String[] cliArgs) throws CLIException {
    AtomicReference<Node> nodeRef = new AtomicReference<>();
    int result =
        start((config, validatorClient) -> nodeRef.set(start(config, validatorClient)), cliArgs);
    if (result != 0) {
      throw new CLIException(result);
    }
    return Optional.ofNullable(nodeRef.get());
  }

  static BeaconNode startBeaconNode(final TekuConfiguration config) {
    return (BeaconNode) start(config, NodeMode.COMBINED);
  }

  static Bootnode startBootnode(final TekuConfiguration config) {
    return (Bootnode) start(config, NodeMode.BOOTNODE_ONLY);
  }

  static ValidatorNode startValidatorNode(final TekuConfiguration config) {
    return (ValidatorNode) start(config, NodeMode.VC_ONLY);
  }

  // Netty's PlatformDependent reads io.netty.maxDirectMemory in a static initializer the
  // first time the class is loaded. Apply the override from CLI args (or the YAML config
  // file, if any) before anything else runs so it takes effect before PlatformDependent loads.
  private static void applyNettyMaxDirectMemoryOverride(final String[] args) {
    findCliOption(args, NETTY_MAX_DIRECT_MEMORY_OPTION)
        .or(
            () ->
                findConfigFile(args, System::getenv)
                    .flatMap(file -> readYamlEntry(file, NETTY_MAX_DIRECT_MEMORY_YAML_KEY)))
        .map(Teku::parseMegabytesOrExit)
        .ifPresent(
            bytes -> System.setProperty(NETTY_MAX_DIRECT_MEMORY_PROPERTY, Long.toString(bytes)));
  }

  @VisibleForTesting
  static Optional<File> findConfigFile(
      final String[] args, final Function<String, String> envLookup) {
    return findCliOption(args, CONFIG_FILE_OPTION_LONG)
        .or(() -> findCliOption(args, CONFIG_FILE_OPTION_SHORT))
        .or(() -> Optional.ofNullable(envLookup.apply(CONFIG_FILE_ENV_VAR)))
        .map(File::new)
        .filter(File::isFile);
  }

  @VisibleForTesting
  static Optional<String> readYamlEntry(final File configFile, final String key) {
    try {
      final Map<String, Object> map =
          new ObjectMapper(new YAMLFactory()).readValue(configFile, new TypeReference<>() {});
      return Optional.ofNullable(map).map(m -> m.get(key)).map(Object::toString);
    } catch (final IOException e) {
      // Silently ignore — picocli will surface a proper error when it loads the file later.
      return Optional.empty();
    }
  }

  @VisibleForTesting
  static Optional<String> findCliOption(final String[] args, final String optionName) {
    final String prefix = optionName + "=";
    for (int i = 0; i < args.length; i++) {
      final String arg = args[i];
      if (arg.startsWith(prefix)) {
        return Optional.of(arg.substring(prefix.length()));
      }
      if (arg.equals(optionName) && i + 1 < args.length) {
        return Optional.of(args[i + 1]);
      }
    }
    return Optional.empty();
  }

  @VisibleForTesting
  static long megabytesToBytes(final String megabytes) {
    final int mb;
    try {
      mb = Integer.parseInt(megabytes.trim());
    } catch (final NumberFormatException e) {
      throw new IllegalArgumentException(
          "expected an integer number of megabytes, got '" + megabytes + "'");
    }
    if (mb <= 0) {
      throw new IllegalArgumentException("must be positive, got " + mb);
    }
    return (long) mb * 1024L * 1024L;
  }

  private static long parseMegabytesOrExit(final String raw) {
    try {
      return megabytesToBytes(raw);
    } catch (final IllegalArgumentException e) {
      System.err.println(
          "Invalid value for " + NETTY_MAX_DIRECT_MEMORY_OPTION + ": " + e.getMessage());
      System.exit(2);
      throw new AssertionError("unreachable");
    }
  }

  private static class CLIException extends RuntimeException {
    private final int resultCode;

    public CLIException(final int resultCode) {
      super("Unable to start Teku. Exit code: " + resultCode);
      this.resultCode = resultCode;
    }

    public int getResultCode() {
      return resultCode;
    }
  }
}
