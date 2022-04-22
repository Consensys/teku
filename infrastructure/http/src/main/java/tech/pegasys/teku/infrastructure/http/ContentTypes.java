/*
 * Copyright 2022 ConsenSys AG.
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

package tech.pegasys.teku.infrastructure.http;

import java.util.Collection;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.commonjava.mimeparse.MIMEParse;

public class ContentTypes {

  public static final String APPLICATION_JSON = "application/json";
  public static final String APPLICATION_OCTET_STREAM = "application/octet-stream";
  private static final Logger LOG = LogManager.getLogger();

  public static Optional<String> getContentType(
      final Collection<String> types, final Optional<String> maybeContentType) {
    if (maybeContentType.isEmpty()) {
      return Optional.empty();
    }
    try {
      final String contentType = maybeContentType.get();
      final String bestMatch = MIMEParse.bestMatch(types, contentType);
      return bestMatch.isEmpty() ? Optional.empty() : Optional.of(bestMatch);
    } catch (Exception e) {
      LOG.trace("Failed to parse accept header", e);
    }
    return Optional.empty();
  }
}
