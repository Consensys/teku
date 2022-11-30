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

package tech.pegasys.teku.test.acceptance.dsl.tools.deposits;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.testcontainers.shaded.com.fasterxml.jackson.core.JsonProcessingException;
import tech.pegasys.signers.bls.keystore.KeyStore;
import tech.pegasys.signers.bls.keystore.KeyStoreLoader;
import tech.pegasys.signers.bls.keystore.model.Cipher;
import tech.pegasys.signers.bls.keystore.model.CipherFunction;
import tech.pegasys.signers.bls.keystore.model.KdfParam;
import tech.pegasys.signers.bls.keystore.model.KeyStoreData;
import tech.pegasys.signers.bls.keystore.model.Pbkdf2Param;
import tech.pegasys.signers.bls.keystore.model.Pbkdf2PseudoRandomFunction;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.crypto.SecureRandomProvider;

public class ValidatorKeystores {
  private static final Logger LOG = LogManager.getLogger();
  private static final String KEYS_DIRECTORY_NAME = "keys";
  private static final String PASSWORDS_DIRECTORY_NAME = "passwords";
  private static final String VALIDATOR_KEYS_PASSWORD = "0xdeadbeef";
  private final SecureRandom secureRandom = SecureRandomProvider.createSecureRandom();
  private final List<ValidatorKeys> validatorKeys;

  Optional<File> maybeTarball = Optional.empty();

  public ValidatorKeystores(List<ValidatorKeys> validatorKeys) {
    this.validatorKeys = validatorKeys;
  }

  public List<String> getKeystores(final Path tempDir) {
    return validatorKeys.stream()
        .map(
            key -> {
              try {
                KeyStoreData keyStoreData =
                    generateKeystoreData(key.getValidatorKey(), VALIDATOR_KEYS_PASSWORD);
                final Path keystoreFile =
                    tempDir.resolve(
                        key.getValidatorKey().getPublicKey().toAbbreviatedString() + ".json");
                KeyStoreLoader.saveToFile(keystoreFile, keyStoreData);
                Files.readString(keystoreFile, StandardCharsets.UTF_8);
                return Files.readString(keystoreFile, StandardCharsets.UTF_8);
              } catch (JsonProcessingException e) {
                return "";
              } catch (IOException e) {
                LOG.error("Failed to write file", e);
                return "";
              }
            })
        .collect(Collectors.toList());
  }

  public List<String> getPasswords() {
    return validatorKeys.stream().map((__) -> VALIDATOR_KEYS_PASSWORD).collect(Collectors.toList());
  }

  public List<BLSPublicKey> getPublicKeys() {
    return validatorKeys.stream()
        .map(key -> key.getValidatorKey().getPublicKey())
        .collect(Collectors.toList());
  }

  public File getTarball() {
    return maybeTarball.orElseGet(
        () -> {
          try {
            maybeTarball = Optional.of(createValidatorKeystoresTarBall());
            return maybeTarball.get();
          } catch (Exception e) {
            throw new IllegalStateException("Unable to create validator tarball", e);
          }
        });
  }

  public int getValidatorCount() {
    return validatorKeys.size();
  }

  public String getKeysDirectoryName() {
    return KEYS_DIRECTORY_NAME;
  }

  public String getPasswordsDirectoryName() {
    return PASSWORDS_DIRECTORY_NAME;
  }

  private File createValidatorKeystoresTarBall() throws Exception {
    final Path validatorInfoDirectoryPath = Path.of("./validatorInfo");
    final Path keysOutputPath = validatorInfoDirectoryPath.resolve(KEYS_DIRECTORY_NAME);
    final Path passwordsOutputPath = validatorInfoDirectoryPath.resolve(PASSWORDS_DIRECTORY_NAME);
    final ValidatorKeystoreGenerator keystoreGenerator =
        new ValidatorKeystoreGenerator(
            VALIDATOR_KEYS_PASSWORD, keysOutputPath, passwordsOutputPath, (__) -> {});

    // create temporary tar file that can be copied into any docker container
    File validatorInfoTar = File.createTempFile("validatorInfo", ".tar");
    validatorInfoTar.deleteOnExit();

    // create keystores using the validator keys generated by deposit sender
    keystoreGenerator.generateKeystoreAndPasswordFiles(
        validatorKeys.stream().map(ValidatorKeys::getValidatorKey).collect(Collectors.toList()));

    // copy keystores directory to tar file and delete the now redundant directory
    copyDirectoryToTarFile(validatorInfoDirectoryPath, validatorInfoTar.toPath());
    FileUtils.deleteDirectory(validatorInfoDirectoryPath.toFile());
    return validatorInfoTar;
  }

  private static void copyDirectoryToTarFile(Path inputDirectoryPath, Path outputPath)
      throws IOException {
    File outputFile = outputPath.toFile();

    try (FileOutputStream fileOutputStream = new FileOutputStream(outputFile);
        BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(fileOutputStream);
        TarArchiveOutputStream tarArchiveOutputStream =
            new TarArchiveOutputStream(bufferedOutputStream)) {

      tarArchiveOutputStream.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX);
      tarArchiveOutputStream.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);

      List<File> files =
          new ArrayList<>(
              FileUtils.listFiles(inputDirectoryPath.toFile(), new String[] {"json", "txt"}, true));

      for (File currentFile : files) {
        String relativeFilePath =
            new File(inputDirectoryPath.toUri())
                .toURI()
                .relativize(new File(currentFile.getAbsolutePath()).toURI())
                .getPath();

        TarArchiveEntry tarEntry = new TarArchiveEntry(currentFile, relativeFilePath);
        tarEntry.setSize(currentFile.length());

        tarArchiveOutputStream.putArchiveEntry(tarEntry);
        tarArchiveOutputStream.write(Files.readAllBytes(currentFile.toPath()));
        tarArchiveOutputStream.closeArchiveEntry();
      }
    }
  }

  private KeyStoreData generateKeystoreData(final BLSKeyPair key, final String password) {
    final KdfParam kdfParam =
        new Pbkdf2Param(
            32, 1, Pbkdf2PseudoRandomFunction.HMAC_SHA256, Bytes32.random(secureRandom));
    final Cipher cipher = new Cipher(CipherFunction.AES_128_CTR, Bytes.random(16, secureRandom));
    return KeyStore.encrypt(
        key.getSecretKey().toBytes(),
        key.getPublicKey().toBytesCompressed(),
        password,
        "",
        kdfParam,
        cipher);
  }
}
