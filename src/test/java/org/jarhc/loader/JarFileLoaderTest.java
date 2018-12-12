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

package org.jarhc.loader;

import org.jarhc.TestUtils;
import org.jarhc.model.JarFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junitpioneer.jupiter.TempDirectory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junitpioneer.jupiter.TempDirectory.TempDir;

@ExtendWith(TempDirectory.class)
class JarFileLoaderTest {

	@Test
	void test_load(@TempDir Path tempDir) throws IOException {

		// prepare
		String resource = "/JarFileLoaderTest/a.jar";
		File file = TestUtils.getResourceAsFile(resource, tempDir);

		// test
		JarFileLoader jarFileLoader = new JarFileLoader();
		JarFile jarFile = jarFileLoader.load(file);

		// assert
		assertNotNull(jarFile);
		assertEquals(file.getName(), jarFile.getFileName());
		assertEquals(1, jarFile.getClassDefs().size());
		assertEquals("a/A", jarFile.getClassDefs().get(0).getClassName());

	}

}