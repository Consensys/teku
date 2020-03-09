package tech.pegasys.artemis.cli.deposit;

import java.io.File;

public interface EncryptedKeysPasswordGroup {
    String getPasswordEnv();
    File getPasswordFile();
    String getPassword();
}
