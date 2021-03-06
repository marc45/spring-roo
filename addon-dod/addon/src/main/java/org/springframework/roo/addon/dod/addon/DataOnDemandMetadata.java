package org.springframework.roo.addon.dod.addon;

import static org.springframework.roo.model.JdkJavaType.LIST;
import static org.springframework.roo.model.JdkJavaType.RANDOM;
import static org.springframework.roo.model.JdkJavaType.SECURE_RANDOM;
import static org.springframework.roo.model.SpringJavaType.COMPONENT;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.springframework.roo.addon.dod.addon.entity.factories.EntityFactoryMetadata;
import org.springframework.roo.addon.dod.annotations.RooDataOnDemand;
import org.springframework.roo.classpath.PhysicalTypeIdentifierNamingUtils;
import org.springframework.roo.classpath.PhysicalTypeMetadata;
import org.springframework.roo.classpath.details.ConstructorMetadataBuilder;
import org.springframework.roo.classpath.details.FieldMetadata;
import org.springframework.roo.classpath.details.FieldMetadataBuilder;
import org.springframework.roo.classpath.details.MethodMetadata;
import org.springframework.roo.classpath.details.MethodMetadataBuilder;
import org.springframework.roo.classpath.details.annotations.AnnotatedJavaType;
import org.springframework.roo.classpath.details.annotations.AnnotationMetadata;
import org.springframework.roo.classpath.details.annotations.AnnotationMetadataBuilder;
import org.springframework.roo.classpath.itd.AbstractItdTypeDetailsProvidingMetadataItem;
import org.springframework.roo.classpath.itd.InvocableMemberBodyBuilder;
import org.springframework.roo.classpath.layers.MemberTypeAdditions;
import org.springframework.roo.metadata.MetadataIdentificationUtils;
import org.springframework.roo.model.DataType;
import org.springframework.roo.model.JavaSymbolName;
import org.springframework.roo.model.JavaType;
import org.springframework.roo.model.JdkJavaType;
import org.springframework.roo.model.JpaJavaType;
import org.springframework.roo.model.Jsr303JavaType;
import org.springframework.roo.project.LogicalPath;

/**
 * Metadata for {@link RooDataOnDemand}.
 *
 * @author Ben Alex
 * @author Stefan Schmidt
 * @author Alan Stewart
 * @author Greg Turnquist
 * @author Andrew Swan
 * @since 1.0
 */
public class DataOnDemandMetadata extends AbstractItdTypeDetailsProvidingMetadataItem {

  private static final String INDEX_VAR = "index";
  private static final JavaSymbolName INDEX_SYMBOL = new JavaSymbolName(INDEX_VAR);
  private static final String OBJ_VAR = "obj";
  private static final String PROVIDES_TYPE_STRING = DataOnDemandMetadata.class.getName();
  private static final String PROVIDES_TYPE = MetadataIdentificationUtils
      .create(PROVIDES_TYPE_STRING);
  private static final String ENTITY_MANAGER_VAR = "entityManager";
  private static final String SIZE_VAR = "size";
  private static final String FACTORY_VAR = "factory";

  public static String createIdentifier(final JavaType javaType, final LogicalPath path) {
    return PhysicalTypeIdentifierNamingUtils.createIdentifier(PROVIDES_TYPE_STRING, javaType, path);
  }

  public static JavaType getJavaType(final String metadataIdentificationString) {
    return PhysicalTypeIdentifierNamingUtils.getJavaType(PROVIDES_TYPE_STRING,
        metadataIdentificationString);
  }

  public static String getMetadataIdentiferType() {
    return PROVIDES_TYPE;
  }

  public static LogicalPath getPath(final String metadataIdentificationString) {
    return PhysicalTypeIdentifierNamingUtils.getPath(PROVIDES_TYPE_STRING,
        metadataIdentificationString);
  }

  public static boolean isValid(final String metadataIdentificationString) {
    return PhysicalTypeIdentifierNamingUtils.isValid(PROVIDES_TYPE_STRING,
        metadataIdentificationString);
  }

