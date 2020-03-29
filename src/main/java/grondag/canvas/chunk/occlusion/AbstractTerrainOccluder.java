package grondag.canvas.chunk.occlusion;

import java.io.File;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.math.Matrix4f;
import net.minecraft.resource.ResourceImpl;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import grondag.canvas.CanvasMod;
import grondag.canvas.Configurator;
import grondag.canvas.render.CanvasWorldRenderer;

public abstract class AbstractTerrainOccluder {
	protected final long[] lowBins = new long[LOW_BIN_COUNT];
	protected final long[] midBins = new long[MIDDLE_BIN_COUNT];
	protected final long[] topBins = new long[TOP_BIN_COUNT];

	protected Matrix4f projectionMatrix;
	protected Matrix4f modelMatrix;
	protected final Matrix4f mvpMatrix = new Matrix4f();

	protected final ProjectionVector4f v000 = new ProjectionVector4f();
	protected final ProjectionVector4f v001 = new ProjectionVector4f();
	protected final ProjectionVector4f v010 = new ProjectionVector4f();
	protected final ProjectionVector4f v011 = new ProjectionVector4f();
	protected final ProjectionVector4f v100 = new ProjectionVector4f();
	protected final ProjectionVector4f v101 = new ProjectionVector4f();
	protected final ProjectionVector4f v110 = new ProjectionVector4f();
	protected final ProjectionVector4f v111 = new ProjectionVector4f();

	// Boumds of current triangle
	protected int minX;
	protected int minY;
	protected int maxX;
	protected int maxY;

	protected int minPixelX;
	protected int minPixelY;
	protected int maxPixelX;
	protected int maxPixelY;

	// Barycentric coordinates at minX/minY corner
	protected int wOrigin0;
	protected int wOrigin1;
	protected int wOrigin2;

	protected int xOrigin;
	protected int yOrigin;
	protected int zOrigin;

	protected double cameraX;
	protected double cameraY;
	protected double cameraZ;

	protected float offsetX;
	protected float offsetY;
	protected float offsetZ;

	protected int x0;
	protected int y0;
	protected int x1;
	protected int y1;
	protected int x2;
	protected int y2;

	protected int a0;
	protected int b0;
	protected int a1;
	protected int b1;
	protected int a2;
	protected int b2;

	protected int aLow0;
	protected int bLow0;
	protected int aLow1;
	protected int bLow1;
	protected int aLow2;
	protected int bLow2;
	protected int abLow0;
	protected int abLow1;
	protected int abLow2;

	public final boolean isChunkVisible()  {
		CanvasWorldRenderer.innerTimer.start();

		computeProjectedBoxBounds(0, 0, 0, 16, 16, 16);

		final boolean result =
				// if camera below top face can't be seen
				(offsetY < -16 && testQuad(v110, v010, v011, v111)) // up
				|| (offsetY > 0 && testQuad(v000, v100, v101, v001)) // down

				|| (offsetX < -16 && testQuad(v101, v100, v110, v111)) // east
				|| (offsetX > 0 && testQuad(v000, v001, v011, v010)) // west

				|| (offsetZ < -16 && testQuad(v001, v101, v111, v011)) // south
				|| (offsetZ > 0 && testQuad(v100, v000, v010, v110)); // north

		CanvasWorldRenderer.innerTimer.stop();

		return result;
	}

	public final boolean isBoxVisible(int packedBox) {
		final int x0  = PackedBox.x0(packedBox);
		final int y0  = PackedBox.y0(packedBox);
		final int z0  = PackedBox.z0(packedBox);
		final int x1  = PackedBox.x1(packedBox);
		final int y1  = PackedBox.y1(packedBox);
		final int z1  = PackedBox.z1(packedBox);

		computeProjectedBoxBounds(x0, y0, z0, x1, y1, z1);

		// if camera below top face can't be seen
		return (offsetY < -y1 && testQuad(v110, v010, v011, v111)) // up
				|| (offsetY > -y0 && testQuad(v000, v100, v101, v001)) // down

				|| (offsetX < -x1 && testQuad(v101, v100, v110, v111)) // east
				|| (offsetX > -x0 && testQuad(v000, v001, v011, v010)) // west

				|| (offsetZ < -z1 && testQuad(v001, v101, v111, v011)) // south
				|| (offsetZ > -z0 && testQuad(v100, v000, v010, v110)); // north
	}

