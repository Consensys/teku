package tech.pegasys.artemis.cli.deposit;

import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Option;

import java.io.File;

class Eth1PrivateKeyOptions {
  @Option(
      names = {"--eth1-private-key"},
      required = true,
      paramLabel = "<KEY>",
      description = "Ethereum 1 private key to use to send transactions")
  String eth1PrivateKey;

  @ArgGroup(exclusive = false, multiplicity = "1")
  Eth1EncryptedPrivateKeystoreOptions keystoreOptions;


static class Eth1EncryptedPrivateKeystoreOptions {
  @Option(
      names = {"--eth1-private-keystore-file"},
      required = true,
      paramLabel = "<FILE>",
      description =
          "Path to encrypted (V3) keystore containing Ethereum 1 private key to use to send transactions")
  File eth1PrivateKeystoreFile;

  @Option(
      names = {"--eth1-private-keystore-password-file"},
      required = true,
      paramLabel = "<FILE>",
      description = "Path to file containing password to decrypt Ethereum 1 keystore")
  File eth1PrivateKeystorePasswordFile;
}


}
