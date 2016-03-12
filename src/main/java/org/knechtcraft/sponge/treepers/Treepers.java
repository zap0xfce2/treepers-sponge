package org.knechtcraft.sponge.treepers;

import com.flowpowered.math.vector.Vector3i;
import com.google.inject.Inject;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.block.BlockType;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.block.trait.BooleanTrait;
import org.spongepowered.api.data.Property;
import org.spongepowered.api.data.property.block.PassableProperty;
import org.spongepowered.api.data.type.TreeType;
import org.spongepowered.api.data.type.TreeTypes;
import org.spongepowered.api.effect.particle.ParticleType;
import org.spongepowered.api.entity.living.monster.Creeper;
import org.spongepowered.api.event.Event;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.api.event.cause.NamedCause;
import org.spongepowered.api.event.game.state.GameStartedServerEvent;
import org.spongepowered.api.event.world.ExplosionEvent;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.channel.MessageChannel;
import org.spongepowered.api.world.World;
import org.spongepowered.api.world.WorldCreationSettings;
import org.spongepowered.api.world.biome.BiomeType;
import org.spongepowered.api.world.biome.BiomeTypes;
import org.spongepowered.api.world.explosion.Explosion;
import org.spongepowered.api.world.gen.PopulatorObject;
import org.spongepowered.api.world.gen.type.BiomeTreeType;
import org.spongepowered.api.world.gen.type.BiomeTreeTypes;

import java.util.Optional;
import java.util.Random;

@Plugin(id = "knechtcraft.treepers", name = "Treepers", version = "1.0",
        description = "Stops Creepers from destroying blocks and plants trees instead.")
public class Treepers {

    private static final boolean CREEPER_BREAKS_BLOCKS = false;

    private Random random;

    @Listener
    public void onServerStart(GameStartedServerEvent event) {
        random = new Random();
    }

    @Listener
    public void onExplode(ExplosionEvent.Detonate event) {
        Cause cause = event.getCause();
        Object root = cause.root();

        //Did a creeper directly cause this ExplosionEvent?
        boolean isCreeper = root instanceof Creeper;
        if (isCreeper) {
            preventExplosion(event);
            plantTree(event);
        }
    }

    private void preventExplosion(ExplosionEvent.Detonate event) {
        //"Clone" explosion, as we cannot change the existing one?
        Explosion old = event.getExplosion();
        Explosion newExplosion =
                Explosion.builder()
                        .from(old)
                        .shouldBreakBlocks(CREEPER_BREAKS_BLOCKS)
                        .sourceExplosive(null) //Do not check for a creeper in next Eventlistener Iteration
                        .build();

        //Cancel default event...
        event.setCancelled(true);
        //...but trigger new event
        newExplosion.getWorld().triggerExplosion(newExplosion);

        //Remove the creeper (as the default event is canceled);
        ((Creeper) event.getCause().root()).remove();
    }

    private void plantTree(ExplosionEvent.Detonate event) {
        int x = event.getExplosion().getOrigin().getFloorX();
        int y = event.getExplosion().getOrigin().getFloorY();
        int z = event.getExplosion().getOrigin().getFloorZ();

        Vector3i pos = new Vector3i(x, y, z);
        Vector3i below = new Vector3i(x, y - 1, z);
        //BiomeType biome = event.getTargetWorld().getBiome(x, y);

        World world = event.getTargetWorld();
        PopulatorObject populator = BiomeTreeTypes.OAK.getPopulatorObject();

        //if the explosion happened on level 0, set it to 1, so we can place a dirt block below
        if (y == 0) {
            y = 1;
        }

        //set Dirt block below if possible (Trees cannot be placed without)
        BlockState blockBelow = world.containsBlock(below) ? world.getBlock(below) : null;
        if (blockBelow != null) {
            world.setBlock(below, BlockState.builder().blockType(BlockTypes.DIRT).build());
        }

        //Remove Grass, redstone, etc.
        BlockState blockOnPosition = world.getBlock(pos);

        Optional<PassableProperty> passableProperty_Op = blockOnPosition.getProperty(PassableProperty.class);
        boolean blockPassable = passableProperty_Op.isPresent() && passableProperty_Op.get().getValue();

        boolean passableBlockRemoved = false;
        if (blockPassable) {
            world.setBlock(pos, BlockState.builder().blockType(BlockTypes.AIR).build(), true);
            passableBlockRemoved = true;
        }

        boolean treePlaced = false;
        //is the tree placeable?
        if (populator.canPlaceAt(world, x, y, z)) {
            //Place the tree
            populator.placeObject(world, random, x, y, z);
            treePlaced = true;
        } else if (passableBlockRemoved) {
            //Reset passable Block
            world.setBlock(pos, blockOnPosition);

        }

        //Replace block below with original Block (unless the tree was placed in mid-air)
        if (blockBelow != null && !(treePlaced && blockBelow.getType() == BlockTypes.AIR)) {
            world.setBlock(below, blockBelow);
        }
    }
}

