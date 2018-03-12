/*
 * This file is part of project Orion, licensed under the MIT License (MIT).
 *
 * Copyright (c) Original contributors ("I don't care" license? See https://github.com/Mojang/LegacyLauncher/issues/1)
 * Copyright (c) 2017-2018 Mark Vainomaa <mikroskeem@mikroskeem.eu>
 * Copyright (c) Contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package eu.mikroskeem.test.launchwrapper;

import net.minecraft.launchwrapper.Launch;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.spongepowered.lwts.runner.LaunchWrapperTestRunner;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

/**
 * @author Mark Vainomaa
 */
@RunWith(LaunchWrapperTestRunner.class)
public class LegacyLauncherTest {
    @Test
    public void testScriptEngine() throws Exception {
        ScriptEngineManager sem = new ScriptEngineManager(Launch.classLoader);
        ScriptEngine nashorn = sem.getEngineByName("nashorn");
        Assert.assertNotNull(nashorn); // TODO: figure out why this keeps failing. See issue #2

        Object result = nashorn.eval("1 + 1");
        Assert.assertEquals(2, result);
    }

    /*@Test
    public void testClassTransforming() throws Exception {
        String className = "eu.mikroskeem.test.launchwrapper.targets.TransformableClass";

        Class<?> clazz = Class.forName(className);
    }*/
}
