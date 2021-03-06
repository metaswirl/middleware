package de.tu_berlin.kbs.reflect;

/**
 * Methods labeled with InvokeThis annotation will be invoked automatically.
 */
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(value = ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface InvokeThis {

}