	public final void occludeChunk()  {
		computeProjectedBoxBounds(0, 0, 0, 16, 16, 16);

		if (offsetY < -16) drawQuad(v110, v010, v011, v111); // up
		if (offsetY > 0) drawQuad(v000, v100, v101, v001); // down
		if (offsetX < -16) drawQuad(v101, v100, v110, v111); // east
		if (offsetX > 0) drawQuad(v000, v001, v011, v010); // west
		if (offsetZ < -16) drawQuad(v001, v101, v111, v011); // south
		if (offsetZ > 0) drawQuad(v100, v000, v010, v110); // north
	}

	protected final void occlude(float x0, float y0, float z0, float x1, float y1, float z1) {
		computeProjectedBoxBounds(x0, y0, z0, x1, y1, z1);

		if (offsetY < -y1) drawQuad(v110, v010, v011, v111); // up
		if (offsetY > -y0) drawQuad(v000, v100, v101, v001); // down
		if (offsetX < -x1) drawQuad(v101, v100, v110, v111); // east
		if (offsetX > -x0) drawQuad(v000, v001, v011, v010); // west
		if (offsetZ < -z1) drawQuad(v001, v101, v111, v011); // south
		if (offsetZ > -z0) drawQuad(v100, v000, v010, v110); // north
	}

	public final void occlude(int[] visData, int range) {
		final int limit= visData.length;

		if (limit > 1) {
			for (int i = 1; i < limit; i++) {
				final int box  = visData[i];

				if (range > PackedBox.range(box)) {
					break;
				}

				occlude(
						PackedBox.x0(box),
						PackedBox.y0(box),
						PackedBox.z0(box),
						PackedBox.x1(box),
						PackedBox.y1(box),
						PackedBox.z1(box));
			}
		}
	}

	protected final void computeProjectedBoxBounds(float x0, float y0, float z0, float x1, float y1, float z1) {
		v000.set(x0, y0, z0, 1);
		v000.transform(mvpMatrix);

		v001.set(x0, y0, z1, 1);
		v001.transform(mvpMatrix);

		v010.set(x0, y1, z0, 1);
		v010.transform(mvpMatrix);

		v011.set(x0, y1, z1, 1);
		v011.transform(mvpMatrix);

		v100.set(x1, y0, z0, 1);
		v100.transform(mvpMatrix);

		v101.set(x1, y0, z1, 1);
		v101.transform(mvpMatrix);

		v110.set(x1, y1, z0, 1);
		v110.transform(mvpMatrix);

		v111.set(x1, y1, z1, 1);
		v111.transform(mvpMatrix);
	}

	public final void prepareScene(Matrix4f projectionMatrix, Matrix4f modelMatrix, Camera camera) {
		this.projectionMatrix = projectionMatrix.copy();
		this.modelMatrix = modelMatrix.copy();
		final Vec3d vec3d = camera.getPos();
		cameraX = vec3d.getX();
		cameraY = vec3d.getY();
		cameraZ = vec3d.getZ();
	}

	public final void clearScene() {
		System.arraycopy(EMPTY_BITS, 0, lowBins, 0, LOW_BIN_COUNT);
		System.arraycopy(EMPTY_BITS, 0, midBins, 0, MIDDLE_BIN_COUNT);
		System.arraycopy(EMPTY_BITS, 0, topBins, 0, TOP_BIN_COUNT);
	}

