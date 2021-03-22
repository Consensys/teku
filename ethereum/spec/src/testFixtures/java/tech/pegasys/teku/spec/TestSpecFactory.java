package tech.pegasys.teku.spec;

import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.config.SpecConfigLoader;
import tech.pegasys.teku.spec.networks.Eth2Network;

public class TestSpecFactory {

  public static Spec createMinimalAltair() {
    final SpecConfigAltair specConfig =
        SpecConfigAltair.required(SpecConfigLoader.loadConfig(Eth2Network.MINIMAL.configName()));
    return SpecFactory.create(specConfig, specConfig.getAltairForkVersion());
  }
}
