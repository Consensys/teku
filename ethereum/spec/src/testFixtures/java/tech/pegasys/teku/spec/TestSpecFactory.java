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

package tech.pegasys.teku.spec;

import com.google.common.base.Preconditions;
import java.util.function.Consumer;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.config.SpecConfigBellatrix;
import tech.pegasys.teku.spec.config.SpecConfigCapella;
import tech.pegasys.teku.spec.config.SpecConfigEip4844;
import tech.pegasys.teku.spec.config.SpecConfigLoader;
import tech.pegasys.teku.spec.config.builder.SpecConfigBuilder;
import tech.pegasys.teku.spec.networks.Eth2Network;

public class TestSpecFactory {

  public static Spec createDefault() {
    return createDefault(__ -> {});
  }

  public static Spec createDefault(final Consumer<SpecConfigBuilder> modifier) {
    final SpecConfig config =
        SpecConfigLoader.loadConfig(Eth2Network.MINIMAL.configName(), modifier);
    return create(config, SpecMilestone.PHASE0);
  }

  public static Spec createMinimal(final SpecMilestone specMilestone) {
    switch (specMilestone) {
      case PHASE0:
        return createMinimalPhase0();
      case ALTAIR:
        return createMinimalAltair();
      case BELLATRIX:
        return createMinimalBellatrix();
      case CAPELLA:
        return createMinimalCapella();
      case EIP4844:
        return createMinimalEip4844();
      default:
        throw new IllegalStateException("unsupported milestone");
    }
  }

  public static Spec createMainnet(final SpecMilestone specMilestone) {
    switch (specMilestone) {
      case PHASE0:
        return createMainnetPhase0();
      case ALTAIR:
        return createMainnetAltair();
      case BELLATRIX:
        return createMainnetBellatrix();
      case CAPELLA:
        return createMainnetCapella();
      case EIP4844:
        return createMainnetEip4844();
      default:
        throw new IllegalStateException("unsupported milestone");
    }
  }

  public static Spec createMinimalWithAltairAndBellatrixForkEpoch(
      final UInt64 altairEpoch, final UInt64 bellatrixForkEpoch) {
    Preconditions.checkArgument(
        altairEpoch.isLessThan(bellatrixForkEpoch),
        String.format(
            "Altair epoch %s must be less than bellatrix epoch %s",
            altairEpoch, bellatrixForkEpoch));
    final SpecConfigBellatrix config =
        getBellatrixSpecConfig(Eth2Network.MINIMAL, altairEpoch, bellatrixForkEpoch);
    return create(config, SpecMilestone.BELLATRIX);
  }

  public static Spec createMinimalBellatrix() {
    final SpecConfigBellatrix specConfig = getBellatrixSpecConfig(Eth2Network.MINIMAL);
    return create(specConfig, SpecMilestone.BELLATRIX);
  }

  public static Spec createMinimalBellatrix(final Consumer<SpecConfigBuilder> configAdapter) {
    final SpecConfigBellatrix specConfig =
        getBellatrixSpecConfig(Eth2Network.MINIMAL, configAdapter);
    return create(specConfig, SpecMilestone.BELLATRIX);
  }

  public static Spec createMinimalAltair() {
    final SpecConfigAltair specConfig = getAltairSpecConfig(Eth2Network.MINIMAL);
    return create(specConfig, SpecMilestone.ALTAIR);
  }

  public static Spec createMinimalCapella() {
    final SpecConfigCapella specConfig = getCapellaSpecConfig(Eth2Network.MINIMAL);
    return create(specConfig, SpecMilestone.CAPELLA);
  }

  public static Spec createMinimalEip4844() {
    final SpecConfigEip4844 specConfig = getEip4844SpecConfig(Eth2Network.MINIMAL);
    return create(specConfig, SpecMilestone.EIP4844);
  }

  /**
   * Create a spec that forks to altair at the provided slot
   *
   * @param altairForkEpoch The altair fork epoch
   * @return A spec with phase0 and altair enabled, forking to altair at the given epoch
   */
  public static Spec createMinimalWithAltairForkEpoch(final UInt64 altairForkEpoch) {
    final SpecConfigAltair config = getAltairSpecConfig(Eth2Network.MINIMAL, altairForkEpoch);
    return create(config, SpecMilestone.ALTAIR);
  }

  /**
   * Create a spec that forks to bellatrix at the provided slot (altair genesis)
   *
   * @param bellatrixForkEpoch The bellatrix fork epoch
   * @return A spec with altair and bellatrix enabled, forking to bellatrix at the given epoch
   */
  public static Spec createMinimalWithBellatrixForkEpoch(final UInt64 bellatrixForkEpoch) {
    final SpecConfigBellatrix config =
        getBellatrixSpecConfig(Eth2Network.MINIMAL, UInt64.ZERO, bellatrixForkEpoch);
    return create(config, SpecMilestone.BELLATRIX);
  }

