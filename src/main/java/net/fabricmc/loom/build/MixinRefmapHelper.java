/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2018-2022 FabricMC
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

package net.fabricmc.loom.build;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.jetbrains.annotations.NotNull;

public final class MixinRefmapHelper {
	private MixinRefmapHelper() {
    }

	@NotNull
	public static Collection<String> getMixinConfigurationFiles(JsonObject modMetadataJson) {
        JsonObject loaderObj = modMetadataJson.getAsJsonObject("loader");
        if (loaderObj == null) return Collections.emptyList();

		JsonArray mixinsArr = loaderObj.getAsJsonArray("mixins");
		if (mixinsArr == null) { // We also support a single-string Mixin file.
            JsonPrimitive mixinsPrim = loaderObj.getAsJsonPrimitive("mixins");
            if (mixinsPrim == null) return Collections.emptyList();
            return Set.of(mixinsPrim.getAsString());
        } else { // We can use the array.
            return StreamSupport.stream(mixinsArr.spliterator(), false)
                    .map(e -> {
                        if (e instanceof JsonPrimitive str) {
                            return str.getAsString();
                        } else if (e instanceof JsonObject obj) {
                            return obj.get("config").getAsString();
                        } else {
                            throw new RuntimeException("Incorrect mod.metadata.json format");
                        }
                    }).collect(Collectors.toSet());
        }
	}
}
