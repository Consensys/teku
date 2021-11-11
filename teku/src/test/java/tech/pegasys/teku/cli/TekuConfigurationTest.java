package tech.pegasys.teku.cli;

import java.nio.file.Path;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.config.TekuConfiguration;

public class TekuConfigurationTest extends AbstractBeaconNodeCommandTest{

  @Test
  void a() {
    TekuConfiguration cliConfig = getTekuConfigurationFromArguments("--data-path=./my.data");
    TekuConfiguration config = TekuConfiguration.builder()
        .data(b -> b.dataBasePath(Path.of("./my.data")))
        .build();

    Assertions.assertThat(config)
        .usingRecursiveComparison()
        .isEqualTo(cliConfig);
//        .isEqualToComparingFieldByField(cliConfig);

  }
}
