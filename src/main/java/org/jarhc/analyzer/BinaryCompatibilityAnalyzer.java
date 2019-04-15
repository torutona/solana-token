/*
 * Copyright 2019 Stephan Markwalder
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

package org.jarhc.analyzer;

import org.jarhc.java.AccessCheck;
import org.jarhc.java.ClassLoader;
import org.jarhc.model.*;
import org.jarhc.report.ReportSection;
import org.jarhc.report.ReportTable;
import org.jarhc.utils.JavaUtils;
import org.jarhc.utils.StringUtils;

import java.util.*;

import static org.jarhc.utils.StringUtils.joinLines;

public class BinaryCompatibilityAnalyzer extends Analyzer {

	private final boolean reportOwnerClassNotFound;

	public BinaryCompatibilityAnalyzer() {
		this(false);
	}

	public BinaryCompatibilityAnalyzer(boolean reportOwnerClassNotFound) {
		this.reportOwnerClassNotFound = reportOwnerClassNotFound;
	}

	@Override
	public ReportSection analyze(Classpath classpath) {

		ReportTable table = buildTable(classpath);

		ReportSection section = new ReportSection("Binary Compatibility", "Checks class hierarchy, method calls, field access, access permissions etc.");
		section.add(table);
		return section;
	}

	private ReportTable buildTable(Classpath classpath) {

		ReportTable table = new ReportTable("JAR File", "Issues");

		AccessCheck accessCheck = new AccessCheck(classpath);

		// TODO: parallelize

		// for every JAR file ...
		List<JarFile> jarFiles = classpath.getJarFiles();
		for (JarFile jarFile : jarFiles) {

			// issues found in JAR file (sorted by class name)
			Set<String> jarIssues = Collections.synchronizedSet(new TreeSet<>());

			// for every class definition ...
			List<ClassDef> classDefs = jarFile.getClassDefs();
			for (ClassDef classDef : classDefs) {

				// issues found in class definition (in order or appearance)
				Set<String> classIssues = new LinkedHashSet<>();

				// validate class definition
				validateClassHierarchy(classDef, classpath, accessCheck, classIssues);
				validateClassRefs(classDef, classpath, accessCheck, classIssues);
				validateMethodRefs(classDef, classpath, accessCheck, classIssues);
				validateFieldRefs(classDef, classpath, accessCheck, classIssues);

				if (!classIssues.isEmpty()) {
					String issue = createJarIssue(classDef, classIssues);
					jarIssues.add(issue);
				}

			}

			if (!jarIssues.isEmpty()) {
				String lines = joinLines(jarIssues).trim();
				table.addRow(jarFile.getFileName(), lines);
			}
		}

		return table;
	}

	private String createJarIssue(ClassDef classDef, Set<String> classIssues) {
		String className = classDef.getClassName();
		String lines = classIssues.stream().map(i -> "\u2022 " + i).collect(StringUtils.joinLines());
		return className + System.lineSeparator() + lines + System.lineSeparator();
	}

	// -----------------------------------------------------------------------------------------------------
	// class hierarchy (superclasses and interfaces)

	private void validateClassHierarchy(ClassDef classDef, Classpath classpath, AccessCheck accessCheck, Set<String> classIssues) {

		// if class has a superclass ...
		String superName = classDef.getSuperName();
		if (superName != null) {
			// check if superclass exists
			Optional<ClassDef> superClassDef = classpath.getClassDef(superName);
			if (!superClassDef.isPresent()) {
				classIssues.add("Superclass not found: " + superName);
			} else {
				ClassDef superClass = superClassDef.get();
				validateSuperclass(superClass, classDef, accessCheck, classIssues);
			}
		}

		// for every interface ...
		List<String> interfaceNames = classDef.getInterfaceNames();
		for (String interfaceName : interfaceNames) {
			// check if interface exists
			Optional<ClassDef> interfaceClassDef = classpath.getClassDef(interfaceName);
			if (!interfaceClassDef.isPresent()) {
				classIssues.add("Interface not found: " + interfaceName);
			} else {
				ClassDef interfaceDef = interfaceClassDef.get();
				validateInterface(interfaceDef, classDef, accessCheck, classIssues);
			}
		}

		// TODO: check if class is abstract OR implements all abstract methods
	}

	private void validateSuperclass(ClassDef superClass, ClassDef classDef, AccessCheck accessCheck, Set<String> classIssues) {

		// TODO: skip checks if superclass is from same JAR file
		// if (superClass.isFromSameJarFileAs(classDef)) {
		// 	return;
		// }

		// check if class is final
		if (superClass.isFinal()) {
			classIssues.add("Superclass is final: " + superClass.getDisplayName());
		}

		// check if class is an annotation, interface, or enum
		if (superClass.isAnnotation()) {
			classIssues.add("Superclass is an annotation: " + superClass.getDisplayName());
		} else if (superClass.isInterface()) {
			classIssues.add("Superclass is an interface: " + superClass.getDisplayName());
		} else if (superClass.isEnum()) {
			if (classDef.isEnum() && classDef.getClassName().startsWith(superClass.getClassName() + "$")) {
				// TODO: this check is not needed anymore if classes in same JAR file are skipped
				// superclass is outer enum class,
				// current class is inner anonymous class implementing the abstract enum class
			} else {
				classIssues.add("Superclass is an enum: " + superClass.getDisplayName());
			}
		} else {
			// OK (regular class or abstract class)
		}

		// check access to superclass
		boolean access = accessCheck.hasAccess(classDef, superClass);
		if (!access) {
			classIssues.add("Superclass is not accessible: " + superClass.getDisplayName());
		}

	}

	private void validateInterface(ClassDef interfaceClass, ClassDef classDef, AccessCheck accessCheck, Set<String> classIssues) {

		// TODO: skip checks if interface is from same JAR file
		// if (interfaceClass.isFromSameJarFileAs(classDef)) {
		// 	return;
		// }

		// check if class is an interface
		if (interfaceClass.isAnnotation()) {
			classIssues.add("Interface is an annotation: " + interfaceClass.getDisplayName());
		} else if (interfaceClass.isInterface()) {
			// OK (regular interface)
		} else if (interfaceClass.isEnum()) {
			classIssues.add("Interface is an enum: " + interfaceClass.getDisplayName());
		} else if (interfaceClass.isAbstract()) {
			classIssues.add("Interface is an abstract class: " + interfaceClass.getDisplayName());
		} else {
			classIssues.add("Interface is a class: " + interfaceClass.getDisplayName());
		}

		// check access to interface class
		boolean access = accessCheck.hasAccess(classDef, interfaceClass);
		if (!access) {
			classIssues.add("Interface is not accessible: " + interfaceClass.getDisplayName());
		}

	}

	// -----------------------------------------------------------------------------------------------------
	// classes (used as method parameter types and return types, field types, annotations, exceptions, ...)

	private void validateClassRefs(ClassDef classDef, ClassLoader classLoader, AccessCheck accessCheck, Set<String> classIssues) {

		// for every class reference ...
		List<ClassRef> classRefs = classDef.getClassRefs();
		for (ClassRef classRef : classRefs) {
			String className = classRef.getClassName();

			// check if class exists
			boolean exists = classLoader.getClassDef(className).isPresent();
			if (!exists) {

				String packageName = JavaUtils.getPackageName(className);
				boolean found = classLoader.containsPackage(packageName);
				if (!found) {
					classIssues.add("Class not found: " + className + " (package not found)");
				} else {
					classIssues.add("Class not found: " + className + " (package found)");
				}

			} else {
				// TODO: access check?
			}

		}

	}

	// -----------------------------------------------------------------------------------------------------
	// methods

	private void validateMethodRefs(ClassDef classDef, Classpath classpath, AccessCheck accessCheck, Set<String> classIssues) {

		// for every method reference ...
		List<MethodRef> methodRefs = classDef.getMethodRefs();
		for (MethodRef methodRef : methodRefs) {

			// validate field reference
			MethodSearchResult result = validateMethodRef(classDef, methodRef, accessCheck, classpath);
			if (!result.isIgnoreResult()) {
				String text = result.getResult();
				if (text != null) {
					classIssues.add(text);
				}
			}
		}
	}

	private MethodSearchResult validateMethodRef(ClassDef classDef, MethodRef methodRef, AccessCheck accessCheck, ClassLoader classLoader) {

		MethodSearchResult searchResult = new MethodSearchResult();

		// try to find owner class
		String targetClassName = methodRef.getMethodOwner();
		ClassDef ownerClassDef = classLoader.getClassDef(targetClassName).orElse(null);
		if (ownerClassDef == null) {
			// owner class not found
			searchResult.addErrorMessage("Method not found: " + methodRef.getDisplayName());
			if (reportOwnerClassNotFound) {
				searchResult.addSearchInfo("> " + targetClassName + " (owner class not found)");
			} else {
				// ignore result if owner class is not found
				// (already reported in missing classes)
				searchResult.setIgnoreResult();
			}
			return searchResult;
		}

		// check access to owner class
		boolean access = accessCheck.hasAccess(classDef, ownerClassDef);
		if (!access) {
			String className = classDef.getClassName();
			searchResult.addErrorMessage("Illegal access from " + className + " to class: " + targetClassName);
			return searchResult;
		}

		// find target field definition
		Optional<MethodDef> methodDef = classLoader.getMethodDef(methodRef, searchResult);
		if (!methodDef.isPresent()) {
			searchResult.addErrorMessage("Method not found: " + methodRef.getDisplayName());
			return searchResult;
		}

		MethodDef method = methodDef.get();

		// check access to method
		access = accessCheck.hasAccess(classDef, method);
		if (!access) {
			String className = classDef.getClassName();
			searchResult.addErrorMessage("Illegal access from " + className + ": " + methodRef.getDisplayName() + " -> " + method.getDisplayName());
		}

		// check method return type
		// TODO
		/*
		if (!method.getFieldType().equals(methodRef.getFieldType())) {
			searchResult.addErrorMessage("Incompatible field type: " + methodRef.getDisplayName() + " -> " + method.getDisplayName());
		}
		*/

		// check static/instance
		if (method.isStatic()) {
			if (!methodRef.isStaticAccess()) {
				searchResult.addErrorMessage("Instance access to static method: " + methodRef.getDisplayName() + " -> " + method.getDisplayName());
			}
		} else {
			if (methodRef.isStaticAccess()) {
				searchResult.addErrorMessage("Static access to instance method: " + methodRef.getDisplayName() + " -> " + method.getDisplayName());
			}
		}

		// TODO: more checks ...?

		return searchResult;
	}

	private class MethodSearchResult implements ClassLoader.Callback {

		private StringBuilder errorMessages;
		private StringBuilder searchInfos;
		private boolean ignoreResult = false;

		void addErrorMessage(String message) {
			if (errorMessages == null) {
				errorMessages = new StringBuilder();
			} else {
				errorMessages.append(System.lineSeparator());
			}
			errorMessages.append(message);
		}

		void addSearchInfo(String info) {
			if (searchInfos == null) {
				searchInfos = new StringBuilder();
			} else {
				searchInfos.append(System.lineSeparator());
			}
			searchInfos.append(info);
		}

		String getResult() {
			if (errorMessages == null) {
				return null;
			} else {
				if (searchInfos != null) {
					errorMessages.append(System.lineSeparator()).append(searchInfos);
				}
				return errorMessages.toString();
			}
		}

		boolean isIgnoreResult() {
			return ignoreResult;
		}

		void setIgnoreResult() {
			this.ignoreResult = true;
		}

		@Override
		public void classNotFound(String className) {
			addSearchInfo("> " + className + " (class not found)");
		}

		@Override
		public void memberNotFound(String className) {
			addSearchInfo("> " + className + " (method not found)");
		}

		@Override
		public void memberFound(String className) {
			addSearchInfo("> " + className + " (method found)");
		}

	}

	// -----------------------------------------------------------------------------------------------------
	// fields

	private void validateFieldRefs(ClassDef classDef, Classpath classpath, AccessCheck accessCheck, Set<String> classIssues) {

		// for every field reference ...
		List<FieldRef> fieldRefs = classDef.getFieldRefs();
		for (FieldRef fieldRef : fieldRefs) {

			// validate field reference
			FieldSearchResult result = validateFieldRef(classDef, fieldRef, accessCheck, classpath);
			if (!result.isIgnoreResult()) {
				String text = result.getResult();
				if (text != null) {
					classIssues.add(text);
				}
			}
		}
	}

	private FieldSearchResult validateFieldRef(ClassDef classDef, FieldRef fieldRef, AccessCheck accessCheck, ClassLoader classLoader) {

		FieldSearchResult searchResult = new FieldSearchResult();

		// try to find owner class
		String targetClassName = fieldRef.getFieldOwner();
		ClassDef ownerClassDef = classLoader.getClassDef(targetClassName).orElse(null);
		if (ownerClassDef == null) {
			// owner class not found
			searchResult.addErrorMessage("Field not found: " + fieldRef.getDisplayName());
			if (reportOwnerClassNotFound) {
				searchResult.addSearchInfo("> " + targetClassName + " (owner class not found)");
			} else {
				// ignore result if owner class is not found
				// (already reported in missing classes)
				searchResult.setIgnoreResult();
			}
			return searchResult;
		}

		// check access to owner class
		boolean access = accessCheck.hasAccess(classDef, ownerClassDef);
		if (!access) {
			String className = classDef.getClassName();
			searchResult.addErrorMessage("Illegal access from " + className + " to class: " + targetClassName);
			return searchResult;
		}

		// find target field definition
		Optional<FieldDef> fieldDef = classLoader.getFieldDef(fieldRef, searchResult);
		if (!fieldDef.isPresent()) {
			searchResult.addErrorMessage("Field not found: " + fieldRef.getDisplayName());
			return searchResult;
		}

		FieldDef field = fieldDef.get();

		// check access to field
		access = accessCheck.hasAccess(classDef, field);
		if (!access) {
			String className = classDef.getClassName();
			searchResult.addErrorMessage("Illegal access from " + className + ": " + fieldRef.getDisplayName() + " -> " + field.getDisplayName());
		}

		// check field type
		if (!field.getFieldType().equals(fieldRef.getFieldType())) {
			searchResult.addErrorMessage("Incompatible field type: " + fieldRef.getDisplayName() + " -> " + field.getDisplayName());
		}

		// check static/instance
		if (field.isStatic()) {
			if (!fieldRef.isStaticAccess()) {
				searchResult.addErrorMessage("Instance access to static field: " + fieldRef.getDisplayName() + " -> " + field.getDisplayName());
			}
		} else {
			if (fieldRef.isStaticAccess()) {
				searchResult.addErrorMessage("Static access to instance field: " + fieldRef.getDisplayName() + " -> " + field.getDisplayName());
			}
		}

		// check access to final fields
		if (field.isFinal()) {
			if (fieldRef.isWriteAccess()) {
				searchResult.addErrorMessage("Write access to final field: " + fieldRef.getDisplayName() + " -> " + field.getDisplayName());
			}
		}

		// TODO: more checks ...?

		return searchResult;
	}

	private class FieldSearchResult implements ClassLoader.Callback {

		private StringBuilder errorMessages;
		private StringBuilder searchInfos;
		private boolean ignoreResult = false;

		void addErrorMessage(String message) {
			if (errorMessages == null) {
				errorMessages = new StringBuilder();
			} else {
				errorMessages.append(System.lineSeparator());
			}
			errorMessages.append(message);
		}

		void addSearchInfo(String info) {
			if (searchInfos == null) {
				searchInfos = new StringBuilder();
			} else {
				searchInfos.append(System.lineSeparator());
			}
			searchInfos.append(info);
		}

		String getResult() {
			if (errorMessages == null) {
				return null;
			} else {
				if (searchInfos != null) {
					errorMessages.append(System.lineSeparator()).append(searchInfos);
				}
				return errorMessages.toString();
			}
		}

		boolean isIgnoreResult() {
			return ignoreResult;
		}

		void setIgnoreResult() {
			this.ignoreResult = true;
		}

		@Override
		public void classNotFound(String className) {
			addSearchInfo("> " + className + " (class not found)");
		}

		@Override
		public void memberNotFound(String className) {
			addSearchInfo("> " + className + " (field not found)");
		}

		@Override
		public void memberFound(String className) {
			addSearchInfo("> " + className + " (field found)");
		}

	}

}