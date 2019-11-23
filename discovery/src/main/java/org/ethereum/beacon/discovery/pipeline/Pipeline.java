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

import org.reactivestreams.Publisher;

/**
 * Pipeline uses several {@link EnvelopeHandler} handlers to pass objects through the chain of
 * linked handlers implementing pipeline (or chain of responsibility) pattern.
 */
public interface Pipeline {
  /** Builds configured pipeline making it active */
  Pipeline build();

  /** Pushes object inside pipeline */
  void push(Object object);

  /** Adds handler at the end of current chain */
  Pipeline addHandler(EnvelopeHandler envelopeHandler);

  /** Stream from the exit of built pipeline */
  Publisher<Envelope> getOutgoingEnvelopes();
}
