package randoop.input.toradocu;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import randoop.condition.Condition;
import randoop.condition.ConditionCollection;
import randoop.reflection.TypeNames;
import randoop.types.ClassOrInterfaceType;

/**
 * Created by bjkeller on 12/19/16.
 */
public class ToradocuConditionCollection implements ConditionCollection {

  private final Map<AccessibleObject, List<Condition>> conditionMap;
  private final Map<AccessibleObject, Map<Condition, ClassOrInterfaceType>> exceptionMap;

  private ToradocuConditionCollection(
      Map<AccessibleObject, List<Condition>> conditionMap,
      Map<AccessibleObject, Map<Condition, ClassOrInterfaceType>> exceptionMap) {
    this.conditionMap = conditionMap;
    this.exceptionMap = exceptionMap;
  }

  /**
   * Creates a {@link ConditionCollection} from Toradocu generated JSON file and condition classes.
   *
   * @param filename  the non-null {@code File} object for the JSON file
   * @return  the collection of conditions for the methods in the JSON file
   */
  public static ToradocuConditionCollection createToradocuConditions(File filename) {
    Map<AccessibleObject, List<Condition>> conditionMap = new LinkedHashMap<>();
    Map<AccessibleObject, Map<Condition, ClassOrInterfaceType>> exceptionMap =
        new LinkedHashMap<>();
    List<DocumentedMethod> methodList = read(filename);
    for (int methodIndex = 0; methodIndex < methodList.size(); methodIndex++) {
      DocumentedMethod method = methodList.get(methodIndex);
      Class<?> conditionClass = getConditionClass(method);
      Class<?> declaringClass = getClass(method.getContainingClass());
      AccessibleObject subject = getCallableObject(declaringClass, method);

      assert conditionMap.get(subject) == null : "do not visit a method more than once";

      List<Tag> paramTagList = new ArrayList<Tag>(method.paramTags());
      List<Condition> conditionList = new ArrayList<>();
      for (int tagIndex = 0; tagIndex < paramTagList.size(); tagIndex++) {
        Tag paramTag = paramTagList.get(tagIndex);
        String methodName = buildConditionMethodName(paramTag, tagIndex, methodIndex);
        Method conditionMethod = getConditionMethod(conditionClass, methodName, paramTag);
        if (conditionMethod != null) {
          conditionList.add(new ToradocuCondition(paramTag, conditionMethod));
        }
      }
      conditionMap.put(subject, conditionList);

      Map<Condition, ClassOrInterfaceType> throwsMap = new LinkedHashMap<>();
      List<Tag> throwsTagList = new ArrayList<Tag>(method.throwsTags());
      for (int tagIndex = 0; tagIndex < throwsTagList.size(); tagIndex++) {
        Tag throwsTag = throwsTagList.get(tagIndex);
        String methodName = buildConditionMethodName(throwsTag, tagIndex, methodIndex);
        Method conditionMethod = getConditionMethod(conditionClass, methodName, throwsTag);
        if (conditionMethod != null) {
          ClassOrInterfaceType exceptionType = getType(((ThrowsTag) throwsTag).exception());
          throwsMap.put(new ToradocuCondition(throwsTag, conditionMethod), exceptionType);
        }
      }
      exceptionMap.put(subject, throwsMap);
    }
    return new ToradocuConditionCollection(conditionMap, exceptionMap);
  }

  public List<Condition> getPreconditions(AccessibleObject member) {
    return conditionMap.get(member);
  }

  public Map<Condition, ClassOrInterfaceType> getThrowsConditions(AccessibleObject member) {
    return exceptionMap.get(member);
  }

  /**
   * Returns the {@code AccessibleObject} reference to the {@code java.lang.reflect.Method} or
   * {@code java.lang.reflect.Constructor} for the given {@link DocumentedMethod}.
   *
   * @param declaringClass  the declaring class of the member
   * @param documentedMethod the Toradocu tagged method or constructor
   * @return the reflection object for the given method or constructor
   */
  private static AccessibleObject getCallableObject(
      Class<?> declaringClass, DocumentedMethod documentedMethod) {
    AccessibleObject subject;
    Class<?>[] parameterTypes = getSubjectMethodParameters(documentedMethod);
    try {
      if (documentedMethod.isConstructor()) {
        subject = declaringClass.getConstructor(parameterTypes);
      } else {
        String methodName = documentedMethod.getName();
        subject = declaringClass.getMethod(methodName, parameterTypes);
      }
    } catch (NoSuchMethodException e) {
      throw new IllegalArgumentException(
          "Unable to find subject method for Torudocu method (" + documentedMethod + ")");
    }
    return subject;
  }

  /**
   * Get the parameters for the subject method of the {@link DocumentedMethod} in the form
   * required to retrieve the reflection method object.
   *
   * @param documentedMethod  the method with Toradocu tags
   * @return the {@code Class<?>[]} array for the method parameters
   */
  private static Class<?>[] getSubjectMethodParameters(DocumentedMethod documentedMethod) {
    Class<?>[] parameters = new Class<?>[documentedMethod.getParameters().size()];
    for (int i = 0; i < documentedMethod.getParameters().size(); i++) {
      Parameter parameter = documentedMethod.getParameters().get(i);
      parameters[i] = getClass(parameter.getType());
    }
    return parameters;
  }

