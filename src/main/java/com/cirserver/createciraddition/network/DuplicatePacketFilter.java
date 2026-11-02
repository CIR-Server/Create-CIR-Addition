package com.cirserver.createciraddition.network;

import com.cirserver.createciraddition.CreateCIRAddition;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class DuplicatePacketFilter {
   private static final Map<Class<?>, Integer> lastPacketHash = new ConcurrentHashMap<>();
   private static final Map<Class<?>, Integer> consecutiveCount = new ConcurrentHashMap<>();
   private static final Map<Class<?>, Long> lastSendTime = new ConcurrentHashMap<>();
   private static final long TIMEOUT_MS = 500L;
   private static final int MAX_CONSECUTIVE = 10;
   private static final AtomicLong filteredCount = new AtomicLong(0L);
   private static final AtomicLong totalCount = new AtomicLong(0L);

   public DuplicatePacketFilter() {
   }

   public static boolean isDuplicate(Object packet, int contentHash) {
      if (packet == null) {
         return false;
      } else {
         Class<?> packetClass = packet.getClass();
         totalCount.incrementAndGet();
         Integer lastHash = lastPacketHash.get(packetClass);
         Long lastTime = lastSendTime.get(packetClass);
         long now = System.currentTimeMillis();
         if (lastHash == null) {
            updateCache(packetClass, contentHash, now);
            return false;
         } else if (lastTime != null && now - lastTime > 500L) {
            updateCache(packetClass, contentHash, now);
            return false;
         } else if (lastHash == contentHash) {
            int count = consecutiveCount.getOrDefault(packetClass, 0) + 1;
            consecutiveCount.put(packetClass, count);
            if (count >= 10) {
               updateCache(packetClass, contentHash, now);
               CreateCIRAddition.LOGGER.debug("Create CIR Addition: Force send after {} consecutive duplicates", count);
               return false;
            } else {
               filteredCount.incrementAndGet();
               return true;
            }
         } else {
            updateCache(packetClass, contentHash, now);
            return false;
         }
      }
   }

   private static void updateCache(Class<?> packetClass, int hash, long time) {
      lastPacketHash.put(packetClass, hash);
      lastSendTime.put(packetClass, time);
      consecutiveCount.put(packetClass, 0);
   }

   public static int computeHash(byte[] data) {
      if (data != null && data.length != 0) {
         int hash = 1;

         for (byte b : data) {
            hash = 31 * hash + b;
         }

         return hash;
      } else {
         return 0;
      }
   }

   public static String getStats() {
      long total = totalCount.get();
      if (total == 0L) {
         return "No packets processed";
      } else {
         long filtered = filteredCount.get();
         float rate = (float)filtered / (float)total * 100.0F;
         return String.format("Filtered: %d / %d (%.1f%%)", filtered, total, rate);
      }
   }

   public static void resetStats() {
      filteredCount.set(0L);
      totalCount.set(0L);
      lastPacketHash.clear();
      consecutiveCount.clear();
      lastSendTime.clear();
   }

   public static long getFilteredCount() {
      return filteredCount.get();
   }

   public static long getTotalCount() {
      return totalCount.get();
   }
}
