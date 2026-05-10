package nl.rutgerkok.blocklocker.protection;

import java.util.Collection;
import java.util.Collections;

import org.bukkit.block.Block;

/**
 * Represents a protected container, like a chest or a furnace.
 *
 */
public interface ContainerProtection extends Protection {

    /**
     * Gets all blocks that belong to this protected container. For example, a
     * double chest returns both chest blocks.
     *
     * @return The protected container blocks.
     */
    default Collection<Block> getProtectedBlocks() {
        return Collections.singletonList(getSomeProtectedBlock());
    }

}
