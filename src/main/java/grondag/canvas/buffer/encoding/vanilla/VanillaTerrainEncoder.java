package grondag.canvas.buffer.encoding.vanilla;

import grondag.canvas.buffer.packing.VertexCollectorImpl;
import grondag.canvas.material.MaterialVertexFormat;

abstract class VanillaTerrainEncoder extends VanillaEncoder {

	VanillaTerrainEncoder(MaterialVertexFormat format) {
		super(format);
	}

	@Override
	public void light(VertexCollectorImpl collector, int blockLight, int skyLight) {
		// flags disable diffuse and AO in shader - mainly meant for fluids
		collector.add(blockLight | (skyLight << 8) | 0b00110000);
	}
}