  private JavaSymbolName dataFieldName;
  private EmbeddedIdHolder embeddedIdHolder;
  private JavaType entity;
  private MemberTypeAdditions findMethod;
  private MethodMetadata modifyMethod;
  private MethodMetadata newTransientEntityMethod;
  private MethodMetadata randomPersistentEntityMethod;
  private JavaSymbolName rndFieldName;
  private EntityFactoryMetadata entityFactoryMetadata;
  private MethodMetadata specificEntityMethod;
  private FieldMetadata sizeField;
  private JavaSymbolName sizeAccesorName;

  /**
   * Constructor
   *
   * @param identifier
   * @param aspectName
   * @param governorPhysicalTypeMetadata
   * @param annotationValues
   * @param identifierAccessor
   * @param findMethod
   * @param findEntriesMethod
   * @param persistMethod
   * @param flushMethod
   * @param locatedFields
   * @param entity
   * @param embeddedIdHolder
   * @param embeddedHolders
   * @param repository
  * @param dataOnDemandClasses
   */
  public DataOnDemandMetadata(final String identifier, final JavaType aspectName,
      final PhysicalTypeMetadata governorPhysicalTypeMetadata,
      final DataOnDemandAnnotationValues annotationValues, final MemberTypeAdditions findMethod,
      final MemberTypeAdditions flushMethod, final EntityFactoryMetadata entityFactoryMetadata) {
    super(identifier, aspectName, governorPhysicalTypeMetadata);
    Validate.isTrue(isValid(identifier),
        "Metadata identification string '%s' does not appear to be a valid", identifier);
    Validate.notNull(annotationValues, "Annotation values required");

    if (!isValid()) {
      return;
    }

    if (findMethod == null) {
      valid = false;
      return;
    }

    this.entityFactoryMetadata = entityFactoryMetadata;
    this.findMethod = findMethod;
    this.entity = annotationValues.getEntity();

    // Add random field
    ensureGovernorHasField(getRndField());

    // Add data field
    ensureGovernorHasField(getDataField());

    // Add repository field
    ensureGovernorHasField(getEntityManagerField());

    // Add size field
    this.sizeField = getSizeField();
    ensureGovernorHasField(new FieldMetadataBuilder(this.sizeField));
    this.sizeAccesorName = getAccessorMethod(this.sizeField).getMethodName();

    // Add EntityFactory field related to this entity
    ensureGovernorHasField(getEntityFactoryField());

    // Add constructors
    ensureGovernorHasConstructor(getConstructorWithEntityManager());
    ensureGovernorHasConstructor(getConstructorWithEntityManagerAndSize());

    // Add newRandomTransient method
    setNewRandomTransientEntityMethod();

    // Add getSpecificEntity method
    setGetSpecificEntityMethod();

    // Add getRandomEntity method
    setGetRandomPersistentEntityMethod();

    // Add init method
    builder.addMethod(getInitMethod(annotationValues.getQuantity(), flushMethod));

    itdTypeDetails = builder.build();
  }

  /**
   * Creates an EntityFactory field related to this entity.
   *
   * @return {@link FieldMetadataBuilder} for building field into ITD.
   */
  private FieldMetadataBuilder getEntityFactoryField() {

    // Create field
    FieldMetadataBuilder entityFactoryField =
        new FieldMetadataBuilder(getId(), Modifier.PRIVATE,
            new ArrayList<AnnotationMetadataBuilder>(), new JavaSymbolName(FACTORY_VAR),
            this.entityFactoryMetadata.getGovernorType());

    entityFactoryField.setFieldInitializer(String.format("new %s()",
        getNameOfJavaType(this.entityFactoryMetadata.getGovernorType())));

    return entityFactoryField;
  }

  /**
   * Builds constructor with EntityManager and size arguments.
   *
   * @return {@link ConstructorMetadataBuilder} for adding constructor to ITD
   */
  private ConstructorMetadataBuilder getConstructorWithEntityManagerAndSize() {
    ConstructorMetadataBuilder constructorBuilder = new ConstructorMetadataBuilder(getId());
    InvocableMemberBodyBuilder bodyBuilder = new InvocableMemberBodyBuilder();
    constructorBuilder.addParameter(ENTITY_MANAGER_VAR, JpaJavaType.ENTITY_MANAGER);
    constructorBuilder.addParameter(SIZE_VAR, JavaType.INT_PRIMITIVE);
    bodyBuilder.appendFormalLine(String.format("this.%1$s = %1$s;", ENTITY_MANAGER_VAR));
    bodyBuilder.appendFormalLine(String.format("this.%1$s = %1$s;", SIZE_VAR));

    constructorBuilder.setModifier(Modifier.PUBLIC);
    constructorBuilder.setBodyBuilder(bodyBuilder);

    return constructorBuilder;
  }

