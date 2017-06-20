package org.felfio.sponge.treepers;

import ninja.leaping.configurate.objectmapping.ObjectMapper;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;
import ninja.leaping.configurate.objectmapping.Setting;

public class Config {

    public final static ObjectMapper<Config> MAPPER;

    static  {
        try {
            MAPPER = ObjectMapper.forClass(Config.class);
        } catch (ObjectMappingException e) {
            throw new ExceptionInInitializerError("Couldn't initialize ObjectMapper for Config");
        }
    }

    @Setting(value = "BreakBlocks", comment = "Should the treeper explosion break blocks.")
    public boolean BREAK_BLOCKS = false;

    @Setting(value = "PlantTree", comment = "Should the treeper explosion plant a tree.")
    public boolean PLANT_TREE = true;

    @Setting(value = "ShowParticles", comment = "Should the explosion particles been shown. (NOTE: It seems that the methods provided in Sponge "
            + "don't work yet)")
    public boolean SHOW_PARTICLES = true;

    @Setting(value = "TreeperChance", comment = "How often should treepers appear?")
    public int TREEPER_CHANCE = 50;
}
