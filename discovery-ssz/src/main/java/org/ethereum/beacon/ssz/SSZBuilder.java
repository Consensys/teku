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

package org.ethereum.beacon.ssz;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.ethereum.beacon.ssz.access.AccessorResolver;
import org.ethereum.beacon.ssz.access.AccessorResolverRegistry;
import org.ethereum.beacon.ssz.access.SSZBasicAccessor;
import org.ethereum.beacon.ssz.access.SSZContainerAccessor;
import org.ethereum.beacon.ssz.access.SSZListAccessor;
import org.ethereum.beacon.ssz.access.SSZUnionAccessor;
import org.ethereum.beacon.ssz.access.basic.BooleanPrimitive;
import org.ethereum.beacon.ssz.access.basic.BytesPrimitive;
import org.ethereum.beacon.ssz.access.basic.HashCodec;
import org.ethereum.beacon.ssz.access.basic.StringPrimitive;
import org.ethereum.beacon.ssz.access.basic.UIntCodec;
import org.ethereum.beacon.ssz.access.basic.UIntPrimitive;
import org.ethereum.beacon.ssz.access.basic.UnionNull;
import org.ethereum.beacon.ssz.access.container.SSZAnnotationSchemeBuilder;
import org.ethereum.beacon.ssz.access.container.SSZSchemeBuilder;
import org.ethereum.beacon.ssz.access.container.SimpleContainerAccessor;
import org.ethereum.beacon.ssz.access.list.ArrayAccessor;
import org.ethereum.beacon.ssz.access.list.BitlistAccessor;
import org.ethereum.beacon.ssz.access.list.BitvectorAccessor;
import org.ethereum.beacon.ssz.access.list.BytesValueAccessor;
import org.ethereum.beacon.ssz.access.list.ListAccessor;
import org.ethereum.beacon.ssz.access.list.ReadListAccessor;
import org.ethereum.beacon.ssz.access.union.GenericTypeSSZUnionAccessor;
import org.ethereum.beacon.ssz.access.union.SchemeSSZUnionAccessor;
import org.ethereum.beacon.ssz.annotation.SSZ;
import org.ethereum.beacon.ssz.annotation.SSZSerializable;
import org.ethereum.beacon.ssz.annotation.SSZTransient;
import org.ethereum.beacon.ssz.creator.CompositeObjCreator;
import org.ethereum.beacon.ssz.creator.ConstructorExtraObjCreator;
import org.ethereum.beacon.ssz.creator.ConstructorObjCreator;
import org.ethereum.beacon.ssz.creator.ObjectCreator;
import org.ethereum.beacon.ssz.creator.SettersExtraObjCreator;
import org.ethereum.beacon.ssz.creator.SettersObjCreator;
import org.ethereum.beacon.ssz.type.SimpleTypeResolver;
import org.ethereum.beacon.ssz.type.TypeResolver;
import org.ethereum.beacon.ssz.visitor.MerkleTrie;
import org.ethereum.beacon.ssz.visitor.SSZIncrementalHasher;
import org.ethereum.beacon.ssz.visitor.SSZSimpleHasher;
import org.ethereum.beacon.ssz.visitor.SSZVisitor;
import org.ethereum.beacon.ssz.visitor.SSZVisitorHost;
import tech.pegasys.artemis.ethereum.core.Hash32;
import tech.pegasys.artemis.util.bytes.BytesValue;

/**
 * SSZ Builder is designed to create {@link SSZSerializer} or {@link SSZHasher} up to your needs.
 *
 * <p>It uses {@link SSZAnnotationSchemeBuilder}to create SSZ model from Java class with annotations
 *
 * <p>Following annotations are used for this:
 *
 * <ul>
 *   <li>{@link SSZSerializable} - Class which stores SSZ serializable data should be annotated with
 *       it, any type which is not java.lang.*
 *   <li>{@link SSZ} - any field with Java type couldn't be automatically mapped to SSZ type, or
 *       with mapping that overrides standard, should be annotated with it. For standard mappings
 *       check {@link SSZ#type()} Javadoc.
 *   <li>{@link SSZTransient} - Fields that should not be used in serialization should be marked
 *       with such annotation
 * </ul>
 *
 * <p>Final {@link SSZSerializer} could be built with {@link #buildSerializer()} method.
 *
 * <p>For serialization use {@link SSZSerializer#encode(Object)}. SSZ serializer uses getters for
 * all non-transient fields to get current values.
 *
 * <p>For deserialization, to restore instance use {@link SSZSerializer#decode(byte[], Class)}. It
 * will try to find constructor with all non-transient field types and the same order, to restore
 * object. If failed, it will try to create empty instance from no-fields constructor and set each
 * one by one by appropriate setter. If at least one field is failed to be set, {@link
 * SSZSchemeException} is thrown.
 */
public class SSZBuilder {

  private static final int SSZ_SCHEMES_CACHE_CAPACITY = 128;
  private static final int SSZ_HASH_BYTES_PER_CHUNK = 32;

