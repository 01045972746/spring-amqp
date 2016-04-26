/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.amqp.rabbit.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Represents an argument used when declaring queues etc within a
 * {@code QueueBinding}.
 *
 * @author Gary Russell
 * @since 1.6
 *
 */
@Target({})
@Retention(RetentionPolicy.RUNTIME)
public @interface Argument {

	/**
	 * @return the argument name.
	 */
	String name();

	/**
	 * The argument value, an empty string is translated to {@code null} for example
	 * to represent a present header test for a headers exchange.
	 * @return the argument value.
	 */
	String value() default "";

	/**
	 * @return the type of the argument value.
	 */
	String type() default "java.lang.String";

}
