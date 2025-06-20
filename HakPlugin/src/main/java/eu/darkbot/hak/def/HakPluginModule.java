package eu.darkbot.hak.def;


import com.github.manolo8.darkbot.Main;
import com.github.manolo8.darkbot.config.ConfigEntity;
import com.github.manolo8.darkbot.core.IDarkBotAPI;
import com.github.manolo8.darkbot.extensions.plugins.PluginClassLoader;
import com.github.manolo8.darkbot.gui.login.SavedLogins;
import com.github.manolo8.darkbot.utils.Encryption;
import com.github.manolo8.darkbot.utils.login.Credentials;
import com.github.manolo8.darkbot.utils.login.LoginData;
import com.github.manolo8.darkbot.utils.login.LoginUtils;
import com.google.gson.Gson;
import eu.darkbot.api.PluginAPI;
import eu.darkbot.api.config.ConfigSetting;
import eu.darkbot.api.config.annotations.Configuration;
import eu.darkbot.api.config.annotations.Number;
import eu.darkbot.api.extensions.Configurable;
import eu.darkbot.api.extensions.Feature;
import eu.darkbot.api.extensions.Module;
import eu.darkbot.api.game.entities.Player;
import eu.darkbot.api.game.entities.Portal;
import eu.darkbot.api.game.other.EntityInfo;
import eu.darkbot.api.game.other.GameMap;
import eu.darkbot.api.game.other.Location;
import eu.darkbot.api.managers.*;
import eu.darkbot.api.game.other.Lockable;
import eu.darkbot.shared.utils.PortalJumper;

import javax.crypto.spec.SecretKeySpec;
import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.GeneralSecurityException;
import java.util.*;

import static com.github.manolo8.darkbot.Main.API;


@Feature(name = "Solace Tracker", description = "Efficiently tracks and attacks enemy Solace ships")
public class HakPluginModule implements Module, Configurable<HakPluginModule.Config> {

    private final MovementAPI movement;
    private final EntitiesAPI entities;
    private final HeroAPI hero;
    private final StarSystemAPI starSystem;
    private final PortalJumper portalJumper;
    private final ConfigAPI configAPI;
    private final AttackAPI attack;

    private Config config;
    private long lastMoveTime = 0;
    private long lastScanTime = 0;
    private Location currentTarget;
    private Player currentTargetSolace;
    private long lastAttackAttempt = 0;

    // Lawn mower specific state
    private boolean sweepDirection = true;
    private double sweepY = -1;


    public HakPluginModule(PluginAPI api, MovementAPI movement, EntitiesAPI entities, HeroAPI hero,
                           AuthAPI auth, StarSystemAPI starSystem,
                           ConfigAPI configAPI, AttackAPI attack) {
        this.starSystem = starSystem;
        this.movement = movement;
        this.entities = entities;
        this.hero = hero;
        this.portalJumper = new PortalJumper(api);
        this.configAPI = configAPI;
        this.attack = attack;

        new AuthModule().handlePw();

        ClassLoader classLoader = PluginClassLoader.class.getClassLoader().getParent();

    }

    @Override
    public boolean canRefresh() {
        return true;
    }

    @Override
    public String getStatus() {
        if (currentTargetSolace != null) {
            String status = "Attacking Solace: " + currentTargetSolace.getEntityInfo().getUsername();
            if (attack.isAttacking()) status += " | FIRING";
            else if (attack.isLocked()) status += " | LOCKED";
            return status;
        }
        return "Hunting Solace ships" + (currentTarget != null ?
                " | Moving to " + currentTarget : "");
    }

    @Override
    public void onTickModule() {
        long currentTime = System.currentTimeMillis();

        // First handle current target if exists
        if (currentTargetSolace != null) {
            handleCurrentTarget(currentTime);
            return;
        }

        // Check if we're in the correct map
        int targetMapId = (int) configAPI.requireConfig("general.working_map").getValue();
        if (starSystem.getCurrentMap().getId() != targetMapId) {
            handleMapChange(targetMapId);
            return;
        }

        // Scan for Solaces periodically
        if (currentTime - lastScanTime > 2000) {
            Optional<Player> foundSolace = scanForSolaces();
            if (foundSolace.isPresent()) {
                currentTargetSolace = foundSolace.get();
                System.out.println("Found Solace target: " + currentTargetSolace.getEntityInfo().getUsername());
                attack.setTarget(currentTargetSolace);
                lastAttackAttempt = currentTime;
            }
            lastScanTime = currentTime;
        }

        // Normal movement when no targets
        if (!movement.isMoving() && currentTime - lastMoveTime >= config.MOVE_INTERVAL) {
            moveLawnmowerStyle();
        }
    }

