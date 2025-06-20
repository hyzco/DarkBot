package com.github.manolo8.darkbot.core.objects;

import com.github.manolo8.darkbot.core.itf.Updatable;
import com.github.manolo8.darkbot.core.objects.itf.HealthHolder;

public class Health extends Updatable implements HealthHolder, eu.darkbot.api.game.other.Health {

    public int hp;
    public int maxHp;
    public int hull;
    public int maxHull;
    public int shield;
    public int maxShield;

    protected long hpLastIncreased, hpLastDecreased,
            hullLastIncreased, hullLastDecreased,
            shieldLastIncreased, shieldLastDecreased;

    @Override
    public void update() {
        int hpLast = hp, maxHpLast = maxHp,
                hullLast = hp, maxHullLast = maxHp,
                shieldLast = shield, maxShieldLast = maxShield;

        hp = readBindableInt(48); //fixme - they changed packet hp type to `long` so it may overflow integer
        maxHp = readBindableInt(56); // same here
        hull = readBindableInt(64);
        maxHull  = readBindableInt(72);
        shield = readBindableInt(80);
        maxShield = readBindableInt(88);

        checkHealth(hpLast, maxHpLast,
                hullLast, maxHullLast,
                shieldLast, maxShieldLast);
    }

    protected void checkHealth(int hp, int maxHp, int hull, int maxHull, int shield, int maxShield) {
        if (maxHp == this.maxHp && hp != this.hp) {
            if (hp > this.hp) hpLastDecreased = System.currentTimeMillis();
            else hpLastIncreased = System.currentTimeMillis();
        }
        if (maxHull == this.maxHull && hull != this.hull) {
            if (hull > this.hull) hullLastDecreased = System.currentTimeMillis();
            else hullLastIncreased = System.currentTimeMillis();
        }
        if (maxShield == this.maxShield && shield != this.shield) {
            if (shield > this.shield) shieldLastDecreased = System.currentTimeMillis();
            else shieldLastIncreased = System.currentTimeMillis();
        }
    }

    public double hpPercent() {
        return maxHp == 0 ? 1 : ((double) hp / (double) maxHp);
    }

    public double shieldPercent() {
        return maxShield == 0 ? 1 : ((double) shield / (double) maxShield);
    }

    public boolean hpDecreasedIn(int time) {
        return System.currentTimeMillis() - hpLastDecreased < time;
    }

    public boolean hpIncreasedIn(int time) {
        return System.currentTimeMillis() - hpLastIncreased < time;
    }

    public boolean hullDecreasedIn(int time) {
        return System.currentTimeMillis() - hullLastDecreased < time;
    }

    public boolean hullIncreasedIn(int time) {
        return System.currentTimeMillis() - hullLastIncreased < time;
    }

    public boolean shDecreasedIn(int time) {
        return System.currentTimeMillis() - shieldLastDecreased < time;
    }

    public boolean shIncreasedIn(int time) {
        return System.currentTimeMillis() - shieldLastIncreased < time;
    }

    @Override
    public int getHp() {
        return hp;
    }

    @Override
    public int getMaxHp() {
        return maxHp;
    }

    @Override
    public int getHull() {
        return hull;
    }

    @Override
    public int getMaxHull() {
        return maxHull;
    }

    @Override
    public int getShield() {
        return shield;
    }

    @Override
    public int getMaxShield() {
        return maxShield;
    }

    @Override
    public boolean shieldDecreasedIn(int time) {
        return shDecreasedIn(time);
    }

    @Override
    public boolean shieldIncreasedIn(int time) {
        return shIncreasedIn(time);
    }
}