  /**
   * Create a spec that forks to Capella at the provided slot (bellatrix genesis)
   *
   * @param capellaForkEpoch The Capella fork epoch
   * @return A spec with Altair, Bellatrix and Capella enabled, forking to Capella at the given
   *     epoch
   */
  public static Spec createMinimalWithCapellaForkEpoch(final UInt64 capellaForkEpoch) {
    final SpecConfigCapella config =
        getCapellaSpecConfig(Eth2Network.MINIMAL, UInt64.ZERO, UInt64.ZERO, capellaForkEpoch);
    return create(config, SpecMilestone.CAPELLA);
  }

  /**
   * Create a spec that forks to EIP4844 at the provided epoch
   *
   * @param eip4844ForkEpoch The eip4844 fork epoch
   * @return A spec with eip4844 enabled, forking to eip4844 at the given epoch
   */
  public static Spec createMinimalWithEip4844ForkEpoch(final UInt64 eip4844ForkEpoch) {
    final SpecConfigEip4844 config =
        getEip4844SpecConfig(
            Eth2Network.MINIMAL, UInt64.ZERO, UInt64.ZERO, UInt64.ZERO, eip4844ForkEpoch);
    return create(config, SpecMilestone.EIP4844);
  }

  public static Spec createMinimalPhase0() {
    final SpecConfig specConfig = SpecConfigLoader.loadConfig(Eth2Network.MINIMAL.configName());
    return create(specConfig, SpecMilestone.PHASE0);
  }

  public static Spec createMainnetBellatrix() {
    final SpecConfigBellatrix specConfig = getBellatrixSpecConfig(Eth2Network.MAINNET);
    return create(specConfig, SpecMilestone.BELLATRIX);
  }

  public static Spec createMainnetAltair() {
    final SpecConfigAltair specConfig = getAltairSpecConfig(Eth2Network.MAINNET);
    return create(specConfig, SpecMilestone.ALTAIR);
  }

  public static Spec createMainnetCapella() {
    final SpecConfigCapella specConfig = getCapellaSpecConfig(Eth2Network.MAINNET);
    return create(specConfig, SpecMilestone.CAPELLA);
  }

  public static Spec createMainnetEip4844() {
    final SpecConfigBellatrix specConfig = getEip4844SpecConfig(Eth2Network.MAINNET);
    return create(specConfig, SpecMilestone.EIP4844);
  }

  public static Spec createMainnetPhase0() {
    final SpecConfig specConfig = SpecConfigLoader.loadConfig(Eth2Network.MAINNET.configName());
    return create(specConfig, SpecMilestone.PHASE0);
  }

  public static Spec createPhase0(final String configName) {
    final SpecConfig specConfig = SpecConfigLoader.loadConfig(configName);
    return createPhase0(specConfig);
  }

  public static Spec createPhase0(final SpecConfig config) {
    return create(config, SpecMilestone.PHASE0);
  }

  public static Spec createAltair(final SpecConfig config) {
    return create(config, SpecMilestone.ALTAIR);
  }

  public static Spec createBellatrix(final SpecConfig config) {
    return create(config, SpecMilestone.BELLATRIX);
  }

  public static Spec createCapella(final SpecConfig config) {
    return create(config, SpecMilestone.CAPELLA);
  }

  public static Spec createEip4844(final SpecConfig config) {
    return create(config, SpecMilestone.EIP4844);
  }

  public static Spec create(final SpecMilestone specMilestone, final Eth2Network network) {
    return create(specMilestone, network, builder -> {});
  }

  public static Spec create(
      final SpecMilestone specMilestone,
      final Eth2Network network,
      final Consumer<SpecConfigBuilder> configModifier) {
    final Consumer<SpecConfigBuilder> actualModifier;
    switch (specMilestone) {
      case PHASE0:
        actualModifier = configModifier;
        break;
      case ALTAIR:
        actualModifier =
            configModifier.andThen(
                builder -> builder.altairBuilder(altair -> altair.altairForkEpoch(UInt64.ZERO)));
        break;
      case BELLATRIX:
        actualModifier =
            configModifier.andThen(
                c ->
                    c.altairBuilder(a -> a.altairForkEpoch(UInt64.ZERO))
                        .bellatrixBuilder(m -> m.bellatrixForkEpoch(UInt64.ZERO)));
        break;
      case CAPELLA:
        actualModifier =
            configModifier.andThen(
                z ->
                    z.altairBuilder(a -> a.altairForkEpoch(UInt64.ZERO))
                        .bellatrixBuilder(b -> b.bellatrixForkEpoch(UInt64.ZERO))
                        .capellaBuilder(c -> c.capellaForkEpoch(UInt64.ZERO)));
        break;
      case EIP4844:
        actualModifier =
            configModifier.andThen(
                z ->
                    z.altairBuilder(a -> a.altairForkEpoch(UInt64.ZERO))
                        .bellatrixBuilder(b -> b.bellatrixForkEpoch(UInt64.ZERO))
                        .capellaBuilder(c -> c.capellaForkEpoch(UInt64.ZERO))
                        .eip4844Builder(d -> d.eip4844ForkEpoch(UInt64.ZERO).kzgNoop(true)));
        break;
      default:
        throw new IllegalStateException("unsupported milestone");
    }
    return create(SpecConfigLoader.loadConfig(network.configName(), actualModifier), specMilestone);
  }

