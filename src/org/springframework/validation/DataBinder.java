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

package org.springframework.validation;

import java.beans.PropertyEditor;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.ConfigurablePropertyAccessor;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.PropertyAccessException;
import org.springframework.beans.PropertyAccessorUtils;
import org.springframework.beans.PropertyBatchUpdateException;
import org.springframework.beans.PropertyEditorRegistry;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.PropertyValues;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.PatternMatchUtils;
import org.springframework.util.StringUtils;

/**
 * Binder that allows for binding property values to a target object.
 * The binding process can be customized through specifying allowed fields,
 * required fields, and custom editors.
 *
 * <p>Note that there are potential security implications in failing to set
 * an array of allowed fields. In the case of HTTP form POST data for example,
 * malicious clients can attempt to subvert an application by supplying values
 * for fields or properties that do not exist on the form. In some cases this
 * could lead to illegal data being set on command objects <i>or their nested
 * objects</i>. For this reason, it is <b>highly recommended to specify the
 * {@link #setAllowedFields allowedFields} property</b> on the DataBinder.
 *
 * <p>The binding results can be examined via the Errors interface,
 * available as BindException instance. Missing field errors and property
 * access exceptions will be converted to FieldErrors, collected in the
 * Errors instance, with the following error codes:
 *
 * <ul>
 * <li>Missing field error: "required"
 * <li>Type mismatch error: "typeMismatch"
 * <li>Method invocation error: "methodInvocation"
 * </ul>
 *
 * <p>Custom validation errors can be added afterwards. You will typically
 * want to resolve such error codes into proper user-visible error messages;
 * this can be achieved through resolving each error via a MessageSource.
 * The list of message codes to try can be customized through the
 * MessageCodesResolver strategy. DefaultMessageCodesResolver's javadoc
 * gives details on the default resolution rules.
 *
 * <p>By default, binding errors are resolved through the binding error processor
 * for required binding errors and property access exceptions. You can override
 * those if needed, for example to generate different error codes.
 *
 * <p>This generic data binder can be used in any sort of environment.
 * It is heavily used by Spring's web MVC controllers, via the subclass
 * <code>org.springframework.web.bind.ServletRequestDataBinder</code>.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Rob Harrop
 * @see #setAllowedFields
 * @see #setRequiredFields
 * @see #registerCustomEditor
 * @see #setMessageCodesResolver
 * @see #setBindingErrorProcessor
 * @see #bind
 * @see #getBindingResult
 * @see DefaultMessageCodesResolver
 * @see DefaultBindingErrorProcessor
 * @see org.springframework.context.MessageSource
 * @see org.springframework.web.bind.ServletRequestDataBinder
 */
public class DataBinder implements PropertyEditorRegistry {

	/** Default object name used for binding: "target" */
	public static final String DEFAULT_OBJECT_NAME = "target";


	/**
	 * We'll create a lot of DataBinder instances: Let's use a static logger.
	 */
	protected static final Log logger = LogFactory.getLog(DataBinder.class);

	private final Object target;

	private final String objectName;

	private AbstractPropertyBindingResult bindingResult;

	private BindException bindException;

	private boolean ignoreUnknownFields = true;

	private boolean ignoreInvalidFields = false;

	private String[] allowedFields;

	private String[] disallowedFields;

	private String[] requiredFields;

	private BindingErrorProcessor bindingErrorProcessor = new DefaultBindingErrorProcessor();


	/**
	 * Create a new DataBinder instance, with default object name.
	 * @param target target object to bind onto
	 * @see #DEFAULT_OBJECT_NAME
	 */
	public DataBinder(Object target) {
		this.target = target;
		this.objectName = DEFAULT_OBJECT_NAME;
	}

	/**
	 * Create a new DataBinder instance.
	 * @param target target object to bind onto
	 * @param objectName name of the target object
	 */
	public DataBinder(Object target, String objectName) {
		this.target = target;
		this.objectName = objectName;
	}


	/**
	 * Return the wrapped target object.
	 */
	public Object getTarget() {
		return target;
	}

	/**
	 * Return the name of the bound object.
	 */
	public String getObjectName() {
		return objectName;
	}

	/**
	 * Initialize standard JavaBean property access for this DataBinder.
	 * <p>This is the default; an explicit call just leads to eager initialization.
	 */
	public void initBeanPropertyAccess() {
		Assert.isNull(this.bindingResult,
				"DataBinder is already initialized - call initBeanPropertyAccess before any other configuration methods");
		this.bindingResult = new BeanPropertyBindingResult(getTarget(), getObjectName());
	}