  /**
   * Creates size field.
   *
   * @return {@link FieldMetadataBuilder} for building field into ITD.
   */
  private FieldMetadata getSizeField() {

    // Create field
    FieldMetadataBuilder sizeField =
        new FieldMetadataBuilder(getId(), Modifier.PRIVATE,
            new ArrayList<AnnotationMetadataBuilder>(), new JavaSymbolName(SIZE_VAR),
            JavaType.INT_PRIMITIVE);

    return sizeField.build();
  }

  /**
   * Creates EntityManager field.
   *
   * @return {@link FieldMetadataBuilder} for building field into ITD.
   */
  private FieldMetadataBuilder getEntityManagerField() {

    // Create field
    FieldMetadataBuilder entityManagerField =
        new FieldMetadataBuilder(getId(), Modifier.PRIVATE,
            new ArrayList<AnnotationMetadataBuilder>(), new JavaSymbolName(ENTITY_MANAGER_VAR),
            JpaJavaType.ENTITY_MANAGER);

    return entityManagerField;
  }

  /**
   * Builds constructor with EntityManager argument.
   *
   * @return ConstructorMetadataBuilder for adding constructor to ITD
   */
  private ConstructorMetadataBuilder getConstructorWithEntityManager() {
    ConstructorMetadataBuilder constructorBuilder = new ConstructorMetadataBuilder(getId());
    InvocableMemberBodyBuilder bodyBuilder = new InvocableMemberBodyBuilder();
    constructorBuilder.addParameter(ENTITY_MANAGER_VAR, JpaJavaType.ENTITY_MANAGER);

    // this(entityManager, 10);
    bodyBuilder.appendFormalLine(String.format("this(%1$s, 10);", ENTITY_MANAGER_VAR));

    constructorBuilder.setModifier(Modifier.PUBLIC);
    constructorBuilder.setBodyBuilder(bodyBuilder);

    return constructorBuilder;
  }

  /**
   * Adds the @org.springframework.stereotype.Component annotation to the
   * type, unless it already exists.
   *
   * @return the annotation is already exists or will be created, or null if
   *         it will not be created (required)
   */
  public AnnotationMetadata getComponentAnnotation() {
    if (governorTypeDetails.getAnnotation(COMPONENT) != null) {
      return null;
    }
    final AnnotationMetadataBuilder annotationBuilder = new AnnotationMetadataBuilder(COMPONENT);
    return annotationBuilder.build();
  }

  /**
   * @return the "data" field to use, which is either provided by the user or
   *         produced on demand (never returns null)
   */
  private FieldMetadataBuilder getDataField() {
    final List<JavaType> parameterTypes = Arrays.asList(entity);
    final JavaType listType =
        new JavaType(LIST.getFullyQualifiedTypeName(), 0, DataType.TYPE, null, parameterTypes);

    int index = -1;
    while (true) {
      // Compute the required field name
      index++;

      // The type parameters to be used by the field type
      final JavaSymbolName fieldName = new JavaSymbolName("data" + StringUtils.repeat("_", index));
      dataFieldName = fieldName;
      final FieldMetadata candidate = governorTypeDetails.getField(fieldName);
      if (candidate != null) {
        // Verify if candidate is suitable
        if (!Modifier.isPrivate(candidate.getModifier())) {
          // Candidate is not private, so we might run into naming
          // clashes if someone subclasses this (therefore go onto the
          // next possible name)
          continue;
        }

        if (!candidate.getFieldType().equals(listType)) {
          // Candidate isn't a java.util.List<theEntity>, so it isn't
          // suitable
          // The equals method also verifies type params are present
          continue;
        }

        // If we got this far, we found a valid candidate
        // We don't check if there is a corresponding initializer, but
        // we assume the user knows what they're doing and have made one
        return new FieldMetadataBuilder(candidate);
      }

      // Candidate not found, so let's create one
      return new FieldMetadataBuilder(getId(), Modifier.PRIVATE,
          new ArrayList<AnnotationMetadataBuilder>(), fieldName, listType);
    }
  }

