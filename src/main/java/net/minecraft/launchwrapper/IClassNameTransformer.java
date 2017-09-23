package net.minecraft.launchwrapper;

import org.jetbrains.annotations.NotNull;


/**
 * Class name transformer, useful for mostly obfuscated &lt;-&gt; mapped names
 */
public interface IClassNameTransformer {
    /**
     * Remap class name to mapped name
     *
     * @param name Unmapped class name
     * @return Remapped class name; or supplied class name if mapping was not found
     */
    @NotNull
    String remapClassName(@NotNull String name);

    /**
     * Unmap class name from remapped name
     *
     * @param name Mapped class name
     * @return Unmapped (original) class name; or supplied class name if mapping was not found
     */
    @NotNull
    String unmapClassName(@NotNull String name);
}
