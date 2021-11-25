package me.jamesj.lambdadeploy.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.jetbrains.annotations.NotNull;

/**
 * Created by @James on 24/11/2021
 * <p>
 * Used to denote a class as a lambda function, and the target lambda name
 *
 * @author James
 * @since 24/11/2021
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiFunction {

    @NotNull
    String value();

}