  private JavaSymbolName getDataFieldName() {
    return dataFieldName;
  }

  public JavaType getEntityType() {
    return entity;
  }

  /**
   * Returns the DoD type's "void init()" method (existing or generated)
   *
   * @param persistMethod
   *            (required)
   * @param flushMethod
   *            (required)
   * @return never `null`
   */
  private MethodMetadataBuilder getInitMethod(final int quantity,
      final MemberTypeAdditions flushMethod) {
    // Method definition to find or build
    final JavaSymbolName methodName = new JavaSymbolName("init");
    final JavaType[] parameterTypes = {};
    final List<JavaSymbolName> parameterNames = Collections.<JavaSymbolName>emptyList();
    final JavaType returnType = JavaType.VOID_PRIMITIVE;

    // Locate user-defined method
    final MethodMetadata userMethod = getGovernorMethod(methodName, parameterTypes);
    if (userMethod != null) {
      Validate.isTrue(userMethod.getReturnType().equals(returnType),
          "Method '%s' on '%s' must return '%s'", methodName, destination,
          returnType.getNameIncludingTypeParameters());
      return new MethodMetadataBuilder(userMethod);
    }

    // Create the method body
    final InvocableMemberBodyBuilder bodyBuilder = new InvocableMemberBodyBuilder();
    final String dataField = getDataFieldName().getSymbolName();
    bodyBuilder.appendFormalLine("int from = 0;");
    bodyBuilder.appendFormalLine("int to = " + quantity + ";");

    // CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    bodyBuilder.newLine();
    bodyBuilder.appendFormalLine("%s cb = %s.getCriteriaBuilder();",
        getNameOfJavaType(JpaJavaType.CRITERIA_BUILDER), ENTITY_MANAGER_VAR);

    // CriteriaQuery<Entity> cq = cb.createQuery(Entity.class);
    bodyBuilder.appendFormalLine("%1$s<%2$s> cq = cb.createQuery(%2$s.class);",
        getNameOfJavaType(JpaJavaType.CRITERIA_QUERY), getNameOfJavaType(this.entity));

    // Root<Entity> rootEntry = cq.from(Entity.class);
    bodyBuilder.appendFormalLine("%1$s<%2$s> rootEntry = cq.from(%2$s.class);",
        getNameOfJavaType(JpaJavaType.ROOT), getNameOfJavaType(this.entity));

    // CriteriaQuery<Owner> all = cq.select(rootEntry);
    bodyBuilder.appendFormalLine("%s<%s> all = cq.select(rootEntry);",
        getNameOfJavaType(JpaJavaType.CRITERIA_QUERY), getNameOfJavaType(this.entity));

    // TypedQuery<Owner> allQuery =
    bodyBuilder.appendFormalLine("%s<%s> allQuery = ", getNameOfJavaType(JpaJavaType.TYPED_QUERY),
        getNameOfJavaType(this.entity));

    //    entityManager.createQuery(all).setFirstResult(from).setMaxResults(to);
    bodyBuilder.indent();
    bodyBuilder.appendFormalLine("%s.createQuery(all).setFirstResult(from).setMaxResults(to);",
        ENTITY_MANAGER_VAR);

    // data = allQuery.getResultList();
    bodyBuilder.indentRemove();
    bodyBuilder.appendFormalLine("%s = allQuery.getResultList();", dataField);

    // if (data == null) {
    bodyBuilder.appendFormalLine("if (%s == null) {", dataField);

    //  throw new IllegalStateException(
    bodyBuilder.indent();
    bodyBuilder.appendFormalLine("throw new IllegalStateException(");

    //      "Find entries implementation for 'Owner' illegally returned null");
    bodyBuilder.indent();
    bodyBuilder.appendFormalLine(
        "\"Find entries implementation for '%s' illegally returned null\");",
        getNameOfJavaType(this.entity));

    // }
    bodyBuilder.indentRemove();
    bodyBuilder.indentRemove();
    bodyBuilder.appendFormalLine("}");

    // if (!data.isEmpty()) {
    bodyBuilder.appendFormalLine("if (!%s.isEmpty()) {", dataField);

    //  return;
    bodyBuilder.indent();
    bodyBuilder.appendFormalLine("return;");

    // }
    bodyBuilder.indentRemove();
    bodyBuilder.appendFormalLine("}");

    // data = new ArrayList<Entity>();
    bodyBuilder.newLine();
    bodyBuilder.appendFormalLine("%s = new %s<%s>();", dataField,
        getNameOfJavaType(JdkJavaType.ARRAY_LIST), getNameOfJavaType(this.entity));

    // for (int i = from; i < to; i++) {
    bodyBuilder.appendFormalLine("for (int i = from; i < to; i++) {");
    bodyBuilder.indent();

    // Entity obj = factory.create(i);
    bodyBuilder.appendFormalLine("%s %s = %s.%s(i);", getNameOfJavaType(this.entity), OBJ_VAR,
        FACTORY_VAR, this.entityFactoryMetadata.getCreateFactoryMethodName());

    // try {
    bodyBuilder.appendFormalLine("try {");
    bodyBuilder.indent();

    // entityManager.persist(obj);
    bodyBuilder.appendFormalLine("%s.persist(%s);", ENTITY_MANAGER_VAR, OBJ_VAR);

    // } catch (final ConstraintViolationException e) {
    bodyBuilder.indentRemove();
    bodyBuilder.appendFormalLine("} catch (final %s e) {",
        getNameOfJavaType(Jsr303JavaType.CONSTRAINT_VIOLATION_EXCEPTION));
    bodyBuilder.indent();

    // final StringBuilder msg = new StringBuilder();
    bodyBuilder.appendFormalLine("final StringBuilder msg = new StringBuilder();");

    // for (Iterator<ConstraintViolation<?>> iter = e.getConstraintViolations().iterator(); iter
    bodyBuilder.appendFormalLine(
        "for (%s<%s<?>> iter = e.getConstraintViolations().iterator(); iter",
        getNameOfJavaType(JdkJavaType.ITERATOR),
        getNameOfJavaType(Jsr303JavaType.CONSTRAINT_VIOLATION));


    //  .hasNext();) {
    bodyBuilder.indent();
    bodyBuilder.appendFormalLine("  .hasNext();) {");

    // final ConstraintViolation<?> cv = iter.next();
    bodyBuilder.appendFormalLine("final %s<?> cv = iter.next();",
        getNameOfJavaType(Jsr303JavaType.CONSTRAINT_VIOLATION));

    // msg.append("[").append(cv.getRootBean().getClass().getName()).append(".")
    bodyBuilder
        .appendFormalLine("msg.append(\"[\").append(cv.getRootBean().getClass().getName()).append(\".\")");

    // .append(cv.getPropertyPath()).append(": ").append(cv.getMessage())
    bodyBuilder
        .appendFormalLine(".append(cv.getPropertyPath()).append(\": \").append(cv.getMessage())");

    // .append(" (invalid value = ").append(cv.getInvalidValue()).append(")").append("]");
    bodyBuilder
        .appendFormalLine(".append(\" (invalid value = \").append(cv.getInvalidValue()).append(\")\").append(\"]\");");

    // }
    bodyBuilder.indentRemove();
    bodyBuilder.appendFormalLine("}");

    // throw new IllegalStateException(msg.toString(), e);
    bodyBuilder.appendFormalLine("throw new IllegalStateException(msg.toString(), e);");

    // }
    bodyBuilder.indentRemove();
    bodyBuilder.appendFormalLine("}");

    // entityManager.flush();
    bodyBuilder.appendFormalLine("%s.%s();", ENTITY_MANAGER_VAR, flushMethod.getMethodName());

    // data.add(obj);
    bodyBuilder.appendFormalLine("%s.add(%s);", dataField, OBJ_VAR);

    // }
    bodyBuilder.indentRemove();
    bodyBuilder.appendFormalLine("}");

    // Create the method
    return new MethodMetadataBuilder(getId(), Modifier.PUBLIC, methodName, returnType,
        AnnotatedJavaType.convertFromJavaTypes(parameterTypes), parameterNames, bodyBuilder);
  }

