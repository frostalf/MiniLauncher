/*
 * MIT License
 *
 * Copyright (c) 2018 Ammar Ahmad
 * Copyright (c) 2018 Martin Benndorf
 * Copyright (c) 2018 Mark Vainomaa
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package me.minidigger.minecraftlauncher.api;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import me.minidigger.minecraftlauncher.api.events.LauncherEventHandler;
import me.minidigger.minecraftlauncher.api.patch.JarPatcher;

/**
 * @author ammar
 */
public class LauncherAPI {
    private final static Logger logger = LoggerFactory.getLogger(LauncherAPI.class);
    private final static Marker downloadMarker = MarkerFactory.getMarker("DOWNLOAD");
    private final static Marker runMarker = MarkerFactory.getMarker("RUN");

    private LauncherEventHandler eventHandler = new LauncherEventHandler() {
    };

    @NonNull
    public LauncherEventHandler getEventHandler() {
        return eventHandler;
    }

    public void setEventHandler(@Nullable LauncherEventHandler eventHandler) {
        this.eventHandler = eventHandler != null ? eventHandler : new LauncherEventHandler() {
        };
    }

    public List<String> getInstallableVersionsList() {
        Local local = new Local();
        local.readJson_versions_id(Utils.getMineCraft_Version_Manifest_json());
        local.readJson_versions_type(Utils.getMineCraft_Version_Manifest_json());
        //local.version_manifest_versions_id;
        List<String> InstallableVersionsList = new ArrayList<>();

        if (local.version_manifest_versions_id.size() == local.version_manifest_versions_type.size()) {
            //we can merge them..
            for (int i = 0; i < local.version_manifest_versions_id.size(); i++) {
                InstallableVersionsList.add(local.version_manifest_versions_id.get(i) + " % " + local.version_manifest_versions_type.get(i));
            }
        } else {
            //don't merge them..

            InstallableVersionsList.addAll(local.version_manifest_versions_id);
        }
        return InstallableVersionsList;
    }

    private List<String> getProfileInstalledVersionsList() {
        Local local = new Local();
        local.readJson_profiles_KEY(Utils.getMineCraft_Launcher_Profiles_json());
        local.readJson_profiles_KEY_lastVersionId(Utils.getMineCraft_Launcher_Profiles_json());
        return local.profiles_lastVersionId;
    }

    public List<String> getInstalledVersionsList() {
        Local local = new Local();
        local.generateVersionJsonPathList(Utils.getMineCraftVersionsLocation());
        local.generateVersionList(Utils.getMineCraftVersionsLocation());

        return local.versions_list;
    }

    @NonNull
    public List<ServerListEntry> getServersIPList() {
        return Utils.getMinecraftClientServerList();
    }

    public void addServerToServersDat(@NonNull String name, @NonNull String ip) {
        List<ServerListEntry> newList = new ArrayList<>();
        newList.add(ServerListEntry.of(null, name, ip));
        newList.addAll(Utils.getMinecraftClientServerList());
        Utils.setMinecraftClientServerList(newList);
    }

    public void syncVersions() {
        Local local = new Local();
        //this function is used to sync json and file system versions together.
        local.fixLauncherProfiles(); //<-- just fix it!
        LauncherAPI api_Interface = new LauncherAPI();

        List<String> ProfileInstalledVersionsList;    //json
        List<String> InstalledVersionsList;           //filesys

        InstalledVersionsList = api_Interface.getInstalledVersionsList();    //get json
        ProfileInstalledVersionsList = api_Interface.getProfileInstalledVersionsList();    //get filesys

        List<String> union = new ArrayList<>(InstalledVersionsList);
        union.addAll(ProfileInstalledVersionsList);
        // Prepare an intersection
        List<String> intersection = new ArrayList<>(InstalledVersionsList);
        intersection.retainAll(ProfileInstalledVersionsList);
        // Subtract the intersection from the union
        union.removeAll(intersection);
        union.removeAll(ProfileInstalledVersionsList); //this is required so that we can get rid of redundant versions
        // Print the result
        if (!union.isEmpty()) {
            for (Object n : union) {
                //add these versions to the system.
                if (n != null) {
                    local.writeJson_launcher_profiles(n.toString() + "_Cracked_" + Utils.nextSessionId(), n.toString());
                }
            }
        }
    }

