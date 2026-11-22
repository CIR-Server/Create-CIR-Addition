package com.cirserver.createciraddition.boss;

final class BerserkState {
    final float initialMaxHealth;
    long startedAt;
    long berserkUntil;
    long readyAt;
    long nextRingAt;
    boolean halfHealth;
    boolean hasTriggered;
    BerserkState(float initialMaxHealth) { this.initialMaxHealth = initialMaxHealth; }
    boolean active(long now) { return hasTriggered && now - berserkUntil < 0; }
    boolean ready(long now) { return !hasTriggered || now - readyAt >= 0; }
}