  /**
   * @return the "modifyEntity(Entity):boolean" method (never returns null)
   */
  public MethodMetadata getModifyMethod() {
    return modifyMethod;
  }

  /**
   * @return the "getNewTransientEntity(int index):Entity" method (never
   *         returns null)
   */
  public MethodMetadata getNewTransientEntityMethod() {
    return newTransientEntityMethod;
  }

  /**
   * @return the "getRandomEntity():Entity" method (never returns null)
   */
  public MethodMetadata getRandomPersistentEntityMethod() {
    return randomPersistentEntityMethod;
  }

  private FieldMetadataBuilder getRndField() {
    int index = -1;
    while (true) {
      // Compute the required field name
      index++;
      final JavaSymbolName fieldName = new JavaSymbolName("rnd" + StringUtils.repeat("_", index));
      this.rndFieldName = fieldName;
      final FieldMetadata candidate = governorTypeDetails.getField(fieldName);
      if (candidate != null) {
        // Verify if candidate is suitable
        if (!Modifier.isPrivate(candidate.getModifier())) {
          // Candidate is not private, so we might run into naming
          // clashes if someone subclasses this (therefore go onto the
          // next possible name)
          continue;
        }
        if (!candidate.getFieldType().equals(RANDOM)) {
          // Candidate isn't a java.util.Random, so it isn't suitable
          continue;
        }
        // If we got this far, we found a valid candidate
        // We don't check if there is a corresponding initializer, but
        // we assume the user knows what they're doing and have made one
        return new FieldMetadataBuilder(candidate);
      }

      // Candidate not found, so let's create one
      builder.getImportRegistrationResolver().addImports(RANDOM, SECURE_RANDOM);

      final FieldMetadataBuilder fieldBuilder = new FieldMetadataBuilder(getId());
      fieldBuilder.setModifier(Modifier.PRIVATE);
      fieldBuilder.setFieldName(fieldName);
      fieldBuilder.setFieldType(RANDOM);
      fieldBuilder.setFieldInitializer("new SecureRandom()");
      return fieldBuilder;
    }
  }

