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

package tech.pegasys.teku.ethtests.finder;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.ethtests.TestFork;
import tech.pegasys.teku.ethtests.TestSpecConfig;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.builder.SpecConfigBuilder;
import tech.pegasys.teku.spec.logic.common.statetransition.blockvalidator.BatchSignatureVerifier;
import tech.pegasys.teku.spec.logic.common.statetransition.blockvalidator.BatchSignatureVerifierImpl;
import tech.pegasys.teku.spec.networks.Eth2Network;

public class TestDefinition {

  private final String fork;
  private final String configName;
  private final String testType;
  private final String testName;
  private final Path pathFromPhaseTestDir;
  private Spec spec;

  public TestDefinition(
      final String fork,
      final String configName,
      final String testType,
      final String testName,
      final Path pathFromPhaseTestDir) {
    this.configName = configName;
    this.fork = fork;
    this.testType = testType.replace("\\", "/");
    this.testName = testName.replace("\\", "/");
    this.pathFromPhaseTestDir = pathFromPhaseTestDir;
  }

  public String getConfigName() {
    return configName;
  }

  public String getFork() {
    return fork;
  }

  public Spec getSpec() {
    return getSpec(true);
  }

  public Spec getSpec(final boolean blsSignatureVerificationEnabled) {
    if (spec == null) {
      createSpec(blsSignatureVerificationEnabled);
    }
    return spec;
  }

  private void createSpec(final boolean blsSignatureVerificationEnabled) {
    final Eth2Network network =
        switch (configName) {
          case TestSpecConfig.MAINNET -> Eth2Network.MAINNET;
          case TestSpecConfig.MINIMAL -> Eth2Network.MINIMAL;
          default -> throw new IllegalArgumentException("Unknown configName: " + configName);
        };
    final SpecMilestone milestone =
        switch (fork) {
          case TestFork.PHASE0 -> SpecMilestone.PHASE0;
          case TestFork.ALTAIR -> SpecMilestone.ALTAIR;
          case TestFork.BELLATRIX -> SpecMilestone.BELLATRIX;
          case TestFork.CAPELLA -> SpecMilestone.CAPELLA;
          case TestFork.DENEB -> SpecMilestone.DENEB;
          case TestFork.ELECTRA -> SpecMilestone.ELECTRA;
          case TestFork.FULU -> SpecMilestone.FULU;
          case TestFork.GLOAS -> SpecMilestone.GLOAS;
          default -> throw new IllegalArgumentException("Unknown fork: " + fork);
        };
    final BLSSignatureVerifier blsSignatureVerifier =
        blsSignatureVerificationEnabled ? BLSSignatureVerifier.SIMPLE : BLSSignatureVerifier.NOOP;
    final Supplier<BatchSignatureVerifier> batchSignatureVerifierSupplier =
        blsSignatureVerificationEnabled
            ? BatchSignatureVerifierImpl::new
            : () -> BatchSignatureVerifier.NOOP;
    final Consumer<SpecConfigBuilder> forkEpochOverrides = readForkEpochOverridesFromConfigYaml();
    spec =
        TestSpecFactory.create(
            milestone,
            network,
            builder -> {
              builder
                  .blsSignatureVerifier(blsSignatureVerifier)
                  .batchSignatureVerifierSupplier(batchSignatureVerifierSupplier);
              forkEpochOverrides.accept(builder);
            });
  }

  /**
   * Pyspec ships per-fixture {@code config.yaml} files that may override fork epochs (e.g. {@code
   * CAPELLA_FORK_EPOCH: 1}) so that the fixture can exercise pre-fork validator behaviour even
   * inside a post-fork suite. {@link TestSpecFactory#create(SpecMilestone, Eth2Network, Consumer)}
   * hardcodes every fork epoch up to the test milestone to {@code 0}, which loses this information.
   * Re-apply non-{@code FAR_FUTURE_EPOCH} fork epoch values from the fixture so the validator's
   * fork-schedule checks behave as the fixture intends. Fork epochs set to {@code FAR_FUTURE_EPOCH}
   * are ignored — these signal "fork not relevant to this fixture" and we keep Teku's defaults (the
   * surrounding suite milestone is still authoritative for schema selection).
   */
  private Consumer<SpecConfigBuilder> readForkEpochOverridesFromConfigYaml() {
    final Path configPath = getTestDirectory().resolve("config.yaml");
    if (!Files.exists(configPath)) {
      return __ -> {};
    }
    final Map<String, Object> config;
    try (final InputStream in = Files.newInputStream(configPath)) {
      config = new ObjectMapper(new YAMLFactory()).readValue(in, new TypeReference<>() {});
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
    Consumer<SpecConfigBuilder> overrides = __ -> {};
    overrides =
        applyIfPresent(overrides, config, "ALTAIR_FORK_EPOCH", SpecConfigBuilder::altairForkEpoch);
    overrides =
        applyIfPresent(
            overrides, config, "BELLATRIX_FORK_EPOCH", SpecConfigBuilder::bellatrixForkEpoch);
    overrides =
        applyIfPresent(
            overrides, config, "CAPELLA_FORK_EPOCH", SpecConfigBuilder::capellaForkEpoch);
    overrides =
        applyIfPresent(overrides, config, "DENEB_FORK_EPOCH", SpecConfigBuilder::denebForkEpoch);
    overrides =
        applyIfPresent(
            overrides, config, "ELECTRA_FORK_EPOCH", SpecConfigBuilder::electraForkEpoch);
    overrides =
        applyIfPresent(overrides, config, "FULU_FORK_EPOCH", SpecConfigBuilder::fuluForkEpoch);
    overrides =
        applyIfPresent(overrides, config, "GLOAS_FORK_EPOCH", SpecConfigBuilder::gloasForkEpoch);
    overrides =
        applyIfPresent(overrides, config, "HEZE_FORK_EPOCH", SpecConfigBuilder::hezeForkEpoch);
    return overrides;
  }

  private static Consumer<SpecConfigBuilder> applyIfPresent(
      final Consumer<SpecConfigBuilder> current,
      final Map<String, Object> config,
      final String key,
      final BiConsumer<SpecConfigBuilder, UInt64> setter) {
    final Object raw = config.get(key);
    if (raw == null) {
      return current;
    }
    final UInt64 value = UInt64.valueOf(raw.toString());
    return current.andThen(builder -> setter.accept(builder, value));
  }

  public String getTestType() {
    return testType;
  }

  public String getTestName() {
    return testName;
  }

  @Override
  public String toString() {
    return fork + " - " + configName + " - " + testType + " - " + testName;
  }

  public String getDisplayName() {
    return toString();
  }

  public Path getPathFromPhaseTestDir() {
    return pathFromPhaseTestDir;
  }

  public Path getTestDirectory() {
    return ReferenceTestFinder.findReferenceTestRootDirectory()
        .resolve(Path.of(configName, fork))
        .resolve(pathFromPhaseTestDir);
  }
}