	public final void prepareChunk(BlockPos origin) {
		xOrigin = origin.getX();
		yOrigin = origin.getY();
		zOrigin = origin.getZ();

		offsetX = (float) (xOrigin - cameraX);
		offsetY = (float) (yOrigin - cameraY);
		offsetZ = (float) (zOrigin - cameraZ);

		mvpMatrix.loadIdentity();
		mvpMatrix.multiply(projectionMatrix);
		mvpMatrix.multiply(modelMatrix);
		mvpMatrix.multiply(Matrix4f.translate(offsetX, offsetY, offsetZ));
	}

	protected abstract void drawQuad(ProjectionVector4f v0, ProjectionVector4f v1, ProjectionVector4f v2, ProjectionVector4f v3);

	protected abstract void drawTri(ProjectionVector4f v0, ProjectionVector4f v1, ProjectionVector4f v2);

	protected abstract boolean testQuad(ProjectionVector4f v0, ProjectionVector4f v1, ProjectionVector4f v2, ProjectionVector4f v3);

	protected abstract boolean testTri(ProjectionVector4f v0, ProjectionVector4f v1, ProjectionVector4f v2);

	protected final boolean prepareTriBounds(ProjectionVector4f v0, ProjectionVector4f v1, ProjectionVector4f v2) {
		final int x0 = v0.ix();
		final int y0 = v0.iy();
		final int x1 = v1.ix();
		final int y1 = v1.iy();
		final int x2 = v2.ix();
		final int y2 = v2.iy();

		int minX = x0;
		int maxX = x0;

		if (x1 < minX) {
			minX = x1;
		} else if (x1 > maxX) {
			maxX = x1;
		}

		if (x2 < minX) {
			minX = x2;
		} else if (x2 > maxX) {
			maxX = x2;
		}

		int minY = y0;
		int maxY = y0;

		if (y1 < minY) {
			minY = y1;
		} else if (y1 > maxY) {
			maxY = y1;
		}

		if (y2 < minY) {
			minY = y2;
		} else if (y2 > maxY) {
			maxY = y2;
		}

		this.minX = minX;
		this.maxX = maxX;
		this.minY = minY;
		this.maxY = maxY;

		this.x0 = x0;
		this.y0 = y0;
		this.x1 = x1;
		this.y1 = y1;
		this.x2 = x2;
		this.y2 = y2;

		minPixelX = (minX + PRECISION_PIXEL_CENTER - 1) >> PRECISION_BITS;
		minPixelY = (minY + PRECISION_PIXEL_CENTER - 1) >> PRECISION_BITS;
		maxPixelX = (maxX - PRECISION_PIXEL_CENTER) >> PRECISION_BITS;
		maxPixelY = (maxY - PRECISION_PIXEL_CENTER) >> PRECISION_BITS;

		if (maxPixelX < 0 || minPixelX >= PIXEL_WIDTH) {
			return false;
		}

		if (minPixelX < 0) {
			minPixelX = 0;
		}

		if (maxPixelX > PIXEL_WIDTH - 1)  {
			maxPixelX = PIXEL_WIDTH  - 1;
		}

		if (maxPixelY < 0 || minPixelY >= PIXEL_HEIGHT) {
			return false;
		}

		if (minPixelY < 0) {
			minPixelY = 0;
		}

		if (maxPixelY > PIXEL_HEIGHT - 1)  {
			maxPixelY = PIXEL_HEIGHT - 1;
		}

		return true;
	}