  /**
   * @return the "rnd" field to use, which is either provided by the user or
   *         produced on demand (never returns null)
   */
  private JavaSymbolName getRndFieldName() {
    return rndFieldName;
  }

  private void setNewRandomTransientEntityMethod() {
    // Method definition to find or build
    final JavaSymbolName methodName =
        new JavaSymbolName("getNewRandomTransient" + this.entity.getSimpleTypeName());

    // Locate user-defined method
    final MethodMetadata userMethod = getGovernorMethod(methodName);
    if (userMethod != null) {
      Validate.isTrue(userMethod.getReturnType().equals(entity),
          "Method '%s' on '%s' must return '%s'", methodName, this.destination,
          this.entity.getSimpleTypeName());
      this.newTransientEntityMethod = userMethod;
      return;
    }

    // Create method
    final InvocableMemberBodyBuilder bodyBuilder = new InvocableMemberBodyBuilder();

    // int randomIndex = getSize() + rnd.nextInt(Integer.MAX_VALUE - getSize());
    bodyBuilder.appendFormalLine(
        "int randomIndex = %1$s() + %2$s.nextInt(Integer.MAX_VALUE - %1$s());",
        this.sizeAccesorName, getRndFieldName());

    // return factory.create(randomIndex);
    bodyBuilder.appendFormalLine("return %s.%s(randomIndex);", FACTORY_VAR,
        this.entityFactoryMetadata.getCreateFactoryMethodName());

    final MethodMetadataBuilder methodBuilder =
        new MethodMetadataBuilder(getId(), Modifier.PUBLIC, methodName, this.entity, bodyBuilder);
    builder.addMethod(methodBuilder);
    this.newTransientEntityMethod = methodBuilder.build();
  }

