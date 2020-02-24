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

package tech.pegasys.artemis.bls.keystore.builder;

import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.artemis.bls.keystore.model.Checksum;
import tech.pegasys.artemis.bls.keystore.model.ChecksumFunction;
import tech.pegasys.artemis.bls.keystore.model.Param;

public final class ChecksumBuilder {
  private ChecksumFunction checksumFunction = ChecksumFunction.SHA256;
  private Param param = new Param();
  private Bytes message;

  private ChecksumBuilder() {}

  public static ChecksumBuilder aChecksum() {
    return new ChecksumBuilder();
  }

  public ChecksumBuilder withMessage(final Bytes message) {
    this.message = message;
    return this;
  }

  public Checksum build() {
    Objects.requireNonNull(message);
    return new Checksum(checksumFunction, param, message);
  }
}
