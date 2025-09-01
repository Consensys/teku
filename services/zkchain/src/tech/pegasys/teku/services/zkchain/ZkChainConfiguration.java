package tech.pegasys.teku.services.zkchain;

public class ZkChainConfiguration {

    public static final boolean DEFAULT_STATELESS_VALIDATION_ENABLED = false;
    public static final boolean DEFAULT_GENERATE_EXECUTION_PROOFS_ENABLED = false;
    public static final int DEFAULT_STATELESS_MIN_PROOFS_REQUIRED = 1;


    private final boolean statelessValidationEnabled;
    private final boolean generateExecutionProofsEnabled;
    private final int statelessMinProofsRequired;

    public ZkChainConfiguration(final boolean statelessValidationEnabled, final boolean generateExecutionProofsEnabled, final int statelessMinProofsRequired) {
        this.statelessValidationEnabled = statelessValidationEnabled;
        this.generateExecutionProofsEnabled = generateExecutionProofsEnabled;
        this.statelessMinProofsRequired = statelessMinProofsRequired;
    }

    public boolean isStatelessValidationEnabled() {
        return statelessValidationEnabled;
    }

    public boolean isGenerateExecutionProofsEnabled() {
        return generateExecutionProofsEnabled;
    }

    public int getStatelessMinProofsRequired() {
        return statelessMinProofsRequired;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private boolean statelessValidationEnabled = DEFAULT_STATELESS_VALIDATION_ENABLED;
        private boolean generateExecutionProofsEnabled = DEFAULT_GENERATE_EXECUTION_PROOFS_ENABLED;
        private int statelessMinProofsRequired = DEFAULT_STATELESS_MIN_PROOFS_REQUIRED;

        public Builder() {}

        public Builder statelessValidationEnabled(final boolean statelessValidationEnabled) {
            this.statelessValidationEnabled = statelessValidationEnabled;
            return this;
        }

        public Builder generateExecutionProofsEnabled(final boolean generateExecutionProofsEnabled) {
            this.generateExecutionProofsEnabled = generateExecutionProofsEnabled;
            return this;
        }

        public Builder statelessMinProofsRequired(final int statelessMinProofsRequired) {
            if (statelessMinProofsRequired < 1) {
                throw new IllegalArgumentException("statelessMinProofsRequired must be at least 1");
            }
            this.statelessMinProofsRequired = statelessMinProofsRequired;
            return this;
        }

        public ZkChainConfiguration build() {
            return new ZkChainConfiguration(statelessValidationEnabled,
                    generateExecutionProofsEnabled,
                    statelessMinProofsRequired);
        }
    }

}
