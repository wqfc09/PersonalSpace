package me.eigenraven.personalspace;

import cpw.mods.fml.common.eventhandler.Event.Result;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.event.terraingen.OreGenEvent;
import net.minecraftforge.event.terraingen.DecorateBiomeEvent;
import me.eigenraven.personalspace.world.PersonalWorldProvider;

public class PersonalSpaceEventHandler {

    @SubscribeEvent
    public void onOreGen(OreGenEvent.GenerateMinable event) {
        if (event.world.provider instanceof PersonalWorldProvider) {
            event.setResult(Result.DENY);
        }
    }

/* 
    @SubscribeEvent
    public void onDecorate(DecorateBiomeEvent.Decorate event) {
        if (event.world.provider instanceof PersonalWorldProvider) {
            // Block dead bushes and cacti for a cleaner look, or other specific decorations if needed
            if (event.type == DecorateBiomeEvent.Decorate.EventType.DEAD_BUSH || 
                event.type == DecorateBiomeEvent.Decorate.EventType.CACTUS) {
                event.setResult(Result.DENY);
            }
        }
    }
*/
}