    private void injectNetty() {
        /* some day...
        try {
            Utils.injectNetty();
        } catch (Exception ex) {
            logger.warn("Failed to inject Netty", ex);
        }

        try {
            Utils.injectPatchy();
        } catch (Exception ex) {
            logger.warn("Failed to inject Mojang Patchy", ex);
        }
        */
    }

    public void runMinecraft(String UsernameToUse, String VersionToUse, Boolean HashCheck, Boolean injectNetty) {
        Local local = new Local();
        //get list of all 
        local.readJson_versions_id(Utils.getMineCraft_Version_Manifest_json());
        local.readJson_versions_type(Utils.getMineCraft_Version_Manifest_json());
        local.readJson_versions_url(Utils.getMineCraft_Version_Manifest_json());

        //inject netty
        if (injectNetty) {
            injectNetty();
        }

        //declaration for mods
        String MOD_inheritsFrom = null;
        String MOD_jar = null;
        String MOD_assets = null;
        String MOD_minecraftArguments;
        String MOD_mainClass = null;
        String MOD_id = null;
        //check if it is vanilla or not
        if (local.checkIfVanillaMC(VersionToUse).equals(true)) {
            logger.info(runMarker, "Vanilla Minecraft found!");
        } else {
            logger.info(runMarker, "Modded Minecraft found!");
            local.MOD_readJson_libraries_name_PLUS_url(Utils.getMineCraft_Version_Json(VersionToUse));
            for (int i = 0; i < local.version_name_list.size(); i++) {
                logger.info(runMarker, local.version_name_list.get(i));
                logger.info(runMarker, local.HALF_URL_version_url_list.get(i));
            }

            logger.info(runMarker, "Fixing url using name.");
            for (int i = 0; i < local.version_name_list.size(); i++) {
                local.version_path_list.add(local.generateLibrariesPath(local.version_name_list.get(i)));

            }

            for (int i = 0; i < local.version_name_list.size(); i++) {
                local.version_url_list.add(local.HALF_URL_version_url_list.get(i) + "/" + local.version_path_list.get(i));
            }

            MOD_inheritsFrom = local.readJson_inheritsFrom(Utils.getMineCraft_Version_Json(VersionToUse));
            logger.info(runMarker, "inheritsFrom: " + MOD_inheritsFrom);

            MOD_jar = local.readJson_jar(Utils.getMineCraft_Version_Json(VersionToUse));
            logger.info(runMarker, "jar: " + MOD_jar);

            MOD_assets = local.readJson_assets(Utils.getMineCraft_Version_Json(VersionToUse));
            logger.info(runMarker, "assets: " + MOD_assets);

            MOD_minecraftArguments = local.readJson_minecraftArguments(Utils.getMineCraft_Version_Json(VersionToUse));
            logger.info(runMarker, "minecraftArguments: " + MOD_minecraftArguments);

            MOD_mainClass = local.readJson_mainClass(Utils.getMineCraft_Version_Json(VersionToUse));
            logger.info(runMarker, "mainClass: " + MOD_mainClass);

            MOD_id = local.readJson_id(Utils.getMineCraft_Version_Json(VersionToUse));
            logger.info(runMarker, "id: " + MOD_id);
        }

        if (MOD_inheritsFrom == null) {
            logger.info(runMarker, "Using: " + VersionToUse);

        } else {
            VersionToUse = MOD_inheritsFrom;
            logger.info(runMarker, "Using: " + VersionToUse);

        }

        //incase the url is empty.. we have to assume that the user has old path system.
        for (int i = 0; i < local.version_manifest_versions_id.size(); i++) {
            logger.debug(runMarker, local.version_manifest_versions_id.get(i));
            logger.debug(runMarker, local.version_manifest_versions_type.get(i));
            logger.debug(runMarker, local.version_manifest_versions_url.get(i));
        }

        //download 1.7.10.json_libs
        try {
            for (int i = 0; i < local.version_manifest_versions_id.size(); i++) {
                if (local.version_manifest_versions_id.get(i).equals(VersionToUse)) {
                    //we will download versionjson everytime.
                    if (HashCheck) {
                        Network.downloadVersionJson(local.version_manifest_versions_url.get(i), local.version_manifest_versions_id.get(i));
                    }
                    break;
                } else {
                    //do nothing...
                }
            }

        } catch (Exception e) {
            logger.error("Something went wrong downloadVersionJson" + e);
        }

        logger.info(runMarker, "{}", Utils.getMinecraftDataDirectory());

        local.generateVersionJsonPathList(Utils.getMineCraftVersionsLocation());
        local.generateVersionList(Utils.getMineCraftVersionsLocation());

        for (int i = 0; i < local.versions_json_path_list.size(); i++) {
            logger.info(runMarker, local.versions_json_path_list.get(i));
        }

        for (int i = 0; i < local.versions_list.size(); i++) {
            logger.info(runMarker, local.versions_list.get(i));
        }

        logger.info(runMarker, "{}", Utils.getMineCraft_Version_Json(VersionToUse));

        try {
            local.readJson_libraries_downloads_artifact_url(Utils.getMineCraft_Version_Json(VersionToUse));

        } catch (Exception ex) {
            logger.error("Unable to get libraries_downloads_artifact_url " + ex);
        }
        try {
            local.readJson_libraries_downloads_artifact_path(Utils.getMineCraft_Version_Json(VersionToUse));

        } catch (Exception ex) {
            logger.error("Unable to get libraries_downloads_artifact_path " + ex);
        }
        try {
            local.readJson_libraries_name(Utils.getMineCraft_Version_Json(VersionToUse));

        } catch (Exception ex) {
            logger.error("Unable to get libraries_name " + ex);
        }

        try {
            logger.info(runMarker, local.readJson_assetIndex_url(Utils.getMineCraft_Version_Json(VersionToUse)));

        } catch (Exception ex) {
            logger.error("Unable to get assetIndex_url" + ex);
        }
        try {
            logger.info(runMarker, local.readJson_assetIndex_id(Utils.getMineCraft_Version_Json(VersionToUse)));
        } catch (Exception ex) {
            logger.error("Unable to get assetIndex_id" + ex);
        }

        logger.info(runMarker, "{}", Utils.getMineCraftAssetsIndexes_X_json(VersionToUse));

        try {
            local.readJson_objects_KEY(Utils.getMineCraftAssetsIndexes_X_json(local.readJson_assetIndex_id(Utils.getMineCraft_Version_Json(VersionToUse))));
        } catch (Exception e) {
            logger.error("Error reading objects KEY" + e);
        }
        try {
            local.readJson_objects_KEY_hash(Utils.getMineCraftAssetsIndexes_X_json(local.readJson_assetIndex_id(Utils.getMineCraft_Version_Json(VersionToUse))));
        } catch (Exception e) {
            logger.error("Error reading objects KEY_hash" + e);

        }

        if (HashCheck) {
            eventHandler.onGameStart(LauncherEventHandler.StartStatus.VALIDATING);
            try {
                for (int i = 0; i < local.objects_hash.size(); i++) {
                    logger.info(runMarker, "HASH: " + local.objects_hash.get(i));
                    logger.info(runMarker, "FOLDER: " + local.objects_hash.get(i).substring(0, 2));
                    logger.info(runMarker, "KEY: " + local.objects_KEY.get(i));
                    Utils.copyToVirtual(local.objects_hash.get(i).substring(0, 2), local.objects_hash.get(i), local.objects_KEY.get(i));
                    //generate virtual folder as well.

                }

            } catch (Exception e) {
                logger.error("Error reading objects KEY + KEY_hash" + e);

            }
        }

        eventHandler.onGameStart(LauncherEventHandler.StartStatus.DOWNLOADING_NATIVES);

        logger.info(runMarker, "Getting NATIVES URL");
        local.readJson_libraries_downloads_classifiers_natives_X(Utils.getMineCraft_Versions_X_X_json(VersionToUse));
        logger.info(runMarker, "Getting NATIVES PATH");
        local.readJson_libraries_downloads_classifiers_natives_Y(Utils.getMineCraft_Versions_X_X_json(VersionToUse));

        for (int i = 0; i < local.version_url_list_natives.size(); i++) {
            logger.info(runMarker, "NATIVE URL: " + local.version_url_list_natives.get(i));
            //extract them here..
            logger.info(runMarker, "Extracting... {} {}", local.version_url_list_natives.get(i), Utils.getMineCraft_Versions_X_Natives_Location(VersionToUse));

            Utils.jarExtract(Paths.get(local.version_path_list_natives.get(i)), Utils.getMineCraft_Versions_X_Natives_Location(VersionToUse));
        }

        //String HalfArgumentTemplate = local.readJson_minecraftArguments(Utils.getMineCraft_Versions_X_X_json(VersionToUse));
        int Xmx = this.getMemory();
        int Xms = this.getMinMemory();
        int Width = this.getWidth();
        int Height = this.getHeight();
        String JavaPath = this.getJavaPath();
        String JVMArgument = this.getJVMArgument();


        String mainClass;
        if (MOD_mainClass == null) {
            mainClass = local.readJson_mainClass(Utils.getMineCraft_Versions_X_X_json(VersionToUse));

        } else {
            mainClass = MOD_mainClass;
        }

        Path NativesDir = Utils.getMineCraft_Versions_X_Natives(VersionToUse);
        String assetsIdexId;
        if (MOD_assets == null) {
            assetsIdexId = local.readJson_assets(Utils.getMineCraft_Versions_X_X_json(VersionToUse));

        } else {
            assetsIdexId = MOD_assets;
        }
        if (assetsIdexId == null) {
            assetsIdexId = "NULL";
        }

        Path gameDirectory = Utils.getMinecraftDataDirectory();
        Path AssetsRoot = Utils.getMineCraftAssetsRootLocation();

        String versionName;
        if (MOD_id == null) {
            versionName = local.readJson_id(Utils.getMineCraft_Versions_X_X_json(VersionToUse));
        } else {
            versionName = MOD_id;
        }

        String authuuid = local.readJson_id(Utils.getMineCraft_X_json(UsernameToUse));
        String Username = UsernameToUse;
        Path MinecraftJar;
        if (MOD_jar == null) {
            MinecraftJar = Utils.getMineCraft_Versions_X_X_jar(VersionToUse);
        } else {
            MinecraftJar = Utils.getMineCraft_Versions_X_X_jar(MOD_jar);
        }

        String VersionType = this.getVersionData();
        String AuthSession = "OFFLINE";

        Path GameAssets = Utils.getMineCraftAssetsVirtualLegacyLocation();
        logger.debug("NativesPath: " + NativesDir);

        for (int i = 0; i < local.version_path_list.size(); i++) {
            local.libraries_path.add(Utils.setMineCraft_librariesLocation(local.version_path_list.get(i)).toString());
            logger.debug(local.libraries_path.get(i));
        }

        String HalfLibraryArgument = local.generateLibrariesArguments();
        String FullLibraryArgument = local.generateLibrariesArguments() + Utils.getArgsDiv() + MinecraftJar;
        logger.debug("HalfLibraryArgument: " + HalfLibraryArgument);
        logger.debug("FullLibraryArgument: " + FullLibraryArgument);

        //argument patch for netty and patchy comes here
        if (injectNetty) {
            eventHandler.onGameStart(LauncherEventHandler.StartStatus.PATCHING_NETTY);
            logger.debug("Netty/Patchy Patch Detected!");

            Path minecraftJar = Utils.getMineCraft_Versions_X_X_jar_Location(VersionToUse);
            new JarPatcher().patchWindowTitle(minecraftJar, VersionToUse, "Minecraft " + VersionToUse + " - " + UsernameToUse);

            String patchy_mod = "";
            String patchy = "";

            String netty_mod = "";
            String netty = "";


            /*try {
                Map<String, String> patchyMAP = new HashMap<>(Utils.getMineCraftLibrariesComMojangPatchy_jar());
                for (Map.Entry<String, String> entry : patchyMAP.entrySet()) {
                    String key = entry.getKey();
                    String value = entry.getValue();

                    if (value.startsWith("mod_")) {
                        patchy_mod = value;
                    } else {
                        patchy = value;
                    }

                    logger.debug("KEY:::::" + key);
                    logger.debug("VALUE:::::" + value);
                }
                HalfLibraryArgument = HalfLibraryArgument.replace(patchy, patchy_mod);
                FullLibraryArgument = FullLibraryArgument.replace(patchy, patchy_mod);
            } catch (Exception ex) {
                ex.printStackTrace();
            }

            try {
                Map<String, String> nettyMAP = new HashMap<>(Utils.getMineCraftLibrariesComMojangNetty_jar());
                for (Map.Entry<String, String> entry : nettyMAP.entrySet()) {
                    String key = entry.getKey();
                    String value = entry.getValue();

                    if (value.startsWith("mod_")) {
                        netty_mod = value;
                    } else {
                        netty = value;
                    }

                    logger.debug("KEY:::::" + key);
                    logger.debug("VALUE:::::" + value);
                }
                HalfLibraryArgument = HalfLibraryArgument.replace(netty, netty_mod);
                FullLibraryArgument = FullLibraryArgument.replace(netty, netty_mod);

            } catch (Exception ex) {
                ex.printStackTrace();
            }*/

        }
        //argument patch netty and patchy ends here

        String[] HalfArgument = local.generateMinecraftArguments(Username, versionName, gameDirectory, AssetsRoot, assetsIdexId, authuuid, "aeef7bc935f9420eb6314dea7ad7e1e5", "{\"twitch_access_token\":[\"emoitqdugw2h8un7psy3uo84uwb8raq\"]}", "mojang", VersionType, GameAssets, AuthSession);
        //logger.debug("HalfArgument: " + HalfArgument);
        for (String HalfArgsVal : HalfArgument) {
            logger.debug("HalfArg: " + HalfArgsVal);
        }
        logger.debug("Minecraft.jar: " + MinecraftJar);

        logger.info(runMarker, "username: " + Username);
        logger.info(runMarker, "version number: " + versionName);
        logger.info(runMarker, "game directory: " + gameDirectory);
        logger.info(runMarker, "assets root directory: " + AssetsRoot);
        logger.info(runMarker, "assets Index Id: " + assetsIdexId);
        logger.info(runMarker, "assets legacy directory: " + GameAssets);
        //won't be using this
        //logger.info(runMarker, local.generateRunnableArguments(Xmx, NativesDir, FullLibraryArgument, mainClass, HalfArgument));

        try {
            String cmds[] = {"-Xms" + Xms + "M", "-Xmx" + Xmx + "M", "-XX:HeapDumpPath=MojangTricksIntelDriversForPerformance_javaw.exe_minecraft.exe.heapdump", "-Djava.library.path=" + NativesDir, "-cp", FullLibraryArgument, mainClass, "--width", String.valueOf(Width), "--height", String.valueOf(Height)};
            //put jvm arguments here
            String[] JVMArguments = JVMArgument.split(" ");
            //we now have all the arguments. merge cmds with JVMArguments
            if (!JVMArgument.isEmpty()) {
                //no need to join.
                cmds = Stream.concat(Arrays.stream(JVMArguments), Arrays.stream(cmds)).toArray(String[]::new);
            }
            String javaPathArr[] = {JavaPath};
            //merge javapath back to cmds
            cmds = Stream.concat(Arrays.stream(javaPathArr), Arrays.stream(cmds)).toArray(String[]::new);

            String[] finalArgs = Stream.concat(Arrays.stream(cmds), Arrays.stream(HalfArgument)).toArray(String[]::new);
            for (String finalArgs_ : finalArgs) {
                logger.info(runMarker, finalArgs_);
            }
            eventHandler.onGameStart(LauncherEventHandler.StartStatus.STARTING);
            logger.info(runMarker, "Starting game... Please wait....");
            Process process = Runtime.getRuntime().exec(finalArgs);

            new MinecraftLogThread(process.getInputStream());

            try {
                process.waitFor(10, TimeUnit.SECONDS);
                //process.waitFor();
                if (process.exitValue() != 0) {
                    //something went wrong.
                    eventHandler.onGameCorrupted(process.exitValue());
                    logger.error("Minecraft Corruption found!");
                }
            } catch (Exception ex) {
                //nothing to print.. everything went fine.
                eventHandler.onGameStarted();
                logger.info(runMarker, "Minecraft Initialized!");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String jvmArgument = "";

    public void setJVMArgument(String jvmArgument_) {
        jvmArgument = jvmArgument_;
    }

    private String getJVMArgument() {
        return jvmArgument;
    }

    private String javaPath = "java";

    public void setJavaPath(String javaPath_) {
        javaPath = javaPath_;
    }

    private String getJavaPath() {
        return javaPath;
    }

    private int width = 854;

    public void setWidth(int width_) {
        width = width_;
    }

    private int getWidth() {
        return width;
    }

    private int height = 480;

    public void setHeight(int height_) {
        height = height_;
    }

    private int getHeight() {
        return height;
    }

    private int memory = 1024;

    public void setMemory(int memory_) {
        memory = memory_;
    }

    private int getMemory() {
        return memory;
    }

    private int minMemory = 1024;

    public void setMinMemory(int memory_) {
        minMemory = memory_;
    }

    private int getMinMemory() {
        return minMemory;
    }

    private String versionData = "#ammarbless";

    public void setVersionData(String versionData_) {
        versionData = versionData_;
    }

    private String getVersionData() {
        return versionData;
    }

    public void downloadVersionManifest() {
        logger.debug("Downloading: version_manifest.json");
        Network.downloadVersionManifest(Utils.getMineCraft_Version_Manifest_json());

    }

    public void downloadProfile(String UsernameToUse) {
        logger.debug("Downloading: " + UsernameToUse + ".json");
        Network.downloadProfile(UsernameToUse);

    }

    public void downloadMinecraft(String VersionToUse, Boolean ForceDownload) {
        Local local = new Local();
        logger.info(downloadMarker, "Downlaoding: " + VersionToUse);

        //add version in launcher_profiles.json
        local.writeJson_launcher_profiles("_Cracked_" + Utils.nextSessionId() + "_" + VersionToUse, VersionToUse);

        //get list of all 
        local.readJson_versions_id(Utils.getMineCraft_Version_Manifest_json());
        local.readJson_versions_type(Utils.getMineCraft_Version_Manifest_json());
        local.readJson_versions_url(Utils.getMineCraft_Version_Manifest_json());

        //declaration for mods
        String MOD_inheritsFrom = null;
        String MOD_jar = null;
        String MOD_assets;
        String MOD_minecraftArguments;
        String MOD_mainClass;
        String MOD_id;
        //check if it is vanilla or not
        if (local.checkIfVanillaMC(VersionToUse).equals(true)) {
            logger.info(runMarker, "Vanilla Minecraft found!");

        } else {
            logger.info(runMarker, "Modded Minecraft found!");
            local.MOD_readJson_libraries_name_PLUS_url(Utils.getMineCraft_Version_Json(VersionToUse));
            for (int i = 0; i < local.version_name_list.size(); i++) {
                logger.debug(local.version_name_list.get(i));
                logger.debug(local.HALF_URL_version_url_list.get(i));
            }

            logger.info(downloadMarker, "Fixing url using name.");
            for (int i = 0; i < local.version_name_list.size(); i++) {
                local.version_path_list.add(local.generateLibrariesPath(local.version_name_list.get(i)));

            }

            for (int i = 0; i < local.version_name_list.size(); i++) {
                local.version_url_list.add(local.HALF_URL_version_url_list.get(i) + "/" + local.version_path_list.get(i));
            }
            for (int i = 0; i < local.version_name_list.size(); i++) {
                logger.info(downloadMarker, "Downloading: " + local.version_url_list.get(i));
                Network.downloadLibraries(local.version_url_list.get(i), local.version_path_list.get(i), ForceDownload);

            }

            MOD_inheritsFrom = local.readJson_inheritsFrom(Utils.getMineCraft_Version_Json(VersionToUse));
            logger.info(downloadMarker, "inheritsFrom: " + MOD_inheritsFrom);

            MOD_jar = local.readJson_jar(Utils.getMineCraft_Version_Json(VersionToUse));
            logger.info(downloadMarker, "jar: " + MOD_jar);

            MOD_assets = local.readJson_assets(Utils.getMineCraft_Version_Json(VersionToUse));
            logger.info(downloadMarker, "assets: " + MOD_assets);

            MOD_minecraftArguments = local.readJson_minecraftArguments(Utils.getMineCraft_Version_Json(VersionToUse));
            logger.info(downloadMarker, "minecraftArguments: " + MOD_minecraftArguments);

            MOD_mainClass = local.readJson_mainClass(Utils.getMineCraft_Version_Json(VersionToUse));
            logger.info(downloadMarker, "mainClass: " + MOD_mainClass);

            MOD_id = local.readJson_id(Utils.getMineCraft_Version_Json(VersionToUse));
            logger.info(downloadMarker, "id: " + MOD_id);
        }

        if (MOD_inheritsFrom == null) {
            logger.info(downloadMarker, "Using: " + VersionToUse);

        } else {
            VersionToUse = MOD_inheritsFrom;
            logger.info(downloadMarker, "Using: " + VersionToUse);

        }

        //incase the url is empty.. we have to assume that the user has old path system.
        eventHandler.onDownload(LauncherEventHandler.DownloadingStatus.LAUNCHER_META);
        for (int i = 0; i < local.version_manifest_versions_id.size(); i++) {
            logger.info(downloadMarker, "ID: " + local.version_manifest_versions_id.get(i));
            logger.info(downloadMarker, "TYPE: " + local.version_manifest_versions_type.get(i));
            logger.info(downloadMarker, "URL: " + local.version_manifest_versions_url.get(i));
        }

        //download 1.7.10.json_libs
        try {
            for (int i = 0; i < local.version_manifest_versions_id.size(); i++) {
                if (local.version_manifest_versions_id.get(i).equals(VersionToUse)) {
                    Network.downloadVersionJson(local.version_manifest_versions_url.get(i), local.version_manifest_versions_id.get(i));
                    break;
                } else {
                    //do nothing...
                }
            }

        } catch (Exception e) {
            logger.error("Something went wrong getting version json" + e);
        }

        logger.info(runMarker, "{}", Utils.getMinecraftDataDirectory());

        local.generateVersionJsonPathList(Utils.getMineCraftVersionsLocation());
        local.generateVersionList(Utils.getMineCraftVersionsLocation());

        try {
            local.readJson_libraries_downloads_artifact_url(Utils.getMineCraft_Version_Json(VersionToUse));

        } catch (Exception ex) {
            logger.error("Exception" + ex);

        }
        try {
            local.readJson_libraries_downloads_artifact_path(Utils.getMineCraft_Version_Json(VersionToUse));

        } catch (Exception ex) {
            logger.error("Exception" + ex);

        }
        try {
            local.readJson_libraries_name(Utils.getMineCraft_Version_Json(VersionToUse));

        } catch (Exception ex) {
            logger.error("Exception" + ex);

        }
        ///************************************************************
        eventHandler.onDownload(LauncherEventHandler.DownloadingStatus.LIBRARIES);
        for (int i = 0; i < local.version_url_list.size(); i++) {
            logger.info(downloadMarker, "Downloading: " + local.version_url_list.get(i));
            try {
                Network.downloadLibraries(local.version_url_list.get(i), local.version_path_list.get(i), ForceDownload);

            } catch (Exception ex) {
                logger.error("Due to: " + ex + " " + local.generateLibrariesPath(local.version_name_list.get(i)));
                local.version_path_list.add(local.generateLibrariesPath(local.version_name_list.get(i)));
                Network.downloadLibraries(local.version_url_list.get(i), local.generateLibrariesPath(local.version_name_list.get(i)), ForceDownload);

            }
        }

        //this may need to be edited!*************//
        logger.info(runMarker, local.readJson_assetIndex_url(Utils.getMineCraft_Version_Json(VersionToUse)));
        logger.info(runMarker, local.readJson_assetIndex_id(Utils.getMineCraft_Version_Json(VersionToUse)));
        //get assets index id!
        Network.downloadLaunchermeta(local.readJson_assetIndex_url(Utils.getMineCraft_Version_Json(VersionToUse)), local.readJson_assetIndex_id(Utils.getMineCraft_Version_Json(VersionToUse)), ForceDownload);

        logger.info(runMarker, "{}", Utils.getMineCraftAssetsIndexes_X_json(VersionToUse));

        local.readJson_objects_KEY(Utils.getMineCraftAssetsIndexes_X_json(local.readJson_assetIndex_id(Utils.getMineCraft_Version_Json(VersionToUse))));
        local.readJson_objects_KEY_hash(Utils.getMineCraftAssetsIndexes_X_json(local.readJson_assetIndex_id(Utils.getMineCraft_Version_Json(VersionToUse))));

        eventHandler.onDownload(LauncherEventHandler.DownloadingStatus.ASSETS);
        for (int i = 0; i < local.objects_hash.size(); i++) {
            logger.info(downloadMarker, "HASH: " + local.objects_hash.get(i));
            logger.info(downloadMarker, "FOLDER: " + local.objects_hash.get(i).substring(0, 2));
            logger.info(downloadMarker, "KEY: " + local.objects_KEY.get(i));

            logger.info(downloadMarker, "DOWNLOADING..." + "HASH: " + local.objects_hash.get(i));
            Network.downloadAssetsObjects(local.objects_hash.get(i).substring(0, 2), local.objects_hash.get(i));
            Utils.copyToVirtual(local.objects_hash.get(i).substring(0, 2), local.objects_hash.get(i), local.objects_KEY.get(i));
            //generate virtual folder as well.

        }

        eventHandler.onDownload(LauncherEventHandler.DownloadingStatus.MINECRAFT);
        logger.info(downloadMarker, "DOWNLOADING MINECRAFT JAR " + VersionToUse);
        if (MOD_jar == null) {
            Network.downloadMinecraftJar(VersionToUse, ForceDownload);

        } else {
            Network.downloadMinecraftJar(MOD_jar, ForceDownload);

        }

        //would have tp edit this line as we also need natives paths!
        eventHandler.onDownload(LauncherEventHandler.DownloadingStatus.NATIVES);
        logger.info(downloadMarker, "Getting NATIVES URL");
        local.readJson_libraries_downloads_classifiers_natives_X(Utils.getMineCraft_Versions_X_X_json(VersionToUse));
        logger.info(downloadMarker, "Getting NATIVES PATH");
        local.readJson_libraries_downloads_classifiers_natives_Y(Utils.getMineCraft_Versions_X_X_json(VersionToUse));

        for (int i = 0; i < local.version_url_list_natives.size(); i++) {
            logger.info(downloadMarker, "NATIVE URL: " + local.version_url_list_natives.get(i));
            Network.downloadLibraries(local.version_url_list_natives.get(i), local.version_path_list_natives.get(i), ForceDownload);
            //extract them here..
            logger.info(runMarker, "Extracting... {} {}", local.version_url_list_natives.get(i), Utils.getMineCraft_Versions_X_Natives_Location(VersionToUse));

            Utils.jarExtract(Paths.get(local.version_path_list_natives.get(i)), Utils.getMineCraft_Versions_X_Natives_Location(VersionToUse));

        }
        eventHandler.onDownloadComplete();
        logger.info(downloadMarker, "Download Complete!");
    }

}