  public static Spec create(
      final SpecConfig config, final SpecMilestone highestSupportedMilestone) {
    return Spec.create(config, highestSupportedMilestone);
  }

  private static SpecConfigAltair getAltairSpecConfig(final Eth2Network network) {
    return getAltairSpecConfig(network, UInt64.ZERO);
  }

  private static SpecConfigAltair getAltairSpecConfig(
      final Eth2Network network, final UInt64 altairForkEpoch) {
    return SpecConfigAltair.required(
        SpecConfigLoader.loadConfig(
            network.configName(), c -> c.altairBuilder(a -> a.altairForkEpoch(altairForkEpoch))));
  }

  private static SpecConfigBellatrix getBellatrixSpecConfig(final Eth2Network network) {
    return getBellatrixSpecConfig(network, UInt64.ZERO, UInt64.ZERO);
  }

  private static SpecConfigBellatrix getBellatrixSpecConfig(
      final Eth2Network network, final UInt64 altairForkEpoch, UInt64 bellatrixForkEpoch) {
    return getBellatrixSpecConfig(
        network,
        c ->
            c.altairBuilder(a -> a.altairForkEpoch(altairForkEpoch))
                .bellatrixBuilder(m -> m.bellatrixForkEpoch(bellatrixForkEpoch)));
  }

  private static SpecConfigBellatrix getBellatrixSpecConfig(
      final Eth2Network network, final Consumer<SpecConfigBuilder> configAdapter) {
    return SpecConfigBellatrix.required(
        SpecConfigLoader.loadConfig(
            network.configName(),
            builder -> {
              builder
                  .altairBuilder(a -> a.altairForkEpoch(UInt64.ZERO))
                  .bellatrixBuilder(b -> b.bellatrixForkEpoch(UInt64.ZERO));
              configAdapter.accept(builder);
            }));
  }

  private static SpecConfigCapella getCapellaSpecConfig(final Eth2Network network) {
    return getCapellaSpecConfig(network, UInt64.ZERO, UInt64.ZERO, UInt64.ZERO);
  }

  private static SpecConfigCapella getCapellaSpecConfig(
      final Eth2Network network,
      final UInt64 altairForkEpoch,
      final UInt64 bellatrixForkEpoch,
      final UInt64 capellaForkEpoch) {
    return getCapellaSpecConfig(
        network,
        z ->
            z.altairBuilder(a -> a.altairForkEpoch(altairForkEpoch))
                .bellatrixBuilder(b -> b.bellatrixForkEpoch(bellatrixForkEpoch))
                .capellaBuilder(c -> c.capellaForkEpoch(capellaForkEpoch)));
  }

  private static SpecConfigCapella getCapellaSpecConfig(
      final Eth2Network network, final Consumer<SpecConfigBuilder> configAdapter) {
    return SpecConfigCapella.required(
        SpecConfigLoader.loadConfig(
            network.configName(),
            builder -> {
              builder
                  .altairBuilder(a -> a.altairForkEpoch(UInt64.ZERO))
                  .bellatrixBuilder(b -> b.bellatrixForkEpoch(UInt64.ZERO))
                  .capellaBuilder(c -> c.capellaForkEpoch(UInt64.ZERO));
              configAdapter.accept(builder);
            }));
  }

  private static SpecConfigEip4844 getEip4844SpecConfig(final Eth2Network network) {
    return getEip4844SpecConfig(network, UInt64.ZERO, UInt64.ZERO, UInt64.ZERO, UInt64.ZERO);
  }

  private static SpecConfigEip4844 getEip4844SpecConfig(
      final Eth2Network network,
      final UInt64 altairForkEpoch,
      final UInt64 bellatrixForkEpoch,
      final UInt64 capellaForkEpoch,
      final UInt64 eip4844ForkEpoch) {
    return getEip4844SpecConfig(
        network,
        z ->
            z.altairBuilder(a -> a.altairForkEpoch(altairForkEpoch))
                .bellatrixBuilder(b -> b.bellatrixForkEpoch(bellatrixForkEpoch))
                .capellaBuilder(c -> c.capellaForkEpoch(capellaForkEpoch))
                .eip4844Builder(d -> d.eip4844ForkEpoch(eip4844ForkEpoch).kzgNoop(true)));
  }

  private static SpecConfigEip4844 getEip4844SpecConfig(
      final Eth2Network network, final Consumer<SpecConfigBuilder> configAdapter) {
    return SpecConfigEip4844.required(
        SpecConfigLoader.loadConfig(
            network.configName(),
            builder -> {
              builder
                  .altairBuilder(a -> a.altairForkEpoch(UInt64.ZERO))
                  .bellatrixBuilder(b -> b.bellatrixForkEpoch(UInt64.ZERO))
                  .capellaBuilder(c -> c.capellaForkEpoch(UInt64.ZERO))
                  .eip4844Builder(d -> d.eip4844ForkEpoch(UInt64.ZERO).kzgNoop(true));
              configAdapter.accept(builder);
            }));
  }
}
