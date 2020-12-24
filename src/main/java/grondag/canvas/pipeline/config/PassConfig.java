/*
 *  Copyright 2019, 2020 grondag
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not
 *  use this file except in compliance with the License.  You may obtain a copy
 *  of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *  WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 *  License for the specific language governing permissions and limitations under
 *  the License.
 */

package grondag.canvas.pipeline.config;

import net.minecraft.util.Identifier;

public class PassConfig {
	public String framebufferName;
	public SamplerConfig[] samplers;
	public Identifier shader;
	// for computing size
	public int lod;

	//Image[] attachments, int[] samplerBinds, ProcessShader shader, Consumer<ProcessShader> activator

	public static PassConfig of(
			String framebufferName,
		SamplerConfig[] samplers,
		Identifier shader,
		int lod
	) {
		final PassConfig result = new PassConfig();
		result.framebufferName = framebufferName;
		result.samplers = samplers;
		result.shader = shader;
		result.lod = lod;
		return result;
	}

	public static PassConfig[] array(PassConfig... passes) {
		return passes;
	}

	public static Identifier CLEAR_ID = new Identifier("frex:clear");
}