  private List<SSZBasicAccessor> basicCodecs = new ArrayList<>();
  private List<SSZListAccessor> listAccessors = new ArrayList<>();
  private List<Supplier<SSZContainerAccessor>> containerAccessors = new ArrayList<>();
  private List<Supplier<SSZUnionAccessor>> unionAccessors = new ArrayList<>();

  private boolean schemeBuilderExplicitAnnotations = true;
  private int schemeBuilderCacheSize = SSZ_SCHEMES_CACHE_CAPACITY;

  private SSZSchemeBuilder sszSchemeBuilder = null;

  private ObjectCreator objCreator = null;

  private AccessorResolver accessorResolverRegistry = null;

  private ExternalVarResolver externalVarResolver =
      v -> {
        throw new SSZSchemeException("Variable resolver not set. Can resolve var: " + v);
      };

  private TypeResolver typeResolver = null;

  private SSZVisitorHost visitorHost = null;

  private int sszHashBytesPerChunk = SSZ_HASH_BYTES_PER_CHUNK;
  private boolean incrementalHasher = true;

  private boolean inited = false;

  public SSZBuilder() {}

  private void checkAlreadyInitialized() {
    if (inited) {
      throw new RuntimeException("Already initialized!");
    }
  }

  public SSZBuilder withSchemeBuilderCacheSize(int schemeBuilderCacheSize) {
    checkAlreadyInitialized();
    this.schemeBuilderCacheSize = schemeBuilderCacheSize;
    return this;
  }

  public SSZBuilder withAccessorResolverRegistry(
      AccessorResolverRegistry accessorResolverRegistry) {
    checkAlreadyInitialized();
    this.accessorResolverRegistry = accessorResolverRegistry;
    return this;
  }

  /**
   * Final {@link SSZSerializer} will use user provided {@link SSZSchemeBuilder} for creating ssz
   * scheme ob objects when {@link SSZBuilder#buildSerializer()} called in the end
   *
   * @param schemeBuilder Scheme builder
   * @return semi-built {@link SSZBuilder}
   */
  public SSZBuilder withSSZSchemeBuilder(SSZSchemeBuilder schemeBuilder) {
    checkAlreadyInitialized();
    this.sszSchemeBuilder = schemeBuilder;
    return this;
  }

  /**
   * Final {@link SSZSerializer} will use user provided {@link CompositeObjCreator} for object
   * instantiation when {@link SSZBuilder#buildSerializer()} called in the end
   *
   * @param modelFactory Model factory
   * @return semi-built {@link SSZBuilder}
   */
  public SSZBuilder withObjectCreator(ObjectCreator modelFactory) {
    checkAlreadyInitialized();
    this.objCreator = modelFactory;
    return this;
  }

  /**
   * With default object creators and object creator which adds extraValue of type extraType to the
   * end of constructor parameters plus setter-based creator which calls constructor with extraType
   * before setters
   */
  public SSZBuilder withExtraObjectCreator(Class extraType, Object extraValue) {
    checkAlreadyInitialized();
    this.objCreator =
        new CompositeObjCreator(
            new ConstructorExtraObjCreator(extraType, extraValue),
            new SettersExtraObjCreator(extraType, extraValue),
            new ConstructorObjCreator(),
            new SettersObjCreator());
    return this;
  }

  public SSZBuilder withExternalVarResolver(Function<String, Object> externalVarResolver) {
    return withExternalVarResolver(ExternalVarResolver.fromFunction(externalVarResolver));
  }

  public SSZBuilder withExternalVarResolver(ExternalVarResolver externalVarResolver) {
    checkAlreadyInitialized();
    this.externalVarResolver = externalVarResolver;
    return this;
  }

  public SSZBuilder withTypeResolver(TypeResolver typeResolver) {
    checkAlreadyInitialized();
    this.typeResolver = typeResolver;
    return this;
  }

  public SSZBuilder withVisitorHost(SSZVisitorHost visitorHost) {
    checkAlreadyInitialized();
    this.visitorHost = visitorHost;
    return this;
  }

  /**
   * {@link SSZSerializer} built with {@link SSZAnnotationSchemeBuilder} which requires {@link SSZ}
   * annotation at each model field
   *
   * <p>Uses {@link CompositeObjCreator} which tries to create model instance by one constructor
   * with all input fields included. If such public constructor is not found, it tries to
   * instantiate object with empty constructor and set all fields directly or using standard setter.
   *
   * @return {@link SSZBuilder} without codecs
   */
  public SSZBuilder withExplicitAnnotations(boolean explicitAnnotations) {
    checkAlreadyInitialized();
    schemeBuilderExplicitAnnotations = explicitAnnotations;
    return this;
  }

  public SSZBuilder withIncrementalHasher(boolean incrementalHasher) {
    checkAlreadyInitialized();
    this.incrementalHasher = incrementalHasher;
    return this;
  }

  public SSZBuilder withSszHashBytesPerChunk(int sszHashBytesPerChunk) {
    checkAlreadyInitialized();
    this.sszHashBytesPerChunk = sszHashBytesPerChunk;
    return this;
  }

