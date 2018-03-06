package onight.tfw.ojpa.api.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import lombok.Getter;
import lombok.Setter;

import org.apache.felix.ipojo.annotations.Stereotype;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Stereotype
public @interface StoreDAO {
	/**
	 * 定义结构体的主键
	 * 
	 * @return
	 */
	Class domain() default Object.class;

	String target() default "mysql";
	
	String key() default "";
	
	Class example() default Object.class;

	Class keyclass() default Object.class;

}