	/**
	 * Initialize direct field access for this DataBinder.
	 * <p>This is the default; an explicit call just leads to eager initialization.
	 */
	public void initDirectFieldAccess() {
		Assert.isNull(this.bindingResult,
				"DataBinder is already initialized - call initDirectFieldAccess before any other configuration methods");
		this.bindingResult = new DirectFieldBindingResult(getTarget(), getObjectName());
	}

	/**
	 * Return the internal BindingResult held by this DataBinder,
	 * as AbstractPropertyBindingResult.
	 */
	protected AbstractPropertyBindingResult getInternalBindingResult() {
		if (this.bindingResult == null) {
			initBeanPropertyAccess();
		}
		return this.bindingResult;
	}

	/**
	 * Return the underlying PropertyAccessor of this binder's BindingResult.
	 * To be used by binder subclasses that need property checks.
	 */
	protected ConfigurablePropertyAccessor getPropertyAccessor() {
		return getInternalBindingResult().getPropertyAccessor();
	}

	/**
	 * Return the BindingResult instance created by this DataBinder.
	 * This allows for convenient access to the binding results after
	 * a bind operation.
	 * @return the BindingResult instance, to be treated as BindingResult
	 * or as Errors instance (Errors is a super-interface of BindingResult)
	 * @see Errors
	 * @see #bind
	 */
	public BindingResult getBindingResult() {
		return getInternalBindingResult();
	}

	/**
	 * Return the Errors instance for this data binder.
	 * @return the Errors instance, to be treated as Errors or as BindException
	 * @deprecated in favor of <code>getBindingResult()</code>
	 * @see #getBindingResult()
	 */
	public BindException getErrors() {
		if (this.bindException == null) {
			this.bindException = new BindException(getBindingResult());
		}
		return this.bindException;
	}


	/**
	 * Set whether to ignore unknown fields, that is, whether to ignore bind
	 * parameters that do not have corresponding fields in the target object.
	 * <p>Default is "true". Turn this off to enforce that all bind parameters
	 * must have a matching field in the target object.
	 */
	public void setIgnoreUnknownFields(boolean ignoreUnknownFields) {
		this.ignoreUnknownFields = ignoreUnknownFields;
	}

	/**
	 * Return whether to ignore unknown fields.
	 */
	public boolean isIgnoreUnknownFields() {
		return ignoreUnknownFields;
	}

	/**
	 * Set whether to ignore invalid fields, that is, whether to ignore bind
	 * parameters that have corresponding fields in the target object which are
	 * not accessible (for example because of null values in the nested path).
	 * <p>Default is "false". Turn this on to ignore bind parameters for
	 * nested objects in non-existing parts of the target object graph.
	 */
	public void setIgnoreInvalidFields(boolean ignoreInvalidFields) {
		this.ignoreInvalidFields = ignoreInvalidFields;
	}

	/**
	 * Return whether to ignore invalid fields.
	 */
	public boolean isIgnoreInvalidFields() {
		return ignoreInvalidFields;
	}

	/**
	 * Register fields that should be allowed for binding. Default is all
	 * fields. Restrict this for example to avoid unwanted modifications
	 * by malicious users when binding HTTP request parameters.
	 * <p>Supports "xxx*", "*xxx" and "*xxx*" patterns. More sophisticated matching
	 * can be implemented by overriding the <code>isAllowed</code> method.
	 * <p>Alternatively, specify a list of <i>disallowed</i> fields.
	 * @param allowedFields array of field names
	 * @see #setDisallowedFields
	 * @see #isAllowed(String)
	 * @see org.springframework.web.bind.ServletRequestDataBinder
	 */
	public void setAllowedFields(String[] allowedFields) {
		this.allowedFields = PropertyAccessorUtils.canonicalPropertyNames(allowedFields);
	}

	/**
	 * Return the fields that should be allowed for binding.
	 * @return array of field names
	 */
	public String[] getAllowedFields() {
		return allowedFields;
	}

	/**
	 * Register fields that should <i>not</i> be allowed for binding. Default is none.
	 * Mark fields as disallowed for example to avoid unwanted modifications
	 * by malicious users when binding HTTP request parameters.
	 * <p>Supports "xxx*", "*xxx" and "*xxx*" patterns. More sophisticated matching
	 * can be implemented by overriding the <code>isAllowed</code> method.
	 * <p>Alternatively, specify a list of <i>allowed</i> fields.
	 * @param disallowedFields array of field names
	 * @see #setAllowedFields
	 * @see #isAllowed(String)
	 * @see org.springframework.web.bind.ServletRequestDataBinder
	 */
	public void setDisallowedFields(String[] disallowedFields) {
		this.disallowedFields = PropertyAccessorUtils.canonicalPropertyNames(disallowedFields);
	}

	/**
	 * Return the fields that should <i>not</i> be allowed for binding.
	 * @return array of field names
	 */
	public String[] getDisallowedFields() {
		return disallowedFields;
	}