  public SSZBuilder addBasicCodecs(SSZBasicAccessor... codec) {
    checkAlreadyInitialized();
    basicCodecs.addAll(Arrays.asList(codec));
    return this;
  }

  public SSZBuilder addListAccessors(SSZListAccessor... accessors) {
    checkAlreadyInitialized();
    listAccessors.addAll(Arrays.asList(accessors));
    return this;
  }

  public SSZBuilder addContainerAccessors(SSZContainerAccessor... accessors) {
    checkAlreadyInitialized();
    for (SSZContainerAccessor accessor : accessors) {
      containerAccessors.add(() -> accessor);
    }
    return this;
  }

  /** Adds {@link SSZBasicAccessor}'s to handle almost all Java primitive types */
  public SSZBuilder addDefaultBasicCodecs() {
    checkAlreadyInitialized();
    basicCodecs.add(new UIntPrimitive());
    basicCodecs.add(new BytesPrimitive());
    basicCodecs.add(new BooleanPrimitive());
    basicCodecs.add(new StringPrimitive());
    basicCodecs.add(new UIntCodec());
    basicCodecs.add(new HashCodec());
    basicCodecs.add(new UnionNull());
    return this;
  }

  public SSZBuilder addDefaultListAccessors() {
    checkAlreadyInitialized();
    // XXX: Bitlist and Bitvector should be at top or BytesValue intercepts it
    listAccessors.add(new BitvectorAccessor());
    listAccessors.add(new BitlistAccessor());
    listAccessors.add(new ArrayAccessor());
    listAccessors.add(new ListAccessor());
    listAccessors.add(new ReadListAccessor());
    listAccessors.add(new BytesValueAccessor());
    return this;
  }

  public SSZBuilder addDefaultContainerAccessors() {
    checkAlreadyInitialized();
    containerAccessors.add(() -> new SimpleContainerAccessor(sszSchemeBuilder, objCreator));
    return this;
  }

  public SSZBuilder addDefaultUnionAccessors() {
    checkAlreadyInitialized();
    unionAccessors.add(() -> new GenericTypeSSZUnionAccessor());
    unionAccessors.add(() -> new SchemeSSZUnionAccessor(sszSchemeBuilder));
    return this;
  }

  /**
   * Finalizes build of {@link SSZSerializer} with builder
   *
   * @return {@link SSZSerializer}
   */
  public SSZSerializer buildSerializer() {
    buildCommon();
    return new SSZSerializer(visitorHost, typeResolver);
  }

  public SSZHasher buildHasher(Function<BytesValue, Hash32> hashFunction) {
    buildCommon();
    SSZVisitor<MerkleTrie, Object> hasherVisitor;
    if (incrementalHasher) {
      hasherVisitor =
          new SSZIncrementalHasher(buildSerializer(), hashFunction, sszHashBytesPerChunk);
    } else {
      hasherVisitor = new SSZSimpleHasher(buildSerializer(), hashFunction, sszHashBytesPerChunk);
    }
    return new SSZHasher(typeResolver, visitorHost, hasherVisitor);
  }

  void buildCommon() {
    if (sszSchemeBuilder == null) {
      SSZAnnotationSchemeBuilder sszAnnotationSchemeBuilder =
          new SSZAnnotationSchemeBuilder(schemeBuilderExplicitAnnotations);
      if (schemeBuilderCacheSize > 0) {
        sszAnnotationSchemeBuilder.withCache(schemeBuilderCacheSize);
      }
      sszSchemeBuilder = sszAnnotationSchemeBuilder;
    }
    if (objCreator == null) {
      objCreator = new CompositeObjCreator(new ConstructorObjCreator(), new SettersObjCreator());
    }
    if (basicCodecs.isEmpty()) {
      addDefaultBasicCodecs();
    }
    if (listAccessors.isEmpty()) {
      addDefaultListAccessors();
    }
    if (containerAccessors.isEmpty()) {
      addDefaultContainerAccessors();
    }
    if (unionAccessors.isEmpty()) {
      addDefaultUnionAccessors();
    }

    if (accessorResolverRegistry == null) {
      accessorResolverRegistry =
          new AccessorResolverRegistry()
              .withBasicAccessors(basicCodecs)
              .withListAccessors(listAccessors)
              .withContainerAccessors(
                  containerAccessors.stream().map(Supplier::get).collect(Collectors.toList()))
              .withUnionAccessors(
                  unionAccessors.stream().map(Supplier::get).collect(Collectors.toList()));
    }

    if (typeResolver == null) {
      typeResolver = new SimpleTypeResolver(accessorResolverRegistry, externalVarResolver);
    }

    if (visitorHost == null) {
      visitorHost = new SSZVisitorHost();
    }

    inited = true;
  }

  TypeResolver getTypeResolver() {
    buildCommon();
    return typeResolver;
  }

  AccessorResolver getAccessorResolver() {
    buildCommon();
    return accessorResolverRegistry;
  }
}
