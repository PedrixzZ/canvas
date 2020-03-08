/*******************************************************************************
 * Copyright 2019 grondag
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You may obtain a copy
 * of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations under
 * the License.
 ******************************************************************************/

package grondag.canvas.shader;

import org.lwjgl.opengl.GL21;

import net.minecraft.util.Identifier;

import grondag.canvas.shader.old.OldShaderContext;

public final class GlFragmentShader extends AbstractGlShader {
	GlFragmentShader(Identifier shaderSource, int shaderProps, OldShaderContext context) {
		super(shaderSource, GL21.GL_FRAGMENT_SHADER, shaderProps, context);
	}

	@Override
	public String getSource() {
		return buildSource(GlShaderManager.INSTANCE.fragmentLibrarySource);
	}
}