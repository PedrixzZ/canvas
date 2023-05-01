/*
 * This file is part of Canvas Renderer and is licensed to the project under
 * terms that are compatible with the GNU Lesser General Public License.
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership and licensing.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package grondag.canvas.shader;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL21;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import io.vram.frex.api.renderer.Renderer;
import io.vram.frex.base.renderer.BaseMaterialShaderManager;

import grondag.canvas.material.property.TargetRenderState;
import grondag.canvas.pipeline.Pipeline;
import grondag.canvas.shader.data.ShaderStrings;

// PERF: emit switch statements on non-Mac
public class GlMaterialShader extends GlShader {
	/**
	 * Target name if compiling separately, null otherwise.  Upper case.
	 */
	private final @Nullable String target;

	GlMaterialShader(ResourceLocation shaderSource, int shaderType, ProgramType programType, int target) {
		super(shaderSource, shaderType, programType);
		this.target = Pipeline.config().materialProgram.compileByTarget ? TargetRenderState.fromIndex(target).name.toUpperCase() : null;
	}

	// all material shaders use the same source so only append extension to keep debug source file names of reasonable length
	@Override
	protected String debugSourceString() {
		String base = programType.name;

		if (target != null) {
			base = base + "_" + target.toLowerCase();
		}

		return base + (shaderType == GL21.GL_FRAGMENT_SHADER ? ".frag" : ".vert");
	}

	@Override
	protected String preprocessSource(ResourceManager resourceManager, String baseSource) {
		if (shaderType == GL21.GL_FRAGMENT_SHADER) {
			baseSource = preprocessFragmentSource(resourceManager, baseSource);
		} else {
			baseSource = preprocessVertexSource(resourceManager, baseSource);
		}

		return super.preprocessSource(resourceManager, baseSource);
	}

	@Override
	protected String getCombinedShaderSource() {
		// Handle #define for compileByTarget pipeline configuration
		// Must happen after super pre-process so that includes are expanded first
		String result = super.getCombinedShaderSource();

		if (target != null) {
			result = StringUtils.replace(result, "#define MATERIAL_TARGET_UNKNOWN", "#define MATERIAL_TARGET_" + target);
		}

		return result;
	}

	private String preprocessFragmentSource(ResourceManager resourceManager, String baseSource) {
		String starts;
		String impl;
		final var shaderManager = (BaseMaterialShaderManager) Renderer.get().shaders();

		final int[] shaders = MaterialShaderIndexer.fragmentIds(programType);
		final int limit = shaders.length;

		if (limit == 0) {
			starts = "\t// NOOP";
			impl = "";
		} else if (limit == 1) {
			impl = loadMaterialFragmentShader(resourceManager, shaderManager.fragmentIdFromIndex(shaders[0]));

			if (impl.contains("frx_startFragment")) {
				starts = "\tfrx_startFragment(compatData);";
			} else if (impl.contains("frx_materialFragment")) {
				starts = "\tfrx_materialFragment();";
			} else {
				starts = "\t// NOOP";
			}
		} else {
			final StringBuilder startsBuilder = new StringBuilder();
			final StringBuilder implBuilder = new StringBuilder();

			startsBuilder.append("\tswitch (cv_programId) {\n");

			for (int i = 0; i < limit; ++i) {
				final int index = shaders[i];

				startsBuilder.append("\tcase ");
				startsBuilder.append(index);
				startsBuilder.append(": ");

				String src = loadMaterialFragmentShader(resourceManager, shaderManager.fragmentIdFromIndex(index));

				// UGLY: some pre-release compat handling here - should eventually be removed
				if (src.contains("frx_startFragment")) {
					startsBuilder.append("frx_startFragment");
					startsBuilder.append(index);
					startsBuilder.append("(compatData); break;\n");

					src = StringUtils.replace(src, "frx_startFragment", "frx_startFragment" + index);
					implBuilder.append(src);
					implBuilder.append("\n");
				} else if (src.contains("frx_materialFragment")) {
					startsBuilder.append("frx_materialFragment");
					startsBuilder.append(index);
					startsBuilder.append("(); break;\n");

					src = StringUtils.replace(src, "frx_materialFragment", "frx_materialFragment" + index);
					implBuilder.append(src);
					implBuilder.append("\n");
				} else {
					startsBuilder.append("break;\n");
				}
			}

			startsBuilder.append("\tdefault: break;\n");
			startsBuilder.append("\t}\n");

			impl = implBuilder.toString();
			starts = startsBuilder.toString();
		}

		final ResourceLocation sourceId = programType.isDepth && Pipeline.config().skyShadow != null
			? Pipeline.config().skyShadow.fragmentSource
			: Pipeline.config().materialProgram.fragmentSource;

		final String pipelineSource = loadShaderSource(resourceManager, sourceId);

		baseSource = StringUtils.replace(baseSource, ShaderStrings.API_TARGET, impl + pipelineSource);
		baseSource = StringUtils.replace(baseSource, ShaderStrings.FRAGMENT_START, starts);
		return baseSource;
	}

	private String preprocessVertexSource(ResourceManager resourceManager, String baseSource) {
		String starts;
		String impl;

		final int[] shaders = MaterialShaderIndexer.vertexIds(programType);
		final var shaderManager = (BaseMaterialShaderManager) Renderer.get().shaders();
		final int limit = shaders.length;

		if (limit == 0) {
			starts = "\t// NOOP";
			impl = "\t// NOOP";
		} else if (limit == 1) {
			impl = loadMaterialVertexShader(resourceManager, shaderManager.vertexIdFromIndex(shaders[0]));

			// prevent abandoned endVertex calls from conflicting
			impl = StringUtils.replace(impl, "frx_endVertex", "frx_endVertex_UNUSED");

			starts = impl.contains("frx_materialVertex") ? "\tfrx_materialVertex();" : "\t// NOOP";
		} else {
			final StringBuilder startsBuilder = new StringBuilder();
			final StringBuilder implBuilder = new StringBuilder();

			startsBuilder.append("\tswitch (cv_programId) {\n");

			for (int i = 0; i < limit; ++i) {
				final int index = shaders[i];

				startsBuilder.append("\tcase ");
				startsBuilder.append(index);
				startsBuilder.append(": ");

				String src = loadMaterialVertexShader(resourceManager, shaderManager.vertexIdFromIndex(index));

				// prevent abandoned endVertex calls from conflicting
				src = StringUtils.replace(src, "frx_endVertex", "frx_endVertex" + i + "_UNUSED");

				if (src.contains("frx_materialVertex")) {
					startsBuilder.append("frx_materialVertex");
					startsBuilder.append(index);
					startsBuilder.append("(); break;\n");
					src = StringUtils.replace(src, "frx_materialVertex", "frx_materialVertex" + index);
				} else {
					startsBuilder.append("break;\n");
				}

				implBuilder.append(src);
				implBuilder.append("\n");
			}

			startsBuilder.append("\tdefault: break;\n");
			startsBuilder.append("\t}\n");

			impl = implBuilder.toString();
			starts = startsBuilder.toString();
		}

		final ResourceLocation sourceId = programType.isDepth && Pipeline.config().skyShadow != null
				? Pipeline.config().skyShadow.vertexSource
				: Pipeline.config().materialProgram.vertexSource;

		final String pipelineSource = PreReleaseShaderCompat.compatifyPipelineVertex(loadShaderSource(resourceManager, sourceId), sourceId);
		baseSource = StringUtils.replace(baseSource, ShaderStrings.API_TARGET, impl + pipelineSource);
		baseSource = StringUtils.replace(baseSource, ShaderStrings.VERTEX_START, starts);
		return baseSource;
	}

	private static String loadMaterialVertexShader(ResourceManager resourceManager, ResourceLocation shaderSourceId) {
		return PreReleaseShaderCompat.compatifyMaterialVertex(loadShaderSource(resourceManager, shaderSourceId), shaderSourceId);
	}

	private static String loadMaterialFragmentShader(ResourceManager resourceManager, ResourceLocation shaderSourceId) {
		return PreReleaseShaderCompat.compatify(loadShaderSource(resourceManager, shaderSourceId), shaderSourceId);
	}
}
