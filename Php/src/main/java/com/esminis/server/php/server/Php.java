/**
 * Copyright 2016 Tautvydas Andrikys
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
package com.esminis.server.php.server;

import android.content.Context;

import com.esminis.server.library.application.LibraryApplication;
import com.esminis.server.library.model.manager.Log;
import com.esminis.server.library.model.manager.Network;
import com.esminis.server.library.model.manager.Process;
import com.esminis.server.library.preferences.Preferences;
import com.esminis.server.library.service.Utils;
import com.esminis.server.library.service.server.ServerControl;
import com.esminis.server.library.service.server.ServerNotification;
import com.esminis.server.php.R;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class Php extends ServerControl {

	private PhpServerLauncher startup = null;

	public Php(
		Network network, Process managerProcess, Preferences preferences, Log log,
		LibraryApplication application, ServerNotification serverNotification
	) {
		super(
			"php", application, network, preferences, log, managerProcess,
			application.getIsMainApplicationProcess(), serverNotification
		);
		this.startup = new PhpServerLauncher(managerProcess);
	}

	@Override
	protected void stop(java.lang.Process process) {}

	@Override
	protected java.lang.Process start(File root, String address) throws IOException {
		validatePhpIni(new File(root, "php.ini"));
		return startup.start(
			getBinary(), address, root.getAbsolutePath(), getBinaryDirectory(), root,
			preferences.getBoolean(context, Preferences.INDEX_PHP_ROUTER),
			getEnabledModules(context, root)
		);
	}

	private File[] getEnabledModules(Context context, File root) {
		List<File> modules = new ArrayList<>();
		String[] list = context.getResources().getStringArray(R.array.modules);
		for (int i = 0; i < list.length; i += 3) {
			final String module = list[i];
			final String moduleName = "module_" + module;
			if (
				!preferences.contains(context, moduleName) || preferences.getBoolean(context, moduleName)
			) {
				if ("zend_opcache".equals(module) && !Utils.canWriteToDirectory(root)) {
					sendWarning(R.string.warning_opcache_disabled);
				} else if (isModuleAvailable(module)) {
					modules.add(getModuleFile(module));
				}
			}
		}
		return modules.toArray(new File[modules.size()]);
	}

	private void validatePhpIni(File file) {
		FileInputStream inputStream = null;
		Properties properties = new Properties();
		try {
			inputStream = new FileInputStream(file);
			properties.load(inputStream);
		} catch (IOException ignored) {
		} finally {
			if (inputStream != null) {
				try {
					inputStream.close();
				} catch (IOException ignored) {}
			}
		}
		validateIsPhpIniDirectory(properties, "session.save_path");
		validateIsPhpIniDirectory(properties, "upload_tmp_dir");
	}

	private void validateIsPhpIniDirectory(Properties properties, String property) {
		final String path = properties.getProperty(property, null);
		final File file = path == null ? null : new File(path);
		Integer error = null;
		if (file == null) {
			error = R.string.warning_php_ini_property_not_defined;
		} else if (!file.isDirectory()) {
			error = R.string.warning_php_ini_directory_does_not_exist;
		} else if (!file.canWrite()) {
			error = R.string.warning_php_ini_directory_not_writable;
		}
		if (error != null) {
			sendWarning(error, property);
		}
	}

	private File getModuleFile(String name) {
		return new File(getBinaryDirectory(), name + ".so");
	}

	public boolean isModuleAvailable(String name) {
		return getModuleFile(name).isFile();
	}

}
