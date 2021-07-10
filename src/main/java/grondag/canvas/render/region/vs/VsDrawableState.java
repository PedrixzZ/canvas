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

package grondag.canvas.render.region.vs;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.render.VertexFormat.DrawMode;

import grondag.canvas.buffer.VboBuffer;
import grondag.canvas.material.state.RenderState;
import grondag.canvas.render.region.base.AbstractDrawableState;
import grondag.canvas.varia.GFX;

public class VsDrawableState extends AbstractDrawableState {
	private VboBuffer vboBuffer;
	private int vertexOffset;

	public VsDrawableState(RenderState renderState, int quadVertexCount, int vertexOffset, VboBuffer vboBuffer) {
		super(renderState, quadVertexCount);
		this.vertexOffset = vertexOffset;
		this.vboBuffer = vboBuffer;
	}

	/**
	 * Assumes pipeline has already been activated and buffer has already been bound
	 * via {@link #bind()}.
	 */
	public void draw() {
		assert !isClosed();

		final int triVertexCount = quadVertexCount() / 4 * 6;
		final RenderSystem.IndexBuffer indexBuffer = RenderSystem.getSequentialBuffer(DrawMode.QUADS, triVertexCount);
		final int elementType = indexBuffer.getElementFormat().count; // "count" appears to be a yarn defect
		GFX.bindBuffer(GFX.GL_ELEMENT_ARRAY_BUFFER, indexBuffer.getId());
		GFX.drawElementsBaseVertex(DrawMode.QUADS.mode, triVertexCount, elementType, 0L, vertexOffset);
	}

	@Override
	protected void closeInner() {
		if (vboBuffer != null) {
			vboBuffer.close();
			vboBuffer = null;
		}
	}

	public void bindIfNeeded() {
		if (vboBuffer != null) {
			vboBuffer.bind();
		}
	}
}