/*
 * Copyright 2019 ConsenSys AG.
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

package org.ethereum.beacon.discovery.pipeline;

import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class HandlerUtil {
  private static final Logger logger = LogManager.getLogger(HandlerUtil.class);

  public static boolean requireField(Field field, Envelope envelope) {
    if (envelope.contains(field)) {
      return true;
    } else {
      logger.trace(
          () ->
              String.format(
                  "Requirement not satisfied: field %s not exists in envelope %s",
                  field, envelope.getId()));
      return false;
    }
  }

  public static boolean requireCondition(
      Function<Envelope, Boolean> conditionFunction, Envelope envelope) {
    if (conditionFunction.apply(envelope)) {
      return true;
    } else {
      logger.trace(
          () ->
              String.format(
                  "Requirement not satisfied: condition %s not met for envelope %s",
                  conditionFunction, envelope.getId()));
      return false;
    }
  }
}
