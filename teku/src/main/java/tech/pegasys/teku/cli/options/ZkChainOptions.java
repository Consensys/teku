package tech.pegasys.teku.cli.options;

import picocli.CommandLine;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.services.zkchain.ZkChainConfiguration;

public class ZkChainOptions {
    @CommandLine.Option(
            names = {"--Xstateless-validation-enabled"},
            description = "Enable stateless validation of blocks and states.")
    private boolean statelessValidationEnabled = ZkChainConfiguration.DEFAULT_STATELESS_VALIDATION_ENABLED;

    @CommandLine.Option(
            names = {"--Xgenerate-execution-proofs-enabled"},
            description = "Enable generation of execution proofs for blocks.")
    private boolean generateExecutionProofsEnabled = ZkChainConfiguration.DEFAULT_GENERATE_EXECUTION_PROOFS_ENABLED;

    @CommandLine.Option(
            names = {"--Xstateless-min-proofs-required"},
            description = "Minimum number of execution proofs required for stateless validation. Must be at least 1.")
    private int statelessMinProofsRequired = ZkChainConfiguration.DEFAULT_STATELESS_MIN_PROOFS_REQUIRED;

    public void configure(final TekuConfiguration.Builder builder) {
        builder.zkchain(zkChainConfiguration ->
                zkChainConfiguration
                        .statelessValidationEnabled(statelessValidationEnabled)
                        .generateExecutionProofsEnabled(generateExecutionProofsEnabled)
                        .statelessMinProofsRequired(statelessMinProofsRequired));
    }
}
