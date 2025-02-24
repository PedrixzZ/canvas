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

package grondag.canvas.buffer.format;

import static grondag.canvas.buffer.format.CanvasVertexFormats.BASE_RGBA_4UB;
import static grondag.canvas.buffer.format.CanvasVertexFormats.BASE_TEX_2US;
import static grondag.canvas.buffer.format.CanvasVertexFormats.MATERIAL_1US;
import static grondag.canvas.buffer.format.CanvasVertexFormats.NORMAL_TANGENT_4B;
import static io.vram.frex.base.renderer.mesh.MeshEncodingHelper.HEADER_FIRST_VERTEX_TANGENT;
import static io.vram.frex.base.renderer.mesh.MeshEncodingHelper.MESH_VERTEX_STRIDE;
import static io.vram.frex.base.renderer.mesh.MeshEncodingHelper.UV_EXTRA_PRECISION;
import static io.vram.frex.base.renderer.mesh.MeshEncodingHelper.UV_ROUNDING_BIT;
import static io.vram.frex.base.renderer.mesh.MeshEncodingHelper.VERTEX_COLOR;
import static io.vram.frex.base.renderer.mesh.MeshEncodingHelper.VERTEX_LIGHTMAP;
import static io.vram.frex.base.renderer.mesh.MeshEncodingHelper.VERTEX_NORMAL;
import static io.vram.frex.base.renderer.mesh.MeshEncodingHelper.VERTEX_U;
import static io.vram.frex.base.renderer.mesh.MeshEncodingHelper.VERTEX_V;
import static io.vram.frex.base.renderer.mesh.MeshEncodingHelper.VERTEX_X;
import static io.vram.frex.base.renderer.mesh.MeshEncodingHelper.VERTEX_Y;
import static io.vram.frex.base.renderer.mesh.MeshEncodingHelper.VERTEX_Z;

import org.joml.Matrix3f;
import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.VertexFormatElement;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

import io.vram.frex.api.material.MaterialConstants;
import io.vram.frex.api.math.FrexMathUtil;
import io.vram.frex.base.renderer.mesh.MeshEncodingHelper;

import grondag.canvas.apiimpl.rendercontext.encoder.TerrainQuadEncoder;
import grondag.canvas.buffer.input.VertexCollector;
import grondag.canvas.material.state.CanvasRenderMaterial;

public class TerrainEncoder {
	private TerrainEncoder() { }

	private static final CanvasVertexFormatElement REGION = new CanvasVertexFormatElement(VertexFormatElement.Type.USHORT, 4, "in_region", false, true);
	private static final CanvasVertexFormatElement BLOCK_POS_AO = new CanvasVertexFormatElement(VertexFormatElement.Type.UBYTE, 4, "in_blockpos_ao", false, true);
	private static final CanvasVertexFormatElement LIGHTMAPS_2UB = new CanvasVertexFormatElement(VertexFormatElement.Type.UBYTE, 2, "in_lightmap", false, true);

	// Would be nice to make this smaller but with less precision in position we start
	// to see Z-fighting on iron bars, fire, etc. Iron bars require a resolution of 1/16000.
	// Reducing resolution of UV is problematic for multi-block textures.
	public static final CanvasVertexFormat TERRAIN_MATERIAL = new CanvasVertexFormat(
			REGION,
			BLOCK_POS_AO,
			BASE_RGBA_4UB,
			BASE_TEX_2US,
			LIGHTMAPS_2UB, MATERIAL_1US,
			NORMAL_TANGENT_4B);

	private static final int TERRAIN_VERTEX_STRIDE = TERRAIN_MATERIAL.vertexStrideInts;

