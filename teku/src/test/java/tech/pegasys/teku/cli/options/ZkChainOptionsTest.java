package tech.pegasys.teku.cli.options;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.cli.AbstractBeaconNodeCommandTest;
import tech.pegasys.teku.config.TekuConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

class ZkChainOptionsTest extends AbstractBeaconNodeCommandTest {

    @Test
    public void statelessValidationEnabled_true() {
        final TekuConfiguration config = getTekuConfigurationFromArguments("--Xstateless-validation-enabled=true");
        assertThat(config.zkChainConfiguration().isStatelessValidationEnabled()).isTrue();
    }

    @Test
    public void generateExecutionProofsEnabled_true() {
        final TekuConfiguration config = getTekuConfigurationFromArguments("--Xgenerate-execution-proofs-enabled=true");
        assertThat(config.zkChainConfiguration().isGenerateExecutionProofsEnabled()).isTrue();
    }

    @Test
    public void statelessMinProofsRequired_receivesCorrectValue() {
        final TekuConfiguration config = getTekuConfigurationFromArguments("--Xstateless-min-proofs-required=2");
        assertThat(config.zkChainConfiguration().getStatelessMinProofsRequired()).isEqualTo(2);
    }
}