	protected void prepareTriScan() {
		final int x0 = this.x0;
		final int y0 = this.y0;
		final int x1 = this.x1;
		final int y1 = this.y1;
		final int x2 = this.x2;
		final int y2 = this.y2;

		final int a0 = (y1 - y2);
		final int b0 = (x2 - x1);
		final int a1 = (y2 - y0);
		final int b1 = (x0 - x2);
		final int a2 = (y0 - y1);
		final int b2 = (x1 - x0);


		final boolean isTopLeft0 = a0 > 0 || (a0 == 0 && b0 < 0);
		final boolean isTopLeft1 = a1 > 0 || (a1 == 0 && b1 < 0);
		final boolean isTopLeft2 = a2 > 0 || (a2 == 0 && b2 < 0);

		final long cx = (minPixelX << PRECISION_BITS) + PRECISION_PIXEL_CENTER;
		final long cy = (minPixelY << PRECISION_BITS) + PRECISION_PIXEL_CENTER;

		// Barycentric coordinates at minX/minY corner
		// Can reduce precision (with accurate rounding) because increments will always be multiple of full pixel width
		wOrigin0 = (int) ((orient2d(x1, y1, x2, y2, cx, cy) + (isTopLeft0 ? PRECISION_PIXEL_CENTER : (PRECISION_PIXEL_CENTER - 1))) >> PRECISION_BITS);
		wOrigin1 = (int) ((orient2d(x2, y2, x0, y0, cx, cy) + (isTopLeft1 ? PRECISION_PIXEL_CENTER : (PRECISION_PIXEL_CENTER - 1))) >> PRECISION_BITS);
		wOrigin2 = (int) ((orient2d(x0, y0, x1, y1, cx, cy) + (isTopLeft2 ? PRECISION_PIXEL_CENTER : (PRECISION_PIXEL_CENTER - 1))) >> PRECISION_BITS);

		this.a0 = a0;
		this.b0 = b0;
		this.a1 = a1;
		this.b1 = b1;
		this.a2 = a2;
		this.b2 = b2;

		aLow0 = a0 * LOW_BIN_PIXEL_DIAMETER - a0;
		bLow0 = b0 * LOW_BIN_PIXEL_DIAMETER - b0;
		aLow1 = a1 * LOW_BIN_PIXEL_DIAMETER - a1;
		bLow1 = b1 * LOW_BIN_PIXEL_DIAMETER - b1;
		aLow2 = a2 * LOW_BIN_PIXEL_DIAMETER - a2;
		bLow2 = b2 * LOW_BIN_PIXEL_DIAMETER - b2;
		abLow0 = aLow0 + bLow0;
		abLow1 = aLow1 + bLow1;
		abLow2 = aLow2 + bLow2;
	}

	protected final long orient2d(long x0, long y0, long x1, long y1, long cx, long cy) {
		return ((x1 - x0) * (cy - y0) - (y1 - y0) * (cx - x0));
	}

	protected  final boolean testPixel(int x, int y) {
		return (lowBins[lowIndexFromPixelXY(x, y)] & (1L << (pixelIndex(x, y)))) == 0;
	}

	protected void drawPixel(int x, int y) {
		lowBins[lowIndexFromPixelXY(x, y)] |= (1L << (pixelIndex(x, y)));
	}

	private long nextTime;

	public final void outputRaster() {
		if (DISABLE_RASTER_OUTPUT) {
			return;
		}

		final long t = System.currentTimeMillis();

		if (t >= nextTime) {
			nextTime = t + 1000;

			final NativeImage nativeImage = new NativeImage(PIXEL_WIDTH, PIXEL_HEIGHT, false);

			for (int x = 0; x < PIXEL_WIDTH; x++) {
				for (int y = 0; y < PIXEL_HEIGHT; y++) {
					nativeImage.setPixelRgba(x, y, testPixel(x, y) ? -1 :0xFF000000);
				}
			}

			nativeImage.mirrorVertically();

			final File file = new File(MinecraftClient.getInstance().runDirectory, "canvas_occlusion_raster.png");

			ResourceImpl.RESOURCE_IO_EXECUTOR.execute(() -> {
				try {
					nativeImage.writeFile(file);
				} catch (final Exception e) {
					CanvasMod.LOG.warn("Couldn't save occluder image", e);
				} finally {
					nativeImage.close();
				}

			});
		}
	}

