/*
 * ProjectE 1.19.2 port - Fabric implementation of the Forge API shapes ProjectE uses.
 * Copyright (c) 2026 ProjectE 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.event.level;

import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import ports.projecte.lib.eventbus.api.Cancelable;
import ports.projecte.lib.eventbus.api.Event;

public class ExplosionEvent extends Event {

    private final Level level;
    private final Explosion explosion;

    public ExplosionEvent(Level level, Explosion explosion) {
        this.level = level;
        this.explosion = explosion;
    }

    public Level getLevel() {
        return level;
    }

    public Explosion getExplosion() {
        return explosion;
    }

    @Cancelable
    public static class Start extends ExplosionEvent {

        public Start(Level level, Explosion explosion) {
            super(level, explosion);
        }

        @Override
        public boolean isCancelable() {
            return true;
        }
    }

    public static class Detonate extends ExplosionEvent {

        private final List<Entity> entityList;

        public Detonate(Level level, Explosion explosion, List<Entity> entityList) {
            super(level, explosion);
            this.entityList = entityList;
        }

        public List<Entity> getAffectedEntities() {
            return entityList;
        }
    }
}
