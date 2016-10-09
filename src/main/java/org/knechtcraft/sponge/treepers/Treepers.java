package org.knechtcraft.sponge.treepers;

import com.flowpowered.math.vector.Vector3i;
import com.google.inject.Inject;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;
import org.slf4j.Logger;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.args.GenericArguments;
import org.spongepowered.api.command.spec.CommandSpec;
import org.spongepowered.api.config.DefaultConfig;
import org.spongepowered.api.data.property.block.PassableProperty;
import org.spongepowered.api.entity.living.monster.Creeper;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.api.event.cause.NamedCause;
import org.spongepowered.api.event.game.state.GameStartedServerEvent;
import org.spongepowered.api.event.world.ExplosionEvent;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.plugin.PluginContainer;
import org.spongepowered.api.plugin.PluginManager;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.format.TextColors;
import org.spongepowered.api.world.BlockChangeFlag;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;
import org.spongepowered.api.world.biome.BiomeGenerationSettings;
import org.spongepowered.api.world.biome.BiomeType;
import org.spongepowered.api.world.explosion.Explosion;
import org.spongepowered.api.world.gen.PopulatorObject;
import org.spongepowered.api.world.gen.populator.Forest;
import org.spongepowered.api.world.gen.type.BiomeTreeTypes;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Random;

@Plugin(id = "treepers", name = "Treepers", version = "5.1.3", authors = "Knechtcraft",
        url = "https://github.com/Knechtcraft/treepers-sponge",
        description = "Stops Creepers from destroying blocks and plants trees instead.")
public class Treepers {

    @DefaultConfig(sharedRoot = false)
    @Inject
    private ConfigurationLoader<CommentedConfigurationNode> configLoader;
    @Inject
    private Logger logger;

    @Inject
    private PluginContainer container;

    private Random random;
    private PopulatorObject fallbackTreePopulator;
    private Config config;

    @Listener
    public void onServerStart(GameStartedServerEvent event) throws IOException {
        fallbackTreePopulator = BiomeTreeTypes.OAK.getPopulatorObject();
        random = new Random();

        logger.info("Treepers PluginContainer: " + container);

        //Setup configuration
        try {
            CommentedConfigurationNode node = configLoader.load();
            config = Config.MAPPER.bindToNew().populate(node);
            Config.MAPPER.bind(config).serialize(node);
            configLoader.save(node);
        } catch (ObjectMappingException e) {
            logger.error("Couldn't populate Config!", e);
        }

        //Reload command
        CommandSpec reloadCommand = CommandSpec.builder().permission("treepers.reload").description(Text.of("Reloads the treepers config"))
                .arguments(GenericArguments.literal(Text.of("reload"), "reload")).executor((src, args) -> {
                    if (args.hasAny("reload")) {
                        reloadConfig();
                        src.sendMessage(Text.builder("[Treepers] Config Reloaded!").color(TextColors.GREEN).build());
                    }
                    return CommandResult.empty();
                }).build();

        Sponge.getCommandManager().register(this, reloadCommand, "treepers");
    }

    public void reloadConfig() {
        try {
            config = Config.MAPPER.bindToNew().populate(configLoader.load());
        } catch (ObjectMappingException e) {
            logger.error("Couldn't repopulate Config!", e);
        } catch (IOException e) {
            logger.error("Couldn't open or didn't have access to config file!", e);
        }
    }

    @Listener
    public void onExplode(ExplosionEvent.Pre event) {
        Cause cause = event.getCause();
        Object root = cause.root();

        //Did a creeper directly cause this ExplosionEvent?
        boolean isCreeper = root instanceof Creeper;
        if (isCreeper) {
            preventExplosion(event);
            if (config.PLANT_TREE) {
                plantTree(event);
            }
        }
    }

    private void preventExplosion(ExplosionEvent.Pre event) {
        //"Clone" explosion, as we cannot change the existing one?
        Explosion old = event.getExplosion();
        Explosion newExplosion = Explosion.builder()
                .from(old)
                .shouldBreakBlocks(config.BREAK_BLOCKS)
                .shouldPlaySmoke(config.SHOW_PARTICLES)
                .sourceExplosive(null) //Do not check for a creeper in next Event listener Iteration
                .build();

        //Cancel default event...
        event.setCancelled(true);
        //...but trigger new event
        Cause genericCause = Cause.of(NamedCause.owner(container));
        newExplosion.getWorld().triggerExplosion(newExplosion, genericCause);

        //Remove the creeper (as the default event is canceled);
        ((Creeper) event.getCause().root()).remove();
    }

    private void plantTree(ExplosionEvent.Pre event) {
        Location explosionLocation = event.getExplosion().getLocation();
        int x = (int) explosionLocation.getX();
        int y = (int) explosionLocation.getY();
        int z = (int) explosionLocation.getZ();

        //Evaluating the tree type, based on biome
        World world = event.getTargetWorld();
        BiomeType biome = world.getBiome(x, z);
        BiomeGenerationSettings biomeSettings = world.getWorldGenerator().getBiomeSettings(biome);
        List<Forest> forestPopulators = biomeSettings.getPopulators(Forest.class);

        PopulatorObject populator;
        try {
            //There may be multiple tree types in one Biome. Get one per weighted random
            populator = forestPopulators.isEmpty() ? fallbackTreePopulator : forestPopulators.get(0).getTypes().get(random).get(0);
        } catch (Exception e) {
            populator = fallbackTreePopulator;
        }

        //if the explosion happened on level 0, set it to 1, so we can place a dirt block below
        if (y == 0) {
            y = 1;
        }

        Vector3i pos = new Vector3i(x, y, z);
        Vector3i below = new Vector3i(x, y - 1, z);

        Cause genericCause = Cause.of(NamedCause.owner(container));


        //set Dirt block below if possible (Trees cannot be placed without)
        BlockState blockBelow = world.containsBlock(below) ? world.getBlock(below) : null;
        if (blockBelow != null) {
            BlockState blockState = BlockState.builder().blockType(BlockTypes.DIRT).build();

            world.setBlock(below, blockState, genericCause);
        }

        //Remove Grass, redstone, etc.
        BlockState blockOnPosition = world.getBlock(pos);

        Optional<PassableProperty> passableProperty_Op = blockOnPosition.getProperty(PassableProperty.class);
        boolean blockPassable = passableProperty_Op.isPresent() && passableProperty_Op.get().getValue();

        boolean passableBlockRemoved = false;
        if (blockPassable) {
            world.setBlock(pos, BlockState.builder().blockType(BlockTypes.AIR).build(), BlockChangeFlag.NEIGHBOR, genericCause);
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
            world.setBlock(pos, blockOnPosition, genericCause);

        }

        //Replace block below with original Block (unless the tree was placed in mid-air or water)
        if (blockBelow != null && !(treePlaced && (blockBelow.getType() == BlockTypes.AIR ||
                blockBelow.getType() == BlockTypes.WATER ||
                blockBelow.getType() == BlockTypes.FLOWING_WATER))) {
            world.setBlock(below, blockBelow, genericCause);
        }
    }
}

