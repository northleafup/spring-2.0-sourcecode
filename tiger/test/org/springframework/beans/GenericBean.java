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

package org.springframework.beans;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.core.io.Resource;

/**
 * @author Juergen Hoeller
 * @since 20.01.2006
 */
public class GenericBean {

	private Set<Integer> integerSet;

	private List<Resource> resourceList;

	private List<List<Integer>> listOfLists;

	private List<Map<Integer, Long>> listOfMaps;

	private Map plainMap;

	private Map<Short, Integer> shortMap;

	private Map<Long, ?> longMap;

	private Map<Number, Collection<? extends Object>> collectionMap;

	private Map<String, Map<Integer, Long>> mapOfMaps;

	private Map<String, List<Integer>> mapOfLists;


	public GenericBean() {
	}

	public GenericBean(Set<Integer> integerSet) {
		this.integerSet = integerSet;
	}

	public GenericBean(Set<Integer> integerSet, List<Resource> resourceList) {
		this.integerSet = integerSet;
		this.resourceList = resourceList;
	}

	public GenericBean(Set<Integer> integerSet, Map<Short, Integer> shortMap) {
		this.integerSet = integerSet;
		this.shortMap = shortMap;
	}

	public GenericBean(Map<Short, Integer> shortMap, Resource resource) {
		this.shortMap = shortMap;
		this.resourceList = Collections.singletonList(resource);
	}

	public GenericBean(Map plainMap, Map<Short, Integer> shortMap) {
		this.plainMap = plainMap;
		this.shortMap = shortMap;
	}

	public GenericBean(Map<Long, ?> longMap) {
		this.longMap = longMap;
	}

	public GenericBean(boolean someFlag, Map<Number, Collection<? extends Object>> collectionMap) {
		this.collectionMap = collectionMap;
	}


	public Set<Integer> getIntegerSet() {
		return integerSet;
	}

	public void setIntegerSet(Set<Integer> integerSet) {
		this.integerSet = integerSet;
	}

	public List<Resource> getResourceList() {
		return resourceList;
	}

	public void setResourceList(List<Resource> resourceList) {
		this.resourceList = resourceList;
	}

	public List<List<Integer>> getListOfLists() {
		return listOfLists;
	}

	public void setListOfLists(List<List<Integer>> listOfLists) {
		this.listOfLists = listOfLists;
	}

	public List<Map<Integer, Long>> getListOfMaps() {
		return listOfMaps;
	}

	public void setListOfMaps(List<Map<Integer, Long>> listOfMaps) {
		this.listOfMaps = listOfMaps;
	}

	public Map getPlainMap() {
		return plainMap;
	}

	public Map<Short, Integer> getShortMap() {
		return shortMap;
	}

	public void setShortMap(Map<Short, Integer> shortMap) {
		this.shortMap = shortMap;
	}

	public Map<Long, ?> getLongMap() {
		return longMap;
	}

	public void setLongMap(Map<Long, ?> longMap) {
		this.longMap = longMap;
	}

	public Map<Number, Collection<? extends Object>> getCollectionMap() {
		return collectionMap;
	}

	public void setCollectionMap(Map<Number, Collection<? extends Object>> collectionMap) {
		this.collectionMap = collectionMap;
	}

	public Map<String, Map<Integer, Long>> getMapOfMaps() {
		return mapOfMaps;
	}

	public void setMapOfMaps(Map<String, Map<Integer, Long>> mapOfMaps) {
		this.mapOfMaps = mapOfMaps;
	}

	public Map<String, List<Integer>> getMapOfLists() {
		return mapOfLists;
	}

	public void setMapOfLists(Map<String, List<Integer>> mapOfLists) {
		this.mapOfLists = mapOfLists;
	}


	public static GenericBean createInstance(Set<Integer> integerSet) {
		return new GenericBean(integerSet);
	}

	public static GenericBean createInstance(Set<Integer> integerSet, List<Resource> resourceList) {
		return new GenericBean(integerSet, resourceList);
	}

	public static GenericBean createInstance(Set<Integer> integerSet, Map<Short, Integer> shortMap) {
		return new GenericBean(integerSet, shortMap);
	}

	public static GenericBean createInstance(Map<Short, Integer> shortMap, Resource resource) {
		return new GenericBean(shortMap, resource);
	}

	public static GenericBean createInstance(Map map, Map<Short, Integer> shortMap) {
		return new GenericBean(map, shortMap);
	}

	public static GenericBean createInstance(Map<Long, ?> longMap) {
		return new GenericBean(longMap);
	}

	public static GenericBean createInstance(boolean someFlag, Map<Number, Collection<? extends Object>> collectionMap) {
		return new GenericBean(someFlag, collectionMap);
	}

}