	@SuppressWarnings("unused")
	private static int mortonNumber(int x, int y) {
		int z = (x & 0b001) | ((y & 0b001) << 1);
		z |= ((x & 0b010) << 1) | ((y & 0b010) << 2);
		return z | ((x & 0b100) << 2) | ((y & 0b100) << 3);
	}

	protected static int midIndex(int midX, int midY) {
		final int topX = (midX >> LOW_AXIS_SHIFT);
		final int topY = (midY >> LOW_AXIS_SHIFT);
		//		return (topIndex(topX, topY) << MID_AXIS_SHIFT) | (midX & BIN_PIXEL_INDEX_MASK) | ((midY & BIN_PIXEL_INDEX_MASK) << BIN_AXIS_SHIFT);
		return (topIndex(topX, topY) << MID_AXIS_SHIFT) | (mortonNumber(midX, midY));
		//return (midY << MID_Y_SHIFT) | midX;
	}

	protected static int topIndex(int topX, int topY) {
		return (topY << TOP_Y_SHIFT) | topX;
	}

	protected static int lowIndex(int lowX, int lowY) {
		//		return (midIndex(lowX >> LOW_AXIS_SHIFT, lowY >> LOW_AXIS_SHIFT) << MID_AXIS_SHIFT)
		//				| (lowX & BIN_PIXEL_INDEX_MASK) | ((lowY & BIN_PIXEL_INDEX_MASK) << BIN_AXIS_SHIFT);

		final int midX = (lowX >> LOW_AXIS_SHIFT) & BIN_PIXEL_INDEX_MASK;
		final int midY = (lowY >> LOW_AXIS_SHIFT) & BIN_PIXEL_INDEX_MASK;

		final int topX = (lowX >> MID_AXIS_SHIFT);
		final int topY = (lowY >> MID_AXIS_SHIFT);

		return (topIndex(topX, topY) << TOP_INDEX_SHIFT) | (mortonNumber(midX, midY) << MID_INDEX_SHIFT) | mortonNumber(lowX & BIN_PIXEL_INDEX_MASK, lowY & BIN_PIXEL_INDEX_MASK);

		//				return (lowY << LOW_Y_SHIFT) | lowX;
	}

	protected static int lowIndexFromPixelXY(int x, int y)  {
		return lowIndex(x >>> LOW_AXIS_SHIFT, y >>> LOW_AXIS_SHIFT);
	}

	protected static int pixelIndex(int x, int y)  {
		return  ((y & BIN_PIXEL_INDEX_MASK) << BIN_AXIS_SHIFT) | (x & BIN_PIXEL_INDEX_MASK);
	}

	protected static boolean isPixelClear(long word, int x, int y)  {
		return (word & (1L << pixelIndex(x, y))) == 0;
	}

	protected static long pixelMask(int x, int y) {
		return 1L << pixelIndex(x, y);
	}

	/** REQUIRES 0-7 inputs! */
	protected static boolean testPixelInWordPreMasked(long word, int x, int y) {
		return (word & (1L << ((y << BIN_AXIS_SHIFT) | x))) == 0;
	}

	protected static long setPixelInWordPreMasked(long word, int x, int y) {
		return word | (1L << ((y << BIN_AXIS_SHIFT) | x));
	}

	protected static final boolean DISABLE_RASTER_OUTPUT = !Configurator.debugOcclusionRaster;

	protected static final int BIN_AXIS_SHIFT = 3;
	protected static final int BIN_PIXEL_DIAMETER = 1 << BIN_AXIS_SHIFT;
	protected static final int BIN_PIXEL_INDEX_MASK = BIN_PIXEL_DIAMETER - 1;
	protected static final int BIN_PIXEL_INVERSE_MASK = ~BIN_PIXEL_INDEX_MASK;

