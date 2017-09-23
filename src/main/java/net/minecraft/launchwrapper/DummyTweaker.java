package net.minecraft.launchwrapper;

import java.util.List;

/**
 * @author Mark Vainomaa
 */
public class DummyTweaker implements ITweaker {
    @Override public void acceptOptions(List<String> args) {}
    @Override public void injectIntoClassLoader(LaunchClassLoader classLoader) {}
    @Override public String getLaunchTarget() { return DummyTweaker.class.getName(); }
    @Override public String[] getLaunchArguments() { return new String[0]; }

    public static void main(String... args){
        String[] text = new String[]{
                "This is dummy tweaker class. You should specify tweak class using ",
                "'--tweakClass' argument or hook into LegacyLauncher programmatically."
        };
        System.out.println(String.join("\n", text));
    }
}
