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

package grondag.canvas.compat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Camera;

import net.fabricmc.loader.api.FabricLoader;

import grondag.canvas.CanvasMod;

class JustMapHolder {
	static JustMapRender justMapRender = (matrixStack, camera, tickDelta) -> { };
	private static boolean warnRender = true;

	static {
		if (FabricLoader.getInstance().isModLoaded("justmap")) {
			final MethodHandles.Lookup lookup = MethodHandles.lookup();

			try {
				final Class<?> clazz = Class.forName("ru.bulldog.justmap.client.render.WaypointRenderer");
				final Method render = clazz.getDeclaredMethod("renderWaypoints", PoseStack.class, Camera.class, float.class);
				final MethodHandle renderHandler = lookup.unreflect(render);

				justMapRender = (matrixStack, camera, tickDelta) -> {
					try {
						renderHandler.invokeExact(matrixStack, camera, tickDelta);
					} catch (final Throwable e) {
						if (warnRender) {
							CanvasMod.LOG.warn("Unable to call Just Map renderWaypoints hook due to exception:", e);
							CanvasMod.LOG.warn("Subsequent errors will be suppressed");
							warnRender = false;
						}
					}
				};

				CanvasMod.LOG.info("Found Just Map - compatibility hook enabled");
			} catch (final Exception e) {
				CanvasMod.LOG.warn("Unable to find Just Map render hook due to exception:", e);
			}
		}
	}

	interface JustMapRender {
		void renderWaypoints(PoseStack matrixStack, Camera camera, float tickDelta);
	}
}
