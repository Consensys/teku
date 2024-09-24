/*
 * Copyright Consensys Software Inc., 2022
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

package tech.pegasys.teku.infrastructure.ssz;

import com.google.common.base.CaseFormat;
import com.google.common.base.Converter;
import com.google.errorprone.annotations.FormatMethod;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.CtNewConstructor;
import javassist.CtNewMethod;
import javassist.NotFoundException;
import tech.pegasys.teku.infrastructure.ssz.impl.AbstractSszImmutableContainer;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszContainerSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszContainerSchema.NamedSchema;

public class SszTypeGenerator<T extends SszContainer, S extends SszSchema<T>> {
  private static final Converter<String, String> LOWER_UNDERSCORE_TO_UPPER_CAMEL =
      CaseFormat.LOWER_UNDERSCORE.converterTo(CaseFormat.UPPER_CAMEL);

  public static String snakeCaseToUpperCamel(final String camelCase) {
    return LOWER_UNDERSCORE_TO_UPPER_CAMEL.convert(camelCase);
  }

  private final ClassPool classPool = ClassPool.getDefault();
  private final Class<T> typeInterface;
  private final Class<S> schemaInterface;
  private final NamedSchema<?>[] fields;
  private final String interfaceName;
  private final String typeImplClassName;
  private final String schemaClassName;

  public SszTypeGenerator(
      final Class<T> typeInterface,
      final Class<S> schemaInterface,
      final NamedSchema<?>... fields) {
    this.typeInterface = typeInterface;
    this.schemaInterface = schemaInterface;
    this.fields = fields;

    interfaceName = typeInterface.getCanonicalName();
    typeImplClassName = interfaceName + "Impl";
    schemaClassName = schemaInterface.getCanonicalName() + "Impl";
  }

  public S defineType()
      throws NotFoundException,
          CannotCompileException,
          InvocationTargetException,
          NoSuchMethodException,
          InstantiationException,
          IllegalAccessException {

    // try to reuse existing class
    final S existingSchemaClassInstance = getInstanceViaExistingClass();
    if (existingSchemaClassInstance != null) {
      return existingSchemaClassInstance;
    }

    // Define the two new classes we'll create so they can reference each other
    // Most of the class implementations come from the abstract super classes written in plain Java.

    final CtClass implClass =
        classPool.makeClass(typeImplClassName, asCtClass(AbstractSszImmutableContainer.class));
    final CtClass schemaClass =
        classPool.makeClass(schemaClassName, asCtClass(AbstractSszContainerSchema.class));
    defineTypeMethods(implClass);
    defineSchemaMethods(schemaClass);
    implClass.toClass(typeInterface);
    return createSchema(schemaClass);
  }

  @SuppressWarnings("unchecked")
  private S getInstanceViaExistingClass() {
    final Class<S> existingSchemaClass;
    try {
      existingSchemaClass = (Class<S>) Class.forName(schemaClassName);
      return existingSchemaClass.getDeclaredConstructor(List.class).newInstance(List.of(fields));
    } catch (NoSuchMethodException
        | InvocationTargetException
        | IllegalAccessException
        | InstantiationException e) {
      throw new RuntimeException(e);
    } catch (ClassNotFoundException ignored) {
      return null;
    }
  }

  private void defineTypeMethods(final CtClass implClass)
      throws CannotCompileException, NotFoundException, NoSuchMethodException {
    // Declare the class as an implementation of the specified interface
    implClass.addInterface(asCtClass(typeInterface));

    // Add a single constructor that accepts the schema we're defining and a backing tree
    addConstructor(
        implClass,
        """
                public %s(%s schema, tech.pegasys.teku.infrastructure.ssz.tree.TreeNode node) {
                  super($1, $2);
                }
                """,
        typeInterface.getSimpleName() + "Impl",
        schemaClassName);

    // For each field, generate a getter method for that field.
    for (int i = 0; i < fields.length; i++) {
      final NamedSchema<?> field = fields[i];
      final String methodName = "get" + snakeCaseToUpperCamel(field.getName());
      final Method declaredMethod = validateInterfaceMethod(methodName, field);
      addMethod(
          implClass,
          """
                  public %s %s() {
                    return (%s) getAny(%d);
                  }
                  """,
          declaredMethod.getReturnType().getCanonicalName(),
          methodName,
          declaredMethod.getReturnType().getCanonicalName(),
          i);
    }

    final String methodName = "getSchema";
    final Method declaredMethod;
    try {
      declaredMethod = typeInterface.getMethod(methodName);
    } catch (NoSuchMethodException e) {
      // interface doesn't care about this schema getter
      return;
    }
    addMethod(
        implClass,
        """
                public %s getSchema() {
                  return (%s) super.getSchema();
                }
                """,
        declaredMethod.getReturnType().getCanonicalName(),
        declaredMethod.getReturnType().getCanonicalName());

    //
    //    implClass.addMethod(CtNewMethod.make(
    //            Modifier.PUBLIC,
    //            asCtClass(declaredMethod.getReturnType()),
    //            methodName,
    //            null,
    //            null,
    //            String.format("{ return (%s) super.getSchema(); }",
    // declaredMethod.getReturnType().getCanonicalName()),
    //            implClass));

  }

  private Method validateInterfaceMethod(final String methodName, final NamedSchema<?> field)
      throws NoSuchMethodException {
    final Method declaredMethod = typeInterface.getMethod(methodName);
    if (!declaredMethod.getReturnType().equals(field.getTypeClass())) {
      throw new IllegalArgumentException(
          String.format(
              "Return type of method %s does not match field type %s",
              methodName, field.getTypeClass()));
    }
    return declaredMethod;
  }

  private void defineSchemaMethods(final CtClass schemaClass)
      throws CannotCompileException, NotFoundException, NoSuchMethodException {
    // Declare the schema class as an implementation of the schema interface
    schemaClass.addInterface(asCtClass(schemaInterface));

    // Add a constructor that accepts the list of NamedField defining the fields for this type
    addConstructor(
        schemaClass,
        """
                public %s(java.util.List fields) {
                  super(\"%s\", $1);
                }
                """,
        schemaClass.getSimpleName(),
        typeInterface.getSimpleName());

    // Define the createFromBackingNode abstract method to call the type class's constructor
    addCreateFromBackingNode(schemaClass);
    //    addMethod(
    //        schemaClass,
    //            """
    //                    public %s
    // createFromBackingNode(tech.pegasys.teku.infrastructure.ssz.tree.TreeNode node) {
    //                      return new %s(this, node);
    //                    }
    //                    """,
    //        interfaceName,
    //        typeImplClassName);

    // For each create method in the schema, add a create method implementation that instantiates
    // an instance of our new type class, passing the schema instance and a backing node tree
    // generated from the arguments.
    for (Method createMethod : schemaInterface.getDeclaredMethods()) {
      if (!createMethod.getName().equals("create")
          || !createMethod.getReturnType().equals(typeInterface)
          // We skip default methods so the schema can define additional default create methods
          // to make it easier to construct the object if required
          || createMethod.isDefault()) {
        continue;
      }
      // Validate parameter types
      Class<?>[] parameterTypes = createMethod.getParameterTypes();
      if (parameterTypes.length != fields.length) {
        throw new IllegalArgumentException(
            String.format(
                "Create params count %s differ from declared field count %s",
                parameterTypes.length, fields.length));
      }
      for (int i = 0; i < parameterTypes.length; i++) {
        if (!parameterTypes[i].equals(fields[i].getTypeClass())) {
          throw new IllegalArgumentException(
              String.format(
                  "Parameter type of method %s does not match field type %s",
                  createMethod.getName(), fields[i].getTypeClass().getCanonicalName()));
        }
      }
      final CtMethod method =
          new CtMethod(
              asCtClass(typeInterface),
              createMethod.getName(),
              asCtClasses(createMethod.getParameterTypes()),
              schemaClass);
      method.setBody(
          String.format(
              "return new %s(this, createTreeFromFieldValues(java.util.Arrays.asList($args)));",
              typeImplClassName));
      schemaClass.addMethod(method);
    }

    // For each field, define a getter for its schema.
    for (int i = 0; i < fields.length; i++) {
      final NamedSchema<?> field = fields[i];
      final String methodName = "get" + snakeCaseToUpperCamel(field.getName()) + "Schema";

      final Method declaredMethod;
      try {
        declaredMethod = schemaInterface.getMethod(methodName);
      } catch (NoSuchMethodException e) {
        // interface doesn't care about this schema getter
        continue;
      }
      // Validate that the return type matches the schema type
      if (!declaredMethod.getReturnType().isAssignableFrom(field.getSchema().getClass())) {
        throw new IllegalArgumentException(
            String.format(
                "Return type of method %s does not match schema type %s",
                methodName, field.getSchema().getClass()));
      }
      addMethod(
          schemaClass,
          """
                  public %s %s() {
                    return (%s) getChildSchema(%d);
                  }
                  """,
          declaredMethod.getReturnType().getCanonicalName(),
          methodName,
          declaredMethod.getReturnType().getCanonicalName(),
          i);
    }
  }

  @SuppressWarnings("unchecked")
  private S createSchema(final CtClass schemaClass)
      throws CannotCompileException,
          NoSuchMethodException,
          InvocationTargetException,
          InstantiationException,
          IllegalAccessException {
    // Actually creates an instance of the schema to return.
    // Instances of the type can be created from the schema create methods.
    // The implementation classes are never exposed.
    // TODO: Should validate that all methods on the interface is implemented
    return (S)
        schemaClass
            .toClass(typeInterface)
            .getDeclaredConstructor(List.class)
            .newInstance(List.of(fields));
  }

  @FormatMethod
  private void addConstructor(final CtClass ctClass, final String method, final Object... args)
      throws CannotCompileException {
    final String methodContent = String.format(method, args);
    System.out.println(methodContent);
    ctClass.addConstructor(CtNewConstructor.make(methodContent, ctClass));
  }

  @FormatMethod
  private void addMethod(final CtClass ctClass, final String method, final Object... args)
      throws CannotCompileException {
    final String methodContent = String.format(method, args);
    System.out.println(methodContent);
    ctClass.addMethod(CtMethod.make(methodContent, ctClass));
  }

  private void addCreateFromBackingNode(final CtClass ctClass)
      throws CannotCompileException, NotFoundException {

    ctClass.addMethod(
        CtNewMethod.make(
            Modifier.PUBLIC,
            asCtClass(SszContainer.class),
            "createFromBackingNode",
            new CtClass[] {classPool.get("tech.pegasys.teku.infrastructure.ssz.tree.TreeNode")},
            null,
            String.format("{ return new %s(this, $1); }", typeImplClassName),
            ctClass));
  }

  private CtClass asCtClass(final Class<?> clazz) throws NotFoundException {
    return classPool.get(clazz.getCanonicalName());
  }

  private CtClass[] asCtClasses(final Class<?>[] classes) throws NotFoundException {
    final CtClass[] ctClasses = new CtClass[classes.length];
    for (int i = 0; i < ctClasses.length; i++) {
      ctClasses[i] = asCtClass(classes[i]);
    }
    return ctClasses;
  }
}