  /**
   * Gets the {@code java.lang.reflect.Method} for the condition method with the given name.
   * A condition method only exists if the corresponding tag has a condition.
   *
   * @param conditionClass  the enclosing class of the condition method
   * @param methodName  the name of the condition method
   * @param tag  the tag to which the condition belongs
   * @return the reflective method object if the method has a condition
   */
  private static Method getConditionMethod(Class<?> conditionClass, String methodName, Tag tag) {
    Method conditionMethod = null;
    String condition = tag.getCondition();
    if (condition != null && !condition.isEmpty()) {
      @SuppressWarnings("rawtypes")
      Class[] parameters = createConditionMethodParameters();
      try {
        conditionMethod = conditionClass.getMethod(methodName, parameters);
      } catch (NoSuchMethodException e) {
        throw new IllegalArgumentException(
            "Unable to find Toradocu condition method "
                + methodName
                + " in class "
                + conditionClass.getName());
      }
    }
    return conditionMethod;
  }

  /**
   * Returns the {@code Class<?>} for the given {@link randoop.input.toradocu.Type}.
   *
   * @param type  the type from the Toradocu input
   * @return the {@code Class<?>} if type represents a valid type name
   * @throws IllegalArgumentException if the type cannot be loaded
   */
  private static Class<?> getClass(randoop.input.toradocu.Type type) {
    try {
      return TypeNames.getTypeForName(type.getQualifiedName());
    } catch (ClassNotFoundException e) {
      throw new IllegalArgumentException("Unable to load type for Toradocu input (" + type + ")");
    }
  }

  /**
   * Returns the {@link ClassOrInterfaceType} for the given {@link randoop.input.toradocu.Type}.
   *
   * @param type  the type from the Toradocu input
   * @return the {@link ClassOrInterfaceType} for type
   */
  private static ClassOrInterfaceType getType(randoop.input.toradocu.Type type) {
    return ClassOrInterfaceType.forClass(getClass(type));
  }

  /**
   * Returns the {@code Class<?>} for the condition class of the given method.
   *
   * @param method  the {@link DocumentedMethod}
   * @return the {@code Class<?>} object for the condition class of the given method
   * @throws IllegalArgumentException if the condition class name cannot be loaded
   */
  private static Class<?> getConditionClass(DocumentedMethod method) {
    Class<?> conditionClass;
    String classname = buildConditionClassName(method);
    try {
      conditionClass = Class.forName(classname);
    } catch (ClassNotFoundException e) {
      throw new IllegalArgumentException(
          "Unable to load condition class for Toradocu input (" + classname + ")");
    }
    return conditionClass;
  }

  /**
   * Creates the array of {@code Class<?>} objects representing the type parameters of a condition
   * method.
   * By convention, this is {@code { Object, Object[] } }.
   *
   * @return the type parameters for a condition method
   */
  private static Class<?>[] createConditionMethodParameters() {
    @SuppressWarnings("rawtypes")
    Class<?>[] paramTypes = new Class[2];
    paramTypes[0] = Object.class;
    paramTypes[1] = (new Object[] {}).getClass();
    return paramTypes;
  }

  /**
   * Reads the {@link DocumentedMethod} objects from the given JSON file into a list, preserving
   * the order of the objects in the JSON file.
   *
   * @param filename  the name of the JSON file
   * @return the list of {@link DocumentedMethod} objects in the JSON file, preserving the order
   */
  @SuppressWarnings("unchecked")
  private static List<DocumentedMethod> read(File filename) {
    Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    Type typeToken = (new TypeToken<List<DocumentedMethod>>() {}).getType();
    List<DocumentedMethod> methods = new ArrayList<>();
    try (BufferedReader rdr = new BufferedReader(new FileReader(filename))) {
      methods.addAll(
          (List<DocumentedMethod>) gson.fromJson(rdr, typeToken)); //cast shouldn't be needed
    } catch (IOException e) {
      e.printStackTrace();
    }
    return methods;
  }

  /**
   * Constructs the condition class name using the convention with Toradocu.
   * The class name is the qualified name of the declaring class of the methods to which the
   * belong, except with periods replaced by the underscore character.
   *
   * @param method  the {@link DocumentedMethod} with enclosing class
   * @return the name of the enclosing class of the method with {@code '.'} replaced with {@code '_'}
   */
  private static String buildConditionClassName(DocumentedMethod method) {
    return method.getContainingClass().getQualifiedName().replace('.', '_');
  }

  /**
   * Constructs the condition method name using the convention with Toradocu.
   * Uses the index of the corresponding {@link DocumentedMethod} in the JSON list, and the
   * index of the tag in the method tag set flattened to a list.
   * The name is the letter {@code 'm'} followed by the method index, an underscore, and then the
   * tag type string followed by the tag index.
   * So, for instance, the method name for the first throws tag of the first method will be
   * {@code "m0_t0"}, while the name for the 2nd param tag of the 5th method would be {@code "m4_p1"}.
   *
   * @param tag  the tag for the condition, must be non-null
   * @param tagIndex  the position of the tag of the tag list
   * @param methodIndex  the position of the method in the JSON list
   * @return  the constructed method name
   */
  private static String buildConditionMethodName(Tag tag, int tagIndex, int methodIndex) {
    return "m" + methodIndex + "_" + tagKindString(tag) + tagIndex;
  }

  /**
   * Returns a string representing the type of the given tag.
   *
   * @param tag  the non-null tag
   * @return  the string {@code "p"} if {@code tag} is {@link Tag.Kind#PARAM}, or
   *           {@code "t"} if {@code tag} is {@link Tag.Kind#THROWS}
   * @throws IllegalStateException if {@code tag} is neither {@link Tag.Kind#PARAM} or {@link Tag.Kind#THROWS}.
   */
  private static String tagKindString(Tag tag) {
    switch (tag.getKind()) {
      case PARAM:
        return "p";
      case THROWS:
        return "t";
      default:
        throw new IllegalStateException("Tag class " + tag.getClass() + " not supported.");
    }
  }
}
