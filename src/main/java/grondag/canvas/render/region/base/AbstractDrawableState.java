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

package grondag.canvas.render.region.base;

import com.mojang.blaze3d.systems.RenderSystem;

import grondag.canvas.material.state.RenderState;

public abstract class AbstractDrawableState {
	private final RenderState renderState;
	private final int quadVertexCount;
	private boolean isClosed = false;

	protected AbstractDrawableState(RenderState renderState, int quadVertexCount) {
		this.renderState = renderState;
		this.quadVertexCount = quadVertexCount;
	}

	public RenderState renderState() {
		return renderState;
	}

	public int quadVertexCount() {
		return quadVertexCount;
	}

	public final void close() {
		assert RenderSystem.isOnRenderThread();

		if (!isClosed) {
			isClosed = true;

			closeInner();
		}
	}

	public boolean isClosed() {
		return isClosed;
	}

	protected abstract void closeInner();
}