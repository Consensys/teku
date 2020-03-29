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

package tech.pegasys.artemis.validator.client.duties;

import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.artemis.validator.api.ValidatorApiChannel;
import tech.pegasys.artemis.validator.client.Validator;

public class AttestationProductionDuty {
  private static final Logger LOG = LogManager.getLogger();
  private final List<Validator> validators = new ArrayList<>();
  private final UnsignedLong slot;

  public AttestationProductionDuty(
      final UnsignedLong slot, final ValidatorApiChannel validatorApiChannel) {
    this.slot = slot;
  }

  public synchronized void addValidator(final Validator validator) {
    validators.add(validator);
  }

  public synchronized void performDuty() {
    LOG.info(
        "Creating attestations for validators {} at slot {}",
        validators.stream()
            .map(Validator::getPublicKey)
            .map(Object::toString)
            .collect(Collectors.joining(", ")),
        slot);
  }
}
