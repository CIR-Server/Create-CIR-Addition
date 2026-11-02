package com.cirserver.createciraddition.network;

import com.github.luben.zstd.Zstd;
import com.cirserver.createciraddition.CreateCIRAddition;
import com.cirserver.createciraddition.config.CirConfig;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import java.io.IOException;
import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4FastDecompressor;

public class CompressionUtil {
   public static final int COMPRESSION_THRESHOLD = 1024;
   public static final int ZSTD_THRESHOLD = 51200;
   public static final float BENEFIT_THRESHOLD = 0.9F;
   public static final byte MODE_LZ4 = 1;
   public static final byte MODE_ZSTD = 2;
   private static final LZ4Factory factory = LZ4Factory.fastestInstance();
   private static final LZ4Compressor lz4Compressor = factory.fastCompressor();
   private static final LZ4FastDecompressor lz4Decompressor = factory.fastDecompressor();

   public CompressionUtil() {
   }

   public static boolean shouldCompress(int size) {
      return size >= getCompressionThreshold();
   }

   public static int getCompressionThreshold() {
      try {
         return (Integer)CirConfig.COMPRESSION_THRESHOLD.get();
      } catch (Exception var1) {
         return 1024;
      }
   }

   public static byte selectMode(int size) {
      try {
         int zstdThreshold = (Integer)CirConfig.ZSTD_THRESHOLD.get();
         return (byte)(size >= zstdThreshold ? 2 : 1);
      } catch (Exception var2) {
         return (byte)(size >= 51200 ? 2 : 1);
      }
   }

   public static String getModeName(byte mode) {
      return switch (mode) {
         case 1 -> "LZ4";
         case 2 -> "Zstd";
         default -> "Unknown";
      };
   }

   public static ByteBuf compress(ByteBuf input, byte mode) throws IOException {
      int originalSize = input.readableBytes();
      byte[] inputBytes = new byte[originalSize];
      input.readBytes(inputBytes);
      byte[] compressedBytes;
      int compressedSize;
      if (mode == 2) {
         compressedBytes = Zstd.compress(inputBytes);
         compressedSize = compressedBytes.length;
      } else {
         int maxCompressedSize = lz4Compressor.maxCompressedLength(originalSize);
         compressedBytes = new byte[maxCompressedSize];
         compressedSize = lz4Compressor.compress(inputBytes, 0, originalSize, compressedBytes, 0, maxCompressedSize);
      }

      float benefitThreshold;
      try {
         benefitThreshold = ((Double)CirConfig.BENEFIT_THRESHOLD.get()).floatValue();
      } catch (Exception var8) {
         benefitThreshold = 0.9F;
      }

      if ((float)compressedSize >= (float)originalSize * benefitThreshold) {
         CreateCIRAddition.LOGGER
            .debug("Create CIR Addition: Compression not beneficial, {} -> {} ({}%)", originalSize, compressedSize, compressedSize * 100 / originalSize);
         ByteBuf result = PooledByteBufAllocator.DEFAULT.buffer(4 + originalSize);
         result.writeInt(originalSize);
         result.writeBytes(inputBytes);
         return result;
      } else {
         ByteBuf result = PooledByteBufAllocator.DEFAULT.buffer(4 + compressedSize);
         result.writeInt(originalSize);
         if (mode == 2) {
            result.writeBytes(compressedBytes);
         } else {
            result.writeBytes(compressedBytes, 0, compressedSize);
         }

         return result;
      }
   }

   public static ByteBuf compress(ByteBuf input) throws IOException {
      return compress(input, (byte)1);
   }

   public static ByteBuf decompress(ByteBuf input, byte mode) throws IOException {
      int originalSize = input.readInt();
      int compressedSize = input.readableBytes();
      byte[] compressedBytes = new byte[compressedSize];
      input.readBytes(compressedBytes);
      byte[] decompressedBytes;
      if (mode == 2) {
         decompressedBytes = Zstd.decompress(compressedBytes, originalSize);
      } else {
         decompressedBytes = new byte[originalSize];
         lz4Decompressor.decompress(compressedBytes, 0, decompressedBytes, 0, originalSize);
      }

      return Unpooled.wrappedBuffer(decompressedBytes);
   }

   public static ByteBuf decompress(ByteBuf input) throws IOException {
      return decompress(input, (byte)1);
   }
}
