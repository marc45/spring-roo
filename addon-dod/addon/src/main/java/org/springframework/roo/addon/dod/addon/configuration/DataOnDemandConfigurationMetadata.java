package org.springframework.roo.addon.dod.addon.configuration;

import java.lang.reflect.Modifier;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.springframework.roo.addon.dod.annotations.RooDataOnDemandConfiguration;
import org.springframework.roo.classpath.PhysicalTypeIdentifierNamingUtils;
import org.springframework.roo.classpath.PhysicalTypeMetadata;
import org.springframework.roo.classpath.details.ConstructorMetadataBuilder;
import org.springframework.roo.classpath.details.FieldMetadataBuilder;
import org.springframework.roo.classpath.details.MethodMetadata;
import org.springframework.roo.classpath.details.MethodMetadataBuilder;
import org.springframework.roo.classpath.details.annotations.AnnotationMetadataBuilder;
import org.springframework.roo.classpath.itd.AbstractItdTypeDetailsProvidingMetadataItem;
import org.springframework.roo.classpath.itd.InvocableMemberBodyBuilder;
import org.springframework.roo.metadata.MetadataIdentificationUtils;
import org.springframework.roo.model.JavaSymbolName;
import org.springframework.roo.model.JavaType;
import org.springframework.roo.model.JpaJavaType;
import org.springframework.roo.model.SpringJavaType;
import org.springframework.roo.project.LogicalPath;

/**
 * Metadata for {@link RooDataOnDemandConfiguration}.
 *
 * @author Sergio Clares
 * @since 2.0
 */
public class DataOnDemandConfigurationMetadata extends AbstractItdTypeDetailsProvidingMetadataItem {

  private static final String PROVIDES_TYPE_STRING = DataOnDemandConfigurationMetadata.class
      .getName();
  private static final String PROVIDES_TYPE = MetadataIdentificationUtils
      .create(PROVIDES_TYPE_STRING);
  private static final String ENTITY_MANAGER_FIELD_NAME = "entityManager";

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

  /**
   * Constructor
   *
   * @param identifier
   * @param aspectName
   * @param governorPhysicalTypeMetadata
   * @param dataOnDemandTypes
   */
  public DataOnDemandConfigurationMetadata(final String identifier, final JavaType aspectName,
      final PhysicalTypeMetadata governorPhysicalTypeMetadata, final Set<JavaType> dataOnDemandTypes) {
    super(identifier, aspectName, governorPhysicalTypeMetadata);
    Validate.isTrue(isValid(identifier),
        "Metadata identification string '%s' does not appear to be a valid", identifier);

    // Add EntityManager field
    ensureGovernorHasField(getEntityManagerField());

    // Add constructor
    ensureGovernorHasConstructor(getConstructor());

    // Add @TestConfiguration
    ensureGovernorIsAnnotated(new AnnotationMetadataBuilder(SpringJavaType.TEST_CONFIGURATION));

    // Add DoD bean creation method
    for (JavaType dodType : dataOnDemandTypes) {
      ensureGovernorHasMethod(new MethodMetadataBuilder(getDodTypeBeanCreationMethod(dodType)));
    }

    // Build the ITD
    itdTypeDetails = builder.build();
  }

  /**
   * Builds and returns a class constructor which injects
   * {@link EntityManager} field.
   * 
   * @return a ConstructorMetadataBuilder to add to ITD.
   */
  private ConstructorMetadataBuilder getConstructor() {

    // Create instance and add parameters
    ConstructorMetadataBuilder constructorBuilder = new ConstructorMetadataBuilder(this.getId());
    constructorBuilder.addParameter(ENTITY_MANAGER_FIELD_NAME, JpaJavaType.ENTITY_MANAGER);

    // Add body
    InvocableMemberBodyBuilder bodyBuilder = new InvocableMemberBodyBuilder();
    bodyBuilder.appendFormalLine("this.%1$s = %1$s;", ENTITY_MANAGER_FIELD_NAME);
    constructorBuilder.setBodyBuilder(bodyBuilder);
    constructorBuilder.setModifier(Modifier.PUBLIC);

    return constructorBuilder;
  }

  /**
   * Builds and returns an {@link EntityManager} field type in the ITD.
   * 
   * @return the FieldMetadataBuilder.
   */
  private FieldMetadataBuilder getEntityManagerField() {
    FieldMetadataBuilder fieldBuilder =
        new FieldMetadataBuilder(this.getId(), Modifier.PRIVATE, new JavaSymbolName(
            ENTITY_MANAGER_FIELD_NAME), JpaJavaType.ENTITY_MANAGER, null);
    return fieldBuilder;
  }

  /**
   * Builds and returns a method used to instance a DataOnDemand class using
   * {@link EntityManager} and `@Bean` annotation.
   * 
   * @param dodType
   *            the class to inject in the Spring context.
   * @return the MethodMetadata to add to ITD.
   */
  private MethodMetadata getDodTypeBeanCreationMethod(JavaType dodType) {

    // Define methodName
    final JavaSymbolName methodName =
        new JavaSymbolName(StringUtils.uncapitalize(dodType.getSimpleTypeName()));

    // Check if method exists
    MethodMetadata existingMethod = getGovernorMethod(methodName);
    if (existingMethod != null) {
      return existingMethod;
    }

    // Add body
    InvocableMemberBodyBuilder bodyBuilder = new InvocableMemberBodyBuilder();
    bodyBuilder.appendFormalLine("return new %s(%s);", getNameOfJavaType(dodType),
        ENTITY_MANAGER_FIELD_NAME);

    // Create method
    MethodMetadataBuilder method =
        new MethodMetadataBuilder(this.getId(), Modifier.PUBLIC, methodName, dodType, bodyBuilder);

    // Add annotation
    method.addAnnotation(new AnnotationMetadataBuilder(SpringJavaType.BEAN));

    return method.build();
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
