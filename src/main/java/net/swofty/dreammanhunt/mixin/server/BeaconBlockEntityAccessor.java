package net.swofty.dreammanhunt.mixin.server;

import net.minecraft.world.level.block.entity.BeaconBeamOwner;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(BeaconBlockEntity.class)
public interface BeaconBlockEntityAccessor {
	@Accessor("levels")
	int dreammanhunt$getLevels();

	@Accessor("levels")
	void dreammanhunt$setLevels(int levels);

	@Accessor("lastCheckY")
	int dreammanhunt$getLastCheckY();

	@Accessor("lastCheckY")
	void dreammanhunt$setLastCheckY(int y);

	@Accessor("beamSections")
	List<BeaconBeamOwner.Section> dreammanhunt$getBeamSections();

	@Accessor("beamSections")
	void dreammanhunt$setBeamSections(List<BeaconBeamOwner.Section> sections);

	@Accessor("checkingBeamSections")
	List<BeaconBeamOwner.Section> dreammanhunt$getCheckingBeamSections();

	@Accessor("checkingBeamSections")
	void dreammanhunt$setCheckingBeamSections(List<BeaconBeamOwner.Section> sections);
}
