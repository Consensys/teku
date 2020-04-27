package tech.pegasys.artemis.cli.options;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.cli.AbstractBeaconNodeCommandTest;
import tech.pegasys.artemis.util.config.ArtemisConfiguration;

public class DepositOptionsTest extends AbstractBeaconNodeCommandTest {

  @Test
  public void shouldReadDepositOptionsFromConfigurationFile() {
    final ArtemisConfiguration config =
        getArtemisConfigurationFromFile("depositOptions_config.yaml");

    assertThat(config.isEth1Enabled()).isFalse();
    assertThat(config.getEth1DepositContractAddress())
        .isEqualTo("0xfe3b557e8fb62b89f4916b721be55ceb828dbd73");
    assertThat(config.getEth1Endpoint()).isEqualTo("http://example.com:1234/path/");
  }

  @Test
  public void eth1Enabled_shouldNotRequireAValue() {
    final ArtemisConfiguration config = getArtemisConfigurationFromArguments("--eth1-enabled");
    assertThat(config.isEth1Enabled()).isTrue();
  }
}
