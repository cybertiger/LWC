/*
 * Copyright (c) 2011, 2012, Tyler Blair
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 *    1. Redistributions of source code must retain the above copyright notice, this list of
 *       conditions and the following disclaimer.
 *
 *    2. Redistributions in binary form must reproduce the above copyright notice, this list
 *       of conditions and the following disclaimer in the documentation and/or other materials
 *       provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ''AS IS'' AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE AUTHOR OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * The views and conclusions contained in the software and documentation are those of the
 * authors and contributors and should not be interpreted as representing official policies,
 * either expressed or implied, of anybody else.
 */

package org.getlwc.bukkit;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.getlwc.Block;
import org.getlwc.Engine;
import org.getlwc.ItemStack;
import org.getlwc.Location;
import org.getlwc.ServerLayer;
import org.getlwc.SimpleEngine;
import org.getlwc.World;
import org.getlwc.bukkit.command.BukkitConsoleCommandSender;
import org.getlwc.bukkit.listeners.BukkitListener;
import org.getlwc.command.CommandContext;
import org.getlwc.command.CommandException;
import org.getlwc.command.CommandSender;
import org.getlwc.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class BukkitPlugin extends JavaPlugin implements Listener {
    private Logger logger = null;

    /**
     * The LWC engine
     */
    private Engine engine;

    /**
     * The server layer
     */
    private final ServerLayer layer = new BukkitServerLayer(this);

    /**
     * Get the LWC engine
     *
     * @return
     */
    public Engine getEngine() {
        return engine;
    }

    /**
     * Wrap a player object to a native version we can work with
     *
     * @param player
     * @return
     */
    public Player wrapPlayer(org.bukkit.entity.Player player) {
        return layer.getPlayer(player.getName());
    }

    /**
     * Get a world
     *
     * @param worldName
     * @return
     */
    public World getWorld(String worldName) {
        return layer.getWorld(worldName);
    }

    /**
     * Called when a player uses a command
     *
     * @param event
     */
    @EventHandler(ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        // Create the internal object
        Player player = wrapPlayer(event.getPlayer());
        String message = event.getMessage();

        // Should we cancel the object?
        if (_onCommand(CommandContext.Type.PLAYER, player, message)) {
            event.setCancelled(true);
        }
    }

    /**
     * Called when the console uses a command.
     * Using LOWEST because of kTriggers
     *
     * @param event
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onServerCommand(ServerCommandEvent event) {
        if (_onCommand(CommandContext.Type.SERVER, engine.getConsoleSender(), event.getCommand())) {
            // TODO how to cancel? just change the command to something else?
        }
    }

    @Override
    public void onEnable() {
        logger = this.getLogger();

        // Create a new LWC engine
        engine = SimpleEngine.getOrCreateEngine(layer, new BukkitServerInfo(), new BukkitConsoleCommandSender(getServer().getConsoleSender()));
        engine.startup();

        // Register events
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new BukkitListener(this), this);

    }

    @Override
    public void onDisable() {
        engine = null;
    }

    /**
     * Command processor
     *
     * @param sender
     * @param message the name of the command followed by any arguments.
     * @return true if the command event should be cancelled
     */
    private boolean _onCommand(CommandContext.Type type, CommandSender sender, String message) {
        // Normalize the command, removing any prepended /, etc
        message = normalizeCommand(message);

        // Separate the command and arguments
        int indexOfSpace = message.indexOf(' ');

        try {
            if (indexOfSpace != -1) {
                String command = message.substring(0, indexOfSpace);
                String arguments = message.substring(indexOfSpace + 1);

                return engine.getCommandHandler().handleCommand(new CommandContext(type, sender, command, arguments));
            } else { // No arguments
                return engine.getCommandHandler().handleCommand(new CommandContext(type, sender, message));
            }
        } catch (CommandException e) {
            // Notify the console
            engine.getConsoleSender().sendTranslatedMessage("An error was encountered while processing a command: {0}", e.getMessage());
            e.printStackTrace();

            // Notify the player / console
            sender.sendTranslatedMessage("&4[LWC] An internal error occurred while processing this command");

            // We failed.. oh we failed
            return false;
        }
    }

    /**
     * Normalize a command, making player and console commands appear to be the same format
     *
     * @param message
     * @return
     */
    private String normalizeCommand(String message) {
        // Remove a prepended /
        if (message.startsWith("/")) {
            if (message.length() == 1) {
                return "";
            } else {
                message = message.substring(1);
            }
        }

        return message.trim();
    }

    /**
     * Cast a location to our native location
     *
     * @param location
     * @return
     */
    public Location castLocation(org.bukkit.Location location) {
        return new Location(getWorld(location.getWorld().getName()), location.getX(), location.getY(), location.getZ());
    }

    /**
     * Cast a list of blocks to our native block list
     *
     * @param world
     * @param list
     * @return
     */
    public List<Block> castBlockList(World world, List<org.bukkit.block.Block> list) {
        List<Block> ret = new ArrayList<Block>();

        for (org.bukkit.block.Block block : list) {
            ret.add(world.getBlockAt(block.getX(), block.getY(), block.getZ()));
        }

        return ret;
    }

    /**
     * Cast a list of state blocks to our native block list
     *
     * @param world
     * @param list
     * @return
     */
    public List<Block> castBlockStateList(World world, List<org.bukkit.block.BlockState> list) {
        List<Block> ret = new ArrayList<Block>();

        for (org.bukkit.block.BlockState block : list) {
            ret.add(world.getBlockAt(block.getX(), block.getY(), block.getZ()));
        }

        return ret;
    }

    /**
     * Cast a map of enchantments to our native enchantment mappings
     *
     * @param enchantments
     * @return
     */
    public Map<Integer, Integer> castEnchantments(Map<org.bukkit.enchantments.Enchantment, Integer> enchantments) {
        Map<Integer, Integer> ret = new HashMap<Integer, Integer>();

        for (Map.Entry<org.bukkit.enchantments.Enchantment, Integer> entry : enchantments.entrySet()) {
            ret.put(entry.getKey().getId(), entry.getValue());
        }

        return ret;
    }

    /**
     * Cast an item stack to our native ItemStack
     * @param item
     * @return
     */
    public ItemStack castItemStack(org.bukkit.inventory.ItemStack item) {
        if (item == null) {
            return null;
        }

        return new ItemStack(item.getTypeId(), item.getAmount(), item.getDurability(), item.getMaxStackSize(), castEnchantments(item.getEnchantments()));
    }

}