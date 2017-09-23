package net.minecraft.launchwrapper;

import org.jetbrains.annotations.NotNull;


/**
 * Class transformer interface
 */
public interface IClassTransformer {
    /**
     * Transforms class
     *
     * @param name             Class unmapped name
     * @param transformedName  Class mapped name, may equal to unmapped name
     * @param classData       Raw class data
     * @return Transformed (or supplied) class data
     */
    @NotNull
    byte[] transform(@NotNull String name, @NotNull String transformedName, @NotNull byte[] classData);
}