	protected static final int LOW_AXIS_SHIFT = BIN_AXIS_SHIFT;
	protected static final int MID_AXIS_SHIFT = BIN_AXIS_SHIFT * 2;
	protected static final int TOP_AXIS_SHIFT = BIN_AXIS_SHIFT * 3;

	protected static final int MID_INDEX_SHIFT = LOW_AXIS_SHIFT * 2;
	protected static final int TOP_INDEX_SHIFT = MID_INDEX_SHIFT * 2;

	protected static final int TOP_WIDTH = 2;
	protected static final int TOP_Y_SHIFT = Integer.bitCount(TOP_WIDTH - 1);
	protected static final int TOP_HEIGHT = 1;

	protected static final int MID_WIDTH = TOP_WIDTH  * 8;
	protected static final int MID_Y_SHIFT = Integer.bitCount(MID_WIDTH - 1);
	protected static final int MIDDLE_HEIGHT = TOP_HEIGHT  * 8;

	protected static final int PRECISION_BITS = 4;
	protected static final int PRECISION_FRACTION_MASK = (1 << PRECISION_BITS) - 1;
	protected static final int PRECISION_INTEGER_MASK = ~PRECISION_FRACTION_MASK;
	protected static final int PRECISION_PIXEL_CENTER = 1 << (PRECISION_BITS - 1);

	protected static final int LOW_WIDTH = MID_WIDTH * 8;
	//protected static final int LOW_Y_SHIFT = Integer.bitCount(LOW_WIDTH - 1);
	protected static final int PIXEL_WIDTH = LOW_WIDTH * BIN_PIXEL_DIAMETER;
	protected static final int HALF_PIXEL_WIDTH = PIXEL_WIDTH / 2;
	protected static final int PRECISION_WIDTH = PIXEL_WIDTH << PRECISION_BITS;
	protected static final int HALF_PRECISION_WIDTH = PRECISION_WIDTH / 2;

	protected static final int LOW_HEIGHT = MIDDLE_HEIGHT * 8;
	protected static final int PIXEL_HEIGHT = LOW_HEIGHT * BIN_PIXEL_DIAMETER;
	protected static final int HALF_PIXEL_HEIGHT = PIXEL_HEIGHT / 2;
	//	protected static final int HEIGHT_WORD_RELATIVE_SHIFT = LOW_Y_SHIFT - BIN_AXIS_SHIFT;
	protected static final int PRECISION_HEIGHT = PIXEL_HEIGHT << PRECISION_BITS;
	protected static final int HALF_PRECISION_HEIGHT = PRECISION_HEIGHT / 2;

	protected static final int GUARD_SIZE = 512;
	protected static final int GUARD_WIDTH = PRECISION_WIDTH + GUARD_SIZE;
	protected static final int GUARD_HEIGHT = PRECISION_HEIGHT + GUARD_SIZE;

	protected static final int LOW_BIN_COUNT = LOW_WIDTH * LOW_HEIGHT;
	protected static final int MIDDLE_BIN_COUNT = MID_WIDTH * LOW_HEIGHT;
	protected static final int TOP_BIN_COUNT = TOP_WIDTH * TOP_HEIGHT;

	protected static final int TOP_BIN_PIXEL_DIAMETER = PIXEL_WIDTH / TOP_WIDTH;
	protected static final int TOP_BIN_PIXEL_INDEX_MASK = TOP_BIN_PIXEL_DIAMETER - 1;

	protected static final int MID_BIN_PIXEL_DIAMETER = PIXEL_WIDTH / MID_WIDTH;
	protected static final int MID_BIN_PIXEL_INDEX_MASK = MID_BIN_PIXEL_DIAMETER - 1;

	protected static final int LOW_BIN_PIXEL_DIAMETER = PIXEL_WIDTH / LOW_WIDTH;
	protected static final int LOW_BIN_PIXEL_INDEX_MASK = LOW_BIN_PIXEL_DIAMETER - 1;

	protected static final long[] EMPTY_BITS = new long[LOW_BIN_COUNT];
}