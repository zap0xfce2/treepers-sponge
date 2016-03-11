package org.knechtcraft.sponge.treepers;

import com.google.inject.Inject;
import org.spongepowered.api.entity.living.monster.Creeper;
import org.spongepowered.api.event.Event;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.api.event.world.ExplosionEvent;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.channel.MessageChannel;
import org.spongepowered.api.world.explosion.Explosion;

import java.util.Optional;

@Plugin(id = "knechtcraft.treepers", name = "Treepers", version = "1.0",
        description = "Stops Creepers from destroying blocks and plants trees instead.")
public class Treepers {

    private static final boolean CREEPER_BREAKS_BLOCKS = false;

    @Listener
    public void onCreeperExplode(ExplosionEvent.Detonate event) {
        Cause cause = event.getCause();
        Optional<Creeper> creeper = cause.first(Creeper.class);

        //Did a creeper cause this?
        if (creeper.isPresent()) {
            Text unformattedText = Text.of("Hey! A creeper is starting to explode!");
            MessageChannel.TO_ALL.send(unformattedText);

            //"Clone" explosion, as we cannot change the existing one?
            Explosion old = event.getExplosion();
            Explosion newExplosion =
                    Explosion.builder()
                            .canCauseFire(old.canCauseFire())
                            .shouldPlaySmoke(old.shouldPlaySmoke())
                            //.sourceExplosive(old.getSourceExplosive().get())
                            .origin(old.getOrigin())
                            .radius(old.getRadius())
                            .shouldBreakBlocks(CREEPER_BREAKS_BLOCKS)
                            .world(old.getWorld()).build();

            event.setCancelled(true);
            newExplosion.getWorld().triggerExplosion(newExplosion);
            creeper.get().remove();
        }
    }

    @Listener
    public void afterExplode(ExplosionEvent.Post event) {

        Text unformattedText = Text.of("Creeper has exploded!");
        MessageChannel.TO_ALL.send(unformattedText);

    }
}

