package tech.pegasys.teku.test.acceptance.dsl;

public enum BesuDockerVersion {
    DEVELOP("develop"),
    V21_10_9("21.10.9");

    private final String version;

    BesuDockerVersion(final String version) {
        this.version = version;
    }

    public String getVersion() {
        return version;
    }
}
