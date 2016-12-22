package randoop.condition;

/**
 * Represents a condition on an operation.
 * Kind of like a predicate, but with names changed to protect the innocent.
 */
public interface Condition {
  boolean check(Object[] values);
}
