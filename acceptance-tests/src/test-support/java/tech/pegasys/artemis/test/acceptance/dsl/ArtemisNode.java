package tech.pegasys.artemis.test.acceptance.dsl;

import static org.apache.tuweni.io.file.Files.deleteRecursively;
import static org.apache.tuweni.toml.Toml.tomlEscape;
import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.artemis.util.Waiter.waitFor;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ArtemisNode {
  private static final Logger LOG = LogManager.getLogger();
  private static final String ARTEMIS_BINARY =
      new File(System.getProperty("artemis.binary.path", "../build/install/artemis/bin/artemis"))
          .getAbsolutePath();

  private final SimpleHttpClient httpClient;

  private boolean started = false;
  private File configFile;
  private Process process;
  private Path workDir;

  ArtemisNode(final SimpleHttpClient httpClient) {
    this.httpClient = httpClient;
  }

  public void start() throws Exception {
    assertThat(started).isFalse();
    started = true;
    workDir = Files.createTempDirectory("artemis");
    configFile = File.createTempFile("config", ".toml");
    configFile.deleteOnExit();
    new ConfigBuilder().writeTo(configFile);
    LOG.info(
        "Starting artemis from {} with config file {}",
        ARTEMIS_BINARY,
        configFile.getAbsolutePath());
    process =
        new ProcessBuilder(
                ARTEMIS_BINARY, "--config", configFile.getAbsolutePath(), "--logging", "DEBUG")
            .directory(workDir.toFile())
            .inheritIO()
            .start();
  }

  public void waitForGenesis() {
    waitFor(
        () -> httpClient.get(getRestApiUrl(), "/node/genesis_time"));
  }

  public void waitForNewBlock() {
    String startingBlockRoot = waitFor(() -> {
      return httpClient.get(getRestApiUrl(), "/node")
    });
  }

  private BeaconHead

  private URI getRestApiUrl() {
    return URI.create("http://127.0.0.1:9051");
  }

  public void stop() {
    if (!started) {
      return;
    }
    LOG.info("Shutting down");
    try {
      process.destroy();
      if (!process.waitFor(1, TimeUnit.MINUTES)) {
        process.destroyForcibly();
      }

      if (!configFile.delete() && configFile.exists()) {
        throw new IOException("Failed to delete config file: " + configFile);
      }
      deleteRecursively(workDir);
    } catch (final IOException | InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  private static class ConfigBuilder {

    private Map<String, Map<String, Object>> options = new HashMap<>();
    private static final int DEFAULT_VALIDATOR_COUNT = 64;

    public ConfigBuilder() {
      final Map<String, Object> node = getSection("node");
      node.put("networkMode", "mock");
      node.put("networkInterface", "127.0.0.1");
      node.put("port", 9000);
      node.put("discovery", "static");
      node.put("constants", "minimal");

      final Map<String, Object> interop = getSection("interop");
      interop.put("genesisTime", 0);
      interop.put("ownedValidatorStartIndex", 0);
      interop.put("ownedValidatorCount", DEFAULT_VALIDATOR_COUNT);

      final Map<String, Object> deposit = getSection("deposit");
      deposit.put("mode", "test");
      deposit.put("numValidators", DEFAULT_VALIDATOR_COUNT);

      final Map<String, Object> beaconRestApi = getSection("beaconrestapi");
      beaconRestApi.put("portNumber", 9051);
    }

    private Map<String, Object> getSection(final String interop) {
      return options.computeIfAbsent(interop, key -> new HashMap<>());
    }

    public void writeTo(final File configFile) throws Exception {
      try (PrintWriter out =
          new PrintWriter(Files.newBufferedWriter(configFile.toPath(), StandardCharsets.UTF_8))) {
        for (Entry<String, Map<String, Object>> entry : options.entrySet()) {
          String sectionName = entry.getKey();
          Map<String, Object> section = entry.getValue();
          out.println("[" + tomlEscape(sectionName) + "]");

          for (Entry<String, Object> e : section.entrySet()) {
            out.print(e.getKey() + "=");
            writeValue(e.getValue(), out);
            out.println();
          }
          out.println();
        }
      }
    }

    private void writeValue(final Object value, final PrintWriter out) {
      if (value instanceof String) {
        out.print("\"" + tomlEscape((String) value) + "\"");
      } else if (value instanceof List) {
        out.print("[");
        writeList((List<?>) value, out);
        out.print("]");
      } else {
        out.print(value.toString());
      }
    }

    private void writeList(final List<?> values, final PrintWriter out) {
      for (int i = 0; i < values.size(); i++) {
        writeValue(values.get(i), out);
        if (i < values.size()) {
          out.print(",");
        }
      }
    }
  }
}
