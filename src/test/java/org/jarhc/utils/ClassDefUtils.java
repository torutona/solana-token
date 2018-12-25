/*
 * Copyright 2018 Stephan Markwalder
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

package org.jarhc.utils;

import org.jarhc.model.ClassDef;
import org.jarhc.model.FieldDef;
import org.jarhc.model.MethodDef;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ClassDefUtils {

	public static ClassDef read(DataInputStream stream) throws IOException {

		String className = stream.readUTF();
		// TODO: read class version
		String superName = stream.readBoolean() ? stream.readUTF() : null;
		int numInterfaceNames = stream.readInt();
		List<String> interfaceNames = new ArrayList<>(numInterfaceNames);
		if (numInterfaceNames > 0) {
			for (int i = 0; i < numInterfaceNames; i++) {
				interfaceNames.add(stream.readUTF());
			}
		}
		int numFieldDefs = stream.readInt();
		List<FieldDef> fieldDefs = new ArrayList<>(numFieldDefs);
		for (int f = 0; f < numFieldDefs; f++) {
			int access = stream.readInt();
			String fieldName = stream.readUTF();
			String fieldDescriptor = stream.readUTF();
			FieldDef fieldDef = new FieldDef(access, fieldName, fieldDescriptor);
			fieldDefs.add(fieldDef);
		}

		int numMethodDefs = stream.readInt();
		List<MethodDef> methodDefs = new ArrayList<>(numMethodDefs);
		for (int f = 0; f < numMethodDefs; f++) {
			int access = stream.readInt();
			String methodName = stream.readUTF();
			String methodDescriptor = stream.readUTF();
			MethodDef methodDef = new MethodDef(access, methodName, methodDescriptor);
			methodDefs.add(methodDef);
		}

		return ClassDef.forClassName(className)
				.withSuperName(superName)
				.withInterfaceNames(interfaceNames)
				.withFieldDefs(fieldDefs)
				.withMethodDefs(methodDefs)
				.build();
	}

	public static void write(ClassDef classDef, DataOutputStream stream) throws IOException {

		String className = classDef.getClassName();
		String superName = classDef.getSuperName();
		List<String> interfaceNames = classDef.getInterfaceNames();
		List<FieldDef> fieldDefs = classDef.getFieldDefs();
		List<MethodDef> methodDefs = classDef.getMethodDefs();

		// write class name, superclass, and interfaces
		stream.writeUTF(className);
		// TODO: write class version
		if (superName != null) {
			stream.writeBoolean(true);
			stream.writeUTF(superName);
		} else {
			stream.writeBoolean(false);
		}
		stream.writeInt(interfaceNames.size());
		for (String interfaceName : interfaceNames) {
			stream.writeUTF(interfaceName);
		}

		// write field definitions
		stream.writeInt(fieldDefs.size());
		for (FieldDef fieldDef : fieldDefs) {
			stream.writeInt(fieldDef.getAccess());
			stream.writeUTF(fieldDef.getFieldName());
			stream.writeUTF(fieldDef.getFieldDescriptor());
		}

		// write method definitions
		stream.writeInt(methodDefs.size());
		for (MethodDef methodDef : methodDefs) {
			stream.writeInt(methodDef.getAccess());
			stream.writeUTF(methodDef.getMethodName());
			stream.writeUTF(methodDef.getMethodDescriptor());
		}

	}

}