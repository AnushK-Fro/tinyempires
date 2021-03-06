package dev.sucrose.tinyempires.models;

import com.mongodb.client.MongoCollection;
import dev.sucrose.tinyempires.TinyEmpires;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class TEPlayer {

    final private static Map<UUID, TEPlayer> playerCache = new HashMap<>();

    private static final MongoCollection<Document> collection = TinyEmpires.getDatabase().getCollection("players");

    private final UUID playerUUID;
    private String name;
    private double balance;
    private ObjectId empire; // empire document ID
    private String position;
    private boolean jumpedInAdvancement;
    private String discordId;

    static {
        fillCache();
    }

    public static void writeCache() {
        final List<Document> documents =
            playerCache.values()
                .stream()
                .map(TEPlayer::toDocument)
                .collect(Collectors.toList());
        collection.deleteMany(new Document());
        collection.insertMany(documents);
    }

    public static void fillCache() {
        playerCache.clear();
        for (final Document document : collection.find()) {
            final TEPlayer player = new TEPlayer(document);
            playerCache.put(player.getPlayerUUID(), player);
        }
    }

    public static TEPlayer createPlayer(UUID uuid, String name) {
        final Document document = new Document();
        document.put("uuid", uuid.toString());
        document.put("name", name);
        document.put("balance", 0d);
        document.put("empire", null);
        document.put("position", null);
        document.put("jumped_in", false);
        document.put("discord_id", null);
        collection.insertOne(document);
        playerCache.put(uuid, new TEPlayer(document));
        return getTEPlayer(uuid);
    }

    public static TEPlayer getTEPlayer(String name) {
        for (final TEPlayer p : playerCache.values()) {
            if (p.getName().equals(name))
                return p;
        }
        return null;
    }

    public static TEPlayer getTEPlayerFromDiscordId(String discordId) {
        for (final TEPlayer p : playerCache.values()) {
            if (p.getDiscordId() != null
                    && p.getDiscordId().equals(discordId))
                return p;
        }
        return null;
    }

    public static TEPlayer getTEPlayer(UUID uuid) {
        // check cache for player
        if (playerCache.containsKey(uuid))
            return playerCache.get(uuid);
        return null;
    }

    public TEPlayer(Document document) {
        System.out.println(document.toJson());
        this.playerUUID = UUID.fromString(document.getString("uuid"));
        this.name = document.getString("name");
        this.balance = document.getDouble("balance");
        this.empire = document.getObjectId("empire");
        this.position = document.getString("position");
        this.jumpedInAdvancement = document.getBoolean("jumped_in");
        this.discordId = document.getString("discord_id");
    }

    public Document toDocument() {
        return new Document("uuid", playerUUID)
            .append("name", name)
            .append("balance", balance)
            .append("empire", empire)
            .append("position", position)
            .append("jumped_in", jumpedInAdvancement)
            .append("discord_id", discordId);
    }

    private static final ScoreboardManager manager;
    static {
        manager = Bukkit.getScoreboardManager();
        if (manager == null)
            throw new NullPointerException("Bukkit#getScoreboardManager() returned null on initialization");
    }

    private static String formatSecondsToTime(int seconds) {
        final int minutes = seconds / 60;
        final int secondsLeft = seconds % 60;
        return String.format(
            "%s:%s",
            minutes,
            secondsLeft < 10
                ? "0" + secondsLeft
                : secondsLeft
        );
    }

    public void updatePlayerScoreboard() {
        final Player player = Bukkit.getPlayer(playerUUID);
        if (player == null)
            return;

        final Scoreboard scoreboard = manager.getNewScoreboard();

        final Objective objective = scoreboard.registerNewObjective(
            "title",
            "dummy",
            "" + ChatColor.YELLOW + ChatColor.BOLD + "Pixel Empires"
        );
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        final TEChunk chunk = TEChunk.getChunk(player.getLocation().getChunk());
        int line = 1;

        // website
        objective.getScore(ChatColor.YELLOW + "www.pixelempiresmc.net").setScore(line++);

        // spacing
        objective.getScore("").setScore(line++);

        // chunk type
        if (chunk != null
                && chunk.getType() != ChunkType.NONE)
            objective.getScore("" +
                (chunk.getType() == ChunkType.TEMPLE
                    ? ChatColor.GREEN
                    : ChatColor.GOLD)
                + ChatColor.BOLD + chunk.getType().name()
            ).setScore(line++);

        // location empire
        objective.getScore(chunk == null ? ChatColor.GRAY + "Wilderness" :
            chunk.getEmpire().getChatColor() + chunk.getEmpire().getName()).setScore(line++);

        // location header
        objective.getScore(ChatColor.BOLD + "Location").setScore(line++);

        // spacing, color for uniqueness
        objective.getScore(ChatColor.RED + "").setScore(line++);

        // empire reserve
        if (empire != null) {
            objective.getScore("Reserve: " + ChatColor.GREEN + String.format(
                "%.1f coins",
                getEmpire().getReserve()
            )).setScore(line++);
            objective.getScore("Position: " + (position == null ? ChatColor.GRAY + "Unassigned" :
                ChatColor.GREEN + position)).setScore(line++);
        }

        // player empire
        if (empire == null || getEmpire().getAtWarWith() == null) {
            objective.getScore(empire == null ? ChatColor.GRAY + "Unaffiliated" :
                "Name: " + getEmpire().getChatColor() + getEmpire().getName()).setScore(line++);
            objective.getScore(ChatColor.BOLD + "Empire").setScore(line++);
        }

        if (empire != null) {
            final Empire empire = getEmpire();
            if (empire.isWaitingForWar()) {
                final Empire atWarWith = empire.getAtWarWith();
                objective.getScore(ChatColor.LIGHT_PURPLE + "").setScore(line++);
                objective.getScore(atWarWith.getChatColor() + atWarWith.getName()).setScore(line++);
                objective.getScore("" + ChatColor.YELLOW + ChatColor.BOLD +
                    String.format("War in %s", formatSecondsToTime(empire.getTimeLeftToWar()))
                ).setScore(line++);
            } else if (empire.getAtWarWith() != null) {
                final Empire atWarWith = empire.getAtWarWith();
                objective.getScore(ChatColor.LIGHT_PURPLE + "").setScore(line++);
                objective.getScore(atWarWith.getChatColor() + atWarWith.getName()).setScore(line++);
                objective.getScore("" + ChatColor.RED + ChatColor.BOLD +
                    String.format("War (%s)", formatSecondsToTime(empire.getTimeLeftInWar()))
                ).setScore(line++);
            }
        }

        // spacing, color for uniqueness
        objective.getScore("" + ChatColor.DARK_GREEN).setScore(line++);

        // balance
        objective.getScore("Balance: " + ChatColor.GREEN + String.format("%.1f coins", balance)).setScore(line++);

        // spacing, color for uniqueness
        objective.getScore("" + ChatColor.AQUA).setScore(line);
        player.setScoreboard(scoreboard);
    }

    private void save(Document document) {
        collection.updateOne(new Document("uuid", playerUUID.toString()), new Document("$set", document));
        updatePlayerScoreboard();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        save(new Document("name", name));
    }

    public UUID getPlayerUUID() {
        return playerUUID;
    }

    public double getBalance() {
        return (double) Math.round(balance * 10d) / 10d;
    }

    public void setBalance(double balance) {
        this.balance = balance;
        save(new Document("balance", balance));
    }

    public void giveCoins(double amount) {
        setBalance(balance + amount);
    }

    public void takeCoins(double amount) {
        setBalance(balance - amount);
    }

    public void pay(TEPlayer player, double amount) {
        player.giveCoins(amount);
        takeCoins(amount);
    }

    public Empire getEmpire() {
        return Empire.getEmpire(empire);
    }

    public void setEmpireId(ObjectId id) {
        this.empire = id;
        save(new Document("empire", id));
    }

    public void leaveEmpire() {
        setEmpireId(null);
        setPositionName(null);
    }

    public boolean isInEmpire() {
        return empire != null;
    }

    public String getPositionName() {
        return position;
    }

    public Position getPosition() {
        return getEmpire().getPosition(position);
    }

    public boolean hasPermission(Permission permission) {
        final Position position = getPosition();
        return isOwner() || (position != null && position.hasPermission(permission));
    }

    public void setPositionName(String position) {
        this.position = position;
        save(new Document("position", position));
    }

    public boolean getJumpedInAdvancement() {
        return jumpedInAdvancement;
    }

    public void setJumpInAdvancement(boolean jumpedInAdvancement) {
        this.jumpedInAdvancement = jumpedInAdvancement;
        save(new Document("jumped_in", jumpedInAdvancement));
    }

    public boolean isOwner() {
        return getEmpire().getOwner().equals(playerUUID);
    }

    public String getDiscordId() {
        return discordId;
    }

    public void setDiscordId(String discordId) {
        this.discordId = discordId;
        save(new Document("discord_id", discordId));
    }

}
