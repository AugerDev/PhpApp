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
package com.esminis.server.library.service.server.installpackage;

import android.content.Context;

import com.esminis.server.library.ErrorWithMessage;
import com.esminis.server.library.R;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Set;

public class InstallHelper {

	public void fromAssetFile(File target, String path, Context context) throws IOException {
		if (target.isFile()) {
			return;
		}
		InputStream input = context.getAssets().open(path);
		OutputStream output = new FileOutputStream(target);
		int read;
		byte[] bytes = new byte[8192];
		while ((read = input.read(bytes)) != -1) {
			output.write(bytes, 0, read);
			output.flush();
		}
		input.close();
		output.close();
	}

	public void fromAssetDirectory(
		File target, String path, Context context, boolean overwrite
	) throws Exception {
		fromAssetDirectory(target, path, context, true, overwrite);
		fromAssetDirectory(target, path, context, false, overwrite);
	}
	
	private void fromAssetDirectory(
		File target, String path, Context context, boolean dryRun, boolean overwrite
	) throws Exception {
		if (!target.isDirectory()) {
			return;
		}
		if (!target.canWrite()) {
			throw new ErrorWithMessage(
				R.string.error_cannot_write_to_directory, target.getAbsolutePath()
			);
		}
		String[] files = context.getAssets().list(path);
		for (String file : files) {
			String filePath = path + File.separator + file;
			File targetFile = new File(target + File.separator + file);
			if (context.getAssets().list(filePath).length > 0) {
				if (!targetFile.isDirectory() && !targetFile.mkdirs()) {
					throw new ErrorWithMessage(R.string.error_cannot_create_directory);
				}
				fromAssetDirectory(targetFile, filePath, context, overwrite);
			} else {
				if (!overwrite && targetFile.exists()) {
					throw new ErrorWithMessage(R.string.error_document_root_not_empty);
				}
				if (!dryRun) {
					fromAssetFile(targetFile, filePath, context);
				}
			}
		}
	}
	
	public void preprocessFile(File file, HashMap<String, String> variables) {
		BufferedReader reader = null;
		PrintWriter writer = null;
		try {
			reader = new BufferedReader(new FileReader(file));			
			StringBuilder content = new StringBuilder();
			String line;
			Set<String> names = variables.keySet();
			while ((line = reader.readLine()) != null) {
				for (String name : names) {
					line = line.replaceAll("\\{\\$" + name + "\\}", variables.get(name));					
				}
				content.append(line);
				content.append("\n");
			}
			reader.close();
			reader = null;
			writer = new PrintWriter(new FileWriter(file));
			writer.write(content.toString());
			writer.close();
		} catch (IOException ignored) {
		} finally {
			try {
				if (reader != null) {
					reader.close();
				}
			} catch (IOException ignored) {}
			if (writer != null) {
				writer.close();
			}			
		}
	}
	
}
