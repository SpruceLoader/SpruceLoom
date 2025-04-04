/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016-2021 FabricMC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package net.fabricmc.loom.configuration;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.Iterables;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.apache.commons.io.FilenameUtils;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.SelfResolvingDependency;

import net.fabricmc.loom.util.ZipUtils;

public class FileDependencyInfo extends DependencyInfo {
	protected final Map<String, File> classifierToFile = new HashMap<>();
	protected final Set<File> resolvedFiles;
	protected final String group, name, version;

	FileDependencyInfo(Project project, SelfResolvingDependency dependency, Configuration configuration) {
		super(project, dependency, configuration);

		Set<File> files = dependency.resolve();
		this.resolvedFiles = files;
		switch (files.size()) {
		case 0 -> //Don't think Gradle would ever let you do this
				throw new IllegalStateException("Empty dependency?");
		case 1 -> //Single file dependency
				classifierToFile.put("", Iterables.getOnlyElement(files));
		default -> { //File collection, try work out the classifiers
			List<File> sortedFiles = files.stream().sorted(Comparator.comparing(File::getName, Comparator.comparingInt(String::length))).collect(Collectors.toList());
			//First element in sortedFiles is the one with the shortest name, we presume all the others are different classifier types of this
			File shortest = sortedFiles.remove(0);
			String shortestName = FilenameUtils.removeExtension(shortest.getName()); //name.jar -> name

			for (File file : sortedFiles) {
				if (!file.getName().startsWith(shortestName)) {
					//If there is another file which doesn't start with the same name as the presumed classifier-less one we're out of our depth
					throw new IllegalArgumentException("Unable to resolve classifiers for " + this + " (failed to sort " + files + ')');
				}
			}

			//We appear to be right, therefore this is the normal dependency file we want
			classifierToFile.put("", shortest);
			int start = shortestName.length();

			for (File file : sortedFiles) {
				//Now we just have to work out what classifier type the other files are, this shouldn't even return an empty string
				String classifier = FilenameUtils.removeExtension(file.getName()).substring(start);

				//The classifier could well be separated with a dash (thing name.jar and name-sources.jar), we don't want that leading dash
				if (classifierToFile.put(classifier.charAt(0) == '-' ? classifier.substring(1) : classifier, file) != null) {
					throw new InvalidUserDataException("Duplicate classifiers for " + this + " (\"" + file.getName().substring(start) + "\" in " + files + ')');
				}
			}
		}
		}

		if (dependency.getGroup() != null && dependency.getVersion() != null) {
			group = dependency.getGroup();
			name = dependency.getName();
			version = dependency.getVersion();
		} else {
			group = "net.fabricmc.synthetic";
			File root = classifierToFile.get(""); // We've built the classifierToFile map, now to try find a name and version for our dependency
			byte[] modJson;

			try {
				if ("jar".equals(FilenameUtils.getExtension(root.getName())) && (modJson = ZipUtils.unpackNullable(root.toPath(), "mod.metadata.json")) != null) {
					//It's a mod, see how much we can extract out
					JsonObject json = new Gson().fromJson(new String(modJson, StandardCharsets.UTF_8), JsonObject.class);

					if (json == null || !json.has("id") || !json.has("version")) {
						throw new IllegalArgumentException("Invalid mod JAR: " + root + " (malformed json: " + json + ')');
					}

					if (json.has("name")) { // Go for the name field if it's got one
						name = json.get("name").getAsString();
					} else {
						name = json.get("id").getAsString();
					}

					version = json.get("version").getAsString();
				} else {
					// Not a mod, just have to make something up
					name = FilenameUtils.removeExtension(root.getName());
					version = "1.0";
				}
			} catch (IOException e) {
				throw new UncheckedIOException("Failed to read input file: " + root, e);
			}
		}
	}

	@Override
	public String getResolvedVersion() {
		return version;
	}

	@Override
	public String getDepString() {
		// Use our custom name and version with the dummy group rather than the null:unspecified:null it would otherwise return
		return group + ':' + name + ':' + version;
	}

	@Override
	public String getResolvedDepString() {
		return getDepString();
	}

	@Override
	public Set<File> resolve() {
		return this.resolvedFiles;
	}
}
