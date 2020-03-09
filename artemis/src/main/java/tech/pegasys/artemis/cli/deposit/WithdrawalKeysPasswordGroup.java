/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.artemis.cli.deposit;

import java.io.File;
import picocli.CommandLine.Option;

public class WithdrawalKeysPasswordGroup implements EncryptedKeysPasswordGroup {
  @Option(
      names = {"--withdrawal-password:file"},
      paramLabel = "<FILE>",
      description = "Path to the file containing password to encrypt the withdrawal keys.")
  private File passwordFile;

  @Option(
      names = {"--withdrawal-password:env"},
      paramLabel = "<ENVIRONMENT_VAR>",
      description = "Environment variable which specifies password to encrypt the withdrawal keys.")
  private String passwordEnv;

  @Option(
      names = {"--withdrawal-password"},
      description = "Provide password (interactive mode) to encrypt withdrawal keys.",
      interactive = true)
  private String password;

  public WithdrawalKeysPasswordGroup() {}

  public WithdrawalKeysPasswordGroup(
      final File passwordFile, final String passwordEnv, final String password) {
    this.passwordFile = passwordFile;
    this.passwordEnv = passwordEnv;
    this.password = password;
  }

  @Override
  public File getPasswordFile() {
    return passwordFile;
  }

  public void setPasswordFile(final File passwordFile) {
    this.passwordFile = passwordFile;
  }

  @Override
  public String getPasswordEnv() {
    return passwordEnv;
  }

  public void setPasswordEnv(final String passwordEnv) {
    this.passwordEnv = passwordEnv;
  }

  @Override
  public String getPassword() {
    return password;
  }

  public void setPassword(final String password) {
    this.password = password;
  }
}
