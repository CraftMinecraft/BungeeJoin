package net.craftminecraft.bukkit.ipwhitelist;

import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;

import com.google.common.reflect.ClassPath;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Arrays;
import java.util.logging.Level;

import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.metadata.FixedMetadataValue;

public class PlayerListener implements Listener {

    IPWhitelist plugin;

    public PlayerListener(IPWhitelist plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = false, priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent ev) {
        try {
            Object entityPlayer = invokeMethod("getHandle", ev.getPlayer());
            Object playerConnection = getField("playerConnection", entityPlayer);
            Object networkManager = getField("networkManager", playerConnection);
            InetAddress addr = null;
            try { // spigot
                addr = ((Socket) invokeMethod("getSocket", networkManager)).getInetAddress();
            } catch (NoSuchMethodException ex) { // bukkit or older versions of spigot.
                if (fieldExists("socket", networkManager))
                    addr = ((Socket)getField("socket", networkManager)).getInetAddress();
                else if (fieldExists("k", networkManager)) {
                    Object channel = getField("k", networkManager);
                    addr = ((InetSocketAddress)invokeMethod("remoteAddress", networkManager)).getAddress();
                }
            }
            if (!this.plugin.allow(addr)) {
                ev.setJoinMessage(null);
                if (ev.getPlayer().getMetadata("IPWhitelist_kick").isEmpty()) { // we only need one metadata. If for some reason it didn't get removed, no need to re-add it.
                    ev.getPlayer().setMetadata("IPWhitelist_kick", new FixedMetadataValue(plugin, true));
                }
                ev.getPlayer().kickPlayer(ChatColor.translateAlternateColorCodes('&', this.plugin.getConfig().getString("playerKickMessage")));
            }
        } catch (InvocationTargetException ex) {
            plugin.getLogger().log(Level.SEVERE, null, ex.getCause());
        } catch (Exception ex) {
            plugin.getLogger().log(Level.SEVERE, null, ex);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerKick(PlayerKickEvent ev) {
        if (!ev.getPlayer().getMetadata("IPWhitelist_kick").isEmpty()) {
            ev.setCancelled(false); // Force the kick to happen.
            ev.setLeaveMessage(null);
            ev.getPlayer().removeMetadata("IPWhitelist_kick", plugin);
        }
    }

    public boolean fieldExists(String fieldname, Object obj)
    {
        for (Field f : obj.getClass().getFields()) {
            if (f.getName().equals(fieldname)) {
                return true;
            }
        }
        return false;
    }
    public Object getField(String fieldname, Object obj) throws NoSuchFieldException, IllegalArgumentException, IllegalAccessException {
        Field f = obj.getClass().getField(fieldname);
        f.setAccessible(true);
        return f.get(obj);
    }

    public boolean methodExists(String methodname, Object obj, Object... args) {
        Class[] paramTypes = new Class[args.length];
        for (int i = 0; i < args.length; i++) {
            paramTypes[i] = args.getClass();
        }
        
        for (Method m : obj.getClass().getMethods())
        {
            if (m.getName().equals(methodname) && Arrays.equals(m.getParameterTypes(), paramTypes))
                return true;
        }
        return false;
    }
    
    public Object invokeMethod(String methodname, Object obj, Object... args) throws NoSuchMethodException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        Class[] paramTypes = new Class[args.length];
        for (int i = 0; i < args.length; i++) {
            paramTypes[i] = args.getClass();
        }
        Method m = obj.getClass().getMethod(methodname, paramTypes);
        m.setAccessible(true);
        return m.invoke(obj, args);
    }
}