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

package org.springframework.scripting.support;

import net.sf.cglib.asm.Type;
import net.sf.cglib.core.Signature;
import net.sf.cglib.proxy.InterfaceMaker;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.aop.TargetSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.DelegatingIntroductionInterceptor;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessorAdapter;
import org.springframework.beans.factory.support.AbstractBeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.core.Conventions;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;
import org.springframework.scripting.ScriptFactory;
import org.springframework.scripting.ScriptSource;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

/**
 * {@link org.springframework.beans.factory.config.BeanPostProcessor} that
 * handles {@link org.springframework.scripting.ScriptFactory} definitions,
 * replacing each factory with the actual scripted Java object generated by it.
 *
 * <p>This is similar to the
 * {@link org.springframework.beans.factory.FactoryBean} mechanism, but is
 * specifically tailored for scripts and not built into Spring's core
 * container itself but rather implemented as an extension.
 *
 * <p><b>NOTE:</b> The most important characteristic of this post-processor
 * is that constructor arguments are applied to the
 * {@link org.springframework.scripting.ScriptFactory} instance
 * while bean property values are applied to the generated scripted object.
 * Typically, constructor arguments include a script source locator and
 * potentially script interfaces, while bean property values include
 * references and config values to inject into the scripted object itself.
 *
 * <p>The followin {@link ScriptFactoryPostProcessor} will automatically be
 * applied to the two
 * {@link org.springframework.scripting.ScriptFactory} definitions below.
 * At runtime, the actual scripted objects will be exposed for
 * "bshMessenger" and "groovyMessenger", rather than the
 * {@link org.springframework.scripting.ScriptFactory} instances. Both of
 * those are supposed to be castable to the example's <code>Messenger</code>
 * interfaces here.
 *
 * <pre class="code">&lt;bean class="org.springframework.scripting.support.ScriptFactoryPostProcessor"/&gt;
 *
 * &lt;bean id="bshMessenger" class="org.springframework.scripting.bsh.BshScriptFactory"&gt;
 *   &lt;constructor-arg value="classpath:mypackage/Messenger.bsh"/&gt;
 *   &lt;constructor-arg value="mypackage.Messenger"/&gt;
 *   &lt;property name="message" value="Hello World!"/&gt;
 * &lt;/bean&gt;
 *
 * &lt;bean id="groovyMessenger" class="org.springframework.scripting.bsh.GroovyScriptFactory"&gt;
 *   &lt;constructor-arg value="classpath:mypackage/Messenger.groovy"/&gt;
 *   &lt;property name="message" value="Hello World!"/&gt;
 * &lt;/bean&gt;</pre>
 * 
 * <p><b>NOTE:</b> Please note that the above excerpt from a Spring
 * XML bean definition file uses just the &lt;bean/&gt;-style syntax
 * (in an effort to illustrate using the {@link ScriptFactoryPostProcessor} itself).
 * In reality, you would never create a &lt;bean/&gt; definition for a
 * {@link ScriptFactoryPostProcessor} explicitly; rather you would import the
 * tags from the <code>'lang'</code> namespace and simply create scripted
 * beans using the tags in that namespace... as part of doing so, a
 * {@link ScriptFactoryPostProcessor} will implicitly be created for you.
 * 
 * <p>The Spring reference documentation contains numerous examples of using
 * tags in the <code>'lang'</code> namespace; by way of an example, find below
 * a Groovy-backed bean defined using the <code>'lang:groovy'</code> tag.
 * 
 * <pre class="code">&lt;?xml version="1.0" encoding="UTF-8"?&gt;
 *&lt;beans xmlns="http://www.springframework.org/schema/beans"
 *       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
 *       xmlns:lang="http://www.springframework.org/schema/lang"&gt;
 *
 *    &lt;!-- this is the bean definition for the Groovy-backed Messenger implementation --&gt;
 *    &lt;lang:groovy id="messenger" script-source="classpath:Messenger.groovy"&gt;
 *        &lt;lang:property name="message" value="I Can Do The Frug" /&gt;
 *    &lt;/lang:groovy&gt;
 *
 *    &lt;!-- an otherwise normal bean that will be injected by the Groovy-backed Messenger --&gt;
 *    &lt;bean id="bookingService" class="x.y.DefaultBookingService"&gt;
 *        &lt;property name="messenger" ref="messenger" /&gt;
 *    &lt;/bean&gt;
 *
 *&lt;/beans&gt;</pre>
 *
 * @author Juergen Hoeller
 * @author Rob Harrop
 * @author Rick Evans
 * @since 2.0
 */
