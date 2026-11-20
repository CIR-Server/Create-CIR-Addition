package com.cirserver.createciraddition.mixin;

import com.cirserver.createciraddition.network.NetworkCompatibility;
import net.neoforged.neoforge.network.negotiation.NegotiableNetworkComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Preserve channel/flow validation; relax only CIR's first version comparison. */
@Mixin(targets = "net.neoforged.neoforge.network.negotiation.NetworkComponentNegotiator", remap = false)
public abstract class MixinNetworkComponentNegotiator {
    @Redirect(
        method = "validateComponent",
        at = @At(value = "INVOKE", target = "Ljava/lang/String;equals(Ljava/lang/Object;)Z", ordinal = 0),
        require = 0
    )
    private static boolean cir$versionsMatch(String version, Object otherVersion,
            NegotiableNetworkComponent left, NegotiableNetworkComponent right, String requestingSide) {
        if (!left.id().equals(right.id())) {
            return version.equals(otherVersion);
        }
        return NetworkCompatibility.matches(left.id().getNamespace(), left.version(), right.version());
    }
}
