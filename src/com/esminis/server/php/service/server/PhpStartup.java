/**
 * Copyright 2014 Tautvydas Andrikys
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. 
 */
package com.esminis.server.php.service.server;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PhpStartup {

	private List<String> getIniModules(File iniDirectory) {
		List<String> list = new ArrayList<String>();
		File[] files = iniDirectory.listFiles();
		if (files != null) {
			for (File file : files) {
				if (file.getName().endsWith(".ini")) {
					list.addAll(getIniModulesFromFile(file));
				}
			}
		}
		return list;
	}

	private List<String> getIniModulesFromFile(File file) {
		List<String> list = new ArrayList<String>();
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new FileReader(file));
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.contains("#") || line.contains(";")) {
					continue;
				}
				if (line.contains("extension")) {
					File fileTemp = new File(
						line.replaceAll("^[^#]*(zend_extension|extension).*=(.+\\.so).*$", "$2").trim()
					);
					list.add(fileTemp.getName().toLowerCase());
				}
			}
			reader.close();
		} catch (IOException ignored) {
		} finally {
			try {
				if (reader != null) {
					reader.close();
				}
			} catch (IOException ignored) {}
		}
		return list;
	}

	private void addStartupModules(
		List<String> options, File moduleDirectory, File iniDirectory, String[] modules,
		String[] modulesZend
	) {
		List<String> iniModules = getIniModules(iniDirectory);
		List<String> list = new ArrayList<String>();
		for (String module : modulesZend) {
			File file = new File(moduleDirectory, module);
			if (file.exists() && !iniModules.contains(file.getName().toLowerCase())) {
				list.add("zend_extension=" + file.getAbsolutePath());
			}
		}
		for (String module : modules) {
			File file = new File(moduleDirectory, module);
			if (file.exists() && !iniModules.contains(file.getName().toLowerCase())) {
				list.add("extension=" + file.getAbsolutePath());
			}
		}
		for (String row : list) {
			options.add("-d");
			options.add(row);
		}
	}

	private String[] createCommand(
		File php, String address, String root, File moduleDirectory, File iniDirectory,
		String[] modules, String[] modulesZend
	) {
		List<String> options = new ArrayList<String>();
		options.add(php.getAbsolutePath());
		options.add("-S");
		options.add(address);
		options.add("-t");
		options.add(root);
		addStartupModules(options, moduleDirectory, iniDirectory, modules, modulesZend);
		return options.toArray(new String[options.size()]);
	}

	public Process start(
		File php, String address, String root, File moduleDirectory, File documentRoot,
		String[] modules, String[] modulesZend
	) throws IOException {
		Process process = Runtime.getRuntime().exec(
			createCommand(php, address, root, moduleDirectory, documentRoot, modules, modulesZend), null,
			documentRoot
		);
		int pid = new com.esminis.model.manager.Process().getPid(php);
		if (pid > 0) {
			Runtime.getRuntime().exec(
				new String[]{
					"/system/bin/sh", "-c",
					"while [ -d /proc/" + android.os.Process.myPid() + " ] && [ -d /proc/" + pid + " ];" +
						"do sleep 5;done; kill -9 " + pid
				}
			);
		}
		return process;
	}

}