package me.jellysquid.mods.sodium.client.render.chunk.translucent_sorting;

import org.joml.Vector3dc;
import org.joml.Vector3fc;

import it.unimi.dsi.fastutil.objects.Object2ReferenceOpenHashMap;
import me.jellysquid.mods.sodium.client.render.chunk.translucent_sorting.TranslucentSorting.SectionTriggers;
import net.minecraft.util.math.ChunkSectionPos;

/**
 * Performs Global Face Normal Indexing-based triggering as described in
 * https://hackmd.io/@douira100/sodium-sl-gfni
 * 
 * Distances are stored as doubles and normals are stored as float vectors.
 */
class GFNITriggers implements SectionTriggers {
	/**
	 * A map of all the normal lists, indexed by their normal.
	 */
	private Object2ReferenceOpenHashMap<Vector3fc, NormalList> normalLists = new Object2ReferenceOpenHashMap<>();

	int getUniqueNormalCount() {
		return this.normalLists.size();
	}

	@Override
	public void processTriggers(TranslucentSorting ts, CameraMovement movement) {
		for (var normalList : this.normalLists.values()) {
			normalList.processMovement(ts, movement);
		}
	}

	private void addSectionInNewNormalLists(DynamicData dynamicData, AccumulationGroup accGroup) {
		var normal = accGroup.normal;
		var normalList = this.normalLists.get(normal);
		if (normalList == null) {
			normalList = new NormalList(normal, accGroup.collectorKey);
			this.normalLists.put(normal, normalList);
			normalList.addSection(accGroup, accGroup.sectionPos.asLong());
		}
	}

	/**
	 * Removes the section from the normal list and returns whether the normal list
	 * is now empty and should itself be removed from the normal lists map. This is
	 * done with a return value so that the iterator can be used to remove it safely
	 * without a concurrent modification.
	 */
	private boolean removeSectionFromList(NormalList normalList, long sectionPos) {
		normalList.removeSection(sectionPos);
		return normalList.isEmpty();
	}

	@Override
	public void removeSection(long sectionPos, TranslucentData data) {
		var iterator = this.normalLists.values().iterator();
		while (iterator.hasNext()) {
			var normalList = iterator.next();
			if (this.removeSectionFromList(normalList, sectionPos)) {
				iterator.remove();
			}
		}
	}

	@Override
	public void addSection(ChunkSectionPos pos, DynamicData data, Vector3dc cameraPos) {
		long sectionPos = pos.asLong();
		var collector = data.getCollector();

		// go through all normal lists and check against the normals that the group
		// builder has. if the normal list has data for the section, but the group
		// builder doesn't, the group is removed. otherwise, the group is updated.
		var iterator = this.normalLists.values().iterator();
		while (iterator.hasNext()) {
			var normalList = iterator.next();

			// check if the geometry collector includes data for this normal.
			var accGroup = collector.getGroupForNormal(normalList);
			if (normalList.hasSection(sectionPos)) {
				if (accGroup == null) {
					if (this.removeSectionFromList(normalList, sectionPos)) {
						iterator.remove();
					}
				} else {
					normalList.updateSection(accGroup, sectionPos);
				}
			} else if (accGroup != null) {
				normalList.addSection(accGroup, sectionPos);
			}
		}

		// go through the data of the geometry collector to check for data of new
		// normals
		// for which there are no normal lists yet. This only checks for new normal
		// lists since new data for existing normal lists is handled above.
		if (collector.axisAlignedDistances != null) {
			for (var accGroup : collector.axisAlignedDistances) {
				if (accGroup != null) {
					this.addSectionInNewNormalLists(data, accGroup);
				}
			}
		}
		if (collector.unalignedDistances != null) {
			for (var accGroup : collector.unalignedDistances.values()) {
				this.addSectionInNewNormalLists(data, accGroup);
			}
		}

		data.deleteCollector();
	}
}
