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

package tech.pegasys.teku.kzg.ckzg4844;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.io.Resources;
import ethereum.ckzg4844.CKZG4844JNI;
import java.util.List;
import java.util.Optional;
import net.jqwik.api.ForAll;
import net.jqwik.api.From;
import net.jqwik.api.Property;
import net.jqwik.api.Tuple;
import net.jqwik.api.lifecycle.AddLifecycleHook;
import net.jqwik.api.lifecycle.LifecycleContext;
import net.jqwik.api.lifecycle.Lifespan;
import net.jqwik.api.lifecycle.ParameterResolutionContext;
import net.jqwik.api.lifecycle.PropagationMode;
import net.jqwik.api.lifecycle.ResolveParameterHook;
import net.jqwik.api.lifecycle.Store;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.kzg.KZGCommitment;
import tech.pegasys.teku.kzg.KZGException;
import tech.pegasys.teku.kzg.KZGProof;
import tech.pegasys.teku.kzg.propertytest.suppliers.BytesSupplier;
import tech.pegasys.teku.kzg.propertytest.suppliers.KZGCommitmentSupplier;
import tech.pegasys.teku.kzg.propertytest.suppliers.KZGProofSupplier;
import tech.pegasys.teku.kzg.trusted_setups.TrustedSetups;

@AddLifecycleHook(KzgResolver.class)
public class CKZG4844PropertyTest {

  @Property(tries = 100)
  void computeAggregateKzgProofThrowsExpected(
      final KZG kzg, @ForAll final List<@From(supplier = BytesSupplier.class) Bytes> blobs) {
    try {
      kzg.computeAggregateKzgProof(blobs);
    } catch (Exception e) {
      assertThat(e).isInstanceOf(KZGException.class);
    }
  }

  @Property(tries = 100)
  void verifyAggregateKzgProofThrowsExpected(
      final KZG kzg,
      @ForAll final List<@From(supplier = BytesSupplier.class) Bytes> blobs,
      @ForAll final List<@From(supplier = KZGCommitmentSupplier.class) KZGCommitment> commitments,
      @ForAll(supplier = KZGProofSupplier.class) final KZGProof proof) {
    try {
      kzg.verifyAggregateKzgProof(blobs, commitments, proof);
    } catch (Exception e) {
      assertThat(e).isInstanceOf(KZGException.class);
    }
  }

  @Property(tries = 100)
  void blobToKzgCommitmentThrowsExpected(
      final KZG kzg, @ForAll(supplier = BytesSupplier.class) final Bytes blob) {
    try {
      kzg.blobToKzgCommitment(blob);
    } catch (Exception e) {
      assertThat(e).isInstanceOf(KZGException.class);
    }
  }
}


/**
 * This class provides a KZG instance with a loaded trusted setup that will automatically free the
 * trusted setup when property test finished. It will re-use the same KZG instance for all
 * iterations of the test, but it will create a new instance for each method. For a class with three
 * property test methods, you can expect it to load/free three times.
 */
class KzgResolver implements ResolveParameterHook {
  public static final Tuple.Tuple2<Class<KzgAutoLoadFree>, String> STORE_IDENTIFIER =
      Tuple.of(KzgAutoLoadFree.class, "KZGs that automatically load & free");

  @Override
  public Optional<ParameterSupplier> resolve(
      final ParameterResolutionContext parameterContext, final LifecycleContext lifecycleContext) {
    return Optional.of(optionalTry -> getKzgWithTrustedSetup());
  }

  @Override
  public PropagationMode propagateTo() {
    return PropagationMode.ALL_DESCENDANTS;
  }

  private KZG getKzgWithTrustedSetup() {
    Store<KzgAutoLoadFree> kzgStore =
        Store.getOrCreate(STORE_IDENTIFIER, Lifespan.PROPERTY, KzgAutoLoadFree::new);
    return kzgStore.get().kzg;
  }

  private static class KzgAutoLoadFree implements Store.CloseOnReset {
    private static final String TRUSTED_SETUP =
        Resources.getResource(TrustedSetups.class, "mainnet/trusted_setup.txt").toExternalForm();
    private final KZG kzg = CKZG4844.createInstance(CKZG4844JNI.Preset.MAINNET.fieldElementsPerBlob);

    private KzgAutoLoadFree() {
      kzg.loadTrustedSetup(TRUSTED_SETUP);
    }

    @Override
    public void close() {
      kzg.freeTrustedSetup();
    }
  }
}