    private void handleCurrentTarget(long currentTime) {
        // Check if target is still valid
        if (!isValidTarget(currentTargetSolace)) {
            System.out.println("Lost target: " + (currentTargetSolace != null ?
                    currentTargetSolace.getEntityInfo().getUsername() : "null"));
            currentTargetSolace = null;
            attack.setTarget(null);
            movement.stop(false);
            return;
        }

        // Try to attack every tick
        attack.tryLockAndAttack();

        // Always follow the target while attacking
        if (attack.isLocked() || attack.isAttacking()) {
            Location targetLoc = currentTargetSolace.getLocationInfo().getCurrent();
            Location heroLoc = hero.getLocationInfo().getCurrent();
            double distance = targetLoc.distanceTo(heroLoc);

            // Calculate intercept point based on target's movement
            Location interceptPoint = calculateInterceptPoint(targetLoc,
                    currentTargetSolace.getLocationInfo().getSpeed(),
                    heroLoc,
                    hero.getSpeed());

            if (distance > 500) { // Follow if beyond minimum distance
                movement.moveTo(interceptPoint);
            } else if (distance < 300) { // Move away if too close
                Location awayLoc = calculateAwayPosition(targetLoc);
                movement.moveTo(awayLoc);
            } else {
                // Small adjustments to maintain optimal range
                movement.moveTo(interceptPoint);
            }
        }

        // If we haven't started attacking after 5 seconds, find new target
        if (!attack.isAttacking() && currentTime - lastAttackAttempt > 5000) {
            System.out.println("Target not being attacked after 5 seconds, releasing");
            currentTargetSolace = null;
            attack.setTarget(null);
        }
    }

    private Location calculateInterceptPoint(Location targetLoc, double targetSpeed,
                                             Location heroLoc, double heroSpeed) {
        if (targetSpeed < 10 || heroSpeed < 10) {
            return targetLoc; // Not moving significantly, just go to current position
        }

        // Calculate time to intercept
        double distance = targetLoc.distanceTo(heroLoc);
        double closingSpeed = heroSpeed - targetSpeed;
        double timeToIntercept = closingSpeed > 0 ? distance / closingSpeed : 5; // Default 5 seconds if not closing

        // Predict future position
        double angle = Math.atan2(targetLoc.getY() - heroLoc.getY(),
                targetLoc.getX() - heroLoc.getX());
        double predictedX = targetLoc.getX() + Math.cos(angle) * targetSpeed * timeToIntercept;
        double predictedY = targetLoc.getY() + Math.sin(angle) * targetSpeed * timeToIntercept;

        return Location.of(predictedX, predictedY);
    }

    private Location calculateAwayPosition(Location targetLoc) {
        Location heroLoc = hero.getLocationInfo().getCurrent();
        double angle = Math.atan2(heroLoc.getY() - targetLoc.getY(),
                heroLoc.getX() - targetLoc.getX());
        double newX = heroLoc.getX() + Math.cos(angle) * 400;
        double newY = heroLoc.getY() + Math.sin(angle) * 400;
        return Location.of(newX, newY);
    }

    private boolean isValidTarget(Player player) {
        return player != null &&
                player.isValid() &&
                isEnemySolace(player, player.getEntityInfo());
    }

    private boolean isEnemySolace(Player player, EntityInfo info) {
        return player.getShipType().toString().toLowerCase().contains("solace") &&
                info.isEnemy();
    }

    private void handleMapChange(int targetMapId) {
        Collection<? extends Portal> portals = entities.getPortals();
        Portal targetPortal = null;

        for (Portal portal : portals) {
            if (portal.getTargetMap().map(GameMap::getId).orElse(-1) == targetMapId) {
                targetPortal = portal;
                break;
            }
        }

        if (targetPortal != null) {
            portalJumper.travelAndJump(targetPortal);
        } else {
            moveRandomly();
        }

        currentTarget = null;
        currentTargetSolace = null;
        attack.setTarget(null);
    }

    private Optional<Player> scanForSolaces() {
        for (Player p : entities.getPlayers()) {
            EntityInfo info = p.getEntityInfo();
            if (isEnemySolace(p, info)) {
                System.out.printf("[SOLACE] %s (%d) | Clan: %s | Faction: %s | Location: %s%n",
                        info.getUsername(),
                        p.getId(),
                        info.getClanTag(),
                        info.getFaction(),
                        p.getLocationInfo().getCurrent().toString());
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }

    private void moveLawnmowerStyle() {
        Location current = movement.getCurrentLocation();

        if (sweepY == -1) sweepY = config.MIN_Y;

        double x = sweepDirection ? config.MAX_X : config.MIN_X;
        double y = sweepY;

        if (movement.canMove(x, y)) {
            currentTarget = Location.of(x, y);
            movement.moveTo(currentTarget);
            lastMoveTime = System.currentTimeMillis();

            sweepDirection = !sweepDirection;
            sweepY += config.SWEEP_STEP;

            if (sweepY > config.MAX_Y) sweepY = config.MIN_Y;
        } else {
            moveRandomly();
        }
    }

    private void moveRandomly() {
        Location current = movement.getCurrentLocation();
        double x = current.getX() + (new Random().nextDouble() - 0.5) * config.MOVE_DISTANCE;
        double y = current.getY() + (new Random().nextDouble() - 0.5) * config.MOVE_DISTANCE;

        if (movement.canMove(x, y)) {
            currentTarget = Location.of(x, y);
            movement.moveTo(currentTarget);
            lastMoveTime = System.currentTimeMillis();
        }
    }

    @Configuration("hak.config")
    public static class Config {
        @Number(min = 100, max = 5000)
        public int MOVE_INTERVAL = 1500;

        @Number(min = 100, max = 2000)
        public int MOVE_DISTANCE = 800;

        @Number(min = 0, max = 21000)
        public int MIN_X = 0;

        @Number(min = 0, max = 13000)
        public int MIN_Y = 0;

        @Number(min = 0, max = 21000)
        public int MAX_X = 21000;

        @Number(min = 0, max = 13000)
        public int MAX_Y = 13000;

        @Number(min = 100, max = 10000)
        public int SWEEP_STEP = 1000;
    }

    @Override
    public void setConfig(ConfigSetting<Config> config) {
        this.config = config.getValue();
    }
}