package com.cirserver.createciraddition.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/** Marker for the narrowly scoped bytecode repair in CirMixinPlugin.preApply. */
@Pseudo
@Mixin(targets = "net.yxiao233.throughputquest.common.part.ThroughputDetectionMonitorPart", remap = false)
public abstract class MixinThroughputDetectionMonitorPart {}
