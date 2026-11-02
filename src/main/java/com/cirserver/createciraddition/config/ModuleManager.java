package com.cirserver.createciraddition.config;

import com.cirserver.createciraddition.CreateCIRAddition;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ModuleManager {
   private static final Map<String, Boolean> modules = new ConcurrentHashMap<>();

   public ModuleManager() {
   }

   public static boolean isEnabled(String module) {
      return modules.getOrDefault(module, false);
   }

   public static void setEnabled(String module, boolean enabled) {
      modules.put(module, enabled);
      CreateCIRAddition.LOGGER.info("Create CIR Addition: Module '{}' is now {}", module, enabled ? "ENABLED" : "DISABLED");
   }

   public static boolean toggle(String module) {
      boolean current = isEnabled(module);
      setEnabled(module, !current);
      return !current;
   }

   public static Map<String, Boolean> getAllModules() {
      return new ConcurrentHashMap<>(modules);
   }

   public static void enableAll() {
      modules.keySet().forEach(m -> modules.put(m, true));
      CreateCIRAddition.LOGGER.info("Create CIR Addition: All modules ENABLED");
   }

   public static void disableAll() {
      modules.keySet().forEach(m -> modules.put(m, false));
      CreateCIRAddition.LOGGER.info("Create CIR Addition: All modules DISABLED");
   }

   static {
      modules.put("network", true);
      modules.put("create", true);
   }
}