	public static void encodeQuad(TerrainQuadEncoder encoder, VertexCollector buff) {
		final var quad = encoder.emitter();
		final var inputContext = encoder.inputContext();

		final var matrixStack = inputContext.matrixStack();
		final Matrix4f matrix = matrixStack.modelMatrix();
		final Matrix3f normalMatrix = matrixStack.normalMatrix();

		final boolean isNormalMatrixUseful = !FrexMathUtil.isIdentity(normalMatrix);

		final boolean aoDisabled = !Minecraft.useAmbientOcclusion();
		final int[] aoData = quad.ao;
		final CanvasRenderMaterial mat = (CanvasRenderMaterial) quad.material();

		assert mat.preset() != MaterialConstants.PRESET_DEFAULT;

		final int quadNormalFlags = quad.normalFlags();
		// don't retrieve if won't be used
		final int faceNormal = quadNormalFlags == 0b1111 ? 0 : quad.packedFaceNormal();
		// bit 16 is set if normal Z component is negative
		int normalSignBit = 0;
		int packedNormal = 0;
		int transformedNormal = 0;

		final int quadTangetFlags = quad.tangentFlags();
		final int faceTangent = quadTangetFlags == 0b1111 ? 0 : quad.packedFaceTanget();
		// bit 15 is set if tangent Z component is negative
		// bit 16 is set if tangent handedness is inverted
		int tangentInverseSignBits = 0;
		int packedTangent = 0;
		int transformedTangent = 0;

		final int material = mat.materialIndexer().index(quad.spriteId()) << 16;
		final boolean unlit = mat.unlit();

		final int[] target = buff.target();
		final int baseSourceIndex = quad.vertexStart();
		final int[] source = quad.data();

		// This and pos vertex encoding are the only differences from standard format
		final int sectorId = encoder.sectorId();
		assert sectorId >= 0;
		final int sectorRelativeRegionOrigin = encoder.sectorRelativeRegionOrigin();

		for (int i = 0; i < 4; i++) {
			final int vertexMask = 1 << i;
			final int fromIndex = baseSourceIndex + i * MESH_VERTEX_STRIDE;
			final int toIndex = i * TERRAIN_VERTEX_STRIDE;

			// We do this here because we need to pack the normal Z sign bit with sector ID
			final int p = ((quadNormalFlags & vertexMask) == 0) ? faceNormal : source[fromIndex + VERTEX_NORMAL];

			if (p != packedNormal) {
				packedNormal = p;
				transformedNormal = isNormalMatrixUseful ? FrexMathUtil.transformPacked3f(normalMatrix, packedNormal) : packedNormal;
				normalSignBit = (transformedNormal >>> 10) & 0x2000;
				transformedNormal = transformedNormal & 0xFFFF;
			}

			// We do this here because we need to pack the tangent Z sign bit with sector ID
			final int t = ((quadTangetFlags & vertexMask) == 0) ? faceTangent : source[baseSourceIndex + i + HEADER_FIRST_VERTEX_TANGENT];

			if (t != packedTangent) {
				packedTangent = t;
				transformedTangent = isNormalMatrixUseful ? FrexMathUtil.transformPacked3f(normalMatrix, packedTangent) : packedTangent;
				tangentInverseSignBits = (transformedTangent >>> 9) & 0xC000;
				transformedTangent = transformedTangent << 16;
			}

			// PERF: Consider fixed precision integer math
			final float x = Float.intBitsToFloat(source[fromIndex + VERTEX_X]);
			final float y = Float.intBitsToFloat(source[fromIndex + VERTEX_Y]);
			final float z = Float.intBitsToFloat(source[fromIndex + VERTEX_Z]);

			final float xOut = matrix.m00() * x + matrix.m10() * y + matrix.m20() * z + matrix.m30();
			final float yOut = matrix.m01() * x + matrix.m11() * y + matrix.m21() * z + matrix.m31();
			final float zOut = matrix.m02() * x + matrix.m12() * y + matrix.m22() * z + matrix.m32();

			int xInt = Mth.floor(xOut);
			int yInt = Mth.floor(yOut);
			int zInt = Mth.floor(zOut);

			final int xFract = Math.round((xOut - xInt) * 0xFFFF);
			final int yFract = Math.round((yOut - yInt) * 0xFFFF);
			final int zFract = Math.round((zOut - zInt) * 0xFFFF);

			// because our integer component could be negative, we have to unpack and re-pack the sector components
			xInt += (sectorRelativeRegionOrigin & 0xFF);
			yInt += ((sectorRelativeRegionOrigin >> 8) & 0xFF);
			zInt += ((sectorRelativeRegionOrigin >> 16) & 0xFF);

			target[toIndex] = sectorId | normalSignBit | tangentInverseSignBits | (xFract << 16);
			target[toIndex + 1] = yFract | (zFract << 16);

			final int ao = aoDisabled ? 0xFF000000 : (aoData[i] << 24);
			target[toIndex + 2] = xInt | (yInt << 8) | (zInt << 16) | ao;

			target[toIndex + 3] = source[fromIndex + VERTEX_COLOR];

			target[toIndex + 4] = (source[fromIndex + VERTEX_U] + UV_ROUNDING_BIT) >> UV_EXTRA_PRECISION
					| ((source[fromIndex + VERTEX_V] + UV_ROUNDING_BIT) >> UV_EXTRA_PRECISION << 16);

			// TODO: should probably pass unlit as a flag vs forcing lightmap
			final int packedLight = unlit ? MeshEncodingHelper.FULL_BRIGHTNESS : source[fromIndex + VERTEX_LIGHTMAP];
			final int blockLight = packedLight & 0xFF;
			final int skyLight = (packedLight >> 16) & 0xFF;
			target[toIndex + 5] = blockLight | (skyLight << 8) | material;

			target[toIndex + 6] = transformedNormal | transformedTangent;
		}

		buff.commit(quad.effectiveCullFaceId(), mat.castShadows());
	}
}