	/**
	 * Register fields that are required for each binding process.
	 * <p>If one of the specified fields is not contained in the list of
	 * incoming property values, a corresponding "missing field" error
	 * will be created, with error code "required" (by the default
	 * binding error processor).
	 * @param requiredFields array of field names
	 * @see #setBindingErrorProcessor
	 * @see DefaultBindingErrorProcessor#MISSING_FIELD_ERROR_CODE
	 */
	public void setRequiredFields(String[] requiredFields) {
		this.requiredFields = PropertyAccessorUtils.canonicalPropertyNames(requiredFields);
		if (logger.isDebugEnabled()) {
			logger.debug("DataBinder requires binding of required fields [" +
					StringUtils.arrayToCommaDelimitedString(requiredFields) + "]");
		}
	}

	/**
	 * Return the fields that are required for each binding process.
	 * @return array of field names
	 */
	public String[] getRequiredFields() {
		return requiredFields;
	}

	/**
	 * Set whether to extract the old field value when applying a
	 * property editor to a new value for a field.
	 * <p>Default is "true", exposing previous field values to custom editors.
	 * Turn this to "false" to avoid side effects caused by getters.
	 */
	public void setExtractOldValueForEditor(boolean extractOldValueForEditor) {
		getPropertyAccessor().setExtractOldValueForEditor(extractOldValueForEditor);
	}

	public void registerCustomEditor(Class requiredType, PropertyEditor propertyEditor) {
		getPropertyAccessor().registerCustomEditor(requiredType, propertyEditor);
	}

	public void registerCustomEditor(Class requiredType, String field, PropertyEditor propertyEditor) {
		getPropertyAccessor().registerCustomEditor(requiredType, field, propertyEditor);
	}

	public PropertyEditor findCustomEditor(Class requiredType, String propertyPath) {
		return getPropertyAccessor().findCustomEditor(requiredType, propertyPath);
	}

	/**
	 * Set the strategy to use for resolving errors into message codes.
	 * Applies the given strategy to the underlying errors holder.
	 * <p>Default is a DefaultMessageCodesResolver.
	 * @see BeanPropertyBindingResult#setMessageCodesResolver
	 * @see DefaultMessageCodesResolver
	 */
	public void setMessageCodesResolver(MessageCodesResolver messageCodesResolver) {
		getInternalBindingResult().setMessageCodesResolver(messageCodesResolver);
	}

	/**
	 * Set the strategy to use for processing binding errors, that is,
	 * required field errors and <code>PropertyAccessException</code>s.
	 * <p>Default is a DefaultBindingErrorProcessor.
	 * @see DefaultBindingErrorProcessor
	 */
	public void setBindingErrorProcessor(BindingErrorProcessor bindingErrorProcessor) {
		this.bindingErrorProcessor = bindingErrorProcessor;
	}

	/**
	 * Return the strategy for processing binding errors.
	 */
	public BindingErrorProcessor getBindingErrorProcessor() {
		return bindingErrorProcessor;
	}


	/**
	 * Bind the given property values to this binder's target.
	 * <p>This call can create field errors, representing basic binding
	 * errors like a required field (code "required"), or type mismatch
	 * between value and bean property (code "typeMismatch").
	 * <p>Note that the given PropertyValues should be a throwaway instance:
	 * For efficiency, it will be modified to just contain allowed fields if it
	 * implements the MutablePropertyValues interface; else, an internal mutable
	 * copy will be created for this purpose. Pass in a copy of the PropertyValues
	 * if you want your original instance to stay unmodified in any case.
	 * @param pvs property values to bind
	 * @see #doBind(org.springframework.beans.MutablePropertyValues)
	 */
	public void bind(PropertyValues pvs) {
		MutablePropertyValues mpvs = (pvs instanceof MutablePropertyValues) ?
				(MutablePropertyValues) pvs : new MutablePropertyValues(pvs);
		doBind(mpvs);
	}

	/**
	 * Actual implementation of the binding process, working with the
	 * passed-in MutablePropertyValues instance.
	 * @param mpvs the property values to bind,
	 * as MutablePropertyValues instance
	 * @see #checkAllowedFields
	 * @see #checkRequiredFields
	 * @see #applyPropertyValues
	 */
	protected void doBind(MutablePropertyValues mpvs) {
		checkAllowedFields(mpvs);
		checkRequiredFields(mpvs);
		applyPropertyValues(mpvs);
	}

