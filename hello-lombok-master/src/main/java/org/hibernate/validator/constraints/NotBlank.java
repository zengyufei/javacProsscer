package org.hibernate.validator.constraints;

/**
 * Created by admin on 2017/9/29.
 */
public @interface NotBlank {

	String message() default "";
}
