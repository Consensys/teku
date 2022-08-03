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

package tech.pegasys.teku.infrastructure.http;

import java.net.URI;
import java.net.URISyntaxException;

public class UrlSanitizer {
  public static String sanitizePotentialUrl(final String possibleUrl) {
    try {
      final URI uri = new URI(possibleUrl);
      return new URI(
              uri.getScheme(),
              null,
              uri.getHost(),
              uri.getPort(),
              uri.getPath(),
              uri.getQuery(),
              uri.getFragment())
          .toASCIIString();
    } catch (URISyntaxException e) {
      // Not actually a URI so no need to sanitize
      return possibleUrl;
    }
  }

  public static boolean urlContainsNonEmptyPath(final String url) {
    try {
      final URI uri = new URI(url);
      return uri.getPath() != null && uri.getPath().length() > 1;
    } catch (URISyntaxException e) {
      return false;
    }
  }
}
