package com.kntrel.mc.underilla.paper.cleaning;

import com.kntrel.mc.underilla.paper.Underilla;
import com.kntrel.mc.underilla.paper.io.UnderillaConfig.SetEntityTypeKeys;
import com.kntrel.mc.underilla.paper.impl.BukkitIDs;
import org.bukkit.Chunk;
import org.bukkit.Registry;
import org.bukkit.entity.Entity;

public class CleanEntities {

    public static void cleanEntities(Chunk chunk) {
        for (Entity entity : chunk.getEntities()) {
            if (Underilla.getUnderillaConfig().isEntityTypeInSet(SetEntityTypeKeys.CLEAN_ENTITY_TO_REMOVE,
                    BukkitIDs.from(Registry.ENTITY_TYPE.getKeyOrThrow(entity.getType())))) {
                entity.remove();
                // Final transformation that can be override by other plugins
            } else if (Underilla.getInstance().hasEndEntityTransformer()) {
                Underilla.getInstance().getEndEntityTransformer().accept(entity);
            }
        }
    }
}
