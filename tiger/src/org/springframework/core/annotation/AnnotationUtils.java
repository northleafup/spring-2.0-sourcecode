/*
 * Copyright 2002-2006 the original author or authors.
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

package org.springframework.core.annotation;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

import org.springframework.core.BridgeMethodResolver;

/**
 * General utility methods for working with annotations.
 *
 * @author Rod Johnson
 * @author Rob Harrop
 * @author Juergen Hoeller
 * @since 2.0
 */
public abstract class AnnotationUtils {

	/**
	 * Get all {@link Annotation Annotations} from the supplied {@link Method}.
	 * <p>Correctly handles bridge {@link Method Methods} generated by the compiler.
	 * @param method the method to look for annotations on
	 * @see org.springframework.core.BridgeMethodResolver#findBridgedMethod(Method)
	 */
	public static Annotation[] getAnnotations(Method method) {
		return BridgeMethodResolver.findBridgedMethod(method).getAnnotations();
	}

	/**
	 * Get a single {@link Annotation} of <code>annotationType</code> from the
	 * supplied {@link Method}.
	 * <p>Correctly handles bridge {@link Method Methods} generated by the compiler.
	 * @param method the method to look for annotations on
	 * @param annotationType the annotation class to look for
	 * @see org.springframework.core.BridgeMethodResolver#findBridgedMethod(Method)
	 */
	public static <A extends Annotation> A getAnnotation(Method method, Class<A> annotationType) {
		return BridgeMethodResolver.findBridgedMethod(method).getAnnotation(annotationType);
	}

	/**
	 * Get a single {@link Annotation} of <code>annotationType</code> from the
	 * supplied {@link Method}.
	 * <p>Annotations on methods are not inherited by default, so we need to handle
	 * this explicitly.
	 * @param method the method to look for annotations on
	 * @param annotationType the annotation class to look for
	 * @return the annotation of the given type found, or <code>null</code>
	 */
	public static <A extends Annotation> A findAnnotation(Method method, Class<A> annotationType) {
		if (!annotationType.isAnnotation()) {
			throw new IllegalArgumentException(annotationType + " is not an annotation");
		}
		A annotation = getAnnotation(method, annotationType);
		Class cl = method.getDeclaringClass();
		while (annotation == null) {
			cl = cl.getSuperclass();
			if (cl == null || cl.equals(Object.class)) {
				break;
			}
			try {
				method = cl.getDeclaredMethod(method.getName(), method.getParameterTypes());
				annotation = getAnnotation(method, annotationType);
			}
			catch (NoSuchMethodException ex) {
				// We're done...
			}
		}
		return annotation;
	}

}