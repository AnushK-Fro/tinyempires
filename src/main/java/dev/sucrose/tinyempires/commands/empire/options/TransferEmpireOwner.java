package dev.sucrose.tinyempires.commands.empire.options;

import dev.sucrose.tinyempires.models.Empire;
import dev.sucrose.tinyempires.models.EmpireCommandOption;
import dev.sucrose.tinyempires.models.TEChunk;
import dev.sucrose.tinyempires.models.TEPlayer;
import dev.sucrose.tinyempires.utils.DrawEmpire;
import dev.sucrose.tinyempires.utils.ErrorUtils;
import org.bson.types.ObjectId;
import org.bukkit.*;
import org.bukkit.entity.Player;

import java.util.UUID;

public class TransferEmpireOwner implements EmpireCommandOption {

    @Override
    public void execute(Player sender, String[] args) {
        // /e owner <player>
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "/e owner <player>");
            return;
        }

        final UUID senderUUID = sender.getUniqueId();
        final TEPlayer tePlayer = TEPlayer.getTEPlayer(senderUUID);
        if (tePlayer == null) {
            sender.sendMessage(ChatColor.RED + ErrorUtils.YOU_DO_NOT_EXIST_IN_THE_DATABASE);
            return;
        }

        if (!tePlayer.isInEmpire()) {
            sender.sendMessage(ChatColor.RED + "You must leave your empire before creating one");
            return;
        }

        // no parsing necessary so no try/catch
        String empireName = args[0];
        if (Empire.getEmpire(empireName) != null) {
            sender.sendMessage(ChatColor.RED + String.format(
                "Empire with the name '%s' already exists",
                empireName
            ));
            return;
        }

        final Location location = sender.getLocation();
        final Chunk chunk = location.getChunk();
        final TEChunk teChunk = TEChunk.getChunk(chunk);
        if (teChunk != null) {
            sender.sendMessage(ChatColor.RED + "This chunk is already owned by another empire");
            return;
        }

        // all checks passed, make empire
        try {
            final World world = location.getWorld();
            if (world == null)
                throw new NullPointerException("World found as undefined when fetching player location");
            final ObjectId id = Empire.createEmpire(empireName, tePlayer);
            tePlayer.setEmpireId(id);

            // insert initial empire chunk
            final Empire empire = Empire.getEmpire(id);
            if (empire == null)
                throw new NullPointerException("Unable to fetch newly created empire and establish first chunk");
            TEChunk.createTEChunk(world.getName(), chunk.getX(), chunk.getZ(), empire);
            DrawEmpire.drawChunk(empire, world.getName(), chunk.getX(), chunk.getZ());
        } catch(Exception err) {
            err.printStackTrace();
            sender.sendMessage(ChatColor.RED + "Failed to create new empire, please notify a developer");
            return;
        }

        Bukkit.broadcastMessage(ChatColor.YELLOW + String.format(
            "%s has created the empire of %s!",
            sender.getDisplayName(),
            empireName
        ));
    }

}
