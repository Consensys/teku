/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.test.acceptance.dsl;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.time.SystemTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecFactory;
import tech.pegasys.teku.spec.config.builder.SpecConfigBuilder;
import tech.pegasys.teku.spec.datastructures.interop.GenesisStateBuilder;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.test.acceptance.dsl.tools.deposits.ValidatorKeystores;

public class GenesisGenerator {

  private final SystemTimeProvider timeProvider = new SystemTimeProvider();
  private String network;
  private ValidatorKeystores validatorKeys;
  private Optional<BesuNode> genesisPayloadSource = Optional.empty();
  private int genesisDelaySeconds = 10;
  private int genesisTime;
  private Consumer<SpecConfigBuilder> specConfigModifier = builder -> {};

  public GenesisGenerator network(final String network) {
    this.network = network;
    return this;
  }

  public GenesisGenerator withAltairEpoch(final UInt64 altairForkEpoch) {
    specConfigModifier =
        specConfigModifier.andThen(
            specConfigBuilder ->
                specConfigBuilder.altairBuilder(
                    altairBuilder -> altairBuilder.altairForkEpoch(altairForkEpoch)));
    return this;
  }

  public GenesisGenerator withBellatrixEpoch(final UInt64 bellatrixForkEpoch) {
    specConfigModifier =
        specConfigModifier.andThen(
            specConfigBuilder ->
                specConfigBuilder.bellatrixBuilder(
                    bellatrixBuilder -> bellatrixBuilder.bellatrixForkEpoch(bellatrixForkEpoch)));
    return this;
  }

  public GenesisGenerator withCapellaEpoch(final UInt64 capellaForkEpoch) {
    specConfigModifier =
        specConfigModifier.andThen(
            specConfigBuilder ->
                specConfigBuilder.capellaBuilder(
                    capellaBuilder -> capellaBuilder.capellaForkEpoch(capellaForkEpoch)));
    return this;
  }

  public GenesisGenerator withTotalTerminalDifficulty(final long totalTerminalDifficulty) {
    return withTotalTerminalDifficulty(UInt256.valueOf(totalTerminalDifficulty));
  }

  public GenesisGenerator withTotalTerminalDifficulty(final UInt256 totalTerminalDifficulty) {
    specConfigModifier =
        specConfigModifier.andThen(
            specConfigBuilder ->
                specConfigBuilder.bellatrixBuilder(
                    bellatrixBuilder ->
                        bellatrixBuilder.terminalTotalDifficulty(totalTerminalDifficulty)));
    return this;
  }

  public GenesisGenerator validatorKeys(final ValidatorKeystores... validatorKeys) {
    this.validatorKeys =
        Stream.of(validatorKeys)
            .reduce(new ValidatorKeystores(Collections.emptyList()), ValidatorKeystores::add);
    return this;
  }

  public GenesisGenerator genesisDelaySeconds(final int genesisDelaySeconds) {
    this.genesisDelaySeconds = genesisDelaySeconds;
    return this;
  }

  public GenesisGenerator genesisPayloadSource(final BesuNode genesisPayloadSource) {
    this.genesisPayloadSource = Optional.of(genesisPayloadSource);
    return this;
  }

  public GenesisGenerator genesisTime(final int genesisTime) {
    this.genesisTime = genesisTime;
    return this;
  }

  public InitialStateData generate() {
    final Spec spec = SpecFactory.create(network, specConfigModifier);
    final GenesisStateBuilder genesisBuilder = new GenesisStateBuilder();
//    genesisBuilder
//        .spec(spec)
//        .genesisTime(timeProvider.getTimeInSeconds().plus(genesisDelaySeconds));
    genesisBuilder
        .spec(spec)
        .genesisTime(genesisTime);
    validatorKeys
        .getValidatorKeys()
        .forEach(
            validator ->
                genesisBuilder.addValidator(
                    validator.getValidatorKey(), validator.getWithdrawalCredentials()));
    genesisPayloadSource.ifPresent(
        source ->
            genesisBuilder.executionPayloadHeader(source.createGenesisExecutionPayload(spec)));
    return new InitialStateData(genesisBuilder.build());
  }

  public static class InitialStateData {

    private final BeaconState genesisState;

    public InitialStateData(final BeaconState genesisState) {
      this.genesisState = genesisState;
    }

    public BeaconState getGenesisState() {
      return genesisState;
    }

    public File writeToTempFile() throws IOException {
      final File tempFile = Files.createTempFile("state", ".ssz").toFile();
      tempFile.deleteOnExit();
      Files.write(tempFile.toPath(), genesisState.sszSerialize().toArrayUnsafe());
      return tempFile;
    }
  }
}
