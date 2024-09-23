/*
 * Copyright (C) 2019-2021 German Vekhorev (DarksideCode)
 *
 * This file is part of Keiko Plugin Inspector.
 *
 * Keiko Plugin Inspector is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Keiko Plugin Inspector is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Keiko Plugin Inspector.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.darksidecode.keiko.registry;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import me.darksidecode.jminima.phase.PhaseExecutionException;
import me.darksidecode.jminima.phase.PhaseExecutionWatcher;
import me.darksidecode.jminima.phase.basic.CloseJarFilePhase;
import me.darksidecode.jminima.phase.basic.OpenJarFilePhase;
import me.darksidecode.jminima.workflow.Workflow;
import me.darksidecode.jminima.workflow.WorkflowExecutionResult;
import me.darksidecode.keiko.config.GlobalConfig;
import me.darksidecode.keiko.config.InspectionsConfig;
import me.darksidecode.keiko.proxy.Keiko;
import me.darksidecode.keiko.util.Holder;
import me.darksidecode.keiko.util.StringUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

@Getter
@RequiredArgsConstructor (access = AccessLevel.PRIVATE)
public class PluginContext {

    private final Collection<IndexedPlugin> plugins;

    public IndexedPlugin getJarOwner(@NonNull File pluginJar) {
        return plugins.stream().filter(plugin
                -> plugin.jar().equals(pluginJar)).findAny().orElse(null);
    }

    public IndexedPlugin getClassOwner(@NonNull String className) {
        // Note: className in format "x.y.z", NOT "x/y/z"!
        return plugins.stream().filter(plugin
                -> plugin.classes().contains(className)).findAny().orElse(null);
    }

    public IndexedPlugin getPlugin(@NonNull String name) {
        return plugins.stream().filter(plugin
                -> plugin.name().equalsIgnoreCase(name)).findAny().orElse(null);
    }

    public static PluginContext currentContext() {
        Keiko.INSTANCE.getLogger().infoLocalized("pluginsIndex.beginning");

        Collection<File> pluginsFiles = new ArrayList<>();
        List<String> scannedDirs = InspectionsConfig.getHandle()
                .get("scanned_directories", new ArrayList<>());

        for (String dirPath : scannedDirs) {
            File dir = new File(StringUtils.basicReplacements(dirPath));
            File[] children;

            if (dir.isDirectory() && (children = dir.listFiles()) != null) {
                pluginsFiles.addAll(Arrays.asList(children));
                Keiko.INSTANCE.getLogger().debug(
                        "Directory will be scanned: %s", dir.getAbsolutePath());
            } else
                Keiko.INSTANCE.getLogger().debug(
                        "Directory will not be scanned: %s", dir.getAbsolutePath());
        }

        Collection<IndexedPlugin> indexedPlugins = new ArrayList<>();

        for (File file : pluginsFiles) {
            if (file.isFile() && file.getName().endsWith(".jar")) {
                Holder<IndexedPlugin> indexedPluginHolder = new Holder<>();

                try (Workflow workflow = new Workflow()
                        .phase(new OpenJarFilePhase(file))
                        .phase(new IndexPluginPhase(Keiko.INSTANCE.getEnv().getPlatform())
                                .watcher(new PhaseExecutionWatcher<IndexedPlugin>()
                                        .doAfterExecution((val, err) -> indexedPluginHolder.setValue(val))
                                ))
                        .phase(new CloseJarFilePhase())) {
                    WorkflowExecutionResult result = workflow.executeAll();

                    if (result == WorkflowExecutionResult.FATAL_FAILURE) {
                        Keiko.INSTANCE.getLogger().warningLocalized(
                                "pluginsIndex.indexErr", file.getName());

                        for (PhaseExecutionException error : workflow.getAllErrorsChronological())
                            Keiko.INSTANCE.getLogger().error("JMinima phase execution error:", error);

                        if (GlobalConfig.getAbortOnError())
                            return null;
                    } else if (indexedPluginHolder.hasValue()) {
                        IndexedPlugin plugin = indexedPluginHolder.getValue();
                        indexedPlugins.add(plugin);

                        Keiko.INSTANCE.getLogger().debugLocalized(
                                "pluginsIndex.indexedInfo",
                                plugin.name(), plugin.jar().getName(), plugin.classes().size());
                    } else {
                        Keiko.INSTANCE.getLogger().warningLocalized(
                                "pluginsIndex.indexErr", file.getName());
                        Keiko.INSTANCE.getLogger().error(
                                "IndexedPlugin Holder has no value, " +
                                        "but workflow execution result is %s", result);

                        if (GlobalConfig.getAbortOnError())
                            return null;
                    }
                }
            }
        }
        Keiko.INSTANCE.getLogger().debugLocalized(
                "pluginsIndex.numPluginsIndexed", indexedPlugins.size());

        return new PluginContext(indexedPlugins);
    }

}