  /**
   * @return the "getRandomEntity():Entity" method (never returns null)
   */
  private void setGetRandomPersistentEntityMethod() {
    // Method definition to find or build
    final JavaSymbolName methodName =
        new JavaSymbolName("getRandom" + this.entity.getSimpleTypeName());

    // Locate user-defined method
    final MethodMetadata userMethod = getGovernorMethod(methodName);
    if (userMethod != null) {
      Validate.isTrue(userMethod.getReturnType().equals(this.entity),
          "Method '%s' on '%s' must return '%s'", methodName, this.destination,
          this.entity.getSimpleTypeName());
      this.randomPersistentEntityMethod = userMethod;
      return;
    }

    // Create method
    final InvocableMemberBodyBuilder bodyBuilder = new InvocableMemberBodyBuilder();

    // init();
    bodyBuilder.appendFormalLine("init();");

    // return data.get(rnd.nextInt(data.size()));
    bodyBuilder.appendFormalLine("return %1$s.get(%2$s.nextInt(%1$s.size()));", getDataFieldName(),
        getRndFieldName());

    this.findMethod.copyAdditionsTo(builder, governorTypeDetails);

    final MethodMetadataBuilder methodBuilder =
        new MethodMetadataBuilder(getId(), Modifier.PUBLIC, methodName, this.entity, bodyBuilder);
    builder.addMethod(methodBuilder);
    this.randomPersistentEntityMethod = methodBuilder.build();
  }

  private void setGetSpecificEntityMethod() {

    // Method definition to find or build
    final JavaSymbolName methodName =
        new JavaSymbolName(EntityFactoryMetadata.SPECIFIC_METHOD_PREFIX
            + this.entity.getSimpleTypeName());
    final JavaType parameterType = JavaType.INT_PRIMITIVE;
    final List<JavaSymbolName> parameterNames = Arrays.asList(INDEX_SYMBOL);

    // Locate user-defined method
    final MethodMetadata userMethod = getGovernorMethod(methodName, parameterType);
    if (userMethod != null) {
      Validate.isTrue(userMethod.getReturnType().equals(this.entity),
          "Method '%s on '%s' must return '%s'", methodName, this.destination,
          this.entity.getSimpleTypeName());
      this.specificEntityMethod = userMethod;
      return;
    }

    // Create method
    final InvocableMemberBodyBuilder bodyBuilder = new InvocableMemberBodyBuilder();
    bodyBuilder.appendFormalLine("init();");
    bodyBuilder.appendFormalLine("if (" + INDEX_VAR + " < 0) {");
    bodyBuilder.indent();
    bodyBuilder.appendFormalLine(INDEX_VAR + " = 0;");
    bodyBuilder.indentRemove();
    bodyBuilder.appendFormalLine("}");
    bodyBuilder.appendFormalLine("if (" + INDEX_VAR + " > (" + getDataFieldName().getSymbolName()
        + ".size() - 1)) {");
    bodyBuilder.indent();
    bodyBuilder.appendFormalLine(INDEX_VAR + " = " + getDataFieldName().getSymbolName()
        + ".size() - 1;");
    bodyBuilder.indentRemove();
    bodyBuilder.appendFormalLine("}");
    bodyBuilder.appendFormalLine("return %s.get(%s);", getDataFieldName(), INDEX_VAR);

    findMethod.copyAdditionsTo(builder, governorTypeDetails);

    final MethodMetadataBuilder methodBuilder =
        new MethodMetadataBuilder(getId(), Modifier.PUBLIC, methodName, this.entity,
            AnnotatedJavaType.convertFromJavaTypes(parameterType), parameterNames, bodyBuilder);
    builder.addMethod(methodBuilder);
    this.specificEntityMethod = methodBuilder.build();
  }

  public boolean hasEmbeddedIdentifier() {
    return this.embeddedIdHolder != null;
  }

  /**
   * @return the "getSpecificEntity(int):Entity" method (never returns null)
   */
  public MethodMetadata getSpecificPersistentEntityMethod() {
    setGetSpecificEntityMethod();
    return specificEntityMethod;
  }

  @Override
  public String toString() {
    final ToStringBuilder builder = new ToStringBuilder(this);
    builder.append("identifier", getId());
    builder.append("valid", valid);
    builder.append("aspectName", aspectName);
    builder.append("destinationType", destination);
    builder.append("governor", governorPhysicalTypeMetadata.getId());
    builder.append("itdTypeDetails", itdTypeDetails);
    return builder.toString();
  }

}
