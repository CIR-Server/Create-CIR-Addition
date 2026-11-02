package com.cirserver.createciraddition.stats;

import java.util.concurrent.atomic.AtomicLong;

public class CompressionStats {
   private static final CompressionStats INSTANCE = new CompressionStats();
   private final AtomicLong totalPackets = new AtomicLong(0L);
   private final AtomicLong totalOriginalBytes = new AtomicLong(0L);
   private final AtomicLong totalCompressedBytes = new AtomicLong(0L);
   private final AtomicLong totalCompressionTimeNanos = new AtomicLong(0L);
   private final AtomicLong lz4Packets = new AtomicLong(0L);
   private final AtomicLong lz4OriginalBytes = new AtomicLong(0L);
   private final AtomicLong lz4CompressedBytes = new AtomicLong(0L);
   private final AtomicLong zstdPackets = new AtomicLong(0L);
   private final AtomicLong zstdOriginalBytes = new AtomicLong(0L);
   private final AtomicLong zstdCompressedBytes = new AtomicLong(0L);

   public CompressionStats() {
   }

   public static CompressionStats get() {
      return INSTANCE;
   }

   public void recordCompression(int originalSize, int compressedSize, long durationNanos, byte mode) {
      this.totalPackets.incrementAndGet();
      this.totalOriginalBytes.addAndGet((long)originalSize);
      this.totalCompressedBytes.addAndGet((long)compressedSize);
      this.totalCompressionTimeNanos.addAndGet(durationNanos);
      if (mode == 1) {
         this.lz4Packets.incrementAndGet();
         this.lz4OriginalBytes.addAndGet((long)originalSize);
         this.lz4CompressedBytes.addAndGet((long)compressedSize);
      } else if (mode == 2) {
         this.zstdPackets.incrementAndGet();
         this.zstdOriginalBytes.addAndGet((long)originalSize);
         this.zstdCompressedBytes.addAndGet((long)compressedSize);
      }
   }

   public long getTotalPackets() {
      return this.totalPackets.get();
   }

   public long getTotalSaved() {
      return this.totalOriginalBytes.get() - this.totalCompressedBytes.get();
   }

   public double getAverageRatio() {
      long compressed = this.totalCompressedBytes.get();
      return compressed == 0L ? 1.0 : (double)this.totalOriginalBytes.get() / (double)compressed;
   }

   public double getAverageCompressionTimeMs() {
      long packets = this.totalPackets.get();
      return packets == 0L ? 0.0 : (double)this.totalCompressionTimeNanos.get() / 1000000.0 / (double)packets;
   }

   public long getLz4Packets() {
      return this.lz4Packets.get();
   }

   public long getLz4Saved() {
      return this.lz4OriginalBytes.get() - this.lz4CompressedBytes.get();
   }

   public double getLz4Ratio() {
      long compressed = this.lz4CompressedBytes.get();
      return compressed == 0L ? 1.0 : (double)this.lz4OriginalBytes.get() / (double)compressed;
   }

   public long getZstdPackets() {
      return this.zstdPackets.get();
   }

   public long getZstdSaved() {
      return this.zstdOriginalBytes.get() - this.zstdCompressedBytes.get();
   }

   public double getZstdRatio() {
      long compressed = this.zstdCompressedBytes.get();
      return compressed == 0L ? 1.0 : (double)this.zstdOriginalBytes.get() / (double)compressed;
   }

   public void reset() {
      this.totalPackets.set(0L);
      this.totalOriginalBytes.set(0L);
      this.totalCompressedBytes.set(0L);
      this.totalCompressionTimeNanos.set(0L);
      this.lz4Packets.set(0L);
      this.lz4OriginalBytes.set(0L);
      this.lz4CompressedBytes.set(0L);
      this.zstdPackets.set(0L);
      this.zstdOriginalBytes.set(0L);
      this.zstdCompressedBytes.set(0L);
   }
}
