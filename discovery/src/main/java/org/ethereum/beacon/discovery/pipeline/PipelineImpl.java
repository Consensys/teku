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

import static org.ethereum.beacon.discovery.pipeline.Field.INCOMING;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.reactivestreams.Publisher;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.ReplayProcessor;

public class PipelineImpl implements Pipeline {
  private final List<EnvelopeHandler> envelopeHandlers = new ArrayList<>();
  private final AtomicBoolean started = new AtomicBoolean(false);
  private Flux<Envelope> pipeline = ReplayProcessor.cacheLast();
  private final FluxSink<Envelope> pipelineSink = ((ReplayProcessor<Envelope>) pipeline).sink();
  private Disposable subscription;

  @Override
  public synchronized Pipeline build() {
    started.set(true);
    for (EnvelopeHandler handler : envelopeHandlers) {
      pipeline = pipeline.doOnNext(handler::handle);
    }
    this.subscription = Flux.from(pipeline).subscribe();
    return this;
  }

  @Override
  public void push(Object object) {
    if (!started.get()) {
      throw new RuntimeException("You should build pipeline first");
    }
    if (!(object instanceof Envelope)) {
      Envelope envelope = new Envelope();
      envelope.put(INCOMING, object);
      pipelineSink.next(envelope);
    } else {
      pipelineSink.next((Envelope) object);
    }
  }

  @Override
  public Pipeline addHandler(EnvelopeHandler envelopeHandler) {
    if (started.get()) {
      throw new RuntimeException("Pipeline already started, couldn't add any handlers");
    }
    envelopeHandlers.add(envelopeHandler);
    return this;
  }

  @Override
  public Publisher<Envelope> getOutgoingEnvelopes() {
    return pipeline;
  }
}
