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

package org.jarhc.analyzer;

import static org.jarhc.TestUtils.assertValuesEquals;
import static org.jarhc.utils.StringUtils.joinLines;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.jarhc.env.JavaRuntime;
import org.jarhc.model.Classpath;
import org.jarhc.report.ReportSection;
import org.jarhc.report.ReportTable;
import org.jarhc.test.ClasspathBuilder;
import org.jarhc.test.JavaRuntimeMock;
import org.junit.jupiter.api.Test;

class BinaryCompatibilityAnalyzerTest {

	@Test
	void test_analyze() {

		// prepare
		JavaRuntime javaRuntime = JavaRuntimeMock.getOracleRuntime();
		Classpath classpath = ClasspathBuilder.create(javaRuntime)
				.addJarFile("a.jar")
				.addClassDef("a.A").addClassRef("b.B").addClassRef("c.C").addClassRef("java.lang.String")
				.addJarFile("b.jar")
				.addClassDef("b.B")
				.build();

		// test
		BinaryCompatibilityAnalyzer analyzer = new BinaryCompatibilityAnalyzer();
		ReportSection section = analyzer.analyze(classpath);

		// assert
		ReportTable table = assertSectionHeader(section);

		List<String[]> rows = table.getRows();
		assertEquals(1, rows.size());
		assertValuesEquals(rows.get(0), "a.jar", joinLines("a.A", "\u2022 Class is not accessible: class b.B", "\u2022 Class not found: c.C (package not found)"));
	}

	@Test
	void test_analyze_withClassFileIssues() {

		// prepare
		JavaRuntime javaRuntime = JavaRuntimeMock.getOracleRuntime();
		Classpath classpath = ClasspathBuilder.create(javaRuntime)
				.addJarFile("a.jar")
				.addClassDef("a.A", 11, 61, 0)
				.build();

		// test
		BinaryCompatibilityAnalyzer analyzer = new BinaryCompatibilityAnalyzer();
		ReportSection section = analyzer.analyze(classpath);

		// assert
		ReportTable table = assertSectionHeader(section);

		List<String[]> rows = table.getRows();
		assertEquals(1, rows.size());
		assertValuesEquals(rows.get(0), "a.jar", joinLines("a.A", "\u2022 Compiled for Java 17, but bundled for Java 11."));
	}

	private ReportTable assertSectionHeader(ReportSection section) {

		assertNotNull(section);
		assertEquals("Binary Compatibility", section.getTitle());
		assertEquals("Compatibility issues between JAR files.", section.getDescription());
		assertEquals(1, section.getContent().size());
		assertTrue(section.getContent().get(0) instanceof ReportTable);

		ReportTable table = (ReportTable) section.getContent().get(0);

		String[] columns = table.getColumns();
		assertValuesEquals(columns, "JAR file", "Issues");

		return table;
	}

}