	/**
	 * Check the given property values against the allowed fields,
	 * removing values for fields that are not allowed.
	 * @param mpvs the property values to be bound (can be modified)
	 * @see #getAllowedFields
	 * @see #isAllowed(String)
	 */
	protected void checkAllowedFields(MutablePropertyValues mpvs) {
		PropertyValue[] pvs = mpvs.getPropertyValues();
		for (int i = 0; i < pvs.length; i++) {
			PropertyValue pv = pvs[i];
			String field = PropertyAccessorUtils.canonicalPropertyName(pv.getName());
			if (!isAllowed(field)) {
				mpvs.removePropertyValue(pv);
				getBindingResult().recordSuppressedField(field);
				if (logger.isDebugEnabled()) {
					logger.debug("Field [" + field + "] has been removed from PropertyValues " +
							"and will not be bound, because it has not been found in the list of allowed fields");
				}
			}
		}
	}

	/**
	 * Return if the given field is allowed for binding.
	 * Invoked for each passed-in property value.
	 * <p>The default implementation checks for "xxx*", "*xxx" and "*xxx*" matches,
	 * as well as direct equality, in the specified lists of allowed fields and
	 * disallowed fields. A field matching a disallowed pattern will not be accepted
	 * even if it also happens to match a pattern in the allowed list.
	 * <p>Can be overridden in subclasses.
	 * @param field the field to check
	 * @return if the field is allowed
	 * @see #setAllowedFields
	 * @see #setDisallowedFields
	 * @see org.springframework.util.PatternMatchUtils#simpleMatch(String, String)
	 */
	protected boolean isAllowed(String field) {
		String[] allowed = getAllowedFields();
		String[] disallowed = getDisallowedFields();
		return ((ObjectUtils.isEmpty(allowed) || PatternMatchUtils.simpleMatch(allowed, field)) &&
				(ObjectUtils.isEmpty(disallowed) || !PatternMatchUtils.simpleMatch(disallowed, field)));
	}

	/**
	 * Check the given property values against the required fields,
	 * generating missing field errors where appropriate.
	 * @param mpvs the property values to be bound (can be modified)
	 * @see #getRequiredFields
	 * @see #getBindingErrorProcessor
	 * @see BindingErrorProcessor#processMissingFieldError
	 */
	protected void checkRequiredFields(MutablePropertyValues mpvs) {
		String[] requiredFields = getRequiredFields();
		if (!ObjectUtils.isEmpty(requiredFields)) {
			Map propertyValues = new HashMap();
			PropertyValue[] pvs = mpvs.getPropertyValues();
			for (int i = 0; i < pvs.length; i++) {
				PropertyValue pv = pvs[i];
				String canonicalName = PropertyAccessorUtils.canonicalPropertyName(pv.getName());
				propertyValues.put(canonicalName, pv);
			}
			for (int i = 0; i < requiredFields.length; i++) {
				String field = requiredFields[i];
				PropertyValue pv = (PropertyValue) propertyValues.get(field);
				if (pv == null || pv.getValue() == null ||
						(pv.getValue() instanceof String && !StringUtils.hasText((String) pv.getValue()))) {
					// Use bind error processor to create FieldError.
					getBindingErrorProcessor().processMissingFieldError(field, getInternalBindingResult());
					// Remove property from property values to bind:
					// It has already caused a field error with a rejected value.
					if (pv != null) {
						mpvs.removePropertyValue(pv);
						propertyValues.remove(field);
					}
				}
			}
		}
	}

	/**
	 * Apply given property values to the target object.
	 * <p>Default implementation applies all of the supplied property
	 * values as bean property values. By default, unknown fields will
	 * be ignored.
	 * @param mpvs the property values to be bound (can be modified)
	 * @see #getTarget
	 * @see #getPropertyAccessor
	 * @see #isIgnoreUnknownFields
	 * @see #getBindingErrorProcessor
	 * @see BindingErrorProcessor#processPropertyAccessException
	 */
	protected void applyPropertyValues(MutablePropertyValues mpvs) {
		try {
			// Bind request parameters onto target object.
			getPropertyAccessor().setPropertyValues(mpvs, isIgnoreUnknownFields(), isIgnoreInvalidFields());
		}
		catch (PropertyBatchUpdateException ex) {
			// Use bind error processor to create FieldErrors.
			PropertyAccessException[] exs = ex.getPropertyAccessExceptions();
			for (int i = 0; i < exs.length; i++) {
				getBindingErrorProcessor().processPropertyAccessException(exs[i], getInternalBindingResult());
			}
		}
	}


	/**
	 * Close this DataBinder, which may result in throwing
	 * a BindException if it encountered any errors.
	 * @return the model Map, containing target object and Errors instance
	 * @throws BindException if there were any errors in the bind operation
	 * @see BindingResult#getModel()
	 */
	public Map close() throws BindException {
		if (getBindingResult().hasErrors()) {
			throw new BindException(getBindingResult());
		}
		return getBindingResult().getModel();
	}

}