public class ScriptFactoryPostProcessor extends InstantiationAwareBeanPostProcessorAdapter
		implements BeanFactoryAware, ResourceLoaderAware, DisposableBean {

	/**
	 * The {@link org.springframework.core.io.Resource}-style prefix that denotes
	 * an inline script.
	 * <p>An inline script is a script that is defined right there in the (typically XML)
	 * configuration, as opposed to being defined in an external file.
	 */
	public static final String INLINE_SCRIPT_PREFIX = "inline:";

	public static final String REFRESH_CHECK_DELAY_ATTRIBUTE =
			Conventions.getQualifiedAttributeName(ScriptFactoryPostProcessor.class, "refreshCheckDelay");

	private static final String SCRIPT_FACTORY_NAME_PREFIX = "scriptFactory.";

	private static final String SCRIPTED_OBJECT_NAME_PREFIX = "scriptedObject.";


	/** Logger available to subclasses */
	protected final Log logger = LogFactory.getLog(getClass());

	private long defaultRefreshCheckDelay = -1;

	private AbstractBeanFactory beanFactory;

	private ResourceLoader resourceLoader = new DefaultResourceLoader();

	private DefaultListableBeanFactory scriptBeanFactory = new DefaultListableBeanFactory();


	/**
	 * Set the delay between refresh checks, in milliseconds.
	 * Default is -1, indicating no refresh checks at all.
	 * <p>Note that an actual refresh will only happen when
	 * the {@link org.springframework.scripting.ScriptSource} indicates
	 * that it has been modified.
	 * @see org.springframework.scripting.ScriptSource#isModified()
	 */
	public void setDefaultRefreshCheckDelay(long defaultRefreshCheckDelay) {
		this.defaultRefreshCheckDelay = defaultRefreshCheckDelay;
	}

	public void setBeanFactory(BeanFactory beanFactory) {
		if (!(beanFactory instanceof AbstractBeanFactory)) {
			throw new IllegalStateException(
					"ScriptFactoryPostProcessor must run in AbstractBeanFactory, not in " + beanFactory);
		}
		this.beanFactory = (AbstractBeanFactory) beanFactory;

		// Required so that references (up container hierarchies) are correctly resolved.
		this.scriptBeanFactory.setParentBeanFactory(this.beanFactory);

		// Required so that all BeanPostProcessors, Scopes, etc become available.
		this.scriptBeanFactory.copyConfigurationFrom(this.beanFactory);
	}

	public void setResourceLoader(ResourceLoader resourceLoader) {
		this.resourceLoader = resourceLoader;
	}


	public Object postProcessBeforeInstantiation(Class beanClass, String beanName) {
		// We only apply special treatment to ScriptFactory implementations here.
		if (!ScriptFactory.class.isAssignableFrom(beanClass)) {
			return null;
		}

		String scriptedObjectBeanName = SCRIPTED_OBJECT_NAME_PREFIX + beanName;

		// Avoid recreation of the script bean definition in case of a prototype.
		if (!this.scriptBeanFactory.containsBeanDefinition(scriptedObjectBeanName)) {
			RootBeanDefinition bd = this.beanFactory.getMergedBeanDefinition(beanName);

			String scriptFactoryBeanName = SCRIPT_FACTORY_NAME_PREFIX + beanName;
			this.scriptBeanFactory.registerBeanDefinition(
					scriptFactoryBeanName, createScriptFactoryBeanDefinition(bd));
			ScriptFactory scriptFactory =
					(ScriptFactory) this.scriptBeanFactory.getBean(scriptFactoryBeanName, ScriptFactory.class);
			ScriptSource scriptSource =
					convertToScriptSource(scriptFactory.getScriptSourceLocator(), this.resourceLoader);
			Class[] interfaces = scriptFactory.getScriptInterfaces();

			if (scriptFactory.requiresConfigInterface() && !bd.getPropertyValues().isEmpty()) {
				PropertyValue[] pvs = bd.getPropertyValues().getPropertyValues();
				Class configInterface = createConfigInterface(pvs, interfaces);
				interfaces = (Class[]) ObjectUtils.addObjectToArray(interfaces, configInterface);
			}

			RootBeanDefinition objectBd = createScriptedObjectBeanDefinition(
					bd, scriptFactoryBeanName, scriptSource, interfaces);

			long refreshCheckDelay = resolveRefreshCheckDelay(bd);

			if (refreshCheckDelay >= 0) {
				objectBd.setSingleton(false);
			}
			this.scriptBeanFactory.registerBeanDefinition(scriptedObjectBeanName, objectBd);

			if (refreshCheckDelay >= 0) {
				RefreshableScriptTargetSource ts =
						new RefreshableScriptTargetSource(this.scriptBeanFactory, scriptedObjectBeanName, scriptSource);
				ts.setRefreshCheckDelay(refreshCheckDelay);
				return createRefreshableProxy(ts, interfaces);
			}
        }

		return this.scriptBeanFactory.getBean(scriptedObjectBeanName);
	}

	/**
	 * Get the refresh check delay for the given {@link ScriptFactory} {@link BeanDefinition}.
	 * If the {@link BeanDefinition} has a
	 * {@link org.springframework.core.AttributeAccessor metadata attribute}
	 * under the key {@link #REFRESH_CHECK_DELAY_ATTRIBUTE} which is a valid {@link Number}
	 * type, then this value is used. Otherwise, the the {@link #defaultRefreshCheckDelay}
	 * value is used.
	 * @return the refresh check delay
	 */
	protected long resolveRefreshCheckDelay(BeanDefinition bd) {
		long refreshCheckDelay = this.defaultRefreshCheckDelay;
		Object attributeValue = bd.getAttribute(REFRESH_CHECK_DELAY_ATTRIBUTE);
		if (attributeValue instanceof Number) {
			refreshCheckDelay = ((Number) attributeValue).longValue();
		}
		else if (attributeValue instanceof String) {
			refreshCheckDelay = Long.parseLong((String) attributeValue);
		}
		else if (attributeValue != null) {
			throw new BeanDefinitionStoreException(
					"Invalid refresh check delay attribute [" + REFRESH_CHECK_DELAY_ATTRIBUTE +
					"] with value [" + attributeValue + "]: needs to be of type Number or String");
		}
		return refreshCheckDelay;
	}

	/**
	 * Create a ScriptFactory bean definition based on the given script definition,
	 * extracting only the definition data that is relevant for the ScriptFactory
	 * (that is, only bean class and constructor arguments).
	 * @param bd the full script bean definition
	 * @return the extracted ScriptFactory bean definition
	 * @see org.springframework.scripting.ScriptFactory
	 */
	protected RootBeanDefinition createScriptFactoryBeanDefinition(RootBeanDefinition bd) {
		RootBeanDefinition scriptBd = new RootBeanDefinition(bd.getBeanClass());
		scriptBd.getConstructorArgumentValues().addArgumentValues(bd.getConstructorArgumentValues());
		return scriptBd;
	}

	/**
	 * Convert the given script source locator to a ScriptSource instance.
	 * <p>By default, supported locators are Spring resource locations
	 * (such as "file:C:/myScript.bsh" or "classpath:myPackage/myScript.bsh")
	 * and inline scripts ("inline:myScriptText...").
	 * @param scriptSourceLocator the script source locator
	 * @param resourceLoader the ResourceLoader to use (if necessary)
	 * @return the ScriptSource instance
	 */
	protected ScriptSource convertToScriptSource(String scriptSourceLocator, ResourceLoader resourceLoader) {
		if (scriptSourceLocator.startsWith(INLINE_SCRIPT_PREFIX)) {
			return new StaticScriptSource(scriptSourceLocator.substring(INLINE_SCRIPT_PREFIX.length()));
		}
		else {
			return new ResourceScriptSource(resourceLoader.getResource(scriptSourceLocator));
		}
	}

	/**
	 * Create a config interface for the given bean property values.
	 * <p>This implementation creates the interface via CGLIB's InterfaceMaker,
	 * determining the property types from the given interfaces (as far as possible).
	 * @param pvs the bean property values to create a config interface for
	 * @param interfaces the interfaces to check against (might define
	 * getters corresponding to the setters we're supposed to generate)
	 * @return the config interface
	 * @see net.sf.cglib.proxy.InterfaceMaker
	 * @see org.springframework.beans.BeanUtils#findPropertyType
	 */
	protected Class createConfigInterface(PropertyValue[] pvs, Class[] interfaces) {
		Assert.notEmpty(pvs, "Property values must not be empty");
		InterfaceMaker maker = new InterfaceMaker();
		for (int i = 0; i < pvs.length; i++) {
			String propertyName = pvs[i].getName();
			Class propertyType = BeanUtils.findPropertyType(propertyName, interfaces);
			String setterName = "set" + StringUtils.capitalize(propertyName);
			Signature signature = new Signature(setterName, Type.VOID_TYPE, new Type[] {Type.getType(propertyType)});
			maker.add(signature, new Type[0]);
		}
		return maker.create();
	}

	/**
	 * Create a bean definition for the scripted object, based on the given script
	 * definition, extracting the definition data that is relevant for the scripted
	 * object (that is, everything but bean class and constructor arguments).
	 * @param bd the full script bean definition
	 * @return the extracted ScriptFactory bean definition
	 * @see org.springframework.scripting.ScriptFactory#getScriptedObject
	 */
	protected RootBeanDefinition createScriptedObjectBeanDefinition(
			RootBeanDefinition bd, String scriptFactoryBeanName, ScriptSource scriptSource, Class[] interfaces) {

		RootBeanDefinition objectBd = new RootBeanDefinition(bd);
		objectBd.setBeanClass(null);
		objectBd.setFactoryBeanName(scriptFactoryBeanName);
		objectBd.setFactoryMethodName("getScriptedObject");
		objectBd.getConstructorArgumentValues().clear();
		objectBd.getConstructorArgumentValues().addIndexedArgumentValue(0, scriptSource);
		objectBd.getConstructorArgumentValues().addIndexedArgumentValue(1, interfaces);
		return objectBd;
	}

	/**
	 * Create a refreshable proxy for the given AOP TargetSource.
	 * @param ts the refreshable TargetSource
	 * @param interfaces the proxy interfaces (may be <code>null</code> to
	 * indicate proxying of all interfaces implemented by the target class)
	 * @return the generated proxy
	 * @see RefreshableScriptTargetSource
	 */
	protected Object createRefreshableProxy(TargetSource ts, Class[] interfaces) {
		ProxyFactory proxyFactory = new ProxyFactory();
		proxyFactory.setTargetSource(ts);

		if (interfaces == null) {
			interfaces = ClassUtils.getAllInterfacesForClass(ts.getTargetClass());
		}
		proxyFactory.setInterfaces(interfaces);

		DelegatingIntroductionInterceptor introduction = new DelegatingIntroductionInterceptor(ts);
		introduction.suppressInterface(TargetSource.class);
		proxyFactory.addAdvice(introduction);

		return proxyFactory.getProxy();
	}


	/**
	 * Destroy the inner bean factory (used for scripts) on shutdown.
	 */
	public void destroy() {
		this.scriptBeanFactory.destroySingletons();
	}

}
